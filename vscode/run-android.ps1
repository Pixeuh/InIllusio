# Compatibility entry point: keep one implementation of the Windows development workflow.
& (Join-Path $PSScriptRoot '..\.vscode\run-android.ps1') @args
exit $LASTEXITCODE
