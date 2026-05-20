#
# In-place converts every PNG under exported/ to grayscale, preserving alpha.
# Uses standard luminance: Y = 0.299*R + 0.587*G + 0.114*B
#

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$dirs = @(
    (Join-Path $root "exported\items"),
    (Join-Path $root "exported\blocks")
)

foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) { continue }
    Get-ChildItem -Path $dir -Filter *.png -File | ForEach-Object {
        $path = $_.FullName

        $src = [System.Drawing.Bitmap]::FromFile($path)
        $bmp = New-Object System.Drawing.Bitmap $src.Width, $src.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

        for ($y = 0; $y -lt $src.Height; $y++) {
            for ($x = 0; $x -lt $src.Width; $x++) {
                $c = $src.GetPixel($x, $y)
                $gray = [int][Math]::Round(0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
                if ($gray -lt 0) { $gray = 0 } elseif ($gray -gt 255) { $gray = 255 }
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $gray, $gray, $gray))
            }
        }

        $src.Dispose()
        # Save to a temp path, then replace, to avoid lock-on-self issues.
        $tmp = "$path.tmp"
        $bmp.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Move-Item -Force -Path $tmp -Destination $path

        Write-Host "grayscaled $($_.Name)"
    }
}

Write-Host "done."
