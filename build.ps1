$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$jdk = $env:JAVA_HOME
if ($jdk) {
    $javac = Join-Path $jdk "bin\javac.exe"
    $jar = Join-Path $jdk "bin\jar.exe"
} else {
    $javac = "javac"
    $jar = "jar"
}

$classes = Join-Path $root "build\classes"
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = Get-ChildItem (Join-Path $root "src\main\java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "编译 $($sources.Count) 个源文件..."
& $javac -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "编译失败" }

# copy UI icon resources into classes so they are packed into the jar
$resDir = Join-Path $root 'src\main\resources'
if (Test-Path $resDir) {
    Copy-Item -Path (Join-Path $resDir '*') -Destination $classes -Recurse -Force
}

$jarFile = Join-Path $root "build\VoiceChanger.jar"
& $jar --create --file $jarFile --main-class com.voicechanger.App -C $classes .
if ($LASTEXITCODE -ne 0) { throw "打包失败" }
Write-Host "已生成: $jarFile"

$toolOut = Join-Path $root "build\test-classes"
if (Test-Path $toolOut) { Remove-Item -Recurse -Force $toolOut }
New-Item -ItemType Directory -Force $toolOut | Out-Null
$tools = Get-ChildItem (Join-Path $root "tools") -Filter *.java | ForEach-Object { $_.FullName }
if ($tools) {
    & $javac -encoding UTF-8 -cp $classes -d $toolOut $tools
    if ($LASTEXITCODE -ne 0) { throw "工具编译失败" }
}
