#!/bin/bash
# 每次 Claude Code 编辑或写入文件后执行
# 作用：触发基本的格式检查

# 如果是 Java 文件，不做实时检查（编译太慢），只记录
if [[ "$1" == *.java ]]; then
  echo "[lint] Java 文件变更: $1 (编译检查请跑 mvn compile)"
fi

# 如果是前端文件，不做实时检查
if [[ "$1" == *.ts || "$1" == *.vue ]]; then
  echo "[lint] 前端文件变更: $1 (检查请跑 pnpm lint)"
fi

exit 0