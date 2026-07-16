#!/bin/bash
set -euo pipefail
APP_ROOT="/home/ubuntu/server/forwardplatform"
INSTALL_DIR="${HOME}/nginx-forward-build/staging/opt/forward-platform/nginx"
BUILD_ROOT="${HOME}/nginx-forward-build"

sed -i "s|root /root/server/forwardplatform/www|root ${APP_ROOT}/www|" "${INSTALL_DIR}/conf/nginx.conf"
sed -i "s|app root in config: .*|app root in config: ${APP_ROOT}|" "${INSTALL_DIR}/BUILD_INFO.txt"

export LD_LIBRARY_PATH="${INSTALL_DIR}/lib"
"${INSTALL_DIR}/sbin/nginx" -t -p "${INSTALL_DIR}" -e "${INSTALL_DIR}/logs/error.log" -c "${INSTALL_DIR}/conf/nginx.conf"

cd "${BUILD_ROOT}/staging"
tar -czf "${BUILD_ROOT}/nginx-forward-1.26.2-linux-x86_64.tar.gz" -C "${BUILD_ROOT}/staging" opt/forward-platform/nginx

echo "REPACK_OK"
grep "root " "${INSTALL_DIR}/conf/nginx.conf"
ls -lh "${BUILD_ROOT}/nginx-forward-1.26.2-linux-x86_64.tar.gz"
