#!/bin/bash
set -euo pipefail
INSTALL_DIR="${HOME}/nginx-forward-build/staging/opt/forward-platform/nginx"
LIB_DIR="${INSTALL_DIR}/lib"
NGINX_BIN="${INSTALL_DIR}/sbin/nginx"
mkdir -p "${INSTALL_DIR}/logs"
export LD_LIBRARY_PATH="${LIB_DIR}"
"${NGINX_BIN}" -t -p "${INSTALL_DIR}" -e "${INSTALL_DIR}/logs/error.log" -c "${INSTALL_DIR}/conf/nginx.conf"
echo "CONFIG_OK"
cat "${INSTALL_DIR}/BUILD_INFO.txt"
ls -lh "${HOME}/nginx-forward-build/nginx-forward-1.26.2-linux-x86_64.tar.gz"
systemctl is-active nginx
ss -lntp | grep -E ':80|:443' | head -5
