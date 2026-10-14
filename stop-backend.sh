#!/usr/bin/env bash
# 停止后端
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${ROOT}/logs/backend.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "未找到 PID 文件: $PID_FILE（可能未启动）"
  exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  # 等待退出
  for _ in $(seq 1 20); do
    if ! kill -0 "$PID" 2>/dev/null; then
      break
    fi
    sleep 0.5
  done
  if kill -0 "$PID" 2>/dev/null; then
    echo "进程未退出，强制 kill -9 $PID"
    kill -9 "$PID" || true
  fi
  echo "后端已停止，PID=${PID}"
else
  echo "进程不存在，PID=${PID}（清理 PID 文件）"
fi
rm -f "$PID_FILE"
