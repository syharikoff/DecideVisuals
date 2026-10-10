# Сборка DecideVocal.dll (детектор вокала, WASAPI loopback) под x64.
# Кладёт готовую DLL в src/client/resources/assets/decide/natives/, откуда её грузит MediaNative.
#
# cl/link вызываются напрямую с явными INCLUDE/LIB: vcvars64.bat в этом BuildTools
# отсутствует, а пути к заголовкам/либам версионированные и известны.

$ErrorActionPreference = 'Stop'

$vcRoot = 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC'
if (-not (Test-Path $vcRoot)) { throw "MSVC не найден: $vcRoot" }
$vcVer  = (Get-ChildItem $vcRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$cl     = Join-Path $vcVer 'bin\Hostx64\x64\cl.exe'
if (-not (Test-Path $cl)) { throw "cl.exe не найден: $cl" }

$sdkRoot = 'C:\Program Files (x86)\Windows Kits\10'
$sdkInc  = (Get-ChildItem "$sdkRoot\Include" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$sdkLib  = (Get-ChildItem "$sdkRoot\Lib"     -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (-not $sdkInc -or -not $sdkLib) { throw "Windows SDK не найден" }

$env:INCLUDE = @(
    "$vcVer\include",
    "$sdkInc\ucrt",
    "$sdkInc\shared",
    "$sdkInc\um",
    "$sdkInc\winrt"
) -join ';'

$env:LIB = @(
    "$vcVer\lib\x64",
    "$sdkLib\ucrt\x64",
    "$sdkLib\um\x64"
) -join ';'

$srcDir = $PSScriptRoot
$outDir = Join-Path $srcDir '..\src\client\resources\assets\client\natives'
$objDir = Join-Path $env:TEMP 'decide_native'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
if (-not (Test-Path $objDir)) { New-Item -ItemType Directory -Path $objDir -Force | Out-Null }

$outDir  = (Resolve-Path $outDir).Path
$outDll  = Join-Path $outDir 'DecideVocal.dll'
$outObj  = Join-Path $objDir 'lyrics_vocal.obj'
$outLib  = Join-Path $objDir 'lyrics_vocal.lib'
$outExp  = Join-Path $objDir 'lyrics_vocal.exp'

Write-Host "[build] lyrics_vocal.cpp -> $outDll"

$args = @(
    '/nologo', '/LD', '/EHsc', '/O2', '/std:c++17', '/W3',
    '/DUNICODE', '/D_UNICODE', '/MT', '/GS-', '/Oi-',
    "/Fo$outObj", "/Fe$outDll",
    'C:\work6\DecideVisuals-main\native\lyrics_vocal.cpp',
    '/link', '/DLL', '/MACHINE:X64', '/SUBSYSTEM:WINDOWS', '/NODEFAULTLIB:libcpmt.lib',
    "/IMPLIB:$outLib", "/OUT:$outDll",
    'ole32.lib', 'uuid.lib', 'avrt.lib', 'advapi32.lib', 'kernel32.lib', 'user32.lib'
)

& $cl @args

if ($LASTEXITCODE -ne 0) { throw "компиляция провалилась (exit $LASTEXITCODE)" }
if (-not (Test-Path $outDll)) { throw "DLL не создана" }

Write-Host "[ok] $outDll ($((Get-Item $outDll).Length) байт)"
