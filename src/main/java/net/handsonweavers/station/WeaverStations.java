package net.handsonweavers.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;

import net.handsonweavers.HandsOnWeavers;
import xox.labvorty.weaversparadise.init.WeaversParadiseBlocks;
import xox.labvorty.weaversparadise.init.WeaversParadiseItems;

/**
 * The stations of Weavers Paradise. Every slot group is a window on the block's own inventory, so the blocks go on working as
 * before: the spinning jenny spins by itself, hoppers reach them, and the mod's own screens (sneaking) see the same items.
 */
public final class WeaverStations {
    // ---- Spinning jenny: raw fibre in slot 0, spools in 1-6 that it winds full one by one.
    public static final ResourceLocation JENNY_ID = HandsOnWeavers.id("spinning_jenny");
    public static final String FIBRE = "fibre";
    public static final String SPOOLS = "spools";
    public static final int SPOOL_COUNT = 6;
    /** Data: how far the spool being wound is, and how far it goes, in ticks. */
    public static final String PROGRESS = "progress";
    public static final String TOTAL = "total";

    public static final StationSchema JENNY_SCHEMA = StationSchema.builder()
            .slots(FIBRE, 1, SlotRules.DEFAULT.kept())
            .slots(SPOOLS, SPOOL_COUNT, SlotRules.DEFAULT.kept().maxStack(1))
            .build();

    // ---- Dyemaking table: the result in slot 0, dyes 1-9, a glass bottle 10, water or a dye to blend with 11, a dye core 12.
    public static final ResourceLocation DYEMAKING_ID = HandsOnWeavers.id("dyemaking_table");
    public static final String DYE_RESULT = "result";
    public static final String DYES = "dyes";
    public static final String BOTTLE = "bottle";
    public static final String BASE = "base";
    public static final String CORE = "core";
    public static final SlotRef DYE_RESULT_SLOT = SlotRef.of(DYE_RESULT, 0);
    public static final TagKey<Item> VANILLA_DYES = TagKey.create(Registries.ITEM, ResourceLocation.parse("weaversparadise:vanilla_dyes"));
    public static final TagKey<Item> CUSTOM_DYES = TagKey.create(Registries.ITEM, ResourceLocation.parse("weaversparadise:custom_dyes"));

