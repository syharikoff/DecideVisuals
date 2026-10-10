# Builds a 3D cosmetic from the logo photo: silhouette -> extruded cubes ->
# Bedrock geometry JSON + embedded texture, written as cosmetic_<id>.json.
#
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 without BOM as ANSI, and
# cyrillic inside the file would break the parse (same trap as rebuild.bat).
param(
    [string]$Photo = "C:\Users\user\Desktop\3D\photo_2026-09-21_23-49-23.jpg",
    [string]$OutDir = "C:\work6\DecideVisuals-main\src\client\resources\assets\decide\cosmetics\models",
    [string]$OutFile = "cosmetic_63.json",
    [int]$Index = 63,

    # silhouette sampling
    [int]$Analyze = 200,     # working resolution for the mask
    [int]$Threshold = 100,   # logo is bright, background is dark
    [int]$Cols = 36,         # model grid width
    [int]$Depth = 3,         # extrusion depth in blocks units (1 unit = 1/16)
    [double]$WidthUnits = 16.0,

    [int]$TexW = 256,
    [int]$TexH = 256,
    [switch]$FlipX
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# ───────────────────────────────────────────────────────1. load + clamp ──
# The photo is a 16-bit JPEG, so channels can exceed 255. Clamp before using
# them as luminance or everything below falls off.
$img = [System.Drawing.Image]::FromFile($Photo)
Write-Host ("source: {0}x{1}  pixelformat={2}" -f $img.Width, $img.Height, $img.PixelFormat)

$A = $Analyze
$work = New-Object System.Drawing.Bitmap($A, $A)
$g = [System.Drawing.Graphics]::FromImage($work)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $A, $A)
$g.Dispose()

$lum = New-Object 'int[]' ($A * $A)
for ($y = 0; $y -lt $A; $y++) {
    for ($x = 0; $x -lt $A; $x++) {
        $c = $work.GetPixel($x, $y)
        $r = [Math]::Min(255, $c.R); $gg = [Math]::Min(255, $c.G); $b = [Math]::Min(255, $c.B)
        $lum[$y * $A + $x] = [int](0.299 * $r + 0.587 * $gg + 0.114 * $b)
    }
}

# ────────────────────────────────────── 2. flood fill background from edges ──
# A plain threshold would punch holes into the logo: the lower-left bevel is
# in shadow and falls under the cut. Flooding the dark area from the borders
# and inverting the result yields a solid silhouette.
$outside = New-Object 'bool[]' ($A * $A)
$stack = New-Object 'System.Collections.Generic.Stack[int]'
for ($x = 0; $x -lt $A; $x++) {
    $stack.Push($x)                     # top
    $stack.Push(($A - 1) * $A + $x)     # bottom
}
for ($y = 0; $y -lt $A; $y++) {
    $stack.Push($y * $A)                # left
    $stack.Push($y * $A + $A - 1)       # right
}

while ($stack.Count -gt 0) {
    $i = $stack.Pop()
    if ($outside[$i]) { continue }
    if ($lum[$i] -gt $Threshold) { continue }
    $outside[$i] = $true
    $px = $i % $A; $py = [int]($i / $A)
    if ($px -gt 0)      { $stack.Push($i - 1) }
    if ($px -lt $A - 1) { $stack.Push($i + 1) }
    if ($py -gt 0)      { $stack.Push($i - $A) }
    if ($py -lt $A - 1) { $stack.Push($i + $A) }
}

