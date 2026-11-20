package net.handsonweavers.station;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import xox.labvorty.weaversparadise.blocks.entities.DyeingBarrelBlockEntity;
import xox.labvorty.weaversparadise.data.texture.StencilRegistry;
import xox.labvorty.weaversparadise.init.WeaversParadiseItems;
import xox.labvorty.weaversparadise.items.clothing.defined.DoubleSidedBlockItem;
import xox.labvorty.weaversparadise.items.clothing.defined.DoubleSidedClothingItem;
import xox.labvorty.weaversparadise.items.clothing.defined.SingleSidedClothingItem;
import xox.labvorty.weaversparadise.items.stencil.Stencil;

/**
 * The dyeing barrel. The block works out the dyed clothing by itself from what lies in it, as Weavers Paradise has it; the station
 * keeps the same rules for what goes where (stencils that fit the clothing, a back side only for double-sided clothing), and taking
 * the dye shows over the barrel as a preview; stirring the bath dyes the clothing in it, using one dose of each dye on the sides
 * it was dyed, as the mod's screen does, and the dyed clothing is taken out of the bath.
 */
public final class BarrelLogic implements StationLogic {
    private final DyeingBarrelBlockEntity barrel;
    private boolean dyeing;

    public BarrelLogic(StationContext station) {
        barrel = station.blockEntity() instanceof DyeingBarrelBlockEntity found ? found : null;
        if (barrel != null) {
            Container container = barrel;
            station.state().bindSlots(WeaverStations.CLOTHING, SlotStorage.of(container, 0, 1));
            station.state().bindSlots(WeaverStations.DYED, SlotStorage.of(container, 1, 1));
            station.state().bindSlots(WeaverStations.STENCILS, SlotStorage.of(container, 2, 2));
            station.state().bindSlots(WeaverStations.FRONT_DYES, SlotStorage.of(container, 4, 2));
            station.state().bindSlots(WeaverStations.BACK_DYES, SlotStorage.of(container, 6, 2));
        }
    }

    public static boolean isClothing(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SingleSidedClothingItem || item instanceof DoubleSidedClothingItem || item instanceof DoubleSidedBlockItem;
    }

    public static boolean isDoubleSided(ItemStack stack) {
        return stack.getItem() instanceof DoubleSidedClothingItem || stack.getItem() instanceof DoubleSidedBlockItem;
    }

    /** Whether a stencil fits the clothing, as the mod's screen checks it. */
    public static boolean stencilFits(ItemStack stencil, ItemStack clothing) {
        if (!(stencil.getItem() instanceof Stencil type)) {
            return false;
        }
        List<StencilRegistry.Stencil> stencils = StencilRegistry.getStencilsForType(type.getType());
        return stencils.stream().anyMatch(fit -> fit.item().equals(clothing.getItem()));
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (barrel == null) {
            return false;
        }
        ItemStack clothing = barrel.getItem(0);
        return switch (slot.group()) {
            case WeaverStations.CLOTHING -> isClothing(stack);
            case WeaverStations.STENCILS -> stencilFits(stack, clothing) && (slot.index() == 0 || isDoubleSided(clothing));
            // The second dye of a side needs that side's stencil, and the back side needs double-sided clothing.
            case WeaverStations.FRONT_DYES -> slot.index() == 0 || barrel.getItem(2).getItem() instanceof Stencil;
            case WeaverStations.BACK_DYES -> isDoubleSided(clothing) && (slot.index() == 0 || barrel.getItem(3).getItem() instanceof Stencil);
            default -> true;
        };
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        // What the block works out is only shown over the barrel: the clothing is dyed by stirring, and taken from the bath.
        return !WeaverStations.DYED.equals(slot.group());
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (dyeing || station.isClientSide() || !WeaverStations.CLOTHING.equals(slot.group())) {
            return;
        }
        // Other clothing in the bath: it has yet to be stirred.
        CompoundTag data = station.state().data();
        if (data.contains(WeaverStations.FRESH) || data.contains(WeaverStations.LAID)) {
            data.remove(WeaverStations.FRESH);
        }
        data.putLong(WeaverStations.LAID, station.level().getGameTime());
        station.state().setData(data);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (barrel == null || station.isClientSide() || !WeaverStations.ACTION_STIRRED.equals(action)) {
            return;
        }
        CompoundTag state = station.state().data();
        ItemStack dyed = barrel.getItem(1);
        // Stirring takes a while: no dye takes in the moment the clothing went in.
        if (dyed.isEmpty() || state.getBoolean(WeaverStations.FRESH)
                || station.level().getGameTime() - state.getLong(WeaverStations.LAID) < 30) {
            return;
        }
        boolean front = isDye(barrel.getItem(4)) || isDye(barrel.getItem(5));
        boolean back = isDye(barrel.getItem(6)) || isDye(barrel.getItem(7));
        dyeing = true;
        try {
            // The clothing in the bath becomes the dyed clothing, and the dye is used as the mod's screen uses it.
            barrel.setItem(0, dyed.copy());
            barrel.setItem(1, ItemStack.EMPTY);
            if (front) {
                useDose(4);
                useDose(5);
            }
            if (back) {
                useDose(6);
                useDose(7);
            }
            barrel.setChanged();
        } finally {
            dyeing = false;
        }
        state.putBoolean(WeaverStations.FRESH, true);
        station.state().setData(state);
        station.effect(WeaverStations.EFFECT_DYED, "bath", new CompoundTag());
    }

    private static boolean isDye(ItemStack stack) {
        return stack.is(WeaversParadiseItems.BOTTLED_DYE.get());
    }

    /** One dose less in a bottle of dye; the block turns an empty one into a glass bottle. */
    private void useDose(int index) {
        ItemStack bottle = barrel.getItem(index).copy();
        if (!isDye(bottle)) {
            return;
        }
        int left = bottle.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("amount") - 1;
        CustomData.update(DataComponents.CUSTOM_DATA, bottle, tag -> tag.putInt("amount", left));
        barrel.setItem(index, bottle);
    }
}
