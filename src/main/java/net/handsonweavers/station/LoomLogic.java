package net.handsonweavers.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import xox.labvorty.weaversparadise.blocks.entities.ClothcraftingStationBlockEntity;
import xox.labvorty.weaversparadise.data.recipes.ClothcraftingRecipe;
import xox.labvorty.weaversparadise.data.recipes.ClothcraftingRecipeInput;
import xox.labvorty.weaversparadise.init.WeaversParadiseRecipes;

/**
 * The clothcrafting station. Weaving starts here as with the mod's own button (the spools are taken, the clock runs in the block);
 * every row of arrows typed right counts one point, checked here against the row the weaving's seed makes; when the clock runs out
 * the block itself weaves the cloth from the points, and the station hands it out.
 */
public final class LoomLogic implements StationLogic {
    private final ClothcraftingStationBlockEntity loom;
    private int shownScore = -1;
    private int shownOutputs = -1;

    public LoomLogic(StationContext station) {
        loom = station.blockEntity() instanceof ClothcraftingStationBlockEntity found ? found : null;
        if (loom != null) {
            station.state().bindSlots(WeaverStations.WARP, SlotStorage.of(loom, 0, 1));
        }
    }

    public static Optional<ClothcraftingRecipe> recipe(Level level, ItemStack spools) {
        if (spools.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(WeaversParadiseRecipes.CLOTHCRAFTING_TYPE.get(),
                new ClothcraftingRecipeInput(spools.copy()), level).map(RecipeHolder::value);
    }

    /** What the weaving made, the cloth first and the spools given back after it. */
    private List<ItemStack> outputs() {
        List<ItemStack> list = loom.getItemsList();
        List<ItemStack> made = list == null ? new ArrayList<>() : new ArrayList<>(list.stream().filter(stack -> !stack.isEmpty()).toList());
        made.sort(java.util.Comparator.comparingInt(stack -> isSpool(stack) ? 1 : 0));
        return made;
    }

    private static boolean isSpool(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("spool");
    }

    /** The quality Weavers Paradise writes on a cloth. */
    public static int quality(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt("quality");
    }

    @Override
    public void updateOutputs(StationContext station) {
        if (loom == null || station.isClientSide()) {
            return;
        }
        List<ItemStack> list = outputs();
        station.state().setOutput(WeaverStations.CLOTH, list.isEmpty() ? ItemStack.EMPTY : list.getFirst().copy());
        shownOutputs = list.stream().mapToInt(ItemStack::getCount).sum() * 31 + list.size();
    }

    @Override
    public ItemStack takeOutput(StationContext station, Player player, String output) {
        if (loom == null || !WeaverStations.CLOTH.equals(output)) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> list = outputs();
        if (list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = list.removeFirst();
        loom.setItems(list);
        loom.setChanged();
        return taken;
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (loom == null || station.isClientSide() || !(player instanceof ServerPlayer weaver)) {
            return;
        }
        if (WeaverStations.ACTION_START.equals(action)) {
            start(station, weaver);
        } else if (WeaverStations.ACTION_ROW.equals(action)) {
            row(station, weaver, data);
        }
    }

    private void start(StationContext station, ServerPlayer weaver) {
        if (loom.getGameOn()) {
            return;
        }
        if (!outputs().isEmpty()) {
            refuse(station, weaver, "handson_weavers_paradise.loom.take_first");
            return;
        }
        ItemStack spools = loom.getItem(0).copy();
        Optional<ClothcraftingRecipe> recipe = recipe(station.level(), spools);
        if (recipe.isEmpty() || spools.getCount() < recipe.get().getSpoolCost()) {
            refuse(station, weaver, "handson_weavers_paradise.loom.need_spools");
            return;
        }
        // As the mod's own start button: the spools are used up, the clock and the points start.
        loom.setClothType(spools.copy());
        spools.shrink(recipe.get().getSpoolCost());
        loom.setItem(0, spools);
        int duration = recipe.get().getGameDuration();
        loom.setGameOn(true);
        loom.setGameScore(0);
        loom.setGameTime(duration);
        loom.setChanged();
        adopt(station, duration, weaver);
        station.level().playSound(null, station.pos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /** The station's data takes up a weaving going on in the block: one started here, or with the mod's own screen. */
    private void adopt(StationContext station, int ticksLeft, Player weaver) {
        CompoundTag data = station.state().data();
        data.putBoolean(WeaverStations.ON, true);
        data.putLong(WeaverStations.SEED, station.level().random.nextLong());
        data.putInt(WeaverStations.SCORE, loom.getGameScore());
        data.putLong(WeaverStations.END, station.level().getGameTime() + ticksLeft);
        data.putInt(WeaverStations.DURATION, Math.max(1, ticksLeft));
        if (weaver != null) {
            data.putUUID(WeaverStations.WEAVER, weaver.getUUID());
        }
        ItemStack type = loom.getClothType();
        if (type != null && !type.isEmpty()) {
            data.putString(WeaverStations.MADE, BuiltInRegistries.ITEM.getKey(type.getItem()).toString());
        }
        station.state().setData(data);
        shownScore = loom.getGameScore();
    }

    private void row(StationContext station, ServerPlayer weaver, CompoundTag typed) {
        CompoundTag data = station.state().data();
        if (!loom.getGameOn() || !data.getBoolean(WeaverStations.ON)) {
            return;
        }
        int score = loom.getGameScore();
        int index = typed.getInt(WeaverStations.ROW);
        int[] arrows = typed.getIntArray(WeaverStations.ARROWS);
        // Only the next row counts, and only typed as the weaving's seed makes it.
        if (index != score || !Arrays.equals(arrows, WeavingRows.row(data.getLong(WeaverStations.SEED), index))) {
            return;
        }
        loom.setGameScore(score + 1);
        loom.setChanged();
        data.putInt(WeaverStations.SCORE, score + 1);
        station.state().setData(data);
        shownScore = score + 1;
        CompoundTag effect = new CompoundTag();
        effect.putUUID(WeaverStations.PLAYER, weaver.getUUID());
        effect.putInt(WeaverStations.SCORE, score + 1);
        station.effect(WeaverStations.EFFECT_ROW, "cloth_roll", effect);
    }

    @Override
    public void tick(StationContext station) {
        if (loom == null || station.isClientSide()) {
            return;
        }
        CompoundTag data = station.state().data();
        boolean on = data.getBoolean(WeaverStations.ON);
        if (loom.getGameOn() && !on) {
            adopt(station, loom.getGameTime(), null);
        } else if (!loom.getGameOn() && on) {
            // The clock ran out and the block wove the cloth from the points.
            int score = data.getInt(WeaverStations.SCORE);
            data.remove(WeaverStations.ON);
            data.remove(WeaverStations.WEAVER);
            data.remove(WeaverStations.END);
            station.state().setData(data);
            List<ItemStack> made = outputs();
            CompoundTag effect = new CompoundTag();
            effect.putInt(WeaverStations.SCORE, score);
            if (!made.isEmpty()) {
                effect.putString(WeaverStations.ITEM, BuiltInRegistries.ITEM.getKey(made.getFirst().getItem()).toString());
                effect.putInt(WeaverStations.QUALITY, quality(made.getFirst()));
            }
            station.effect(WeaverStations.EFFECT_WOVEN, "cloth_roll", effect);
            station.level().playSound(null, station.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5F, 1.4F);
        } else if (on && loom.getGameScore() != shownScore) {
            shownScore = loom.getGameScore();
            data.putInt(WeaverStations.SCORE, shownScore);
            station.state().setData(data);
        }
        List<ItemStack> list = outputs();
        int key = list.stream().mapToInt(ItemStack::getCount).sum() * 31 + list.size();
        if (key != shownOutputs) {
            updateOutputs(station);
        }
    }

    private static void refuse(StationContext station, ServerPlayer weaver, String reason) {
        CompoundTag effect = new CompoundTag();
        effect.putUUID(WeaverStations.PLAYER, weaver.getUUID());
        effect.putString(WeaverStations.REASON, reason);
        station.effect(WeaverStations.EFFECT_REFUSED, "warp", effect);
    }
}
