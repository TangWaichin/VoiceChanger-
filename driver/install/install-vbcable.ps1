#Requires -RunAsAdministrator
[CmdletBinding()]
param(
    [string]$PackageDir = ""
)

$ErrorActionPreference = "Stop"
$setupName = "VBCABLE_Setup_x64.exe"

function Fail([string]$message) {
    Write-Host ""
    Write-Host "安装失败：$message" -ForegroundColor Red
    exit 1
}

function Get-CableEndpoints {
    return @(Get-PnpDevice -PresentOnly -Class AudioEndpoint -ErrorAction SilentlyContinue |
        Where-Object { $_.FriendlyName -match "CABLE|VB-Audio" })
}

if ([string]::IsNullOrWhiteSpace($PackageDir)) {
    $PackageDir = Join-Path (Split-Path -Parent $PSScriptRoot) "vbcable"
}

if (-not (Test-Path -LiteralPath $PackageDir -PathType Container)) {
    Fail "VB-CABLE 驱动包目录不存在：$PackageDir"
}

$PackageDir = (Resolve-Path -LiteralPath $PackageDir).Path
$setup = Join-Path $PackageDir $setupName
if (-not (Test-Path -LiteralPath $setup -PathType Leaf)) {
    Fail "找不到 $setupName，请确认随包分发的 VB-CABLE 包完整。"
}

Write-Host "驱动包  : $PackageDir"
Write-Host "安装器  : $setup"
Write-Host ""

$existing = Get-CableEndpoints
if ($existing.Count -gt 0) {
    Write-Host "VB-CABLE 已安装，无需重复安装：" -ForegroundColor Green
    $existing | Select-Object Status, FriendlyName | Format-Table -AutoSize
    exit 0
}

Write-Host "正在静默安装 VB-CABLE（-i -h）..." -ForegroundColor Yellow
$process = Start-Process -FilePath $setup -ArgumentList '-i', '-h' -WorkingDirectory $PackageDir -Wait -PassThru
Write-Host "安装器退出码：$($process.ExitCode)"

$found = @()
for ($i = 0; $i -lt 25; $i++) {
    Start-Sleep -Milliseconds 800
    $found = Get-CableEndpoints
    if ($found.Count -gt 0) { break }
}

Write-Host ""
if ($found.Count -gt 0) {
    Write-Host "VB-CABLE 安装成功：" -ForegroundColor Green
    $found | Select-Object Status, FriendlyName | Format-Table -AutoSize
    Write-Host "播放端：CABLE Input；录音端：CABLE Output"
    Write-Host "建议重启 Windows 完成驱动初始化。" -ForegroundColor Cyan
    exit 0
}

$packageInStore = [bool](pnputil /enum-drivers | Select-String -Pattern "vbmmecable" -Quiet)
if ($packageInStore) {
    Write-Host "驱动已写入系统，但音频端点尚未出现。" -ForegroundColor Yellow
    Write-Host "请重启 Windows 后再次打开本软件。" -ForegroundColor Cyan
    exit 3
}

Fail "安装器已退出，但未检测到 VB-CABLE 端点。若系统曾安装旧版 VB-CABLE，请先重启、卸载旧版后重试。"
