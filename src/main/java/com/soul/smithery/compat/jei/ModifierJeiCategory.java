package com.soul.smithery.compat.jei;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.modifier.Modifier;
import com.soul.smithery.api.modifier.ModifierEffect;
import com.soul.smithery.compat.jei.SmitheryJeiRecipes.JeiAnvilSource;
import com.soul.smithery.compat.jei.SmitheryJeiRecipes.JeiMaterialGrant;
import com.soul.smithery.compat.jei.SmitheryJeiRecipes.JeiModifier;
import com.soul.smithery.compat.jei.SmitheryJeiRecipes.JeiSynergyGrant;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category that surfaces every registered Smithery modifier with all three acquisition
 * paths — anvil sources, material grants, and synergy pairs — laid out on a single page per
 * modifier.
 *
 * <p>The layout is deliberately verbose: each row is labelled in plain English with a colored
 * tag, slot items are accompanied by hover tooltips explaining exactly what placing/using each
 * one will do, and the footer summarises leveling economics (max level, per-level cost ramp,
 * durability multiplier). The category icon is a vanilla anvil because that is the only block
 * a player physically interacts with to apply modifiers.
 */
public class ModifierJeiCategory extends SmitheryJeiCategory<JeiModifier> {
    /** Width of the category background in GUI pixels. */
    public static final int WIDTH = 188;
    /** Height of the category background in GUI pixels. */
    public static final int HEIGHT = 104;

    /** Left-edge x coordinate where each section's label is drawn. */
    private static final int LABEL_X = 4;
    /** Left-edge x coordinate where each section's slot row begins. */
    private static final int SLOTS_X = 70;
    /** Width of one slot in GUI pixels (matches JEI's standard slot background). */
    private static final int SLOT_W = 18;

    /** y coordinate of the modifier name. */
    private static final int NAME_Y = 2;
    /** y coordinate where the wrapped description begins. */
    private static final int DESC_Y = 14;
    /** y coordinate of the divider line above the first acquisition row. */
    private static final int DIVIDER_Y = 32;
    /** y coordinate of the "Anvil:" row label. */
    private static final int ANVIL_LABEL_Y = 36;
    /** y coordinate of the anvil slot row. */
    private static final int ANVIL_SLOTS_Y = 34;
    /** y coordinate of the "Material:" row label. */
    private static final int MATERIAL_LABEL_Y = 56;
    /** y coordinate of the material slot row. */
    private static final int MATERIAL_SLOTS_Y = 54;
    /** y coordinate of the "Synergy:" row label. */
    private static final int SYNERGY_LABEL_Y = 76;
    /** y coordinate of the synergy slot row. */
    private static final int SYNERGY_SLOTS_Y = 74;
    /** y coordinate of the footer line (level economics). */
    private static final int FOOTER_Y = 94;

