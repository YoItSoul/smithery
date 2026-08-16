// priority: 0
// requires: ponderjs

/**
 * Ponder scenes for Smithery's three in-world machines: the Part Press, the Forge
 * multiblock, and the Casting Table pour.
 *
 * KubeJS loads client scripts out of any resource pack, and a mod jar is one, so this
 * file ships inside Smithery at assets/smithery/kubejs/ and needs no pack setup. The
 * "requires" header above skips the whole file when PonderJS is absent, which is what
 * makes the integration optional.
 *
 * Everything here is staged rather than simulated. A ponder world is client-side, so
 * none of Smithery's block entities tick and none of its neighbour-driven blockstate
 * updates run: forge validation, drain pumping and pipe auto-connection are all server
 * work. Block states are therefore written out in full (see the pipe below) and block
 * entity fields are set through NBT, which is what the renderers read.
 *
 * Scene text is written here in English and looked up at render time under
 * "smithery.ponder.<scene>.text_<n>", numbered by call order within each scene. Those
 * keys live in assets/smithery/lang/en_us.json — inserting a scene.text() call shifts
 * every later index in that scene, so the lang file has to move with it.
 */

// ---------------------------------------------------------------------------
// Shared layout
// ---------------------------------------------------------------------------

/**
 * The smallest legal forge, matching the field guide's "Smallest Forge": a hollow 3x3x3
 * of Furnace Bricks around a single interior air block, with the three required ports in
 * the walls that touch it.
 *
 * Only the six blocks orthogonally adjacent to the interior are counted as shell by the
 * validator, so those are the ones that must be right; the rest of the box is plain brick.
 */
const FORGE = {
    interior: [1, 2, 2],
    controller: [1, 2, 3], // front wall, facing the camera
    fuelPort: [0, 2, 2], // left wall
    drain: [2, 2, 2], // right wall
    floor: [1, 1, 2],
};

const BRICKS = "smithery:furnace_bricks";

/**
 * PonderJS's own fallback schematic: a 5x5 checkerboard base plate with ten blocks of
 * headroom. All three scenes build inside it procedurally, so Smithery ships no scene
 * .nbt of its own. It has to be named explicitly because the short scene() overload that
 * defaults to it has no room for the trailing highlight tags.
 */
const BASIC_PLATE = "ponderjs:basic";

/**
 * Fills the 3x3x3 brick box, hollows out the single interior block and drops the three
 * required ports into the walls that touch it. Built with the roof on; scenes that want to
 * talk about the open top reveal that layer separately.
 *
 * @param scene the scene being built
 */
function buildForge(scene) {
    scene.world.setBlocks([0, 1, 1, 2, 3, 3], BRICKS, false);
    scene.world.setBlocks(FORGE.interior, "minecraft:air", false);
    scene.world.setBlocks(FORGE.controller, "smithery:forge_controller[status=idle]", false);
    scene.world.setBlocks(FORGE.fuelPort, "smithery:forge_fuel_port[connected_up=false,connected_down=false]", false);
    scene.world.setBlocks(FORGE.drain, "smithery:forge_drain", false);
}

// ---------------------------------------------------------------------------
// Index category
// ---------------------------------------------------------------------------

Ponder.tags((event) => {
    event.createTag(
        "smithery:machines",
        "smithery:forge_controller",
        "Smithery Machinery",
        "The Forge multiblock and the machines that feed off it.",
        ["smithery:forge_controller", "smithery:part_press", "smithery:casting_table"]
    );
});

// ---------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------

