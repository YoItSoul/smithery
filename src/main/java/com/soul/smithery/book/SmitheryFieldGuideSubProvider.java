package com.soul.smithery.book;

import com.klikli_dev.modonomicon.api.datagen.CategoryProvider;
import com.klikli_dev.modonomicon.api.datagen.EntryBackground;
import com.klikli_dev.modonomicon.api.datagen.EntryProvider;
import com.klikli_dev.modonomicon.api.datagen.IndexModeCategoryProvider;
import com.klikli_dev.modonomicon.api.datagen.SingleBookSubProvider;
import com.klikli_dev.modonomicon.api.datagen.book.BookIconModel;
import com.klikli_dev.modonomicon.api.datagen.book.BookModel;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookCraftingRecipePageModel;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookMultiblockPageModel;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookSpotlightPageModel;
import com.klikli_dev.modonomicon.api.datagen.book.page.BookTextPageModel;
import com.klikli_dev.modonomicon.book.BookDisplayMode;
import com.klikli_dev.modonomicon.client.gui.book.theme.GuiSprite;
import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.api.tool.ToolType;
import com.soul.smithery.content.SmitheryPartTypes;
import com.soul.smithery.registry.SmitheryBlocks;
import com.soul.smithery.registry.SmitheryItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Smithery field guide, in Botania-style INDEX display mode: categories are a list, entries
 * are a list, and pages mix item spotlights, crafting recipes, a 3D multiblock preview, and
 * markdown text. The materials category generates one entry per registered material with stats
 * and trait grants pulled live from the registry at datagen — the book can never drift from
 * the code.
 */
public class SmitheryFieldGuideSubProvider extends SingleBookSubProvider {

    /**
     * Constructs the sub-provider for the smithery book id with a default-language sink.
     */
    public SmitheryFieldGuideSubProvider(BiConsumer<String, String> defaultLang) {
        super(SmitheryBook.BOOK_ID, Smithery.MODID, defaultLang);
    }

    @Override
    protected String bookName() {
        return "Smithery Field Guide";
    }

    @Override
    protected String bookTooltip() {
        return "Melting, casting, pressing, assembling — all of it.";
    }

    @Override
    protected void registerDefaultMacros() {
    }

    @Override
    protected void generateCategories() {
        add(new GettingStartedCategory(this).generate());
        add(new ForgeCategory(this).generate());
        add(new PartPressCategory(this).generate());
        add(new ModifiersCategory(this).generate());
        add(new MaterialsCategory(this).generate());
    }

    @Override
    protected BookModel additionalSetup(BookModel book) {
        return book
                .withDisplayMode(BookDisplayMode.INDEX)
                .withCreativeTab(Identifier.fromNamespaceAndPath(Smithery.MODID, "blocks_tab"));
    }

    private static java.util.Map<String, String> langCache;

