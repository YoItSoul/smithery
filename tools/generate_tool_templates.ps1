#
# Generates 16x16 grayscale PNG templates for tools (sword, pickaxe).
# Output: src/main/resources/assets/smithery/textures/item/tool/<tool>.png
#
# Same pixel legend as part templates:
#   .  transparent
#   X  white  (full tint)
#   x  light  (~75%)
#   o  mid    (~63%)
#   #  dark   (outline)
#

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "src\main\resources\assets\smithery\textures\item\tool"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Template {
    param([string]$Name, [string[]]$Rows)

    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    for ($y = 0; $y -lt 16; $y++) {
        $row = $Rows[$y]
        for ($x = 0; $x -lt 16; $x++) {
            $c = $row[$x]
            if     ($c -eq '.') { $color = [System.Drawing.Color]::FromArgb(0,   0,   0,   0)   }
            elseif ($c -eq 'X') { $color = [System.Drawing.Color]::FromArgb(255, 255, 255, 255) }
            elseif ($c -eq 'x') { $color = [System.Drawing.Color]::FromArgb(255, 192, 192, 192) }
            elseif ($c -eq 'o') { $color = [System.Drawing.Color]::FromArgb(255, 160, 160, 160) }
            elseif ($c -eq '#') { $color = [System.Drawing.Color]::FromArgb(255,  96,  96,  96) }
            else { throw "Unknown pixel char '$c' in $Name row $y" }
            $bmp.SetPixel($x, $y, $color)
        }
    }

    $outPath = Join-Path $outDir "$Name.png"
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "wrote $outPath"
}

# Full sword shape — single tinted layer for now. Composite (per-part) rendering is a
# follow-up; for v1 the tool just tints by the primary additive material.
Write-Template -Name "sword" -Rows @(
    "............X...",
    "...........#X#..",
    "..........#XX#..",
    ".........#XXX#..",
    "........#XXXx#..",
    ".......#XXXx#...",
    "......#XXXx#....",
    ".....#XXXx#.....",
    "....#XXXx#.x....",
    "...#XXXx#x#.....",
    "..#XXXxX#.......",
    ".#XXX##.........",
    "#XX##...........",
    "##X#............",
    ".##.............",
    "................"
)

Write-Template -Name "pickaxe" -Rows @(
    "..XXXXXXXXXX....",
    ".X##X##X##X#X...",
    "XXXxoxoxoxxXX...",
    ".XXxxxxxxxxX....",
    "...X##..........",
    "...#X#..........",
    "....X#..........",
    "....#X..........",
    ".....X#.........",
    ".....#X.........",
    "......X#........",
    "......#X........",
    ".......X#.......",
    ".......#X.......",
    "........X#......",
    "........#.......",
    "................"
)

Write-Host "done."
