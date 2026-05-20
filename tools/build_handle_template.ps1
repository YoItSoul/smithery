#
# Builds the handle part template from the vanilla stick texture: grayscale + invert
# so the wood-toned source becomes a bright template that takes material tints cleanly.
# One-shot tool — re-run only if the source stick.png changes.
#

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $env:TEMP "stick.png"
$dst  = Join-Path $root "src\main\resources\assets\smithery\textures\item\part\handle.png"

if (-not (Test-Path $src)) { throw "missing source: $src (extract assets/minecraft/textures/item/stick.png first)" }

$bmpSrc = [System.Drawing.Bitmap]::FromFile($src)
$bmp = New-Object System.Drawing.Bitmap $bmpSrc.Width, $bmpSrc.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($y = 0; $y -lt $bmpSrc.Height; $y++) {
    for ($x = 0; $x -lt $bmpSrc.Width; $x++) {
        $c = $bmpSrc.GetPixel($x, $y)
        $gray = [int][Math]::Round(0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
        if ($gray -lt 0) { $gray = 0 } elseif ($gray -gt 255) { $gray = 255 }
        $inv = 255 - $gray
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $inv, $inv, $inv))
    }
}

$bmpSrc.Dispose()
$tmp = "$dst.tmp"
$bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Move-Item -Force -Path $tmp -Destination $dst
Write-Host "wrote $dst"
