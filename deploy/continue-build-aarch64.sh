#!/bin/bash
set -euo pipefail

BUILD_ROOT="${HOME}/nginx-forward-build-aarch64"
DEPS="${BUILD_ROOT}/deps-aarch64"
STAGING="${BUILD_ROOT}/staging"
PREFIX="/opt/forward-platform/nginx"
NGINX_VER="1.26.2"
PCRE2_VER="10.44"
OPENSSL_VER="3.0.15"
APP_ROOT="/home/ubuntu/server/forwardplatform"
export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++
export AR=aarch64-linux-gnu-ar
export RANLIB=aarch64-linux-gnu-ranlib
JOBS="$(nproc)"

cd "${BUILD_ROOT}"

echo "=== [1/3] OpenSSL aarch64 ==="
if [ ! -f "${DEPS}/lib/libssl.a" ]; then
  rm -rf "openssl-${OPENSSL_VER}"
  tar -xzf "openssl-${OPENSSL_VER}.tar.gz"
  cd "openssl-${OPENSSL_VER}"
  ./Configure linux-aarch64 --prefix="${DEPS}" --cross-compile-prefix=aarch64-linux-gnu- no-shared no-tests
  make -j"${JOBS}"
  make install_sw
  cd "${BUILD_ROOT}"
else
  echo "OpenSSL already built"
fi

echo "=== [2/3] PCRE2 aarch64 ==="
if [ ! -f "${DEPS}/lib/libpcre2-8.a" ]; then
  if [ ! -f "pcre2-${PCRE2_VER}.tar.gz" ]; then
    wget -q "https://github.com/PCRE2Project/pcre2/releases/download/pcre2-${PCRE2_VER}/pcre2-${PCRE2_VER}.tar.gz"
  fi
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
  make -j"${JOBS}"
  make install
  cd "${BUILD_ROOT}"
else
  echo "PCRE2 already built"
fi

echo "=== [3/3] nginx aarch64 ==="
if [ ! -f "nginx-${NGINX_VER}.tar.gz" ]; then
  wget -q "https://nginx.org/download/nginx-${NGINX_VER}.tar.gz"
fi
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
  --with-cc-opt="-I${DEPS}/include" \
  --with-ld-opt="-L${DEPS}/lib -static -latomic" \
  --with-http_ssl_module \
  --with-http_v2_module \
  --with-http_gzip_static_module \
  --with-http_realip_module \
  --with-threads \
  --with-file-aio

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

for script in start-nginx.sh stop-nginx.sh reload-nginx.sh; do
  if [ ! -f "${INSTALL_DIR}/sbin/${script}" ]; then
    case "${script}" in
      start-nginx.sh)
        cat > "${INSTALL_DIR}/sbin/${script}" <<'EOF'
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
        ;;
      stop-nginx.sh)
        cat > "${INSTALL_DIR}/sbin/${script}" <<'EOF'
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
        ;;
      reload-nginx.sh)
        cat > "${INSTALL_DIR}/sbin/${script}" <<'EOF'
#!/bin/bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
"${BASE}/sbin/nginx" -p "${BASE}" -e "${BASE}/logs/error.log" -c "${BASE}/conf/nginx.conf" -s reload
echo "nginx 已重载"
EOF
        ;;
    esac
    chmod +x "${INSTALL_DIR}/sbin/${script}"
  fi
done

rm -rf "${INSTALL_DIR}/lib"
rm -f "${INSTALL_DIR}/logs/nginx.pid" "${INSTALL_DIR}/logs/access.log" "${INSTALL_DIR}/logs/error.log"

cat > "${INSTALL_DIR}/BUILD_INFO.txt" <<INFO
nginx version: ${NGINX_VER}
target arch: aarch64 (ARM64)
linking: static
app root: ${APP_ROOT}/frontend/dist
listen port: 30011
INFO

cd "${BUILD_ROOT}/staging"
TAR_NAME="nginx-forward-${NGINX_VER}-linux-aarch64.tar.gz"
tar -czf "${BUILD_ROOT}/${TAR_NAME}" -C "${BUILD_ROOT}/staging" opt/forward-platform/nginx
ls -lh "${BUILD_ROOT}/${TAR_NAME}"
file "${INSTALL_DIR}/sbin/nginx"
echo "BUILD_OK ${BUILD_ROOT}/${TAR_NAME}"
