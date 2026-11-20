package net.handsonweavers.client;

import java.util.Optional;

import org.joml.Vector3f;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;

import net.handsonweavers.station.WeaverStations;
import xox.labvorty.weaversparadise.init.WeaversParadiseItems;

/**
 * The dyeing barrel, worked as the cooking pot of Hands-On Delight: the clothing floats in the bath, the bottles of dye stand on the
 * rim (the front side's on the left, the back side's on the right) and colour the bath, the stencils hang on the barrel's front,
 * and the paddle stirs the dye through. The dyed clothing rises over the barrel, dripping, to be taken.
 */
public final class BarrelBehavior implements StationBehavior {
    /** The bath's surface, scene pixels. */
    private static final float SURFACE = 14.7F;
    private static final float PADDLE_REST_X = 4.0F;
    private static final float PADDLE_REST_Z = 12.0F;
    /** How high the paddle's middle stands over the surface: its blade dips into the bath. */
    private static final float PADDLE_HEIGHT = 2.6F;

    private int bathColour;
    private float drip;
    private float stirring;
    private float paddleX = PADDLE_REST_X;
    private float paddleZ = PADDLE_REST_Z;
    private boolean stroking;
    private boolean held;
    private float strokeTime;
    private float lastX = Float.NaN;
    private float lastZ = Float.NaN;
    private boolean hadDyed;