    /**
     * The hand-maintained en_us.json, loaded from the datagen classpath. Lets the book reuse
     * the exact modifier names and descriptions players see on tooltips instead of
     * restating (and drifting from) them.
     */
    static java.util.Map<String, String> modLang() {
        if (langCache == null) {
            langCache = new java.util.HashMap<>();
            try (var in = SmitheryFieldGuideSubProvider.class
                    .getResourceAsStream("/assets/" + Smithery.MODID + "/lang/en_us.json")) {
                if (in != null) {
                    var json = com.google.gson.JsonParser.parseReader(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        langCache.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Field guide could not read en_us.json", e);
            }
        }
        return langCache;
    }

    /** Display name of a modifier, from the same lang key its tooltip uses. */
    static String modifierName(String path) {
        return modLang().getOrDefault("smithery.modifier.smithery." + path, titleCase(path));
    }

    /** Tooltip description of a modifier, from lang. */
    static String modifierDescription(String path) {
        return modLang().getOrDefault("smithery.modifier.smithery." + path + ".description", "");
    }

    /** Title-cases an identifier path: {@code fire_resistant} → {@code Fire Resistant}. */
    static String titleCase(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (char c : path.toCharArray()) {
            if (c == '_') { sb.append(' '); upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ page helpers

    /**
     * Entry base with fluent page helpers. Every page gets a unique anchor — anchorless pages
     * share one lang key and silently overwrite each other's text.
     */
    static abstract class RichEntry extends EntryProvider {
        private int nextAnchor = 0;

        RichEntry(CategoryProvider parent) {
            super(parent);
        }

        private String anchor() { return "page" + (nextAnchor++); }

        /** Markdown text page. */
        protected void text(String title, String markdown) {
            this.page(anchor(), () -> BookTextPageModel.create()
                    .withTitle(this.context().pageTitle())
                    .withText(this.context().pageText()));
            this.pageTitle(title);
            this.pageText(markdown);
        }

        /** Item spotlight page: the item rendered large above markdown text. */
        protected void spotlight(ItemLike item, String title, String markdown) {
            this.page(anchor(), () -> BookSpotlightPageModel.create()
                    .withItem(item)
                    .withTitle(this.context().pageTitle())
                    .withText(this.context().pageText()));
            this.pageTitle(title);
            this.pageText(markdown);
        }

        /** Crafting recipe page rendering the live recipe grid for {@code recipeId}. */
        protected void recipe(String recipeId, String markdown) {
            this.page(anchor(), () -> BookCraftingRecipePageModel.create()
                    .withRecipeId1(Identifier.fromNamespaceAndPath(Smithery.MODID, recipeId))
                    .withText(this.context().pageText()));
            this.pageText(markdown);
        }

        /**
         * Spotlight page for one anvil modifier: the source item rendered large, the
         * modifier's tooltip name as title, its tooltip description plus how to apply it.
         */
        protected void modifier(ItemLike sourceItem, String modifierPath) {
            String descId = sourceItem.asItem().getDescriptionId();
            String itemName = titleCase(descId.substring(descId.lastIndexOf('.') + 1));
            spotlight(sourceItem, modifierName(modifierPath),
                    modifierDescription(modifierPath) + ".\n\n"
                    + "**Apply with:** " + itemName + " at an anvil.");
        }

        /** Multiblock page: rotating 3D preview plus the in-world ghost-projection button. */
        protected void multiblock(String multiblockPath, String name, String markdown) {
            this.page(anchor(), () -> BookMultiblockPageModel.create()
                    .withMultiblockId(Identifier.fromNamespaceAndPath(Smithery.MODID, multiblockPath))
                    .withMultiblockName(name)
                    .withText(this.context().pageText()));
            this.pageText(markdown);
        }

        @Override protected GuiSprite entryBackground() { return EntryBackground.SQUARE_GRAY; }
    }

    /** A rich entry declared inline: id, name, icon, and a body that emits pages. */
    static class InlineEntry extends RichEntry {
        private final String id;
        private final String name;
        private final ItemLike icon;
        private final java.util.function.Consumer<RichEntry> body;

        InlineEntry(CategoryProvider parent, String id, String name, ItemLike icon,
                    java.util.function.Consumer<RichEntry> body) {
            super(parent);
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.body = body;
        }

        @Override protected void generatePages() { body.accept(this); }
        @Override protected String entryName() { return name; }
        @Override protected BookIconModel entryIcon() { return BookIconModel.create(icon); }
        @Override protected String entryId() { return id; }
    }

    // ------------------------------------------------------------------ categories

    static class GettingStartedCategory extends IndexModeCategoryProvider {
        GettingStartedCategory(SmitheryFieldGuideSubProvider parent) { super(parent); }

        @Override protected void generateEntries() {
            add(new InlineEntry(this, "welcome", "Welcome to Smithery", Items.WRITABLE_BOOK, e -> {
                e.text("Tools From Parts",
                        "Smithery replaces fixed recipes with **parts**. Every tool and armor piece "
                        + "is assembled from interchangeable parts, and every part carries the stats "
                        + "and traits of its material.\n\n"
                        + "An **iron** pick head mines like iron. Put it on **slime** handles and the "
                        + "tool bounces back from its own mistakes. Materials matter more than shapes.");
                e.text("The Loop",
                        "1. Build a **Forge** and melt raw materials into molten metal\n"
                        + "2. **Cast** parts on the Casting Table — or **press** non-metals on the Part Press\n"
                        + "3. **Assemble** the parts in any crafting grid\n"
                        + "4. **Upgrade** at an anvil: modifiers, repairs, embossments\n\n"
                        + "Each step has its own chapter in this guide.");
            }).generate());

            add(new InlineEntry(this, "first_tool", "Your First Tool", Items.CRAFTING_TABLE, e -> {
                e.spotlight(SmitheryBlocks.PART_PRESS.get(), "No Forge Required",
                        "Before you can melt anything, the **Part Press** cuts parts from wood and "
                        + "flint. Press a pick head, two handles, and a binder from logs, then drop "
                        + "all four parts into a crafting grid together.\n\n"
                        + "The parts sort themselves — **no shape to memorize**.");
                e.text("Reading a Tool",
                        "Hover any smithery tool:\n\n"
                        + "- Hold **Shift** for stats, parts, modifiers and synergies\n"
                        + "- Hold **Shift + Ctrl** for every parameter\n\n"
                        + "Durability, mining speed and damage all come from the parts you chose. "
                        + "There is no such thing as a plain iron pickaxe — only *your* pickaxe.");
            }).generate());
        }

        @Override protected String categoryName() { return "Getting Started"; }
        @Override protected BookIconModel categoryIcon() { return BookIconModel.create(Items.WRITABLE_BOOK); }
        @Override public String categoryId() { return "getting_started"; }
    }

    static class ForgeCategory extends IndexModeCategoryProvider {
        ForgeCategory(SmitheryFieldGuideSubProvider parent) { super(parent); }

        @Override protected void generateEntries() {
            add(new InlineEntry(this, "building", "Building the Forge", SmitheryBlocks.FORGE_CONTROLLER.get(), e -> {
                e.multiblock("forge_small", "Smallest Forge",
                        "The smallest legal forge: a hollow shell of **Furnace Bricks** around one "
                        + "interior air block, with a **Controller**, a **Fuel Port** and a **Drain** "
                        + "in the walls.\n\nUse the anchor button to project this ghost into your world "
                        + "and build along it.");
                e.text("Scaling Up",
                        "The shell can be almost any shape:\n\n"
                        + "- Corners are **optional**\n"
                        + "- The top can be **open or closed** — closed heats 20% faster\n"
                        + "- Partial structures still run at reduced effectiveness\n\n"
                        + "Every interior air block adds one working slot and **1,000 mB** of molten "
                        + "storage. Right-click the controller to see everything at once.");
            }).generate());

            add(new InlineEntry(this, "fuel", "Fuel & Heat", SmitheryBlocks.FORGE_FUEL_PORT.get(), e -> {
                e.spotlight(SmitheryBlocks.FORGE_FUEL_PORT.get(), "Feeding the Fire",
                        "Fuel Ports hold **liquid fuel** — bucket it in or pipe it. Ports stacked "
                        + "vertically merge into one tank per fluid.\n\n"
                        + "- **Lava** burns toward 1,650°C\n"
                        + "- **Molten Blaze** reaches 3,500°C for the hottest melts");
                e.text("Temperature Matters",
                        "The forge heats toward its fuel's target and cools when starved. Every "
                        + "material has a melting point — **iron needs 1,538°C**, so a fresh lava "
                        + "forge warms up for a moment before iron starts to drip.\n\n"
                        + "Hotter forges melt *faster*, not just hotter. Blaze fuel pays for itself.");
            }).generate());

            add(new InlineEntry(this, "melting", "Melting", Items.IRON_INGOT, e -> {
                e.text("Items to Metal",
                        "Drop items into the forge interior, or feed them through an **Item Port**. "
                        + "Once the forge passes their melting point they liquefy:\n\n"
                        + "- Nugget: **16 mB**\n"
                        + "- Ingot: **144 mB**\n"
                        + "- Raw ore: **288 mB** — *double yield!*\n"
                        + "- Block: **1,296 mB**");
                e.text("A Warning About Mobs",
                        "Living creatures inside a hot forge are **scalded**, dripping fluid with "
                        + "every tick of damage. Most mobs render down to blood.\n\n"
                        + "Some render down to... other things. Experiment respectfully.");
            }).generate());

            add(new InlineEntry(this, "alloying", "Alloying", Items.NETHERITE_INGOT, e -> {
                e.text("Automatic Alloys",
                        "When the right molten metals share a hot enough forge, they combine on "
                        + "their own. **Netherite** forms from molten gold and ancient debris at "
                        + "2,200°C.\n\n"
                        + "Toggle auto-alloying with the **A** button on the controller screen if "
                        + "you want to keep your metals separate.");
                e.text("Finding Recipes",
                        "Alloy recipes are data-driven — modpacks add their own. Check **JEI** for "
                        + "the full list, and watch item tooltips: anything meltable shows what it "
                        + "melts into and which alloys want it.");
            }).generate());

            add(new InlineEntry(this, "draining", "Draining & Pipes", SmitheryBlocks.FORGE_DRAIN.get(), e -> {
                e.spotlight(SmitheryBlocks.FORGE_DRAIN.get(), "Getting Metal Out",
                        "Pick the output fluid by clicking its layer in the controller's tank, then:\n\n"
                        + "- **Bucket** it straight from the Drain, or\n"
                        + "- **Power the Drain with redstone** to pump into connected Fluid Pipes — "
                        + "5 mB per tick along the network, straight to your casting tables.");
            }).generate());

            add(new InlineEntry(this, "casting", "Sand Casting", SmitheryBlocks.CASTING_TABLE.get(), e -> {
                e.recipe("casting_sand",
                        "**Casting Sand** — the consumable half of sand casting. Eight sand around "
                        + "a clay ball makes eight.");
                e.spotlight(SmitheryBlocks.CASTING_TABLE.get(), "Impress a Shape",
                        "Place Casting Sand on a **Casting Table**, then press any existing part "
                        + "into the sand to impress its shape. Templates are never consumed.\n\n"
                        + "No part yet? A vanilla **ingot** impresses the ingot shape, **flint** a "
                        + "sharpening stone, a **brick** a polishing stone.");
                e.text("Pour, Cool, Brush",
                        "Pour molten metal onto the impressed sand — bucket or pipe, from above. "
                        + "It fills, then cools from glowing to solid.\n\n"
                        + "Sweep the sand away with a **brush** and take your part, or let a hopper "
                        + "collect it underneath.");
            }).generate());
        }

        @Override protected String categoryName() { return "The Forge"; }
        @Override protected BookIconModel categoryIcon() { return BookIconModel.create(SmitheryBlocks.FORGE_CONTROLLER.get()); }
        @Override public String categoryId() { return "forge"; }
    }

    static class PartPressCategory extends IndexModeCategoryProvider {
        PartPressCategory(SmitheryFieldGuideSubProvider parent) { super(parent); }

        @Override protected void generateEntries() {
            add(new InlineEntry(this, "press", "The Part Press", SmitheryBlocks.PART_PRESS.get(), e -> {
                e.spotlight(SmitheryBlocks.PART_PRESS.get(), "Parts Without Melting",
                        "Wood, flint, slime, resin and coral never melt — they get **cut**.\n\n"
                        + "Right-click the press with your input (logs, flint, slime balls, resin "
                        + "clumps, coral blocks), and right-click the frame to cycle which part "
                        + "shape it cuts.");
                e.text("Automation",
                        "A **redstone pulse** drives the press down and cuts the input into the "
                        + "selected part. Hoppers feed inputs and collect outputs while the press "
                        + "is open — a redstone clock turns it into a part factory.\n\n"
                        + "Early game this is your only source of handles and binders: wood first, "
                        + "metal later.");
            }).generate());
        }

        @Override protected String categoryName() { return "The Part Press"; }
        @Override protected BookIconModel categoryIcon() { return BookIconModel.create(SmitheryBlocks.PART_PRESS.get()); }
        @Override public String categoryId() { return "part_press"; }
    }

    static class ModifiersCategory extends IndexModeCategoryProvider {
        ModifiersCategory(SmitheryFieldGuideSubProvider parent) { super(parent); }

        @Override protected void generateEntries() {
            add(new InlineEntry(this, "how", "How Modifiers Work", Items.ANVIL, e -> {
                e.text("Slots",
                        "A tool's modifier capacity comes from its **binder** material; armor's "
                        + "from its **plates**:\n\n"
                        + "- Iron: 2 slots\n"
                        + "- Gold: 3 slots\n"
                        + "- Netherite: 4 slots\n"
                        + "- Bedrock: 5 slots\n\n"
                        + "Each modifier level spends one slot. Traits granted by materials are "
                        + "**free** and never spend slots.");
                e.text("Applying",
                        "Combine gear with a modifier item at any **anvil**. Costs scale per level, "
                        + "and part-way contributions are remembered — you can pay a level's cost "
                        + "across several visits.\n\n"
                        + "Vanilla enchanted books are refused; smithery gear speaks modifiers.");
            }).generate());

            add(new InlineEntry(this, "tool_mods", "Tool Modifiers", Items.NETHER_STAR, e -> {
                e.modifier(Items.QUARTZ, "sharp");
                e.modifier(Items.REDSTONE, "haste");
                e.modifier(Items.LAPIS_LAZULI, "lapis_blessing");
                e.modifier(Items.EMERALD, "silky");
                e.modifier(Items.SLIME_BALL, "knockback");
                e.modifier(Items.BLAZE_POWDER, "fiery");
                e.modifier(Items.OBSIDIAN, "reinforced");
                e.modifier(Items.STICKY_PISTON, "excavating");
                e.modifier(Items.NETHER_STAR, "nether_sharpened");
                e.modifier(Items.PHANTOM_MEMBRANE, "lunge");
                e.modifier(Items.ENDER_PEARL, "ender_affinity");
                e.modifier(Items.TOTEM_OF_UNDYING, "soulbound");
            }).generate());

            add(new InlineEntry(this, "armor_mods", "Armor Modifiers", Items.TURTLE_SCUTE, e -> {
                e.modifier(Items.AMETHYST_SHARD, "resistant");
                e.modifier(Items.MAGMA_CREAM, "fire_resistant");
                e.modifier(Items.TNT, "blast_resistant");
                e.modifier(Items.TURTLE_SCUTE, "projectile_resistant");
                e.modifier(Items.SUGAR, "speedy");
                e.modifier(Items.PISTON, "high_stride");
                e.modifier(Items.PRISMARINE_CRYSTALS, "amphibious");
                e.modifier(Items.MOSS_BLOCK, "restoring");
                e.modifier(Items.BLUE_ICE, "frost_walker");
                e.modifier(Items.BLAZE_ROD, "powerful");
                e.modifier(Items.FEATHER, "dexterous");
                e.modifier(Items.END_ROD, "telekinetic");
            }).generate());

            add(new InlineEntry(this, "repair", "Repair", Items.FLINT, e -> {
                e.text("Stones",
                        "Cast a **Sharpening Stone** (1 ingot) for tools or a **Polishing Stone** "
                        + "(2 ingots) for armor, then combine with your gear at an anvil.\n\n"
                        + "Each stone restores a quarter of max durability; the anvil only consumes "
                        + "what the damage needs.");
                e.text("Tier Matters",
                        "A stone only works gear at or below its material's harvest level — wood "
                        + "cannot hold an edge on netherite.\n\n"
                        + "Broken armor **never shatters**: it stays equipped, granting nothing, "
                        + "until you repair it back to life.");
            }).generate());

            add(new InlineEntry(this, "embossment", "Embossment", Items.GOLD_INGOT, e -> {
                e.text("Borrowed Traits",
                        "Place **any smithery part** in an anvil with a finished tool or armor "
                        + "piece to emboss it: the part's material grants its *traits* to the gear "
                        + "without changing any stats. The part is consumed.");
                e.text("Endlessly Replaceable",
                        "Re-embossing with a new part **replaces** the old donor, so grafted traits "
                        + "grow with your progression — emboss iron today, netherite next month.\n\n"
                        + "Embossed traits never override anything the gear already has; they only "
                        + "fill gaps.");
            }).generate());
        }

        @Override protected String categoryName() { return "Modifiers & Upgrades"; }
        @Override protected BookIconModel categoryIcon() { return BookIconModel.create(Items.ANVIL); }
        @Override public String categoryId() { return "modifiers"; }
    }

    static class MaterialsCategory extends IndexModeCategoryProvider {
        MaterialsCategory(SmitheryFieldGuideSubProvider parent) { super(parent); }

        @Override protected void generateEntries() {
            add(new InlineEntry(this, "traits_overview", "Traits & Synergies", Items.BOOK, e -> {
                e.text("Traits",
                        "Materials grant traits to specific tools and armor **for free** — no "
                        + "modifier slots spent. Iron pickaxes magnetize drops; slime boots bounce; "
                        + "blaze armor shrugs off fire.\n\n"
                        + "Every material's entry in this chapter lists its grants.");
                e.text("Synergies",
                        "Mixing specific materials in one tool unlocks bonus modifiers at no cost:\n\n"
                        + "- **Galvanic** — iron + copper\n"
                        + "- **Gilded** — iron + gold\n"
                        + "- **Verdant Veil** — copper + gold\n\n"
                        + "Experiment with mixed-material builds.");
            }).generate());

            for (Material m : SmitheryAPI.MATERIALS.all()) {
                if (!m.id().getNamespace().equals(Smithery.MODID)) continue;
                add(new MaterialEntry(this, m).generate());
            }
        }

        @Override protected String categoryName() { return "Materials & Traits"; }
        @Override protected BookIconModel categoryIcon() { return BookIconModel.create(Items.IRON_INGOT); }
        @Override public String categoryId() { return "materials"; }
    }

    /**
     * One generated entry per material: the material's real-world source item (ingot, block,
     * ore input, or press input) spotlighted over its stat sheet, then its tinted part item
     * over the trait list.
     */
    static class MaterialEntry extends RichEntry {
        private final Material material;

        MaterialEntry(CategoryProvider parent, Material material) {
            super(parent);
            this.material = material;
        }

        /** Hand-mapped source items for materials the forge never melts (press/bowstring inputs). */
        private static final java.util.Map<String, ItemLike> NON_MELT_SOURCES = java.util.Map.ofEntries(
                java.util.Map.entry("wood", Items.OAK_LOG),
                java.util.Map.entry("flint", Items.FLINT),
                java.util.Map.entry("slime", Items.SLIME_BALL),
                java.util.Map.entry("resin", Items.RESIN_CLUMP),
                java.util.Map.entry("coral", Items.BRAIN_CORAL_BLOCK),
                java.util.Map.entry("string", Items.STRING),
                java.util.Map.entry("kelp_string", Items.KELP));

        /**
         * The item shown for this material: an ingot-sized melting input when one exists
         * (144 mB), else any melting input, else the press/bowstring source, else the
         * tinted part item.
         */
        private ItemLike sourceItem() {
            Item ingotLike = null;
            Item anyInput = null;
            for (var recipe : SmitheryAPI.MELTING_RECIPES.values()) {
                if (!recipe.outputMaterialId().equals(material.id())) continue;
                // Part-remelt recipes would win here (every part melts back at its cast
                // volume) — the book wants the raw resource, not a smithery part.
                if (recipe.inputItemId().getNamespace().equals(Smithery.MODID)) continue;
                Item item = BuiltInRegistries.ITEM.getValue(recipe.inputItemId());
                if (item == null || item == Items.AIR) continue;
                if (recipe.outputMb() == 144) { ingotLike = item; break; }
                if (anyInput == null || recipe.outputMb() < 144) anyInput = item;
            }
            if (ingotLike != null) return ingotLike;
            ItemLike nonMelt = NON_MELT_SOURCES.get(material.id().getPath());
            if (nonMelt != null) return nonMelt;
            if (anyInput != null) return anyInput;
            return partItem();
        }

        private ItemLike partItem() {
            var part = SmitheryItems.getBuiltInPart(material.id(), SmitheryPartTypes.PICK_HEAD.id());
            if (part == null) {
                part = SmitheryItems.getBuiltInPart(material.id(), SmitheryPartTypes.BOWSTRING.id());
            }
            return part != null ? part.get() : Items.PAPER;
        }

        @Override
        protected void generatePages() {
            var stats = material.stats();

            StringBuilder sheet = new StringBuilder();
            sheet.append("- Harvest level: **").append(stats.harvestLevel()).append("**\n");
            sheet.append("- Mining speed: **").append(String.format("%.1f", stats.miningSpeed())).append("**\n");
            sheet.append("- Attack: **").append(String.format("%.1f", stats.attackDamage())).append("**\n");
            sheet.append("- Durability: **").append(stats.durabilityPerIngot()).append("** per ingot\n");
            if (stats.meltingTemp() > 0) {
                sheet.append("- Melts at **").append((int) stats.meltingTemp()).append("°C**\n");
            } else {
                sheet.append("- Does not melt — cut it on the **Part Press**\n");
            }
            if (stats.castOnly()) {
                sheet.append("- *Cast-only intermediate*\n");
            }
            spotlight(sourceItem(), titleCase(material.id().getPath()), sheet.toString());

            List<String> lines = new ArrayList<>();
            for (ToolType tt : SmitheryAPI.TOOL_TYPES.all()) {
                if (!tt.id().getNamespace().equals(Smithery.MODID)) continue;
                List<ModifierEffect> grants = stats.modifiersFor(tt);
                if (grants.isEmpty()) continue;
                StringBuilder line = new StringBuilder("- **").append(titleCase(tt.id().getPath())).append("**: ");
                for (int i = 0; i < grants.size(); i++) {
                    if (i > 0) line.append(", ");
                    line.append(titleCase(grants.get(i).modifierId().getPath()));
                }
                lines.add(line.toString());
            }
            String traits = lines.isEmpty()
                    ? "No innate traits — this material is carried by its raw stats."
                    : String.join("\n", lines);
            spotlight(partItem(), "Traits", traits);
        }

        @Override protected String entryName() { return titleCase(material.id().getPath()); }
        @Override protected BookIconModel entryIcon() { return BookIconModel.create(sourceItem()); }
        @Override protected String entryId() { return material.id().getPath(); }
    }
}
