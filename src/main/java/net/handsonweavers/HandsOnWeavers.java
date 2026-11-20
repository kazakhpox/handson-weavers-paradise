package net.handsonweavers;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;

import net.handsonweavers.station.WeaverStations;

/**
 * Hands-On Weavers Paradise: the stations of Weavers Paradise open in the world as Diegesis stations. Adds nothing to the
 * registries, so a player or a server without it keeps the mod's own screens.
 */
@Mod(HandsOnWeavers.MOD_ID)
public final class HandsOnWeavers {
    public static final String MOD_ID = "handson_weavers_paradise";

    public HandsOnWeavers() {
        WeaverStations.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
