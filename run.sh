#!/bin/bash
# 本地开发启动脚本
# 已加入 .gitignore，不会提交
set -a
source "$(dirname "$0")/.env"
set +a

cd "$(dirname "$0")"

echo "=== 构建并启动后端 ==="
mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1
