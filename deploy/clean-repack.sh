#!/bin/bash
set -euo pipefail
INSTALL_DIR="${HOME}/nginx-forward-build/staging/opt/forward-platform/nginx"
BUILD_ROOT="${HOME}/nginx-forward-build"
rm -f "${INSTALL_DIR}/logs/nginx.pid" "${INSTALL_DIR}/logs/access.log" "${INSTALL_DIR}/logs/error.log"
cd "${BUILD_ROOT}/staging"
tar -czf "${BUILD_ROOT}/nginx-forward-1.26.2-linux-x86_64.tar.gz" -C "${BUILD_ROOT}/staging" opt/forward-platform/nginx
echo CLEAN_REPACK_OK
