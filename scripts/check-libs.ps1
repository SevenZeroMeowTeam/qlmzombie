# 依赖检查脚本 (PowerShell)
# 检查 src/main/libs/ 目录是否包含所有必需的依赖 jar 文件
# 用法: .\scripts\check-libs.ps1

# 设置 UTF-8 编码（解决中文文件名乱码）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"
$libsDir = "src\main\libs"
$listFile = "scripts\libs-list.txt"

if (-not (Test-Path $listFile)) {
    Write-Host "[ERROR] 依赖列表文件不存在: $listFile" -ForegroundColor Red
    exit 1
}

# 读取需要的依赖列表
$required = Get-Content $listFile -Encoding UTF8 | Where-Object { $_.Trim() -ne "" }
$total = $required.Count

# 获取实际存在的文件名数组
$actualFiles = @()
if (Test-Path $libsDir) {
    $actualFiles = (Get-ChildItem -Path $libsDir -Filter "*.jar").Name
}

$missing = @()
$found = 0

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  依赖检查 - 七零喵僵尸末日生存 Mod" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "需要依赖文件总数: $total" -ForegroundColor Yellow
Write-Host ""

foreach ($jar in $required) {
    if ($actualFiles -contains $jar) {
        $found++
    } else {
        $missing += $jar
    }
}

if ($missing.Count -eq 0) {
    Write-Host "[OK] 所有 $total 个依赖文件均已存在 ($found/$total)" -ForegroundColor Green
    Write-Host "可以正常编译: .\gradlew.bat build" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[MISSING] 缺少 $($missing.Count) 个依赖文件 ($found/$total)" -ForegroundColor Red
    Write-Host ""
    Write-Host "缺少的文件:" -ForegroundColor Yellow
    foreach ($m in $missing) {
        Write-Host "  - $m" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "请从以下网站下载缺失的依赖:" -ForegroundColor Cyan
    Write-Host "  CurseForge:  https://www.curseforge.com/minecraft/mc-mods" -ForegroundColor White
    Write-Host "  Modrinth:    https://modrinth.com/mods" -ForegroundColor White
    Write-Host "  将下载的 jar 文件放入: src/main/libs/" -ForegroundColor White
    exit 1
}
