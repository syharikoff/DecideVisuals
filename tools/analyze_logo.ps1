# Analyzes the logo photo: luminance histogram + bounding box of the logo.
# ASCII-only on purpose: PowerShell 5.1 reads .ps1 without BOM as ANSI and
# cyrillic comments would break the parse.
param(
    [string]$Path = "C:\Users\user\Desktop\3D\photo_2026-09-21_23-49-23.jpg"
)

Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile($Path)
Write-Host ("source: {0}x{1}" -f $img.Width, $img.Height)

# Downscale for analysis; the logo edge stays smooth enough at this size
$W = 200; $H = 200
$small = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($small)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $W, $H)
$g.Dispose()

# luminance histogram over 32 buckets
$hist = New-Object int[] 32
$lum = New-Object int[] ($W * $H)
for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $c = $small.GetPixel($x, $y)
        # relative luminance, ignores the slight warm tint of the light
        $l = [int](0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
        $lum[$y * $W + $x] = $l
        $hist[[int]($l / 8)]++
    }
}

Write-Host "`n--- luminance histogram (bucket = 8 levels) ---"
for ($i = 0; $i -lt 32; $i++) {
    if ($hist[$i] -gt 0) {
        $bar = '#' * [int](60 * $hist[$i] / ($hist | Measure-Object -Maximum).Maximum)
        Write-Host ("{0,3}-{1,-3} {2,6} {3}" -f ($i * 8), ($i * 8 + 7), $hist[$i], $bar)
    }
}

# bounding box for a few candidate thresholds
Write-Host "`n--- bounding box per threshold ---"
foreach ($t in @(60, 80, 100, 120, 140, 160, 180)) {
    $minX = 9999; $maxX = -1; $minY = 9999; $maxY = -1; $cnt = 0
    for ($y = 0; $y -lt $H; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            if ($lum[$y * $W + $x] -gt $t) {
                $cnt++
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($maxX -ge 0) {
        Write-Host ("t>{0,3}: px={1,6}  bbox=({2},{3})-({4},{5})  w={6} h={7}" -f `
            $t, $cnt, $minX, $minY, $maxX, $maxY, ($maxX - $minX + 1), ($maxY - $minY + 1))
    }
}

$small.Dispose()
$img.Dispose()