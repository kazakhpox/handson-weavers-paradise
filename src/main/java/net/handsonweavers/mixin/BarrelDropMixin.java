package net.handsonweavers.mixin;

import java.util.Comparator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.Diegesis;

import xox.labvorty.weaversparadise.blocks.DyeingBarrelBlock;

/**
 * The dyeing barrel hands back its stencils and dyes when the clothing is gone by throwing them out at its corner. They go to the
 * player instead: the one working at the barrel, else the nearest; into the inventory, and what does not fit is dropped before
 * them as if they had thrown it. With nobody near they come out over the barrel's top.
 */
@Mixin(value = DyeingBarrelBlock.class, remap = false)
public abstract class BarrelDropMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private static void handson$toThePlayer(ItemStack stack, ServerLevel level, int x, int y, int z, CallbackInfo info) {
        info.cancel();
        if (stack.isEmpty()) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        Player player = Diegesis.activeStation(level, pos)
                .flatMap(station -> station.users().stream().map(user -> (Player) user).findFirst())
                .orElseGet(() -> level.players().stream()
                        .filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(pos.getCenter()) < 64.0)
                        .min(Comparator.comparingDouble(p -> p.distanceToSqr(pos.getCenter())))
                        .orElse(null));
        if (player == null) {
            ItemEntity item = new ItemEntity(level, x + 0.5, y + 1.05, z + 0.5, stack, 0.0, 0.1, 0.0);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
            return;
        }
        ItemStack left = stack.copy();
        player.getInventory().add(left);
        if (!left.isEmpty()) {
            player.drop(left, false);
        }
    }
}
