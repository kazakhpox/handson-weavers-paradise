package net.handsonweavers.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;

import net.handsonweavers.station.LoomLogic;
import net.handsonweavers.station.WeaverStations;
import net.handsonweavers.station.WeavingRows;
import xox.labvorty.weaversparadise.data.recipes.ClothcraftingRecipe;
import xox.labvorty.weaversparadise.init.WeaversParadiseKeyMappings;

/**
 * The clothcrafting station. Before weaving, the spools lie on the loom and a click on the shuttle starts. While weaving, a board
 * over the loom shows the row of arrows to type and the next ones, a strip under it runs down with the clock; the arrow keys (or
 * WASD, or the mod's own minigame keys, or the four pedals) type the row, and every row typed right throws the shuttle across and
 * the cloth on the loom grows. When the clock runs out the woven cloth hovers beside the loom.
 */
public final class LoomBehavior implements StationBehavior {
    private static final SlotRef WARP_SLOT = SlotRef.of(WeaverStations.WARP, 0);
    private static final float ROW_GAP = 2.6F;
    private static final float POPUP_TIME = 1.0F;

    private final List<Integer> typed = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private int localScore = -1;
    private float ahead;
    private float wrong;
    private float done;
    private final float[] pressed = new float[4];
    private boolean shuttleLeft;
    private Item shownCloth;
    private Item shownSpool;

    public LoomBehavior(StationView view) {
    }

    // ------------------------------------------------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------------------------------------------------

    private static boolean weaving(StationView view) {
        return view.state().data().getBoolean(WeaverStations.ON);
    }

    private int score(StationView view) {
        return Math.max(view.state().data().getInt(WeaverStations.SCORE), localScore);
    }

    private static float secondsLeft(StationView view) {
        CompoundTag data = view.state().data();
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return Math.max(0.0F, (data.getLong(WeaverStations.END) - view.level().getGameTime() - partial) / 20.0F);
    }

