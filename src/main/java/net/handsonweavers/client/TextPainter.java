package net.handsonweavers.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

/** Draws text standing in a scene, facing the block's front (-Z), where the stations' cameras stand. */
final class TextPainter {
    private TextPainter() {
    }

    /**
     * Draws {@code text} centred on the node's origin, {@code height} pixels high, raised by {@code up} pixels.
     *
     * @param argb colour; its alpha fades the text
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, Component text, float height, float up, int argb) {
        if ((argb >>> 24) < 8) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float scale = height / font.lineHeight;
        pose.pushPose();
        pose.translate(0.0F, up, 0.0F);
        // Seen from the front, the screen's right is -X: text x runs there, text y downwards, the glyphs face -Z.
        Matrix4f face = new Matrix4f(
                -scale, 0.0F, 0.0F, 0.0F,
                0.0F, -scale, 0.0F, 0.0F,
                0.0F, 0.0F, -scale, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        pose.last().pose().mul(face);
        float width = font.width(text);
        font.drawInBatch(text, -width / 2.0F, -font.lineHeight / 2.0F, argb, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
