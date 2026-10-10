# ---------------------------------------------------------------------------
# DecideVisuals launcher (offline, no third-party launcher required)
#
#   start.bat            - build nothing, just launch the game
#   launch.ps1 -PrepareOnly   - prepare assets / libraries / natives only
#
# What it does:
#   1. finds Java 21
#   2. reads the Fabric + vanilla version json from .minecraft
#   3. makes sure every library, client jar, asset and native exists
#      (copies them from local caches first, downloads only what is missing)
#   4. starts net.fabricmc.loader.impl.launch.knot.KnotClient with gameDir=run
# ---------------------------------------------------------------------------
param(
    [string]$Player = 'Dev',
    [string]$Mem = '4G',
    [string]$VersionId = 'fabric-loader-0.19.5-1.21.11',
    [switch]$PrepareOnly,
    [switch]$SkipAssetCheck
)

$ErrorActionPreference = 'Stop'

$Root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$GameDir  = Join-Path $Root 'run'
$Natives  = Join-Path $GameDir 'natives'
$McDir    = if ($env:MC_DIR) { $env:MC_DIR } else { Join-Path $env:APPDATA '.minecraft' }

$OsName = 'windows'
$OsArch = switch ($env:PROCESSOR_ARCHITECTURE) {
    'AMD64'  { 'x64' }
    'x86'    { 'x86' }
    'ARM64'  { 'arm64' }
    default  { $env:PROCESSOR_ARCHITECTURE.ToLower() }
}

function Say([string]$m) { Write-Host "[launch] $m" -ForegroundColor Cyan }
function Warn([string]$m) { Write-Host "[launch] $m" -ForegroundColor Yellow }
function Fail([string]$m) {
    Write-Host "[launch] ERROR: $m" -ForegroundColor Red
    exit 1
}

# --------------------------------------------------------------- Java 21 ---
function Find-Java {
    $candidates = @()
    if ($env:JAVA21_EXE) { $candidates += $env:JAVA21_EXE }
    $candidates += (Join-Path $Root 'jdk\bin\java.exe')
    foreach ($base in @(
            'C:\Program Files\Eclipse Adoptium',
            'C:\Program Files\Java',
            'C:\Program Files\Microsoft',
            'C:\Program Files\Amazon Corretto',
            'C:\Program Files\Zulu',
            'C:\Program Files\BellSoft',
            "$env:USERPROFILE\.jdks"
        )) {
        if (Test-Path $base) {
            foreach ($d in (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue)) {
                $candidates += (Join-Path $d.FullName 'bin\java.exe')
            }
        }
    }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += 'java'

    foreach ($c in $candidates) {
        $exe = $c
        if ($c -ne 'java') {
            if (-not (Test-Path $exe)) { continue }
        }
        try {
            $oldEap = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $v = (& $exe -version 2>&1 | Out-String)
            $ErrorActionPreference = $oldEap
            if ($v -match 'version "21[\.,]') { return $exe }
        } catch { }
    }
    return $null
}

