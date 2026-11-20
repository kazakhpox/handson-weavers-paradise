package net.handsonweavers.client;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.StationState;

import net.handsonweavers.station.WeaverStations;

/**
 * The dyemaking table: the dyes stand in a rack behind the table, the bottles at its front, the core in the pot; when they make a
 * dye the pot bubbles and the bottled dye hovers over the table.
 */
public final class DyemakingBehavior implements StationBehavior {
    private boolean ready;

    public DyemakingBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        update(view, false);
        view.node("label").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (!view.isFocused()) {
                return;
            }
            ItemStack result = view.state().getItem(WeaverStations.DYE_RESULT_SLOT);
            Component line = result.isEmpty() ? Component.translatable("handson_weavers_paradise.dye.hint")
                    : result.getHoverName();
            TextPainter.draw(pose, buffers, line, 0.9F, 0.0F, result.isEmpty() ? 0xC0FFFFFF : 0xFFFFFFFF);
        }));
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        update(view, true);
    }

    private void update(StationView view, boolean sound) {
        boolean now = !view.state().getItem(WeaverStations.DYE_RESULT_SLOT).isEmpty();
        view.node("pot").ifPresent(node -> node.setEmitting(now));
        if (now && !ready && sound) {
            view.playSound(SoundEvents.BREWING_STAND_BREW, "pot", 0.4F, 1.4F);
            view.spawnParticles(DiegesisParticles.BUBBLE_POP, "pot", 6, 0);
        }
        ready = now;
    }
}