    public BarrelBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        bathColour = -1;
        hadDyed = !view.state().getItem(WeaverStations.DYED_SLOT).isEmpty();
        view.node("label").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            if (view.isFocused()) {
                TextPainter.draw(pose, buffers, Component.translatable(hint(view)), 0.9F, 0.0F, 0xD8FFFFFF);
            }
        }));
        view.node("bath").ifPresent(node -> node.setWakeLook(3.0F, 0.7F));
    }

    private static String hint(StationView view) {
        if (!view.state().getItem(WeaverStations.DYED_SLOT).isEmpty()) {
            return "handson_weavers_paradise.barrel.take";
        }
        if (view.state().getItem(WeaverStations.CLOTHING_SLOT).isEmpty()) {
            return "handson_weavers_paradise.barrel.clothing";
        }
        return "handson_weavers_paradise.barrel.dye";
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        boolean dyed = !view.state().getItem(WeaverStations.DYED_SLOT).isEmpty();
        if (dyed && !hadDyed) {
            // The dye took: the clothing comes up out of the bath.
            view.playSound(SoundEvents.BUCKET_FILL, "bath", 0.5F, 1.3F);
            view.node("bath").ifPresent(node -> node.ripple(8.0F, 8.0F, 0.6F));
            view.spawnParticles(DiegesisParticles.SPLASH, "dyed", 8, colour(view) | 0xFF000000);
        }
        hadDyed = dyed;
        for (SlotRef dye : changes.slots()) {
            if (dye.group().equals(WeaverStations.FRONT_DYES) || dye.group().equals(WeaverStations.BACK_DYES)) {
                if (!view.state().getItem(dye).isEmpty()) {
                    view.playSound(SoundEvents.BOTTLE_EMPTY, "bath", 0.6F, 1.0F);
                    view.node("bath").ifPresent(node -> node.ripple(8.0F, 8.0F, 0.4F));
                }
            }
        }
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (WeaverStations.EFFECT_DYED.equals(effect)) {
            view.playSound(SoundEvents.GENERIC_SPLASH, "bath", 0.4F, 1.4F);
            view.spawnParticles(DiegesisParticles.DROPLET, "dyed", 10, colour(view) | 0xFF000000);
        }
    }

    /** The colour the dyes in the barrel give the bath: the first colour of every bottle, mixed; 0 for none. */
    private static int colour(StationView view) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (String group : new String[]{WeaverStations.FRONT_DYES, WeaverStations.BACK_DYES}) {
            for (ItemStack bottle : view.state().getItems(group)) {
                if (!bottle.is(WeaversParadiseItems.BOTTLED_DYE.get())) {
                    continue;
                }
                CompoundTag tag = bottle.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                red += tag.contains("colorRedOne") ? tag.getInt("colorRedOne") : 255;
                green += tag.contains("colorGreenOne") ? tag.getInt("colorGreenOne") : 255;
                blue += tag.contains("colorBlueOne") ? tag.getInt("colorBlueOne") : 255;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return (Mth.clamp(red / count, 0, 255) << 16) | (Mth.clamp(green / count, 0, 255) << 8) | Mth.clamp(blue / count, 0, 255);
    }

    @Override
    public void frame(StationView view, float dt) {
        int colour = colour(view);
        if (colour != bathColour) {
            bathColour = colour;
            int shown = colour == 0 ? 0 : 0xD8000000 | colour;
            view.node("bath").ifPresent(node -> node.setLiquidColor(shown));
        }
        float t = view.age();
        // The clothing floats, rocking on the bath.
        view.node("garment").ifPresent(node -> {
            node.moveTo(Mth.sin(t * 0.9F) * 0.25F, Mth.sin(t * 2.1F) * 0.12F, Mth.cos(t * 0.7F) * 0.25F, 0.0F);
            node.rotateTo(Mth.sin(t * 1.3F) * 4.0F, t * 8.0F % 360.0F, Mth.cos(t * 1.1F) * 4.0F, 0.0F);
        });
        // The dyed clothing drips while it hangs over the barrel.
        if (!view.state().getItem(WeaverStations.DYED_SLOT).isEmpty()) {
            drip -= dt;
            if (drip <= 0.0F) {
                drip = 0.25F + 0.2F * (float) Math.random();
                int drop = colour == 0 ? 0xFF6FA8DC : 0xFF000000 | colour;
                view.node("dyed").ifPresent(node -> view.spawnParticles(DiegesisParticles.DROPLET,
                        node.scenePosition().add(0.0F, -1.2F, 0.0F), new Vector3f(0.0F, -4.0F, 0.0F), 0.8F, 1, drop));
            }
        }
        movePaddle(view, dt);
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The paddle
    // ------------------------------------------------------------------------------------------------------------------

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "paddle".equals(stroke.node())) {
            switch (stroke.phase()) {
                case START -> {
                    stroking = true;
                    strokeTime = view.age();
                    lastX = Float.NaN;
                }
                case END -> {
                    // A short click takes the paddle up until the next click, as the spoon of Hands-On Delight's pot.
                    if (view.age() - strokeTime < 0.25F && !held) {
                        held = true;
                        view.holdStroke();
                    } else {
                        held = false;
                        stroking = false;
                    }
                }
                default -> {
                }
            }
        }
    }

    private void movePaddle(StationView view, float dt) {
        NodeView paddle = view.node("paddle").orElse(null);
        NodeView bath = view.node("bath").orElse(null);
        if (paddle == null || bath == null) {
            return;
        }
        Optional<Vector3f> cursor = stroking && view.isFocused() ? view.cursorOn(SURFACE) : Optional.empty();
        float wantX = PADDLE_REST_X;
        float wantZ = PADDLE_REST_Z;
        if (cursor.isPresent()) {
            wantX = Mth.clamp(cursor.get().x, 3.2F, 12.8F);
            wantZ = Mth.clamp(cursor.get().z, 3.2F, 12.8F);
        }
        float follow = 1.0F - (float) Math.exp(-dt * 18.0F);
        float oldX = paddleX;
        float oldZ = paddleZ;
        paddleX += (wantX - paddleX) * follow;
        paddleZ += (wantZ - paddleZ) * follow;
        float speed = dt > 0.0F ? (float) Math.hypot(paddleX - oldX, paddleZ - oldZ) / dt : 0.0F;
        paddle.moveTo(paddleX, SURFACE + PADDLE_HEIGHT, paddleZ, 0.0F);
        paddle.rotateTo(cursor.isPresent() ? -6.0F : -18.0F, 0.0F, cursor.isPresent() ? 0.0F : 16.0F, 0.15F);
        if (cursor.isPresent()) {
            stirring = Math.min(1.0F, stirring + speed * dt * 0.08F);
            // Bath pixels: the bath node sits at the scene's origin.
            bath.leaveWake(paddleX, paddleZ, 1.1F, Mth.clamp(speed / 30.0F, 0.1F, 0.8F));
            bath.setFloatingObstacles(paddleX, paddleZ, 0.8F);
            if (speed > 6.0F && Math.random() < dt * 4.0F) {
                view.playSound(SoundEvents.GENERIC_SWIM, "bath", 0.15F, 1.6F);
            }
            if (!Float.isNaN(lastX)) {
                // Round the middle: which way the hand goes sets the current.
                float cross = (oldX - 8.0F) * (paddleZ - oldZ) - (oldZ - 8.0F) * (paddleX - oldX);
                bath.setCurrent(Mth.clamp(cross * 0.05F / Math.max(dt, 0.001F), -3.0F, 3.0F));
            }
            lastX = paddleX;
            lastZ = paddleZ;
        } else {
            stirring = Math.max(0.0F, stirring - dt * 0.3F);
            bath.setCurrent(0.0F);
        }
        bath.setAgitation(stirring * 0.4F);
    }
}