# ------------------------------------------------------- json / rules ------
function Get-Json([string]$path) {
    if (-not (Test-Path $path)) { Fail "missing version json: $path" }
    return (Get-Content $path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

function Match-Conditions($rule) {
    if ($rule.os) {
        if ($rule.os.name -and $rule.os.name -ne $OsName) { return $false }
        if ($rule.os.arch -and $rule.os.arch -ne $OsArch) { return $false }
    }
    if ($rule.features) {
        # we launch offline: no premium/demo/custom-resolution/quick-play features
        return $false
    }
    return $true
}

function Test-Rules($rules) {
    if (-not $rules -or @($rules).Count -eq 0) { return $true }
    $hasAllow = $false
    $allowed = $false
    foreach ($r in $rules) {
        if ($r.action -eq 'allow') {
            $hasAllow = $true
            if (Match-Conditions $r) { $allowed = $true }
        }
    }
    if (-not $hasAllow) { $allowed = $true }
    foreach ($r in $rules) {
        if ($r.action -eq 'disallow' -and (Match-Conditions $r)) { $allowed = $false }
    }
    return $allowed
}

# ---------------------------------------------------- download helpers -----
function Ensure-File([string]$path, [string]$url) {
    if (Test-Path $path) { return $false }
    $dir = Split-Path -Parent $path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Say "downloading $(Split-Path -Leaf $path)"
    Invoke-WebRequest -Uri $url -OutFile $path -UseBasicParsing
    return $true
}

function Lib-Path($lib) {
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) {
        return $lib.downloads.artifact.path
    }
    # derive from maven name: group:artifact:version[:classifier][@ext]
    $name = $lib.name
    $ext = 'jar'
    if ($name -match '@(.+)$') { $ext = $Matches[1]; $name = $name -replace '@.+$', '' }
    $parts = $name -split ':'
    $group = $parts[0].Replace('.', '/')
    $artifact = $parts[1]
    $version = $parts[2]
    $file = "$artifact-$version.$ext"
    if ($parts.Count -gt 3 -and $parts[3]) { $file = "$artifact-$version-$($parts[3]).$ext" }
    return "$group/$artifact/$version/$file"
}

# ---------------------------------------------------- offline uuid ---------
function Get-OfflineUuid([string]$name) {
    # uuid v3 style, same as vanilla offline mode: md5("OfflinePlayer:" + name)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$name")
    $hash = $md5.ComputeHash($bytes)
    $hash[6] = (($hash[6] -band 0x0f) -bor 0x30)  # version 3
    $hash[8] = (($hash[8] -band 0x3f) -bor 0x80)  # variant
    $hex = ([System.BitConverter]::ToString($hash)).Replace('-', '').ToLower()
    return "$($hex.Substring(0,8))-$($hex.Substring(8,4))-$($hex.Substring(12,4))-$($hex.Substring(16,4))-$($hex.Substring(20,12))"
}

# ------------------------------------------------------------- assets ------
function Ensure-Assets($baseJson) {
    $idxId  = $baseJson.assetIndex.id
    $idxUrl = $baseJson.assetIndex.url
    $assetsRoot = Join-Path $McDir 'assets'
    $idxFile = Join-Path $assetsRoot "indexes\$idxId.json"

    # local caches that may already hold the files (no download needed)
    $srcRoots = @()
    $loom = Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom\assets'
    if (Test-Path $loom) { $srcRoots += $loom }
    foreach ($d in (Get-ChildItem "$env:USERPROFILE\Desktop" -Directory -ErrorAction SilentlyContinue)) {
        $cand = Join-Path $d.FullName 'minecraft\assets'
        if (Test-Path $cand) { $srcRoots += $cand }
    }

    if (-not (Test-Path $idxFile)) {
        $copied = $false
        foreach ($s in $srcRoots) {
            $alt = Join-Path $s "indexes\$idxId.json"
            if (Test-Path $alt) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $idxFile) | Out-Null
                Copy-Item $alt $idxFile -Force
                Say "asset index $idxId.json copied from local cache"
                $copied = $true
                break
            }
            # loom names it <version>-<id>.json
            $named = Get-ChildItem (Join-Path $s 'indexes') -Filter "*-$idxId.json" -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($named) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $idxFile) | Out-Null
                Copy-Item $named.FullName $idxFile -Force
                Say "asset index $idxId.json copied from local cache"
                $copied = $true
                break
            }
        }
        if (-not $copied) {
            Say "asset index $idxId.json downloading"
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $idxFile) | Out-Null
            Invoke-WebRequest -Uri $idxUrl -OutFile $idxFile -UseBasicParsing
        }
    }

    $index = Get-Content $idxFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $objRoot = Join-Path $assetsRoot 'objects'
    $missing = @()
    foreach ($p in $index.objects.PSObject.Properties) {
        $h = $p.Value.hash
        $dest = Join-Path $objRoot "$($h.Substring(0,2))\$h"
        if (-not (Test-Path $dest)) { $missing += $h }
    }
    if ($missing.Count -eq 0) {
        Say "assets: all $(@($index.objects.PSObject.Properties).Count) files present"
        return
    }

    Warn "assets: $($missing.Count) file(s) missing, restoring from local caches / network"
    $needDownload = @()
    foreach ($h in $missing) {
        $rel = "$($h.Substring(0,2))\$h"
        $done = $false
        foreach ($s in $srcRoots) {
            $src = Join-Path $s "objects\$rel"
            if (Test-Path $src) {
                $dest = Join-Path $objRoot $rel
                $dd = Split-Path -Parent $dest
                if (-not (Test-Path $dd)) { New-Item -ItemType Directory -Force -Path $dd | Out-Null }
                [System.IO.File]::Copy($src, $dest, $false)
                $done = $true
                break
            }
        }
        if (-not $done) { $needDownload += $h }
    }

    if ($needDownload.Count -gt 0) {
        Warn "downloading $($needDownload.Count) asset(s) from Mojang CDN"
        foreach ($h in $needDownload) {
            $rel = "$($h.Substring(0,2))\$h"
            $dest = Join-Path $objRoot $rel
            $dd = Split-Path -Parent $dest
            if (-not (Test-Path $dd)) { New-Item -ItemType Directory -Force -Path $dd | Out-Null }
            try {
                Invoke-WebRequest -Uri "https://resources.download.minecraft.net/$rel" -OutFile $dest -UseBasicParsing
            } catch {
                Warn "failed to download asset $h"
            }
        }
    }
    $left = 0
    foreach ($p in $index.objects.PSObject.Properties) {
        $h = $p.Value.hash
        if (-not (Test-Path (Join-Path $objRoot "$($h.Substring(0,2))\$h"))) { $left++ }
    }
    if ($left -gt 0) { Warn "$left asset(s) still missing - the game may show missing textures" }
    else { Say "assets ready" }
}

