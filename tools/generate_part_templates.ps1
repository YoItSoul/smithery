#
# Generates 16x16 grayscale PNG templates for each built-in PartType.
# Output: src/main/resources/assets/smithery/textures/item/part/<part>.png
#
# Pixel legend:
#   .  transparent
#   X  white  (full tint)         — main shape, fully tinted by material color
#   x  light  (192/255 = ~75%)    — soft highlight, slightly desaturated tint
#   o  mid    (160/255 = ~63%)    — body shadow
#   #  dark   (96/255  = ~38%)    — outline / strong shadow
#
# Replace these placeholders with real pixel art any time. The model JSONs only
# reference the file name, not its contents — so re-running this script (or
# overwriting the PNGs by hand) updates rendering immediately.
#

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "src\main\resources\assets\smithery\textures\item\part"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Template {
    param([string]$Name, [string[]]$Rows)

    if ($Rows.Length -ne 16) { throw "Template $Name must have 16 rows, got $($Rows.Length)" }

    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    for ($y = 0; $y -lt 16; $y++) {
        $row = $Rows[$y]
        if ($row.Length -ne 16) { throw "Row $y of $Name must be 16 chars, got $($row.Length)" }
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

# ---- Templates ----

Write-Template -Name "sword_blade" -Rows @(
    "................",
    "................",
    "..............#.",
    ".............#X#",
    "............#XX#",
    "...........#XXX#",
    "..........#XXXx#",
    ".........#XXXx#.",
    "........#XXXx#..",
    ".......#XXXx#...",
    "......#XXXx#....",
    ".....#XXXx#.....",
    "....#XXXo#......",
    "...#XXX##.......",
    "....#X#.........",
    ".....#.........."
)

Write-Template -Name "guard" -Rows @(
    "................",
    "................",
    "................",
    "................",
    "................",
    ".##############.",
    "#XXXXXXXXXXXXXX#",
    "#XxxxxxxxxxxxxX#",
    "#XxxxoooooxxxxX#",
    "#XxxxxxxxxxxxxX#",
    "#XXXXXXXXXXXXXX#",
    ".##############.",
    "................",
    "................",
    "................",
    "................"
)

Write-Template -Name "handle" -Rows @(
    "......##........",
    ".....#XX#.......",
    ".....#Xx#.......",
    ".....#Xx#.......",
    ".....#Xx#.......",
    ".....#Xx#.......",
    ".....#Xx#.......",
    ".....#Xo#.......",
    ".....#Xo#.......",
    ".....#Xo#.......",
    ".....#Xo#.......",
    ".....#Xo#.......",
    ".....#Xo#.......",
    "....#XXXX#......",
    "....#XXXX#......",
    ".....####......."
)

Write-Template -Name "binder" -Rows @(
    "................",
    "................",
    "................",
    "................",
    "....########....",
    "...#XXXXXXXX#...",
    "..#XxxxxxxxxX#..",
    "..#XxxxoooxxX#..",
    "..#XxxxxxxxxX#..",
    "...#XXXXXXXX#...",
    "....########....",
    "................",
    "................",
    "................",
    "................",
    "................"
)

Write-Template -Name "pick_head" -Rows @(
    "................",
    ".##..........##.",
    "#XX#........#XX#",
    "#XxX#......#XxX#",
    "#XxxX#....#XxxX#",
    ".#XxxX#..#XxxX#.",
    "..#XxxXXXXxxX#..",
    "...#XxxXXxxX#...",
    "....#XxxxxX#....",
    ".....#XxxX#.....",
    "......#XX#......",
    ".......##.......",
    "................",
    "................",
    "................",
    "................"
)

Write-Host "done."
