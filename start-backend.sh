#!/usr/bin/env bash
# 从项目根目录启动后端，并加载 forward.env
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

ENV_FILE="${ROOT}/forward.env"
JAR="${ROOT}/backend/forward-platform.jar"
LOG_DIR="${ROOT}/logs"
PID_FILE="${LOG_DIR}/backend.pid"

if [[ ! -f "$JAR" ]]; then
  echo "找不到 JAR: $JAR" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "找不到环境文件: $ENV_FILE" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "${OLD_PID}" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "后端已在运行，PID=${OLD_PID}。请先执行 ./stop-backend.sh" >&2
    exit 1
  fi
  rm -f "$PID_FILE"
fi

# 导出 forward.env 中的变量（忽略空行与 # 注释）
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# Spring Boot 认 SERVER_PORT；若只写了 SERVER_PORT 也会覆盖 application.yml
export SERVER_PORT="${SERVER_PORT:-30010}"

echo "使用数据库: ${FP_DB_HOST:-localhost}:${FP_DB_PORT:-5432}/${FP_DB_NAME:-forward_platform}"
echo "服务端口: ${SERVER_PORT}"

nohup java ${JAVA_OPTS:-} -jar "$JAR" > "${LOG_DIR}/backend.log" 2>&1 &
echo $! > "$PID_FILE"
echo "后端已启动，PID=$(cat "$PID_FILE")"
echo "日志: ${LOG_DIR}/backend.log"