# ------------------------------------------------------------- natives -----
function Ensure-Natives($libs, [string]$baseId) {
    $marker = Join-Path $Natives ".built-$baseId"
    $wanted = if ($OsArch -eq 'arm64') { @('natives-windows', 'natives-windows-arm64') }
              elseif ($OsArch -eq 'x86') { @('natives-windows', 'natives-windows-x86') }
              else { @('natives-windows') }

    $jars = @()
    foreach ($lib in $libs) {
        if (-not (Test-Rules $lib.rules)) { continue }
        $classifier = ($lib.name -split ':')[-1]
        $isNative = $false
        if ($lib.natives) { $isNative = $true }              # legacy format
        elseif ($classifier -like 'natives-*' -and $wanted -contains $classifier) { $isNative = $true }
        if (-not $isNative) { continue }

        if ($lib.natives -and $lib.downloads.classifiers) {
            $key = $lib.natives.windows
            if (-not $key) { $key = ($lib.natives.PSObject.Properties | Select-Object -First 1).Value }
            $art = $lib.downloads.classifiers.$key
            if (-not $art) { continue }
            $path = Join-Path $McDir "libraries\$($art.path)"
            Ensure-File $path $art.url | Out-Null
            $jars += $path
        } else {
            $path = Join-Path $McDir "libraries\$(Lib-Path $lib)"
            if (-not (Test-Path $path)) { continue }
            $jars += $path
        }
    }

    if (Test-Path $marker) { return }
    if (Test-Path $Natives) { Remove-Item $Natives -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $Natives | Out-Null

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($jar in $jars) {
        $zip = $null
        try { $zip = [System.IO.Compression.ZipFile]::OpenRead($jar) } catch { continue }
        foreach ($e in $zip.Entries) {
            if ($e.FullName.EndsWith('/')) { continue }
            $leaf = Split-Path -Leaf ($e.FullName -replace '\\', '/')
            $dest = Join-Path $Natives $leaf
            if (-not (Test-Path $dest)) {
                try { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true) } catch { }
            }
        }
        $zip.Dispose()
    }
    Set-Content -Path $marker -Value (Get-Date -Format s)
    Say "natives extracted: $(@($jars).Count) jar(s) -> run\natives"
}

# =========================================================== main ==========
Say "java..."
$java = Find-Java
if (-not $java) { Fail "Java 21 not found. Install Temurin/Adoptium JDK 21 or set JAVA21_EXE." }
Say "java: $java"

if (-not (Test-Path $GameDir)) { New-Item -ItemType Directory -Force -Path $GameDir | Out-Null }
if (-not (Test-Path (Join-Path $GameDir 'mods'))) { New-Item -ItemType Directory -Force -Path (Join-Path $GameDir 'mods') | Out-Null }

$verDir = Join-Path $McDir "versions\$VersionId"
$fabricJson = Get-Json (Join-Path $verDir "$VersionId.json")
$baseId = $fabricJson.inheritsFrom
$baseJson = Get-Json (Join-Path $McDir "versions\$baseId\$baseId.json")
Say "version: $VersionId (minecraft $baseId)"

