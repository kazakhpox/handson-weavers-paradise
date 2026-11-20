package net.handsonweavers.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.pockyl.diegesis.api.client.DiegesisClient;

import net.handsonweavers.HandsOnWeavers;
import net.handsonweavers.station.WeaverStations;

/** Client setup: the stations' behaviours, and their effects (each behaviour plays them in its {@code onEffect}). */
@Mod(value = HandsOnWeavers.MOD_ID, dist = Dist.CLIENT)
public final class HandsOnWeaversClient {
    public HandsOnWeaversClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(HandsOnWeaversClient::setup));
    }

    private static void setup() {
        DiegesisClient.registerBehavior(WeaverStations.JENNY_ID, JennyBehavior::new);
        DiegesisClient.registerBehavior(WeaverStations.DYEMAKING_ID, DyemakingBehavior::new);
        DiegesisClient.registerBehavior(WeaverStations.LOOM_ID, LoomBehavior::new);
        for (ResourceLocation effect : new ResourceLocation[]{WeaverStations.EFFECT_ROW, WeaverStations.EFFECT_WOVEN,
                WeaverStations.EFFECT_REFUSED}) {
            DiegesisClient.registerEffect(effect, (view, node, data) -> {
            });
        }
    }
}
