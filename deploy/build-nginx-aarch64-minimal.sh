#!/bin/bash
set -euo pipefail

BUILD_ROOT="${HOME}/nginx-forward-build-aarch64"
ZLIB_SRC="${BUILD_ROOT}/zlib-1.3.1"
STAGING="${BUILD_ROOT}/staging"
PREFIX="/opt/forward-platform/nginx"
NGINX_VER="1.26.2"
APP_ROOT="/home/ubuntu/server/forwardplatform"
export CC=aarch64-linux-gnu-gcc
JOBS="$(nproc)"

cd "${BUILD_ROOT}"

echo "=== 编译 nginx aarch64（无 SSL，仅静态文件+反代）==="
if [ ! -f "nginx-${NGINX_VER}.tar.gz" ]; then
  wget -q "https://nginx.org/download/nginx-${NGINX_VER}.tar.gz"
fi
rm -rf "nginx-${NGINX_VER}"
tar -xzf "nginx-${NGINX_VER}.tar.gz"
cd "nginx-${NGINX_VER}"

./configure \
  --prefix="${PREFIX}" \
  --sbin-path="${PREFIX}/sbin/nginx" \
  --conf-path="${PREFIX}/conf/nginx.conf" \
  --error-log-path="${PREFIX}/logs/error.log" \
  --http-log-path="${PREFIX}/logs/access.log" \
  --pid-path="${PREFIX}/logs/nginx.pid" \
  --lock-path="${PREFIX}/logs/nginx.lock" \
  --crossbuild=Linux::aarch64 \
  --with-cc="${CC} -static" \
  --with-cpp="${CC} -E" \
  --with-zlib="${ZLIB_SRC}" \
  --without-pcre \
  --without-http_rewrite_module \
  --with-cc-opt="-static" \
  --with-ld-opt="-static" \
  --without-http_auth_basic_module \
  --with-http_gzip_static_module \
  --with-threads

make -j"${JOBS}"
rm -rf "${STAGING}"
make install DESTDIR="${STAGING}"

INSTALL_DIR="${STAGING}${PREFIX}"
file "${INSTALL_DIR}/sbin/nginx"

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

cat > "${INSTALL_DIR}/sbin/start-nginx.sh" <<'EOF'
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
EOF

cat > "${INSTALL_DIR}/sbin/stop-nginx.sh" <<'EOF'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "${BASE}/logs/nginx.pid" ]; then
  "${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -s quit || true
  echo "nginx 已停止"
else
  echo "未找到 PID 文件"
fi
EOF

cat > "${INSTALL_DIR}/sbin/reload-nginx.sh" <<'EOF'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
"${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -s reload
echo "nginx 已重载"
EOF

chmod +x "${INSTALL_DIR}/sbin/"*.sh
rm -rf "${INSTALL_DIR}/lib"
rm -f "${INSTALL_DIR}/logs/"*.log "${INSTALL_DIR}/logs/nginx.pid"

cat > "${INSTALL_DIR}/BUILD_INFO.txt" <<INFO
nginx version: ${NGINX_VER}
target arch: aarch64 (ARM64)
modules: static files + proxy (no SSL)
app root: ${APP_ROOT}/frontend/dist
listen port: 30011
INFO

cd "${BUILD_ROOT}/staging"
TAR_NAME="nginx-forward-${NGINX_VER}-linux-aarch64.tar.gz"
tar -czf "${BUILD_ROOT}/${TAR_NAME}" -C "${BUILD_ROOT}/staging" opt/forward-platform/nginx
ls -lh "${BUILD_ROOT}/${TAR_NAME}"
file "${INSTALL_DIR}/sbin/nginx"
echo "BUILD_OK ${BUILD_ROOT}/${TAR_NAME}"
