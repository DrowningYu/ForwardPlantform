#!/bin/bash
set -euo pipefail

BUILD_ROOT="${HOME}/nginx-forward-build-aarch64"
DEPS="${BUILD_ROOT}/deps-aarch64"
STAGING="${BUILD_ROOT}/staging"
PREFIX="/opt/forward-platform/nginx"
NGINX_VER="1.26.2"
ZLIB_VER="1.3.1"
PCRE2_VER="10.44"
OPENSSL_VER="3.0.15"
APP_ROOT="/home/ubuntu/server/forwardplatform"
export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++
export AR=aarch64-linux-gnu-ar
export RANLIB=aarch64-linux-gnu-ranlib

echo "=== 安装交叉编译器（仅 x86 包，不装 arm64 架构）==="
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq build-essential gcc-aarch64-linux-gnu g++-aarch64-linux-gnu wget tar file cmake perl > /dev/null

mkdir -p "${BUILD_ROOT}"
cd "${BUILD_ROOT}"

fetch() {
  local url="$1"
  local file
  file="$(basename "$url")"
  [ -f "${file}" ] || wget -q "${url}"
}

echo "=== 编译 zlib (aarch64 static) ==="
fetch "https://zlib.net/fossils/zlib-${ZLIB_VER}.tar.gz"
rm -rf "zlib-${ZLIB_VER}"
tar -xzf "zlib-${ZLIB_VER}.tar.gz"
cd "zlib-${ZLIB_VER}"
./configure --prefix="${DEPS}" --static
make -j"$(nproc)" CC="${CC}"
make install
cd "${BUILD_ROOT}"

echo "=== 编译 OpenSSL (aarch64 static) ==="
fetch "https://www.openssl.org/source/openssl-${OPENSSL_VER}.tar.gz"
rm -rf "openssl-${OPENSSL_VER}"
tar -xzf "openssl-${OPENSSL_VER}.tar.gz"
cd "openssl-${OPENSSL_VER}"
./Configure linux-aarch64 --prefix="${DEPS}" --cross-compile-prefix=aarch64-linux-gnu- no-shared no-tests
make -j"$(nproc)"
make install_sw
cd "${BUILD_ROOT}"

echo "=== 编译 PCRE2 (aarch64 static) ==="
fetch "https://github.com/PCRE2Project/pcre2/releases/download/pcre2-${PCRE2_VER}/pcre2-${PCRE2_VER}.tar.gz"
rm -rf "pcre2-${PCRE2_VER}"
tar -xzf "pcre2-${PCRE2_VER}.tar.gz"
mkdir -p "pcre2-${PCRE2_VER}/build"
cd "pcre2-${PCRE2_VER}/build"
cmake .. \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER="${CC}" \
  -DCMAKE_INSTALL_PREFIX="${DEPS}" \
  -DBUILD_SHARED_LIBS=OFF \
  -DPCRE2_BUILD_PCRE2_8=ON \
  -DPCRE2_BUILD_PCRE2_16=OFF \
  -DPCRE2_BUILD_PCRE2_32=OFF
make -j"$(nproc)"
make install
cd "${BUILD_ROOT}"

echo "=== 编译 nginx (aarch64) ==="
fetch "https://nginx.org/download/nginx-${NGINX_VER}.tar.gz"
rm -rf "nginx-${NGINX_VER}"
tar -xzf "nginx-${NGINX_VER}.tar.gz"
cd "nginx-${NGINX_VER}"

./configure \
  --prefix="${PREFIX}" \
  --sbin-path="${PREFIX}/sbin/nginx" \
  --modules-path="${PREFIX}/modules" \
  --conf-path="${PREFIX}/conf/nginx.conf" \
  --error-log-path="${PREFIX}/logs/error.log" \
  --http-log-path="${PREFIX}/logs/access.log" \
  --pid-path="${PREFIX}/logs/nginx.pid" \
  --lock-path="${PREFIX}/logs/nginx.lock" \
  --crossbuild=Linux::aarch64 \
  --with-cc="${CC}" \
  --with-cpp="${CC} -E" \
  --with-zlib="${DEPS}" \
  --with-openssl="${DEPS}" \
  --with-pcre="${DEPS}" \
  --with-cc-opt="-I${DEPS}/include -static" \
  --with-ld-opt="-L${DEPS}/lib -static -latomic" \
  --with-http_ssl_module \
  --with-http_v2_module \
  --with-http_gzip_static_module \
  --with-http_realip_module \
  --with-threads \
  --with-file-aio

make -j"$(nproc)"
rm -rf "${STAGING}"
make install DESTDIR="${STAGING}"

INSTALL_DIR="${STAGING}${PREFIX}"
NGINX_BIN="${INSTALL_DIR}/sbin/nginx"
file "${NGINX_BIN}"

cat > "${INSTALL_DIR}/conf/nginx.conf" <<NGINX_CONF
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

        root ${APP_ROOT}/frontend/dist;
        index index.html;

        location / {
            try_files \$uri \$uri/ /index.html;
        }

        location /api/ {
            proxy_pass http://127.0.0.1:30010/api/;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto \$scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 300s;
            proxy_read_timeout 300s;
        }
    }
}
NGINX_CONF

cat > "${INSTALL_DIR}/sbin/start-nginx.sh" <<'START_SCRIPT'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
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
if [ -f "${BASE}/logs/nginx.pid" ]; then
  "${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -s quit || true
  echo "nginx 已停止"
else
  echo "未找到 PID 文件"
fi
STOP_SCRIPT

cat > "${INSTALL_DIR}/sbin/reload-nginx.sh" <<'RELOAD_SCRIPT'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
"${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -s reload
echo "nginx 已重载"
RELOAD_SCRIPT

chmod +x "${INSTALL_DIR}/sbin/"*.sh
rm -rf "${INSTALL_DIR}/lib"

cat > "${INSTALL_DIR}/BUILD_INFO.txt" <<INFO
nginx version: ${NGINX_VER}
target arch: aarch64 (ARM64)
linking: static (zlib/openssl/pcre2)
build host: $(hostname)
build date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
app root: ${APP_ROOT}/frontend/dist
listen port: 30011
backend proxy: 127.0.0.1:30010
INFO

rm -f "${INSTALL_DIR}/logs/nginx.pid" "${INSTALL_DIR}/logs/access.log" "${INSTALL_DIR}/logs/error.log"

cd "${BUILD_ROOT}/staging"
TAR_NAME="nginx-forward-${NGINX_VER}-linux-aarch64.tar.gz"
tar -czf "${BUILD_ROOT}/${TAR_NAME}" -C "${BUILD_ROOT}/staging" opt/forward-platform/nginx
ls -lh "${BUILD_ROOT}/${TAR_NAME}"
systemctl is-active nginx
echo "BUILD_OK ${BUILD_ROOT}/${TAR_NAME}"
