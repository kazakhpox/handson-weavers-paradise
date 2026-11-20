package net.handsonweavers.station;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import xox.labvorty.weaversparadise.blocks.SpinningJennyBlock;
import xox.labvorty.weaversparadise.blocks.entities.SpinningJennyBlockEntity;
import xox.labvorty.weaversparadise.data.recipes.SpinningJennyRecipe;

/**
 * The spinning jenny spins by itself, as Weavers Paradise has it: the station shows its fibre and spools and tells the clients how
 * far the spool being wound is, so the wheel turns and the thread grows while it works.
 */
public final class JennyLogic implements StationLogic {
    private final SpinningJennyBlockEntity jenny;
    private int shownProgress = -1;
    private int shownTotal = -1;

    public JennyLogic(StationContext station) {
        jenny = station.blockEntity() instanceof SpinningJennyBlockEntity found ? found : null;
        if (jenny != null) {
            Container container = jenny;
            station.state().bindSlots(WeaverStations.FIBRE, SlotStorage.of(container, 0, 1));
            station.state().bindSlots(WeaverStations.SPOOLS, SlotStorage.of(container, 1, WeaverStations.SPOOL_COUNT));
        }
    }

    @Override
    public void tick(StationContext station) {
        if (jenny == null || station.isClientSide() || !(station.blockState().getBlock() instanceof SpinningJennyBlock block)) {
            return;
        }
        int total = recipe(station, block).map(SpinningJennyRecipe::getCraftTime).orElse(0);
        int progress = total > 0 ? jenny.getWorkingState() : 0;
        if (progress != shownProgress || total != shownTotal) {
            shownProgress = progress;
            shownTotal = total;
            CompoundTag data = station.state().data();
            data.putInt(WeaverStations.PROGRESS, progress);
            data.putInt(WeaverStations.TOTAL, total);
            station.state().setData(data);
        }
    }

    /** The recipe the jenny works now, found as the block finds it: the fibre with the first spool it fits. */
    private Optional<SpinningJennyRecipe> recipe(StationContext station, SpinningJennyBlock block) {
        ItemStack fibre = jenny.getItem(0);
        for (int i = 1; i <= WeaverStations.SPOOL_COUNT; i++) {
            Optional<SpinningJennyRecipe> found = block.getCurrentRecipe(station.level(), fibre, jenny.getItem(i));
            if (found.isPresent()) {
                return fibre.getCount() >= found.get().getCountRequired() ? found : Optional.empty();
            }
        }
        return Optional.empty();
    }
}
