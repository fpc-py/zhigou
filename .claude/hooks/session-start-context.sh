#!/bin/bash
# 每次 Claude Code 会话启动时执行
# 作用：提醒 Claude 阅读关键上下文文件

echo "=== 智购项目会话启动 ==="
echo "请务必阅读以下文件："
echo "  1. CLAUDE.md — 项目硬约束"
echo "  2. docs/progress.md — 当前进度"
echo "  3. .claude/rules/security.md — 安全红线"
echo "  4. .claude/rules/git-workflow.md — Git 规范"
echo "=========================="