# ─────────────────────────────────────────────── 3. bounding box + grid ──
$minX = $A; $maxX = -1; $minY = $A; $maxY = -1
for ($y = 0; $y -lt $A; $y++) {
    for ($x = 0; $x -lt $A; $x++) {
        if (-not $outside[$y * $A + $x]) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -lt 0) { throw "logo not found at threshold $Threshold" }

$bw = $maxX - $minX + 1
$bh = $maxY - $minY + 1
Write-Host ("bbox=({0},{1})-({2},{3})  {4}x{5}  aspect={6:N3}" -f `
    $minX, $minY, $maxX, $maxY, $bw, $bh, ($bw / $bh))

$rows = [int][Math]::Round($Cols * $bh / $bw)
Write-Host ("grid: {0} x {1} cells" -f $Cols, $rows)

# Art strip keeps the photo's own aspect, otherwise the logo is squashed
$ArtH = [int][Math]::Round($TexW * $bh / $bw)
if ($ArtH -gt $TexH - 16) { $ArtH = $TexH - 16 }
Write-Host ("art strip: {0}x{1} (aspect {2:N3})" -f $TexW, $ArtH, ($bw / $bh))

$grid = New-Object 'bool[]' ($Cols * $rows)
for ($r = 0; $r -lt $rows; $r++) {
    for ($c = 0; $c -lt $Cols; $c++) {
        # sample the middle 60% of the cell: edges are anti-aliased anyway and
        # a full-cell average thins the outline
        $x0 = $minX + [int]($c * $bw / $Cols)
        $x1 = $minX + [int](($c + 1) * $bw / $Cols)
        $y0 = $minY + [int]($r * $bh / $rows)
        $y1 = $minY + [int](($r + 1) * $bh / $rows)
        $padX = [int](($x1 - $x0) * 0.2); $padY = [int](($y1 - $y0) * 0.2)
        if ($padX -lt 1) { $padX = 1 }; if ($padY -lt 1) { $padY = 1 }

        $hits = 0; $tot = 0
        for ($y = $y0 + $padY; $y -lt ($y1 - $padY); $y += 2) {
            for ($x = $x0 + $padX; $x -lt ($x1 - $padX); $x += 2) {
                $tot++
                if (-not $outside[$y * $A + $x]) { $hits++ }
            }
        }
        if ($tot -gt 0 -and $hits * 2 -ge $tot) { $grid[$r * $Cols + $c] = $true }
    }
}

$filled = 0
foreach ($v in $grid) { if ($v) { $filled++ } }
Write-Host ("filled cells: {0} / {1} ({2:P1})" -f $filled, ($Cols * $rows), ($filled / ($Cols * $rows)))

# ────────────────────────────────────── 4. drop lonely speckles + holes ──
# Single cells surrounded by nothing are JPEG noise, not geometry.
for ($pass = 0; $pass -lt 2; $pass++) {
    $copy = $grid.Clone()
    for ($r = 1; $r -lt $rows - 1; $r++) {
        for ($c = 1; $c -lt $Cols - 1; $c++) {
            $i = $r * $Cols + $c
            if (-not $copy[$i]) { continue }
            $n = 0
            foreach ($d in @(-1, 1, -$Cols, $Cols)) { if ($copy[$i + $d]) { $n++ } }
            if ($n -eq 0) { $grid[$i] = $false }
        }
    }
}

# ───────────────────────────────────────── 5. greedy rectangle merging ──
# One cube per cell would blow the vertex budget. Merging runs of cells into
# rectangles keeps the silhouette while cutting the cube count by ~2-3x.
$used = New-Object 'bool[]' ($Cols * $rows)
$rects = New-Object System.Collections.ArrayList

for ($r = 0; $r -lt $rows; $r++) {
    for ($c = 0; $c -lt $Cols; $c++) {
        $i = $r * $Cols + $c
        if (-not $grid[$i] -or $used[$i]) { continue }

        $w = 1
        while (($c + $w) -lt $Cols -and $grid[($r * $Cols) + $c + $w] -and -not $used[($r * $Cols) + $c + $w]) { $w++ }

        $h = 1
        $grow = $true
        while ($grow -and ($r + $h) -lt $rows) {
            $rowBase = ($r + $h) * $Cols + $c
            for ($k = 0; $k -lt $w; $k++) {
                if (-not $grid[$rowBase + $k] -or $used[$rowBase + $k]) { $grow = $false; break }
            }
            if ($grow) { $h++ }
        }

        for ($rr = $r; $rr -lt ($r + $h); $rr++) {
            for ($cc = $c; $cc -lt ($c + $w); $cc++) { $used[$rr * $Cols + $cc] = $true }
        }
        [void]$rects.Add(@($c, $r, $w, $h))
    }
}
Write-Host ("rectangles after merge: {0} (was {1} cells)" -f $rects.Count, $filled)

# ─────────────────────────────────────────────────────── 6. the texture ──
# Art fills the top strip, a dark metal swatch sits in the corner for the
# extruded side and back faces.
$tex = New-Object System.Drawing.Bitmap($TexW, $TexH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$tg = [System.Drawing.Graphics]::FromImage($tex)
$tg.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))

$srcRect = New-Object System.Drawing.Rectangle($minX, $minY, $bw, $bh)
$dstRect = New-Object System.Drawing.Rectangle(0, 0, $TexW, $ArtH)
$tg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$tg.DrawImage($work, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)

# mark every cell we turned into a cube, so UV placement is visible at a glance
$mark = New-Object System.Drawing.Bitmap($TexW, $ArtH)
$mg = [System.Drawing.Graphics]::FromImage($mark)
$mg.Clear([System.Drawing.Color]::FromArgb(255, 0, 0, 0))
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90, 255, 60, 60)), 1
foreach ($rect in $rects) {
    $x = $rect[0] / $Cols * $TexW
    $y = $rect[1] / $rows * $ArtH
    $w = $rect[2] / $Cols * $TexW
    $h = $rect[3] / $rows * $ArtH
    $mg.DrawRectangle($pen, [float]$x, [float]$y, [float]$w, [float]$h)
}
$pen.Dispose()
$mg.Dispose()

$preview = New-Object System.Drawing.Bitmap($TexW, $ArtH)
$pv = [System.Drawing.Graphics]::FromImage($preview)
$pv.DrawImage($tex, (New-Object System.Drawing.Rectangle(0, 0, $TexW, $ArtH)), 0, 0, $TexW, $ArtH, [System.Drawing.GraphicsUnit]::Pixel)
$pv.DrawImageUnscaled($mark, 0, 0)
$pv.Dispose(); $mark.Dispose()

# swatch used by every non-art face
$tg.FillRectangle([System.Drawing.Brushes]::DimGray, $TexW - 16, $TexH - 16, 16, 16)
$tg.Dispose()

$texPath = Join-Path $env:TEMP "logo_tex.png"
$tex.Save($texPath, [System.Drawing.Imaging.ImageFormat]::Png)
$prevPath = Join-Path $env:TEMP "logo_grid.png"
$preview.Save($prevPath, [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()
$work.Dispose(); $img.Dispose()
Write-Host ("texture: {0}x{1}, art 0,0 {2}x{3}, swatch {4},{5}" -f `
    $TexW, $TexH, $TexW, $ArtH, ($TexW - 16), ($TexH - 16))

# ────────────────────────────────────────────────── 7. geometry: cubes ──
$cellW = $WidthUnits / $Cols
$HeightUnits = $WidthUnits * $rows / $Cols
$cellH = $HeightUnits / $rows

function F([double]$v) {
    # invariant, no trailing zeros - keeps the json small and stable
    return $v.ToString('0.####', [System.Globalization.CultureInfo]::InvariantCulture)
}

$swatchU = $TexW - 16
$swatchV = $TexH - 16
$cubes = New-Object System.Collections.ArrayList

foreach ($rect in $rects) {
    $c = $rect[0]; $r = $rect[1]; $w = $rect[2]; $h = $rect[3]

    # Bedrock origin/size. buildFaceVertices maps u=0 to maxX, so a cell's
    # texture-left must sit at the cell's high-X side: column 0 -> origin 0.
    # -FlipX does the opposite and is only needed if the letter renders
    # backwards on screen.
    if ($FlipX) { $bx = ($Cols - $c - $w) * $cellW } else { $bx = $c * $cellW }
    $by = ($rows - $r - $h) * $cellH

    $origin = "[{0},{1},0]" -f (F $bx), (F $by)
    $size = "[{0},{1},{2}]" -f (F ($w * $cellW)), (F ($h * $cellH)), $Depth

    # art UVs follow the cell rectangle inside the cropped photo
    $u0 = $c / $Cols * $TexW
    $v0 = $r / $rows * $ArtH
    $uw = $w / $Cols * $TexW
    $vh = $h / $rows * $ArtH

    # only faces that can actually be seen: the front always, plus every
    # side whose neighbour cell is empty. The renderer skips missing faces,
    # so interior quads are simply left out.
    $faces = New-Object System.Collections.ArrayList
    [void]$faces.Add(('"north":{{"uv":[{0},{1}],"uv_size":[{2},{3}]}}' -f `
        (F $u0), (F $v0), (F $uw), (F $vh)))

    $hasL = ($c -gt 0)     -and $grid[$r * $Cols + $c - 1]
    $hasR = ($c + $w) -lt $Cols -and $grid[$r * $Cols + $c + $w]
    $hasU = ($r -gt 0)     -and $grid[($r - 1) * $Cols + $c]
    $hasD = ($r + $h) -lt $rows -and $grid[($r + $h) * $Cols + $c]

    $side = '"uv":[{0},{1}],"uv_size":[8,8]' -f $swatchU, $swatchV
    if (-not $hasL) { [void]$faces.Add("`"west`":{$side}") }
    if (-not $hasR) { [void]$faces.Add("`"east`":{$side}") }
    if (-not $hasU) { [void]$faces.Add("`"up`":{$side}") }
    if (-not $hasD) { [void]$faces.Add("`"down`":{$side}") }
    if ($hasL -or $hasR -or $hasU -or $hasD) { [void]$faces.Add("`"south`":{$side}") }

    [void]$cubes.Add(("{{`"origin`":{0},`"size`":{1},`"uv`":{{{2}}}}}" -f `
        $origin, $size, ($faces -join ',')))
}

Write-Host ("cubes: {0}  faces: ~{1}" -f $cubes.Count, `
    ($rects.Count + ($rects | Where-Object {
        $c = $_[0]; $r = $_[1]; $w = $_[2]; $h = $_[3]
        (-not (($c -gt 0) -and $grid[$r * $Cols + $c - 1])) -or
        (-not (($c + $w) -lt $Cols -and $grid[$r * $Cols + $c + $w])) -or
        (-not (($r -gt 0) -and $grid[($r - 1) * $Cols + $c])) -or
        (-not (($r + $h) -lt $rows -and $grid[($r + $h) * $Cols + $c]))
    }).Count * 4))

$geometry = @"
{"format_version":"1.8.0","minecraft:geometry":[{"description":{"identifier":"geometry.decidevisuals_logo","texture_width":$TexW,"texture_height":$TexH,"visible_bounds_width":4,"visible_bounds_height":4,"visible_bounds_offset":[0,1,0]},"bones":[{"name":"logo","pivot":[$(F ($WidthUnits / 2)),0,0],"cubes":[$($cubes -join ',')]}]}]}
"@

# ──────────────────────────────────────────────────── 8. cosmetic entry ──
$bytes = [System.IO.File]::ReadAllBytes($texPath)
$b64 = [Convert]::ToBase64String($bytes)

$entry = @"
{"name":"DecideVisuals Logo","id":$Index,"type":"hat","model":$geometry,"texture":"$b64","config":{"pos":3,"scale":1.0,"x":0,"y":0.35,"z":0,"yaw":0,"pitch":0,"roll":0,"height":0.1},"height":0.1,"scale":1,"previewY":0.2,"previewScale":1.4}
"@

$outPath = Join-Path $OutDir $OutFile
[System.IO.File]::WriteAllText($outPath, $entry, (New-Object System.Text.UTF8Encoding $false))
Write-Host ("written: {0} ({1:N1} KB)" -f $outPath, ((Get-Item $outPath).Length / 1KB))
Write-Host ("texture kept at: {0}" -f $texPath)