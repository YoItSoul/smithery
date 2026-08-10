package com.soul.smithery.client;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.material.Material;
import com.soul.smithery.registry.SmitheryMaterialForms;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tints generated storage forms with their material's {@code partColor}.
 *
 * <p>Every form drawn by {@link SmitheryMaterialForms} shares two greyscale textures, so without
 * these handlers a hundred materials would all render as the same grey lump. The ingot model is
 * {@code item/generated}, whose single layer carries tint index 0; the block model declares its
 * faces by hand for the same reason, since vanilla's {@code cube_all} has no tint index.</p>
 *
 * <p>Colours are read at registration time rather than per-frame — materials are immutable by then,
 * and a lambda per form is cheaper than a registry lookup on every quad.</p>
 */
@Mod.EventBusSubscriber(modid = Smithery.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmitheryMaterialFormColors {
    private SmitheryMaterialFormColors() {}

    private static int colorOf(ResourceLocation materialId) {
        Material mat = SmitheryAPI.MATERIALS.get(materialId);
        return mat == null ? -1 : mat.stats().partColor();
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        for (SmitheryMaterialForms.Forms forms : SmitheryMaterialForms.all().values()) {
            int color = colorOf(forms.materialId());
            event.register((stack, tint) -> tint == 0 ? color : -1,
                    forms.ingot().get(), forms.blockItem().get());
        }
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (SmitheryMaterialForms.Forms forms : SmitheryMaterialForms.all().values()) {
            int color = colorOf(forms.materialId());
            event.register((state, level, pos, tint) -> tint == 0 ? color : -1, forms.block().get());
        }
    }
}
