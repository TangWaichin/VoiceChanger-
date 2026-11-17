#Requires -RunAsAdministrator
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$setupName = "VBCABLE_Setup_x64.exe"

function Fail([string]$message) {
    Write-Host ""
    Write-Host "卸载失败：$message" -ForegroundColor Red
    exit 1
}

function Get-CableEndpoints {
    return @(Get-PnpDevice -PresentOnly -Class AudioEndpoint -ErrorAction SilentlyContinue |
        Where-Object { $_.FriendlyName -match "CABLE|VB-Audio" })
}

$existing = Get-CableEndpoints
if ($existing.Count -eq 0) {
    Write-Host "未检测到 VB-CABLE 音频端点，可能已经卸载。" -ForegroundColor Yellow
    Write-Host "若设备管理器中仍有残留设备，请重启后重试。"
    exit 0
}

$candidates = @(
    (Join-Path ${env:ProgramFiles} "VB\CABLE\$setupName"),
    (Join-Path (Split-Path -Parent $PSScriptRoot) "vbcable\$setupName")
)
$setup = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1

if (-not $setup) {
    Fail "找不到 $setupName，无法静默卸载。请运行 VB-CABLE 安装器手动移除驱动。"
}

Write-Host "卸载器  : $setup"
Write-Host ""
Write-Host "正在静默卸载 VB-CABLE（-u -h）..." -ForegroundColor Yellow
$process = Start-Process -FilePath $setup -ArgumentList '-u', '-h' -Wait -PassThru
Write-Host "卸载器退出码：$($process.ExitCode)"

$left = @()
for ($i = 0; $i -lt 25; $i++) {
    Start-Sleep -Milliseconds 800
    $left = Get-CableEndpoints
    if ($left.Count -eq 0) { break }
}

Write-Host ""
if ($left.Count -eq 0) {
    Write-Host "VB-CABLE 音频端点已移除。" -ForegroundColor Green
    Write-Host "服务、驱动包与安装目录会在重启后完成清理，建议重启 Windows。" -ForegroundColor Cyan
    exit 0
}

Fail "VB-CABLE 端点仍然存在，请打开设备管理器手动卸载，或重启后重试。"