    /**
     * Constructs the category, providing JEI with id, title, icon (vanilla anvil), and layout
     * dimensions.
     *
     * @param guiHelper JEI gui helper used to build the icon drawable
     */
    public ModifierJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                SmitheryJeiTypes.MODIFIER,
                Component.translatable("jei." + Smithery.MODID + ".category.modifier"),
                new ItemStack(Items.ANVIL),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JeiModifier recipe, IFocusGroup focuses) {
        Modifier modifier = recipe.modifier();

        int x = SLOTS_X;
        for (JeiAnvilSource src : recipe.anvilSources()) {
            IRecipeSlotBuilder slot = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, ANVIL_SLOTS_Y).setBackground(guiHelper.getSlotDrawable(), -1, -1);
            slot.addItemStack(src.item());
            final JeiAnvilSource captured = src;
            slot.addTooltipCallback((view, tooltip) -> appendAnvilTooltip(tooltip, modifier, captured));
            x += SLOT_W;
        }

        x = SLOTS_X;
        for (JeiMaterialGrant grant : recipe.materialGrants()) {
            IRecipeSlotBuilder slot = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, MATERIAL_SLOTS_Y).setBackground(guiHelper.getSlotDrawable(), -1, -1);
            slot.addItemStack(grant.displayItem());
            final JeiMaterialGrant captured = grant;
            slot.addTooltipCallback((view, tooltip) -> appendGrantTooltip(tooltip, captured));
            x += SLOT_W;
        }

        x = SLOTS_X;
        for (JeiSynergyGrant syn : recipe.synergies()) {
            IRecipeSlotBuilder slotA = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, SYNERGY_SLOTS_Y).setBackground(guiHelper.getSlotDrawable(), -1, -1);
            slotA.addItemStack(syn.itemA());
            final JeiSynergyGrant capturedA = syn;
            slotA.addTooltipCallback((view, tooltip) -> appendSynergyTooltip(tooltip, capturedA));
            x += SLOT_W;

            IRecipeSlotBuilder slotB = builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, x, SYNERGY_SLOTS_Y).setBackground(guiHelper.getSlotDrawable(), -1, -1);
            slotB.addItemStack(syn.itemB());
            final JeiSynergyGrant capturedB = syn;
            slotB.addTooltipCallback((view, tooltip) -> appendSynergyTooltip(tooltip, capturedB));
            x += SLOT_W + 2;
        }
    }

    @Override
    public void draw(JeiModifier recipe,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        Modifier modifier = recipe.modifier();

        Component name = nameOf(modifier.id()).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int nameW = font.width(name);
        guiGraphics.drawString(font, name, (WIDTH - nameW) / 2, NAME_Y, 0xFFFFFF, false);

        Component desc = descriptionOf(modifier.id()).copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(desc, WIDTH - 8);
        int dy = DESC_Y;
        for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
            int lineW = font.width(wrapped.get(i));
            guiGraphics.drawString(font, wrapped.get(i), (WIDTH - lineW) / 2, dy, 0xFFFFFF, false);
            dy += 9;
        }

        int dividerWidth = WIDTH - 8;
        guiGraphics.fill(4, DIVIDER_Y, 4 + dividerWidth, DIVIDER_Y + 1, 0xFF555555);

        drawRow(guiGraphics, font, ANVIL_LABEL_Y, ChatFormatting.AQUA, "anvil",
                !recipe.anvilSources().isEmpty());
        drawRow(guiGraphics, font, MATERIAL_LABEL_Y, ChatFormatting.GREEN, "material",
                !recipe.materialGrants().isEmpty());
        drawRow(guiGraphics, font, SYNERGY_LABEL_Y, ChatFormatting.LIGHT_PURPLE, "synergy",
                !recipe.synergies().isEmpty());

        Component footer = footerComponent(modifier).withStyle(ChatFormatting.DARK_GRAY);
        List<net.minecraft.util.FormattedCharSequence> footerLines = font.split(footer, WIDTH - 8);
        if (!footerLines.isEmpty()) {
            guiGraphics.drawString(font, footerLines.get(0), 4, FOOTER_Y, 0xFFFFFF, false);
        }
    }

    /**
     * Draws one "Section: " label at {@code y}. When the section is empty, suffixes a faded
     * "(none)" so the player still sees that the section was considered and intentionally empty
     * rather than missing.
     */
    private static void drawRow(GuiGraphics guiGraphics, Font font, int y,
                                ChatFormatting accent, String key, boolean populated) {
        Component label = Component.translatable("jei." + Smithery.MODID + ".modifier." + key)
                .copy().withStyle(accent, ChatFormatting.BOLD);
        guiGraphics.drawString(font, label, LABEL_X, y, 0xFFFFFF, false);
        if (!populated) {
            Component none = Component.translatable("jei." + Smithery.MODID + ".modifier.none")
                    .copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            guiGraphics.drawString(font, none, SLOTS_X + 2, y, 0xFFFFFF, false);
        }
    }

    /**
     * Builds the modifier's translated display name from its identifier path
     * ({@code smithery.modifier.<namespace>.<path>}).
     */
    private static Component nameOf(ResourceLocation id) {
        return Component.translatable("smithery.modifier." + id.getNamespace() + "." + id.getPath());
    }

    /**
     * Builds the modifier's translated description from its identifier path
     * ({@code smithery.modifier.<namespace>.<path>.description}).
     */
    private static Component descriptionOf(ResourceLocation id) {
        return Component.translatable("smithery.modifier." + id.getNamespace() + "." + id.getPath() + ".description");
    }

    /**
     * Builds the one-line footer summarising leveling economics. Single-shot modifiers
     * (max level 1) say "Single application"; multi-level modifiers show the level-1 cost and
     * scaling factor. A non-1.0 durability multiplier is appended.
     */
    private static MutableComponent footerComponent(Modifier modifier) {
        MutableComponent out;
        if (modifier.maxLevel() <= 1) {
            out = Component.translatable("jei." + Smithery.MODID + ".modifier.single_use");
        } else {
            out = Component.translatable(
                    "jei." + Smithery.MODID + ".modifier.level_summary",
                    modifier.maxLevel(),
                    modifier.levelCost(),
                    String.format("%.2f", modifier.levelCostScaling()));
        }
        if (Math.abs(modifier.durabilityMultiplier() - 1.0f) > 1e-3) {
            out = out.append(Component.literal(" • "))
                     .append(Component.translatable(
                             "jei." + Smithery.MODID + ".modifier.durability_mult",
                             String.format("%.2fx", modifier.durabilityMultiplier())));
        }
        return out;
    }

    private static void appendAnvilTooltip(java.util.List<Component> tooltip, Modifier modifier, JeiAnvilSource src) {
        tooltip.add(Component.translatable("jei." + Smithery.MODID + ".modifier.anvil_header")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(Component.translatable(
                "jei." + Smithery.MODID + ".modifier.anvil_units",
                src.unitValue()).withStyle(ChatFormatting.GRAY));
        if (modifier.maxLevel() <= 1) {
            tooltip.add(Component.translatable("jei." + Smithery.MODID + ".modifier.anvil_single")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            int level1Items = (int) Math.ceil(modifier.levelCost() / (double) Math.max(1, src.unitValue()));
            tooltip.add(Component.translatable(
                    "jei." + Smithery.MODID + ".modifier.anvil_level1",
                    level1Items).withStyle(ChatFormatting.GRAY));
        }
        appendParams(tooltip, src.effect());
    }

    private static void appendGrantTooltip(java.util.List<Component> tooltip, JeiMaterialGrant grant) {
        tooltip.add(Component.translatable("jei." + Smithery.MODID + ".modifier.grant_header")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        tooltip.add(Component.translatable(
                "jei." + Smithery.MODID + ".modifier.grant_body",
                materialName(grant.material().id()),
                toolName(grant.toolType().id())
        ).withStyle(ChatFormatting.GRAY));
        appendParams(tooltip, grant.effect());
    }

    private static void appendSynergyTooltip(java.util.List<Component> tooltip, JeiSynergyGrant syn) {
        tooltip.add(Component.translatable("jei." + Smithery.MODID + ".modifier.synergy_header")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        tooltip.add(Component.translatable(
                "jei." + Smithery.MODID + ".modifier.synergy_body",
                materialName(syn.synergy().materialA()),
                materialName(syn.synergy().materialB()),
                toolName(syn.toolType().id())
        ).withStyle(ChatFormatting.GRAY));
        appendParams(tooltip, syn.effect());
    }

    /**
     * Pretty-prints the modifier effect's parameter map so the player sees the literal numbers
     * driving this entry. Parameters are stable JSON-keyed fields (e.g. {@code damage},
     * {@code chance}, {@code radius}), so verbatim {@code key=value} lines are clearer than
     * trying to translate each key.
     */
    private static void appendParams(java.util.List<Component> tooltip, ModifierEffect effect) {
        if (effect.params().isEmpty()) return;
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("jei." + Smithery.MODID + ".modifier.params_header")
                .withStyle(ChatFormatting.DARK_GRAY));
        for (var e : effect.params().entrySet()) {
            tooltip.add(Component.literal("  " + e.getKey() + " = " + formatParam(e.getValue()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String formatParam(Object value) {
        if (value instanceof Float f)  return String.format("%.2f", f);
        if (value instanceof Double d) return String.format("%.2f", d);
        return String.valueOf(value);
    }

    private static Component materialName(ResourceLocation materialId) {
        return Component.translatable("smithery.material." + materialId.getNamespace() + "." + materialId.getPath());
    }

    private static Component toolName(ResourceLocation toolId) {
        return Component.translatable("smithery.tool." + toolId.getNamespace() + "." + toolId.getPath());
    }
}