    @Override
    public void onSceneReady(StationView view) {
        shownCloth = null;
        shownSpool = null;
        view.node("label").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintLabel(view, pose, buffers)));
        view.node("popup").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintPopups(pose, buffers)));
        for (int i = 0; i < 4; i++) {
            int direction = i;
            view.node("pad_" + i).ifPresent(node -> {
                MeshShape.Builder mesh = MeshShape.builder();
                Plates.rect(mesh, 1.3F, -1.3F, -1.3F, 1.3F, 0.0F, Plates.BOARD);
                Plates.arrow(mesh, 0.0F, 0.0F, direction, 1.8F, 0.06F, Plates.ARROW);
                node.setShape(mesh.build(), Plates.TEXTURE, true);
            });
        }
        update(view);
    }

    @Override
    public void onOpen(StationView view) {
        typed.clear();
        if (!weaving(view)) {
            view.showCaption(Component.translatable("handson_weavers_paradise.loom.hint_start"), 4.0F);
        }
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        if (changes.data()) {
            int server = view.state().data().getInt(WeaverStations.SCORE);
            if (!weaving(view)) {
                localScore = -1;
                typed.clear();
            } else if (server >= localScore) {
                localScore = -1;
                ahead = 0.0F;
            }
        }
        update(view);
    }

    private void update(StationView view) {
        boolean on = weaving(view);
        view.node("board").ifPresent(node -> node.setVisible(on));
        for (int i = 0; i < 4; i++) {
            view.node("pad_" + i).ifPresent(node -> {
                node.setVisible(on);
                node.setEnabled(on);
            });
        }
        view.node("shuttle").ifPresent(node -> node.setEnabled(!on));
        // The shuttle carries the spools laid on the loom (or the ones being woven), an empty spool when there are none.
        ItemStack warp = view.state().getItem(WARP_SLOT);
        Item spool = !warp.isEmpty() ? warp.getItem() : on && clothSpool(view) != null ? clothSpool(view) : null;
        if (spool != shownSpool) {
            shownSpool = spool;
            view.node("shuttle").ifPresent(node -> node.setVisual(spool == null ? null
                    : Visual.ofItem(spool).withAnchor(new org.joml.Vector3f(8.0F, 8.0F, 8.0F))));
        }
        // The cloth on the loom: what these spools weave, growing with the rows.
        Item cloth = clothOf(view);
        view.node("woven").ifPresent(node -> {
            node.setVisible(on && cloth != null);
            if (cloth != null && cloth != shownCloth) {
                shownCloth = cloth;
                node.setVisual(Visual.ofItem(cloth));
            }
        });
    }

    /** The spools being woven. */
    private static Item clothSpool(StationView view) {
        String made = view.state().data().getString(WeaverStations.MADE);
        ResourceLocation spool = made.isEmpty() ? null : ResourceLocation.tryParse(made);
        return spool == null ? null : BuiltInRegistries.ITEM.getOptional(spool).orElse(null);
    }

    /** The cloth the spools being woven make: Weavers Paradise names its cloth after its spool. */
    private static Item clothOf(StationView view) {
        String made = view.state().data().getString(WeaverStations.MADE);
        ResourceLocation spool = made.isEmpty() ? null : ResourceLocation.tryParse(made);
        if (spool == null || !spool.getPath().endsWith("_spool")) {
            return null;
        }
        ResourceLocation cloth = spool.withPath(spool.getPath().replace("_spool", "_cloth"));
        return BuiltInRegistries.ITEM.getOptional(cloth).orElse(null);
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------------------------------

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (!weaving(view)) {
            return false;
        }
        int direction = direction(keyCode, scanCode);
        if (direction < 0) {
            return false;
        }
        type(view, direction);
        return true;
    }

    private static int direction(int keyCode, int scanCode) {
        int[] arrows = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT};
        int[] wasd = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D};
        KeyMapping[] mod = {WeaversParadiseKeyMappings.MINIGAME_UP_BUTTON, WeaversParadiseKeyMappings.MINIGAME_LEFT_BUTTON,
                WeaversParadiseKeyMappings.MINIGAME_DOWN_BUTTON, WeaversParadiseKeyMappings.MINIGAME_RIGHT_BUTTON};
        for (int i = 0; i < 4; i++) {
            if (keyCode == arrows[i] || keyCode == wasd[i] || mod[i] != null && mod[i].matches(keyCode, scanCode)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        String node = click.node();
        if (node.startsWith("pad_") && weaving(view)) {
            type(view, node.charAt(4) - '0');
        } else if ("shuttle".equals(node) && !weaving(view)) {
            start(view);
        }
    }

    private void start(StationView view) {
        ItemStack spools = view.state().getItem(WARP_SLOT);
        Optional<ClothcraftingRecipe> recipe = LoomLogic.recipe(view.level(), spools);
        if (recipe.isEmpty()) {
            view.showCaption(Component.translatable("handson_weavers_paradise.loom.need_spools").withStyle(ChatFormatting.RED), 3.0F);
            return;
        }
        if (spools.getCount() < recipe.get().getSpoolCost()) {
            view.showCaption(Component.translatable("handson_weavers_paradise.loom.need_count", recipe.get().getSpoolCost())
                    .withStyle(ChatFormatting.RED), 3.0F);
            return;
        }
        view.sendAction(WeaverStations.ACTION_START, new CompoundTag());
        view.playSound(SoundEvents.WOOL_PLACE, "shuttle", 0.8F, 1.2F);
    }

    private void type(StationView view, int direction) {
        pressed[direction] = 0.18F;
        int score = score(view);
        int[] row = WeavingRows.row(view.state().data().getLong(WeaverStations.SEED), score);
        int at = typed.size();
        if (row[at] != direction) {
            typed.clear();
            wrong = 0.35F;
            view.playSound(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), "board", 0.5F, 0.7F);
            view.node("board").ifPresent(node -> node.shake(0.3F, 0.25F));
            return;
        }
        typed.add(direction);
        view.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), "board", 0.4F, 1.0F + typed.size() * 0.12F);
        if (typed.size() < WeavingRows.LENGTH) {
            return;
        }
        // A row woven: the server checks and counts it.
        CompoundTag data = new CompoundTag();
        data.putInt(WeaverStations.ROW, score);
        data.putIntArray(WeaverStations.ARROWS, row);
        view.sendAction(WeaverStations.ACTION_ROW, data);
        localScore = score + 1;
        ahead = 0.0F;
        typed.clear();
        done = 0.25F;
        view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "board", 0.6F, 1.0F + Math.min(score, 20) * 0.03F);
        throwShuttle(view);
        popup(Component.literal("+1"), 0xFF7FD27A, 1.0F);
    }

    private void throwShuttle(StationView view) {
        shuttleLeft = !shuttleLeft;
        view.node("shuttle").ifPresent(node -> node.hopTo(shuttleLeft ? -7.0F : 0.0F, 0.0F, 0.0F, 1.2F, 0.22F));
        view.playSound(SoundEvents.WOOL_STEP, "shuttle", 0.6F, 1.6F);
        view.node("woven").ifPresent(node -> node.shake(0.15F, 0.15F));
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Each frame
    // ------------------------------------------------------------------------------------------------------------------

    @Override
    public void frame(StationView view, float dt) {
        wrong = Math.max(0.0F, wrong - dt);
        done = Math.max(0.0F, done - dt);
        for (int i = 0; i < 4; i++) {
            pressed[i] = Math.max(0.0F, pressed[i] - dt);
            float push = pressed[i] > 0.0F ? 0.35F : 0.0F;
            view.node("pad_" + i).ifPresent(node -> node.moveTo(0.0F, 0.0F, push, 0.0F));
        }
        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup popup = it.next();
            popup.age += dt;
            if (popup.age > POPUP_TIME) {
                it.remove();
            }
        }
        if (localScore >= 0) {
            ahead += dt;
            // The server did not count a row this client thought woven: take its word.
            if (ahead > 3.0F) {
                localScore = -1;
                ahead = 0.0F;
            }
        }
        if (weaving(view)) {
            drawBoard(view);
            float grow = Mth.clamp(0.35F + score(view) * 0.035F, 0.35F, 1.1F);
            view.node("woven").ifPresent(node -> node.scaleTo(grow, 0.2F));
        }
    }

    private void drawBoard(StationView view) {
        NodeView board = view.node("board").orElse(null);
        if (board == null) {
            return;
        }
        CompoundTag data = view.state().data();
        long seed = data.getLong(WeaverStations.SEED);
        int score = score(view);
        MeshShape.Builder mesh = MeshShape.builder();
        float half = 7.8F;
        Plates.rect(mesh, half, -half, -3.4F, 9.6F, 0.0F, Plates.FRAME);
        Plates.rect(mesh, half - 0.3F, -half + 0.3F, -3.1F, 9.3F, 0.03F, Plates.BOARD);
        // The rows to come, smaller and dimmer the further away.
        float[] heights = {0.0F, 3.2F, 5.6F, 7.6F};
        float[] sizes = {2.3F, 1.7F, 1.4F, 1.15F};
        for (int r = 3; r >= 0; r--) {
            int[] row = WeavingRows.row(seed, score + r);
            float gap = r == 0 ? ROW_GAP + 0.3F : ROW_GAP * 0.75F;
            for (int i = 0; i < WeavingRows.LENGTH; i++) {
                float x = (2 - i) * gap;
                int swatch;
                if (r > 0) {
                    swatch = Plates.ARROW_DIM;
                } else if (wrong > 0.0F) {
                    swatch = Plates.WRONG;
                } else if (done > 0.0F || i < typed.size()) {
                    swatch = Plates.ARROW_DONE;
                } else {
                    swatch = Plates.ARROW;
                }
                Plates.arrow(mesh, x, heights[r], row[i], sizes[r], 0.08F, swatch);
            }
        }
        // The clock: a strip that runs down.
        int duration = Math.max(1, data.getInt(WeaverStations.DURATION));
        float left = Mth.clamp(secondsLeft(view) * 20.0F / duration, 0.0F, 1.0F);
        Plates.rect(mesh, half - 0.6F, -half + 0.6F, -2.6F, -1.9F, 0.06F, Plates.TRACK);
        float width = (half - 0.6F) * 2.0F * left;
        if (width > 0.0F) {
            Plates.rect(mesh, half - 0.6F, half - 0.6F - width, -2.6F, -1.9F, 0.09F, left < 0.25F ? Plates.TIME_LOW : Plates.TIME);
        }
        board.setShape(mesh.build(), Plates.TEXTURE, true);
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Effects from the server
    // ------------------------------------------------------------------------------------------------------------------

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean byMe = player != null && data.hasUUID(WeaverStations.PLAYER) && data.getUUID(WeaverStations.PLAYER).equals(player.getUUID());
        if (WeaverStations.EFFECT_ROW.equals(effect) && !byMe) {
            throwShuttle(view);
            popup(Component.literal("+1"), 0xFF7FD27A, 1.0F);
        } else if (WeaverStations.EFFECT_WOVEN.equals(effect)) {
            localScore = -1;
            typed.clear();
            Item made = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(data.getString(WeaverStations.ITEM)))
                    .orElse(Items.AIR);
            popup(Component.translatable("handson_weavers_paradise.loom.woven", data.getInt(WeaverStations.SCORE)), 0xFFFFC93C, 1.3F);
            view.spawnParticles(DiegesisParticles.GLINT, "cloth_out", 10, 0xFFFFF4C0);
            if (view.isFocused()) {
                view.showCaption(Component.translatable("handson_weavers_paradise.loom.take", made.getDescription(),
                        data.getInt(WeaverStations.QUALITY)), 5.0F);
            }
        } else if (WeaverStations.EFFECT_REFUSED.equals(effect) && byMe) {
            view.showCaption(Component.translatable(data.getString(WeaverStations.REASON)).withStyle(ChatFormatting.RED), 3.0F);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Words over the loom
    // ------------------------------------------------------------------------------------------------------------------

    private void paintLabel(StationView view, PoseStack pose, MultiBufferSource buffers) {
        if (weaving(view)) {
            int seconds = (int) Math.ceil(secondsLeft(view));
            Component line = Component.translatable("handson_weavers_paradise.loom.rows", score(view), seconds / 60,
                    String.format("%02d", seconds % 60));
            TextPainter.draw(pose, buffers, line, 1.1F, 0.0F, 0xFFFFFFFF);
            return;
        }
        if (!view.isFocused()) {
            return;
        }
        ItemStack spools = view.state().getItem(WARP_SLOT);
        Optional<ClothcraftingRecipe> recipe = LoomLogic.recipe(view.level(), spools);
        Component line = recipe.isPresent()
                ? Component.translatable("handson_weavers_paradise.loom.ready", spools.getCount(), recipe.get().getSpoolCost())
                : Component.translatable("handson_weavers_paradise.loom.hint_spools");
        TextPainter.draw(pose, buffers, line, 0.9F, 0.0F, 0xD0FFFFFF);
    }

    private void popup(Component text, int colour, float size) {
        popups.add(new Popup(text, colour, size));
        if (popups.size() > 4) {
            popups.removeFirst();
        }
    }

    private void paintPopups(PoseStack pose, MultiBufferSource buffers) {
        for (Popup popup : popups) {
            float t = popup.age / POPUP_TIME;
            int alpha = (int) (255 * Mth.clamp(1.0F - (t - 0.5F) / 0.5F, 0.0F, 1.0F));
            TextPainter.draw(pose, buffers, popup.text, 1.4F * popup.size, popup.age * 3.0F, (alpha << 24) | (popup.colour & 0xFFFFFF));
        }
    }

    private static final class Popup {
        final Component text;
        final int colour;
        final float size;
        float age;

        Popup(Component text, int colour, float size) {
            this.text = text;
            this.colour = colour;
            this.size = size;
        }
    }
}