Ponder.registry((event) => {
    // -----------------------------------------------------------------------
    // Part Press
    // -----------------------------------------------------------------------
    event.create("smithery:part_press").scene(
        "smithery:part_press",
        "Cutting Parts",
        BASIC_PLATE,
        (scene, util) => {
            const press = [2, 1, 2];
            const button = [2, 1, 3]; // on the Press's own front face
            const above = util.vector.topOf(2, 1, 2);

            scene.configureBasePlate(0, 0, 5);
            scene.world.setBlocks(press, "smithery:part_press[powered=false]", false);
            scene.world.setBlocks(button, "minecraft:stone_button[face=wall,facing=south,powered=false]", false);

            scene.showBasePlate();
            scene.idle(10);
            scene.world.showSection(press, Facing.DOWN);
            scene.idle(15);

            scene.text(70, "The Part Press cuts solid stock into tool parts.", above)
                .placeNearTarget()
                .attachKeyFrame();
            scene.idle(80);

            scene.addKeyframe();
            scene.showControls(60, above, "down").rightClick().whileSneaking();
            scene.text(70, "Sneak and right-click to open the picker and choose which part to cut.", above)
                .placeNearTarget();
            scene.idle(80);

            scene.addKeyframe();
            scene.showControls(60, above, "down").rightClick().withItem("minecraft:oak_log");
            scene.text(
                90,
                "Right-click with the stock. The Press handles what the Forge cannot melt: logs, flint, slime balls, coral and Red Slime.",
                above
            ).placeNearTarget();
            scene.idle(100);

            scene.addKeyframe();
            scene.world.showSection(button, Facing.DOWN);
            scene.idle(15);
            scene.text(60, "Any redstone signal closes the Press and cuts one part.", util.vector.centerOf(2, 1, 3))
                .placeNearTarget();
            scene.idle(50);

            scene.world.toggleRedstonePower(util.select.fromTo(2, 1, 2, 2, 1, 3));
            scene.effects.indicateRedstone(button);
            scene.world.modifyBlockEntityNBT(press, (nbt) => nbt.putBoolean("closed", true));
            scene.idle(30);

            scene.world.toggleRedstonePower(util.select.fromTo(2, 1, 2, 2, 1, 3));
            scene.world.modifyBlockEntityNBT(press, (nbt) => nbt.putBoolean("closed", false));
            scene.world.createItemEntity(above, util.vector.of(0, 0.1, 0), "smithery:wood_handle");
            scene.idle(20);

            scene.addKeyframe();
            scene.showControls(50, above, "down").rightClick();
            scene.text(80, "Right-click to take the part out. Hoppers can load and empty the Press while it is open.", above)
                .placeNearTarget();
            scene.idle(90);

            scene.markAsFinished();
        },
        "smithery:machines"
    );

    // -----------------------------------------------------------------------
    // Forge multiblock
    // -----------------------------------------------------------------------
    event.create("smithery:forge_controller").scene(
        "smithery:forge_structure",
        "Building a Forge",
        BASIC_PLATE,
        (scene, util) => {
            scene.configureBasePlate(0, 0, 5);
            buildForge(scene);

            scene.showBasePlate();
            scene.idle(10);
            scene.world.showSection(util.select.layers(1, 2), Facing.DOWN);
            scene.idle(20);

            scene.text(80, "A Forge is a hollow shell of Furnace Bricks wrapped around an air pocket.", util.vector.centerOf(1, 2, 2))
                .placeNearTarget()
                .attachKeyFrame();
            scene.idle(90);

            scene.addKeyframe();
            scene.overlay.showOutline(PonderPalette.BLUE, "interior", FORGE.interior, 100);
            scene.text(
                100,
                "Every block beside and below that pocket has to be shell. One gap anywhere and the Forge will not form.",
                util.vector.centerOf(1, 2, 2)
            ).colored(PonderPalette.BLUE).placeNearTarget();
            scene.idle(110);

            scene.addKeyframe();
            scene.world.showSection(util.select.layer(3), Facing.DOWN);
            scene.idle(20);
            scene.text(80, "The top is the exception. Leave it open, or close it and the Forge heats 20% faster.", util.vector.topOf(1, 3, 2))
                .placeNearTarget();
            scene.idle(90);

            scene.addKeyframe();
            scene.overlay.showOutline(PonderPalette.GREEN, "controller", FORGE.controller, 80);
            scene.text(80, "Exactly one Controller. It is the only block with a screen.", util.vector.blockSurface(FORGE.controller, Facing.SOUTH))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
            scene.idle(90);

            scene.overlay.showOutline(PonderPalette.GREEN, "fuelport", FORGE.fuelPort, 80);
            scene.text(80, "At least one Fuel Port.", util.vector.blockSurface(FORGE.fuelPort, Facing.WEST))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
            scene.idle(90);

            scene.overlay.showOutline(PonderPalette.GREEN, "drain", FORGE.drain, 80);
            scene.text(80, "At least one Drain.", util.vector.blockSurface(FORGE.drain, Facing.EAST))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
            scene.idle(90);

            scene.addKeyframe();
            scene.world.setBlocks(FORGE.floor, "smithery:forge_rf_coil[lit=false]", false);
            scene.overlay.showOutline(PonderPalette.RED, "coil", FORGE.floor, 80);
            scene.text(90, "An RF Coil may take the place of a shell block to heat without fuel — but never more than one.", util.vector.centerOf(1, 1, 2))
                .colored(PonderPalette.RED)
                .placeNearTarget();
            scene.idle(100);
            scene.world.setBlocks(FORGE.floor, BRICKS, false);
            scene.idle(10);

            scene.addKeyframe();
            scene.world.setBlocks(FORGE.controller, "smithery:forge_controller[status=formed]", false);
            scene.text(80, "Pass all of that and the Controller's lamp turns amber: built, but cold.", util.vector.blockSurface(FORGE.controller, Facing.SOUTH))
                .placeNearTarget();
            scene.idle(90);

            scene.addKeyframe();
            scene.showControls(60, util.vector.blockSurface(FORGE.fuelPort, Facing.WEST), "right")
                .rightClick()
                .withItem("minecraft:lava_bucket");
            scene.world.modifyBlockEntityNBT(FORGE.fuelPort, (nbt) => {
                nbt.putString("fuelFluid", "minecraft:lava");
                nbt.putInt("fuelMb", 1000);
            });
            scene.world.setBlocks(FORGE.controller, "smithery:forge_controller[status=burning]", false);
            scene.idle(20);
            scene.text(
                100,
                "Bucket fuel into a port and the lamp goes green. Every interior block adds one melting slot and 1,000 mB of molten storage.",
                util.vector.blockSurface(FORGE.controller, Facing.SOUTH)
            ).colored(PonderPalette.GREEN).placeNearTarget();
            scene.idle(110);

            scene.markAsFinished();
        },
        "smithery:machines"
    );

    // -----------------------------------------------------------------------
    // Casting
    // -----------------------------------------------------------------------
    event.create("smithery:casting_table").scene(
        "smithery:casting",
        "Casting Metal",
        BASIC_PLATE,
        (scene, util) => {
            const table = [3, 1, 2];
            const pipe = [3, 2, 2];
            // The Drain's one exposed face is taken by the pipe, so the button goes on the
            // brick above it: that brick is a full block, so it conducts the button's power
            // down into the Drain. Anything touching the Drain works the same way.
            const trigger = [3, 3, 2];
            const triggerAt = util.vector.centerOf(3, 3, 2);
            const tableTop = util.vector.topOf(3, 1, 2);

            // A ponder world never runs neighbour updates, and the pipe only recomputes its
            // faces server-side, so the connected state is written out by hand: a toother
            // flange into the Drain, a bare arm down onto the table.
            const PIPE_STATE =
                "smithery:fluid_pipe[north=none,east=none,south=none,west=arm_toother,up=none,down=arm_open]";

            scene.configureBasePlate(0, 0, 5);
            buildForge(scene);
            scene.world.setBlocks(FORGE.controller, "smithery:forge_controller[status=burning]", false);
            scene.world.modifyBlockEntityNBT(FORGE.fuelPort, (nbt) => {
                nbt.putString("fuelFluid", "minecraft:lava");
                nbt.putInt("fuelMb", 1000);
            });
            scene.world.setBlocks(table, "smithery:casting_table", false);
            scene.world.setBlocks(pipe, PIPE_STATE, false);
            scene.world.setBlocks(trigger, "minecraft:stone_button[face=wall,facing=east,powered=false]", false);

            scene.showBasePlate();
            scene.idle(10);
            scene.world.showSection(util.select.layers(1, 3), Facing.DOWN);
            scene.idle(20);

            scene.text(70, "A Casting Table turns the Forge's molten metal back into parts.", tableTop)
                .placeNearTarget()
                .attachKeyFrame();
            scene.idle(80);

            scene.addKeyframe();
            scene.showControls(50, tableTop, "down").rightClick().withItem("smithery:casting_sand");
            scene.world.modifyBlockEntityNBT(table, (nbt) => nbt.putString("state", "SAND"));
            scene.idle(20);
            scene.text(70, "Right-click with Casting Sand to lay a bed.", tableTop).placeNearTarget();
            scene.idle(80);

            scene.addKeyframe();
            scene.showControls(50, tableTop, "down").rightClick().withItem("smithery:iron_pick_head");
            scene.world.modifyBlockEntityNBT(table, (nbt) => {
                nbt.putString("state", "IMPRESSED");
                nbt.putString("impressedPartType", "smithery:pick_head");
                nbt.putInt("requiredMb", 144);
            });
            scene.idle(20);
            scene.text(90, "Press a part into the sand to shape it. The part is only a template — it is not used up.", tableTop)
                .placeNearTarget();
            scene.idle(100);

            scene.addKeyframe();
            scene.overlay.showOutline(PonderPalette.BLUE, "route", util.select.fromTo(2, 2, 2, 3, 2, 2), 90);
            scene.text(
                90,
                "Run Fluid Pipe from the Drain to directly above the table. Tables only take metal poured in from the top.",
                util.vector.centerOf(3, 2, 2)
            ).colored(PonderPalette.BLUE).placeNearTarget();
            scene.idle(100);

            scene.addKeyframe();
            scene.overlay.showOutline(PonderPalette.WHITE, "ctrl", FORGE.controller, 80);
            scene.text(
                90,
                "In the Controller, click the metal's layer in the tank to make it the output. The Drain pumps that one metal and nothing else.",
                util.vector.blockSurface(FORGE.controller, Facing.SOUTH)
            ).placeNearTarget();
            scene.idle(100);

            scene.addKeyframe();
            scene.showControls(50, triggerAt, "down").rightClick();
            scene.world.toggleRedstonePower(trigger);
            scene.effects.indicateRedstone(trigger);
            scene.idle(15);
            scene.world.modifyBlockEntityNBT(table, (nbt) => {
                nbt.putString("state", "FILLING");
                nbt.putString("pouredFluid", "smithery:molten_iron");
                nbt.putInt("filledMb", 72);
            });
            scene.text(90, "A button pours one job: the Drain pumps 5 mB a tick until the table is full, then stops.", tableTop)
                .placeNearTarget();
            scene.idle(60);
            scene.world.toggleRedstonePower(trigger);
            scene.world.modifyBlockEntityNBT(table, (nbt) => {
                nbt.putString("state", "COOLING");
                nbt.putInt("filledMb", 144);
                nbt.putInt("coolingTicks", 288);
            });
            scene.idle(50);

            scene.addKeyframe();
            scene.world.modifyBlockEntityNBT(table, (nbt) => {
                nbt.putString("state", "READY");
                nbt.putInt("coolingTicks", 0);
            });
            scene.showControls(50, tableTop, "down").rightClick();
            scene.text(90, "Once it cools, right-click to take the part. The impression stays behind, ready for the next pour.", tableTop)
                .placeNearTarget();
            scene.idle(100);

            scene.addKeyframe();
            scene.world.setBlocks(trigger, "minecraft:lever[face=wall,facing=east,powered=true]", false);
            scene.effects.indicateRedstone(trigger);
            scene.world.modifyBlockEntityNBT(table, (nbt) => {
                nbt.putString("state", "IMPRESSED");
                nbt.putInt("filledMb", 0);
            });
            scene.idle(20);
            scene.text(
                100,
                "Hold the signal with a lever instead and the Drain keeps re-arming — pour after pour, as fast as the Forge can melt.",
                triggerAt
            ).placeNearTarget();
            scene.idle(110);

            scene.markAsFinished();
        },
        "smithery:machines"
    );
});
