#!/bin/bash
set -euo pipefail

BUILD_ROOT="${HOME}/nginx-forward-build"
STAGING="${BUILD_ROOT}/staging"
PREFIX="/opt/forward-platform/nginx"
NGINX_VER="1.26.2"
APP_ROOT="/home/ubuntu/server/forwardplatform"

echo "=== 检查系统 nginx 不受影响 ==="
systemctl is-active nginx 2>/dev/null || true
pgrep -a nginx | head -5 || true

echo "=== 安装编译依赖（不修改系统 nginx 配置）==="
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq build-essential libpcre2-dev zlib1g-dev libssl-dev wget tar patchelf > /dev/null

echo "=== 准备源码目录 ==="
mkdir -p "${BUILD_ROOT}"
cd "${BUILD_ROOT}"
if [ ! -f "nginx-${NGINX_VER}.tar.gz" ]; then
  wget -q "https://nginx.org/download/nginx-${NGINX_VER}.tar.gz"
fi
rm -rf "nginx-${NGINX_VER}"
tar -xzf "nginx-${NGINX_VER}.tar.gz"
cd "nginx-${NGINX_VER}"

echo "=== 配置编译（独立安装前缀）==="
./configure \
  --prefix="${PREFIX}" \
  --sbin-path="${PREFIX}/sbin/nginx" \
  --modules-path="${PREFIX}/modules" \
  --conf-path="${PREFIX}/conf/nginx.conf" \
  --error-log-path="${PREFIX}/logs/error.log" \
  --http-log-path="${PREFIX}/logs/access.log" \
  --pid-path="${PREFIX}/logs/nginx.pid" \
  --lock-path="${PREFIX}/logs/nginx.lock" \
  --with-http_ssl_module \
  --with-http_v2_module \
  --with-http_gzip_static_module \
  --with-http_realip_module \
  --with-threads \
  --with-file-aio

echo "=== 编译 ==="
make -j"$(nproc)"

echo "=== 安装到 staging（不写入 /opt）==="
rm -rf "${STAGING}"
make install DESTDIR="${STAGING}"

INSTALL_DIR="${STAGING}${PREFIX}"
NGINX_BIN="${INSTALL_DIR}/sbin/nginx"

echo "=== 打包运行时依赖库 ==="
LIB_DIR="${INSTALL_DIR}/lib"
mkdir -p "${LIB_DIR}"

copy_lib() {
  local name="$1"
  local path
  path="$(ldd "${NGINX_BIN}" | awk -v n="${name}" '$1 ~ n {print $3; exit}')"
  if [ -n "${path}" ] && [ -f "${path}" ]; then
    cp -L "${path}" "${LIB_DIR}/"
  fi
}

copy_lib "libpcre2-8.so"
copy_lib "libz.so"
copy_lib "libssl.so"
copy_lib "libcrypto.so"

patchelf --set-rpath '$ORIGIN/../lib' "${NGINX_BIN}" 2>/dev/null || true

echo "=== 写入 ForwardPlantform 专用配置 ==="
mkdir -p "${INSTALL_DIR}/conf" "${INSTALL_DIR}/logs" "${INSTALL_DIR}/html"
cat > "${INSTALL_DIR}/conf/nginx.conf" <<'NGINX_CONF'
worker_processes auto;
pid logs/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    access_log    logs/access.log;
    error_log     logs/error.log;
    sendfile      on;
    keepalive_timeout 65;
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    server {
        listen 30011;
        server_name _;

        root __APP_ROOT__/www;
        index index.html;

        location / {
            try_files $uri $uri/ /index.html;
        }

        location /api/ {
            proxy_pass http://127.0.0.1:30010/api/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 300s;
            proxy_read_timeout 300s;
        }
    }
}
NGINX_CONF

sed -i "s|__APP_ROOT__|${APP_ROOT}|g" "${INSTALL_DIR}/conf/nginx.conf"

cat > "${INSTALL_DIR}/sbin/start-nginx.sh" <<'START_SCRIPT'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
export LD_LIBRARY_PATH="${BASE}/lib:${LD_LIBRARY_PATH:-}"
cd "${BASE}"
mkdir -p logs
if [ -f logs/nginx.pid ] && kill -0 "$(cat logs/nginx.pid)" 2>/dev/null; then
  echo "nginx 已在运行，PID=$(cat logs/nginx.pid)"
  exit 0
fi
"${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -t
"${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf"
echo "nginx 已启动，监听 30011"
START_SCRIPT

cat > "${INSTALL_DIR}/sbin/stop-nginx.sh" <<'STOP_SCRIPT'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
export LD_LIBRARY_PATH="${BASE}/lib:${LD_LIBRARY_PATH:-}"
if [ -f "${BASE}/logs/nginx.pid" ]; then
  "${BASE}/sbin/nginx" -p "${BASE}" -c "${BASE}/conf/nginx.conf" -s quit || true
  echo "nginx 已停止"
else
  echo "未找到 PID 文件"
fi
STOP_SCRIPT

cat > "${INSTALL_DIR}/sbin/reload-nginx.sh" <<'RELOAD_SCRIPT'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
export LD_LIBRARY_PATH="${BASE}/lib:${LD_LIBRARY_PATH:-}"
"${BASE}/sbin/nginx" -p "${BASE}" -c "${BASE}/conf/nginx.conf" -s reload
echo "nginx 已重载"
RELOAD_SCRIPT

chmod +x "${INSTALL_DIR}/sbin/"*.sh

cat > "${INSTALL_DIR}/BUILD_INFO.txt" <<INFO
nginx version: ${NGINX_VER}
build host: $(hostname)
build date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
build os: $(. /etc/os-release && echo "${PRETTY_NAME}")
arch: $(uname -m)
glibc: $(ldd --version | head -1)
install prefix on target: ${PREFIX}
app root in config: ${APP_ROOT}
listen port: 30011
backend proxy: 127.0.0.1:30010
INFO

echo "=== 验证二进制 ==="
mkdir -p "${INSTALL_DIR}/logs"
export LD_LIBRARY_PATH="${LIB_DIR}"
"${NGINX_BIN}" -V 2>&1 | head -3
"${NGINX_BIN}" -t -p "${INSTALL_DIR}" -e "${INSTALL_DIR}/logs/error.log" -c "${INSTALL_DIR}/conf/nginx.conf"

echo "=== 打包 tar.gz ==="
cd "${STAGING}"
TAR_NAME="nginx-forward-${NGINX_VER}-linux-$(uname -m).tar.gz"
tar -czf "${BUILD_ROOT}/${TAR_NAME}" -C "${STAGING}" opt/forward-platform/nginx
ls -lh "${BUILD_ROOT}/${TAR_NAME}"

echo "=== 再次确认系统 nginx 仍在运行 ==="
systemctl is-active nginx
ss -lntp | grep -E ':80|:443' || true

echo "BUILD_OK ${BUILD_ROOT}/${TAR_NAME}"
