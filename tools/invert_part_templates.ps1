#
# Inverts the RGB channels of part template PNGs in place. Alpha is preserved.
# Use this when your template source art is dark — invert it so light areas take
# the material tint cleanly (dark areas become outline/shadow detail).
#

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$targets = @(
    "src\main\resources\assets\smithery\textures\item\part\sword_blade.png",
    "src\main\resources\assets\smithery\textures\item\part\guard.png",
    "src\main\resources\assets\smithery\textures\item\part\binder.png"
)

foreach ($rel in $targets) {
    $path = Join-Path $root $rel
    if (-not (Test-Path $path)) {
        Write-Host "skipped (missing): $rel"
        continue
    }

    $src = [System.Drawing.Bitmap]::FromFile($path)
    $bmp = New-Object System.Drawing.Bitmap $src.Width, $src.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, 255 - $c.R, 255 - $c.G, 255 - $c.B))
        }
    }

    $src.Dispose()
    $tmp = "$path.tmp"
    $bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Move-Item -Force -Path $tmp -Destination $path
    Write-Host "inverted $rel"
}

Write-Host "done."
