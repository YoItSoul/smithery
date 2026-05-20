package com.soul.smithery.content;

import com.soul.smithery.Smithery;
import com.soul.smithery.api.SmitheryAPI;
import com.soul.smithery.api.part.PartType;
import net.minecraft.resources.Identifier;

/**
 * Built-in part types. New tool types that need new part shapes register their parts here
 * (or via SmitheryAPI from outside).
 *
 * durabilityScalar is the multiplier applied to material.durabilityPerIngot when this part
 * type contributes to the additive durability sum. Heads carry the bulk; handles less; binders
 * don't contribute additively (their role is multiplicative — see Material.binderMultiplier).
 */
public final class SmitheryPartTypes {
    public static PartType SWORD_BLADE;
    public static PartType GUARD;
    public static PartType HANDLE;
    public static PartType BINDER;
    public static PartType PICK_HEAD;

    public static void register() {
        SWORD_BLADE = SmitheryAPI.registerPartType(PartType.builder(id("sword_blade"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .build());

        GUARD = SmitheryAPI.registerPartType(PartType.builder(id("guard"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .build());

        HANDLE = SmitheryAPI.registerPartType(PartType.builder(id("handle"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .build());

        BINDER = SmitheryAPI.registerPartType(PartType.builder(id("binder"))
                .durabilityScalar(0.0f) // binder is purely multiplicative — no additive durability
                .partColorTint(true)
                .build());

        PICK_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("pick_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryPartTypes() {}
}
