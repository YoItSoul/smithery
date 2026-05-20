#
# Generates the built-in en_us.json lang file from a single source of truth.
#
# Model JSONs and item-definition JSONs are NO LONGER generated to disk — they're served
# at runtime by SmitheryGeneratedPack, regenerated on every resource reload from live
# registry state. Adding a material via datapack JSON works without any static asset files.
#
# This script only exists for built-in lang entries. Datapack-added materials should ship
# their own lang files (or rely on the raw translation keys being shown).
#

$root = Split-Path -Parent $PSScriptRoot
$langFile = Join-Path $root "src\main\resources\assets\smithery\lang\en_us.json"

# Keep these in sync with content/ classes (SmitheryMaterials, SmitheryPartTypes,
# SmitheryToolTypes, SmitheryModifiers, SmitherySynergies)
$materials = @{
    'wood'   = 'Wood'
    'copper' = 'Copper'
    'gold'   = 'Gold'
    'iron'   = 'Iron'
}
$partTypes = @{
    'sword_blade' = 'Sword Blade'
    'guard'       = 'Guard'
    'handle'      = 'Handle'
    'binder'      = 'Binder'
    'pick_head'   = 'Pick Head'
}
$toolTypes = @{
    'sword'   = 'Sword'
    'pickaxe' = 'Pickaxe'
}
$modifierNames = @{
    'sharp'             = 'Sharp'
    'magnetized'        = 'Magnetized'
    'verdant'           = 'Verdant'
    'corrosive'         = 'Corrosive'
    'lucky_strike'      = 'Lucky Strike'
    'gilded'            = 'Gilded'
    'nether_sharpened'  = 'Nether Sharpened'
}
$synergyNames = @{
    'galvanic'      = 'Galvanic'
    'gilded'        = 'Gilded'
    'verdant_veil'  = 'Verdant Veil'
}

$lang = [ordered]@{}

# Creative tabs
$lang['itemGroup.smithery.parts']   = 'Smithery Parts'
$lang['itemGroup.smithery.tools']   = 'Smithery Tools'
$lang['itemGroup.smithery.forge']   = 'Smithery Forge'

# Forge blocks
$lang['block.smithery.forge_controller'] = 'Forge Controller'
$lang['block.smithery.forge_fuel_port']  = 'Forge Fuel Port'
$lang['block.smithery.forge_drain']      = 'Forge Drain'

# Item name template + tooltip strings
$lang['item.smithery.part_combo']             = '%1$s %2$s'
$lang['tooltip.smithery.part.harvest_level']  = 'Harvest Level: %s'
$lang['tooltip.smithery.part.modifier_slots'] = 'Modifier Slots: %s'
$lang['tooltip.smithery.part.in_tool']        = 'In %s:'
$lang['tooltip.smithery.tool.uncomposed']     = 'Uncomposed — assemble at a crafting table'
$lang['tooltip.smithery.tool.parts']          = 'Parts:'
$lang['tooltip.smithery.tool.modifiers']      = 'Modifiers:'
$lang['tooltip.smithery.tool.synergies']      = 'Synergies:'

foreach ($k in $materials.Keys) { $lang["smithery.material.smithery.$k"] = $materials[$k] }
foreach ($k in $partTypes.Keys) { $lang["smithery.part.smithery.$k"]     = $partTypes[$k] }
foreach ($k in $toolTypes.Keys) { $lang["smithery.tool.smithery.$k"]     = $toolTypes[$k] }
foreach ($k in $modifierNames.Keys) { $lang["smithery.modifier.smithery.$k"] = $modifierNames[$k] }
foreach ($k in $synergyNames.Keys)  { $lang["smithery.synergy.smithery.$k"]  = $synergyNames[$k] }

# Per-item fallback entries (also produced by item.smithery.part_combo, but keeps /give
# autocomplete clean)
foreach ($mat in $materials.Keys) {
    foreach ($part in $partTypes.Keys) {
        $lang["item.smithery.${mat}_${part}"] = "$($materials[$mat]) $($partTypes[$part])"
    }
}
foreach ($tool in $toolTypes.Keys) {
    $lang["item.smithery.$tool"] = $toolTypes[$tool]
}

$lang | ConvertTo-Json -Depth 4 | Out-File -FilePath $langFile -Encoding utf8 -NoNewline
Write-Host "wrote $langFile ($($lang.Count) entries)"
