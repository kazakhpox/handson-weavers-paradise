package net.handsonweavers.client;

import org.joml.Vector3f;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;

import net.handsonweavers.station.WeaverStations;

/**
 * The spinning jenny at work: the spool being wound turns and the thread runs to it from the fibre, lint drifts off, and a strip
 * over the jenny fills as the spool does.
 */
public final class JennyBehavior implements StationBehavior {
    private float shownProgress;
    private int winding = -1;
    private int lastTotal;
    private float clack;

    public JennyBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        winding = -1;
        view.node("label").ifPresent(node -> node.setPainter((pose, buffers, light) -> {
            CompoundTag data = view.state().data();
            int total = data.getInt(WeaverStations.TOTAL);
            if (total > 0) {
                int percent = Mth.clamp(Math.round(shownProgress * 100.0F), 0, 100);
                TextPainter.draw(pose, buffers, Component.translatable("handson_weavers_paradise.jenny.spinning", percent), 1.1F, 0.0F,
                        0xFFFFFFFF);
            } else if (view.isFocused()) {
                TextPainter.draw(pose, buffers, Component.translatable(hint(view)), 0.9F, 0.0F, 0xD0FFFFFF);
            }
        }));
    }

    private static String hint(StationView view) {
        boolean fibre = !view.state().getItem(SlotRef.of(WeaverStations.FIBRE, 0)).isEmpty();
        boolean spool = false;
        for (ItemStack stack : view.state().getItems(WeaverStations.SPOOLS)) {
            spool |= isEmptySpool(stack);
        }
        if (!fibre) {
            return "handson_weavers_paradise.jenny.need_fibre";
        }
        return spool ? "handson_weavers_paradise.jenny.need_more" : "handson_weavers_paradise.jenny.need_spool";
    }

    private static boolean isEmptySpool(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("empty_spool");
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        if (changes.data()) {
            int total = view.state().data().getInt(WeaverStations.TOTAL);
            if (total > 0 && view.state().data().getInt(WeaverStations.PROGRESS) == 0 && lastTotal > 0 && shownProgress > 0.8F) {
                // A spool came full.
                view.playSound(SoundEvents.WOOL_PLACE, "fibre", 0.7F, 1.4F);
                view.spawnParticles(DiegesisParticles.GLINT, winding >= 0 ? "spool_" + winding : "fibre", 5, 0xFFFFFFFF);
                shownProgress = 0.0F;
            }
            lastTotal = total;
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        CompoundTag data = view.state().data();
        int total = data.getInt(WeaverStations.TOTAL);
        boolean working = total > 0;
        float target = working ? data.getInt(WeaverStations.PROGRESS) / (float) total : 0.0F;
        // The server counts in ticks: the strip runs on smoothly between its reports.
        if (working && target >= shownProgress) {
            shownProgress = Math.min(1.0F, Math.max(target, shownProgress + dt * 20.0F / total));
            shownProgress = Math.min(shownProgress, target + 2.5F / total);
        } else {
            shownProgress = target;
        }
        int next = -1;
        if (working) {
            ItemStack[] spools = view.state().getItems(WeaverStations.SPOOLS);
            for (int i = 0; i < spools.length; i++) {
                if (isEmptySpool(spools[i])) {
                    next = i;
                    break;
                }
            }
        }
        if (next != winding) {
            if (winding >= 0) {
                view.node("spool_" + winding).ifPresent(node -> node.spin(0.0F, 0.0F, 0.0F));
            }
            winding = next;
            if (winding >= 0) {
                view.node("spool_" + winding).ifPresent(node -> node.spin(0.0F, 0.0F, 540.0F));
            }
        }
        view.node("bar").ifPresent(node -> {
            node.setVisible(working);
            if (working) {
                MeshShape.Builder mesh = MeshShape.builder();
                Plates.rect(mesh, 6.3F, -6.3F, -0.3F, 1.3F, 0.0F, Plates.FRAME);
                Plates.rect(mesh, 6.0F, -6.0F, 0.0F, 1.0F, 0.05F, Plates.TRACK);
                if (shownProgress > 0.0F) {
                    Plates.rect(mesh, 6.0F, 6.0F - 12.0F * shownProgress, 0.0F, 1.0F, 0.1F, Plates.ARROW);
                }
                node.setShape(mesh.build(), Plates.TEXTURE, true);
            }
        });
        view.node("thread").ifPresent(node -> node.setVisible(working && winding >= 0));
        if (working && winding >= 0) {
            drawThread(view);
            clack -= dt;
            if (clack <= 0.0F) {
                clack = 0.45F;
                view.spawnParticles(DiegesisParticles.DUST, "fibre", 1, 0xFFF4EEE0);
            }
        }
    }

    /** A thread from the fibre to the spool being wound, as a thin band facing the camera. */
    private void drawThread(StationView view) {
        var fibre = view.node("fibre").map(node -> node.scenePosition());
        var spool = view.node("spool_" + winding).map(node -> node.scenePosition());
        if (fibre.isEmpty() || spool.isEmpty()) {
            return;
        }
        Vector3f a = fibre.get().add(0.0F, 0.6F, 0.0F);
        Vector3f b = spool.get();
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.01F) {
            return;
        }
        float nx = -dy / length * 0.08F;
        float ny = dx / length * 0.08F;
        float u = (2 * 4 + 2) / 16.0F;
        float v = 2 / 16.0F;
        MeshShape.Builder mesh = MeshShape.builder();
        float z = Math.min(a.z, b.z) - 0.6F;
        // The thread node sits at the scene's origin: points are scene pixels. Counter-clockwise from the front (-Z).
        mesh.corner(a.x + nx, a.y + ny, z, u, v, 0, 0, -1)
                .corner(b.x + nx, b.y + ny, z, u, v, 0, 0, -1)
                .corner(b.x - nx, b.y - ny, z, u, v, 0, 0, -1)
                .corner(a.x - nx, a.y - ny, z, u, v, 0, 0, -1);
        mesh.corner(a.x - nx, a.y - ny, z, u, v, 0, 0, -1)
                .corner(b.x - nx, b.y - ny, z, u, v, 0, 0, -1)
                .corner(b.x + nx, b.y + ny, z, u, v, 0, 0, -1)
                .corner(a.x + nx, a.y + ny, z, u, v, 0, 0, -1);
        view.node("thread").ifPresent(node -> node.setShape(mesh.build(), Plates.TEXTURE, true));
    }
}