    public static final StationSchema DYEMAKING_SCHEMA = StationSchema.builder()
            .slots(DYE_RESULT, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(DYES, 9, SlotRules.DEFAULT.kept().accepts(WeaverStations::isDye))
            .slots(BOTTLE, 1, SlotRules.DEFAULT.kept().accepts(stack -> stack.is(Items.GLASS_BOTTLE)))
            .slots(BASE, 1, SlotRules.DEFAULT.kept().accepts(stack -> stack.is(Items.POTION)
                    || stack.is(WeaversParadiseItems.BOTTLED_DYE.get()) || stack.is(Items.GLASS_BOTTLE)))
            .slots(CORE, 1, SlotRules.DEFAULT.kept().accepts(stack -> stack.is(WeaversParadiseItems.DYE_CORE.get())))
            .build();

    // ---- Clothcrafting station: the spools to weave in slot 0; the woven cloth and the spools given back wait in the block.
    public static final ResourceLocation LOOM_ID = HandsOnWeavers.id("clothcrafting_station");
    public static final String WARP = "warp";
    /** The output: the first of what the weaving made, taken one stack at a time. */
    public static final String CLOTH = "cloth";
    /** Actions: start weaving; one row woven ({@link #ROW}, the arrows typed as {@link #ARROWS}). */
    public static final String ACTION_START = "start";
    public static final String ACTION_ROW = "row";
    public static final String ROW = "row";
    public static final String ARROWS = "arrows";
    /** Data: weaving now, the seed of its rows, rows done, the game tick it ends, how long it lasts, and what is woven. */
    public static final String ON = "on";
    public static final String WEAVER = "weaver";
    public static final String SEED = "seed";
    public static final String SCORE = "score";
    public static final String END = "end";
    public static final String DURATION = "duration";
    public static final String MADE = "made";
    public static final ResourceLocation EFFECT_ROW = HandsOnWeavers.id("row");
    public static final ResourceLocation EFFECT_WOVEN = HandsOnWeavers.id("woven");
    public static final ResourceLocation EFFECT_REFUSED = HandsOnWeavers.id("refused");
    public static final String PLAYER = "player";
    public static final String REASON = "reason";
    public static final String ITEM = "item";
    public static final String QUALITY = "quality";

    public static final StationSchema LOOM_SCHEMA = StationSchema.builder()
            .slots(WARP, 1, SlotRules.DEFAULT.kept())
            .output(CLOTH)
            .build();

    // ---- Dyeing barrel: clothing 0, the dyed clothing 1, stencils 2 (front) and 3 (back), dyes 4-5 (front) and 6-7 (back).
    public static final ResourceLocation BARREL_ID = HandsOnWeavers.id("dyeing_barrel");
    public static final String CLOTHING = "clothing";
    public static final String DYED = "dyed";
    public static final String STENCILS = "stencils";
    public static final String FRONT_DYES = "front_dyes";
    public static final String BACK_DYES = "back_dyes";
    /** The dye bath the clothing floats in: water, coloured by the client with the dyes put in. */
    public static final String BATH = "bath";
    public static final SlotRef CLOTHING_SLOT = SlotRef.of(CLOTHING, 0);
    public static final SlotRef DYED_SLOT = SlotRef.of(DYED, 0);
    public static final ResourceLocation EFFECT_DYED = HandsOnWeavers.id("dyed");
    /** Action: the bath was stirred enough for the dye to take. */
    public static final String ACTION_STIRRED = "stirred";
    /** Data: the clothing in the bath is freshly dyed (no preview until it changes); the tick the clothing went in. */
    public static final String FRESH = "fresh";
    public static final String LAID = "laid";

    public static final StationSchema BARREL_SCHEMA = StationSchema.builder()
            .slots(CLOTHING, 1, SlotRules.DEFAULT.kept().maxStack(1))
            .slots(DYED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .slots(STENCILS, 2, SlotRules.DEFAULT.kept().maxStack(1))
            .slots(FRONT_DYES, 2, SlotRules.DEFAULT.kept().maxStack(1)
                    .accepts(stack -> stack.is(WeaversParadiseItems.BOTTLED_DYE.get())))
            .slots(BACK_DYES, 2, SlotRules.DEFAULT.kept().maxStack(1)
                    .accepts(stack -> stack.is(WeaversParadiseItems.BOTTLED_DYE.get())))
            .tank(BATH, TankSpec.of(1000).filledWith(ResourceLocation.withDefaultNamespace("water"), 1000))
            .build();

    public static final StationType JENNY = Diegesis.station(JENNY_ID)
            .block(WeaversParadiseBlocks.SPINNING_JENNY)
            .schema(JENNY_SCHEMA)
            .logic(JennyLogic::new)
            .openWhen(WeaverStations::opens)
            .build();

    public static final StationType DYEMAKING = Diegesis.station(DYEMAKING_ID)
            .block(WeaversParadiseBlocks.DYEMAKING_BLOCK)
            .schema(DYEMAKING_SCHEMA)
            .logic(DyemakingLogic::new)
            .openWhen(WeaverStations::opens)
            .build();

    public static final StationType LOOM = Diegesis.station(LOOM_ID)
            .block(WeaversParadiseBlocks.CLOTHCRAFTING_STATION)
            .schema(LOOM_SCHEMA)
            .logic(LoomLogic::new)
            .openWhen(WeaverStations::opens)
            .build();

    public static final StationType BARREL = Diegesis.station(BARREL_ID)
            .block(WeaversParadiseBlocks.DYEING_BARREL_BLOCK)
            .schema(BARREL_SCHEMA)
            .logic(BarrelLogic::new)
            .openWhen(WeaverStations::opens)
            .orientation(StationType.Orientation.PLAYER)
            .build();

    private WeaverStations() {
    }

    public static void register() {
        Diegesis.register(JENNY);
        Diegesis.register(DYEMAKING);
        Diegesis.register(LOOM);
        Diegesis.register(BARREL);
    }

    public static boolean isDye(ItemStack stack) {
        return stack.is(VANILLA_DYES) || stack.is(CUSTOM_DYES);
    }

    /** Sneaking keeps the block's own screen; every other use opens the station. */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive();
    }
}
