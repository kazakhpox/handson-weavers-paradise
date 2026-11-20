package net.handsonweavers.station;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import xox.labvorty.weaversparadise.blocks.entities.DyemakingBlockEntity;

/**
 * The dyemaking table mixes its dye by itself as Weavers Paradise has it; taking the bottled dye uses up one of everything on the
 * table and leaves the base bottle empty, as the mod's own screen does.
 */
public final class DyemakingLogic implements StationLogic {
    private final DyemakingBlockEntity table;

    public DyemakingLogic(StationContext station) {
        table = station.blockEntity() instanceof DyemakingBlockEntity found ? found : null;
        if (table != null) {
            Container container = table;
            station.state().bindSlots(WeaverStations.DYE_RESULT, SlotStorage.of(container, 0, 1));
            station.state().bindSlots(WeaverStations.DYES, SlotStorage.of(container, 1, 9));
            station.state().bindSlots(WeaverStations.BOTTLE, SlotStorage.of(container, 10, 1));
            station.state().bindSlots(WeaverStations.BASE, SlotStorage.of(container, 11, 1));
            station.state().bindSlots(WeaverStations.CORE, SlotStorage.of(container, 12, 1));
        }
    }

    @Override
    public void onTaken(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (table == null || station.isClientSide() || !WeaverStations.DYE_RESULT.equals(slot.group())) {
            return;
        }
        for (int i = 1; i < 13; i++) {
            ItemStack left = table.getItem(i).copy();
            left.shrink(1);
            if (i == 11) {
                left = new ItemStack(Items.GLASS_BOTTLE);
            }
            table.setItem(i, left);
        }
    }
}
