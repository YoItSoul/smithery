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
        // castMb rationale: a vanilla sword is 2 ingots, vanilla pick is 3 ingots; the
        // tool's metal portion is one of two halves (head/blade + accessories) so the
        // "main metal" part gets half the tool's ingot count, accessories get half an
        // ingot. 1 ingot = 144 mB.
        SWORD_BLADE = SmitheryAPI.registerPartType(PartType.builder(id("sword_blade"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(144)              // 1 ingot
                .build());

        GUARD = SmitheryAPI.registerPartType(PartType.builder(id("guard"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .castMb(72)               // ½ ingot
                .build());

        HANDLE = SmitheryAPI.registerPartType(PartType.builder(id("handle"))
                .durabilityScalar(0.4f)
                .partColorTint(true)
                .castMb(72)               // ½ ingot
                .build());

        BINDER = SmitheryAPI.registerPartType(PartType.builder(id("binder"))
                .durabilityScalar(0.0f) // binder is purely multiplicative — no additive durability
                .partColorTint(true)
                .castMb(72)               // ½ ingot
                .build());

        PICK_HEAD = SmitheryAPI.registerPartType(PartType.builder(id("pick_head"))
                .durabilityScalar(1.0f)
                .partColorTint(true)
                .castMb(216)              // 1½ ingots (pick = 3 ingots, head is half the tool)
                .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Smithery.MODID, path);
    }

    private SmitheryPartTypes() {}
}