# ---- libraries / classpath
$libs = @()
if ($fabricJson.libraries) { $libs += $fabricJson.libraries }
if ($baseJson.libraries)   { $libs += $baseJson.libraries }

$classpath = @()
foreach ($lib in $libs) {
    if (-not (Test-Rules $lib.rules)) { continue }
    $rel = Lib-Path $lib
    $path = Join-Path $McDir "libraries\$rel"
    $url = $null
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.url) { $url = $lib.downloads.artifact.url }
    if (-not $url) { $url = "https://libraries.minecraft.net/$rel" }
    Ensure-File $path $url | Out-Null
    $classpath += $path
}

# ---- client jar
$clientJar = Join-Path $McDir "versions\$baseId\$baseId.jar"
$clientUrl = $baseJson.downloads.client.url
Ensure-File $clientJar $clientUrl | Out-Null
$classpath += $clientJar

# ---- assets + natives
if (-not $SkipAssetCheck) { Ensure-Assets $baseJson }
Ensure-Natives $libs $baseId

if ($PrepareOnly) {
    Say "prepare complete"
    exit 0
}

# ---- mods check
$modsDir = Join-Path $GameDir 'mods'
$modList = @(Get-ChildItem $modsDir -Filter *.jar -ErrorAction SilentlyContinue)
if ($modList.Count -eq 0) { Warn "run\mods is empty - the visual will not be loaded" }
else { Say ("mods: " + (($modList | ForEach-Object { $_.Name }) -join ', ')) }

# ---- placeholders
$map = @{
    'auth_player_name'  = $Player
    'version_name'      = $baseId
    'game_directory'    = $GameDir
    'assets_root'       = (Join-Path $McDir 'assets')
    'assets_index_name' = $baseJson.assetIndex.id
    'auth_uuid'         = (Get-OfflineUuid $Player)
    'auth_access_token' = '0'
    'clientid'          = '0'
    'auth_xuid'         = '0'
    'version_type'      = $baseJson.type
    'natives_directory' = $Natives
    'launcher_name'     = 'decidevisuals'
    'launcher_version'  = '1.0'
    'library_directory' = (Join-Path $McDir 'libraries')
    'classpath'         = ($classpath -join ';')
    'classpath_separator' = ';'
}

function Expand-Value([string]$v) {
    foreach ($k in $map.Keys) { $v = $v.Replace('${' + $k + '}', $map[$k]) }
    return $v
}

# ---- jvm args
$jvmArgs = @("-Xmx$Mem")
$jvmSource = @()
if ($fabricJson.arguments -and $fabricJson.arguments.jvm) { $jvmSource += $fabricJson.arguments.jvm }
if ($baseJson.arguments -and $baseJson.arguments.jvm)   { $jvmSource += $baseJson.arguments.jvm }
foreach ($item in $jvmSource) {
    if ($item -is [string]) { $jvmArgs += (Expand-Value $item) }
    else {
        if (-not (Test-Rules $item.rules)) { continue }
        foreach ($v in @($item.value)) { $jvmArgs += (Expand-Value $v) }
    }
}

# ---- game args
$gameArgs = @()
$gameSource = @()
if ($fabricJson.arguments -and $fabricJson.arguments.game) { $gameSource += $fabricJson.arguments.game }
if ($baseJson.arguments -and $baseJson.arguments.game)   { $gameSource += $baseJson.arguments.game }
foreach ($item in $gameSource) {
    if ($item -is [string]) { $gameArgs += (Expand-Value $item) }
    else {
        if (-not (Test-Rules $item.rules)) { continue }
        foreach ($v in @($item.value)) { $gameArgs += (Expand-Value $v) }
    }
}

$mainClass = $fabricJson.mainClass
if (-not $mainClass) { $mainClass = 'net.fabricmc.loader.impl.launch.knot.KnotClient' }

Say "player: $Player | memory: $Mem | gameDir: $GameDir"
Write-Host ""

Push-Location $GameDir
try {
    $ErrorActionPreference = 'Continue'   # game writes to stderr - never abort on it
    # the game prints UTF-8 to stdout/stderr - decode it as UTF-8 (no mojibake)
    try {
        [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
        [Console]::ErrorEncoding  = New-Object System.Text.UTF8Encoding $false
        $OutputEncoding = New-Object System.Text.UTF8Encoding $false
    } catch { }
    & $java @jvmArgs -cp ($classpath -join ';') $mainClass @gameArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
