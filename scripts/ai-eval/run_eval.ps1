# AI 评测回归集 · 统一入口（Windows 本地）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\ai-eval\run_eval.ps1
# 说明：离线确定性断言，零 LLM key；任何脚本 FAIL 则退出码 1（CI 同步判定）。

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent (Resolve-Path $PSScriptRoot))
$VenvPy = Join-Path $Root "services\ai-orchestrator\.venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $VenvPy)) { $VenvPy = "python" }

$scripts = @(
  "test_tool_schema.py",
  "test_tool_routing.py",
  "test_injection_regression.py"
)

$failed = @()
foreach ($s in $scripts) {
  Write-Host "== ai-eval $s =="
  & $VenvPy (Join-Path $PSScriptRoot $s)
  if ($LASTEXITCODE -ne 0) { $failed += $s }
}

if ($failed.Count -gt 0) {
  Write-Host ("FAIL ai-eval: " + ($failed -join ", "))
  exit 1
}
Write-Host "PASS ai-eval: 3/3 全绿（schema / routing / injection）"
exit 0
