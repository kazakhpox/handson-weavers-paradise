package net.handsonweavers.client;

import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.api.client.MeshShape;

import net.handsonweavers.HandsOnWeavers;

/**
 * Flat shapes made by code in the upright plane z = const of a node, facing the block's front (-Z), coloured by the swatches of one
 * small texture: bars, arrows, frames.
 */
final class Plates {
    static final ResourceLocation TEXTURE = HandsOnWeavers.id("block/swatches");

    static final int FRAME = 0;
    static final int TRACK = 1;
    static final int ARROW = 2;
    static final int ARROW_DONE = 3;
    static final int ARROW_DIM = 4;
    static final int TIME = 5;
    static final int TIME_LOW = 6;
    static final int WRONG = 7;
    static final int PROGRESS = 8;
    static final int BOARD = 9;

    private Plates() {
    }

    /** A rectangle from {@code left} (screen left, +X) to {@code right}, {@code y0} to {@code y1}, {@code depth} in front of z = 0. */
    static void rect(MeshShape.Builder mesh, float left, float right, float y0, float y1, float depth, int swatch) {
        quad(mesh, left, y0, right, y0, right, y1, left, y1, depth, swatch);
    }

    /** Any four corners, given in screen order (bottom left, bottom right, top right, top left), x to the screen's left. */
    static void quad(MeshShape.Builder mesh, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float depth,
            int swatch) {
        float u = ((swatch % 4) * 4 + 2) / 16.0F;
        float v = ((swatch / 4) * 4 + 2) / 16.0F;
        float z = -depth;
        mesh.corner(x0, y0, z, u, v, 0, 0, -1)
                .corner(x1, y1, z, u, v, 0, 0, -1)
                .corner(x2, y2, z, u, v, 0, 0, -1)
                .corner(x3, y3, z, u, v, 0, 0, -1);
    }

    /**
     * An arrow centred on ({@code cx}, {@code cy}) pointing {@code direction} (0 up, 1 left, 2 down, 3 right, on the screen),
     * {@code size} pixels long.
     */
    static void arrow(MeshShape.Builder mesh, float cx, float cy, int direction, float size, float depth, int swatch) {
        float h = size / 2.0F;
        // Up arrow in screen units (u to the right, v up): a shaft and a head.
        float[][] shaft = {{-0.22F, -1.0F}, {0.22F, -1.0F}, {0.22F, 0.1F}, {-0.22F, 0.1F}};
        float[][] head = {{-0.8F, 0.05F}, {0.8F, 0.05F}, {0.0F, 1.0F}, {0.0F, 1.0F}};
        for (float[][] part : new float[][][]{shaft, head}) {
            float[] p = new float[8];
            for (int i = 0; i < 4; i++) {
                float u = part[i][0];
                float v = part[i][1];
                float ru;
                float rv;
                switch (direction) {
                    case 1 -> { ru = -v; rv = u; }
                    case 2 -> { ru = -u; rv = -v; }
                    case 3 -> { ru = v; rv = -u; }
                    default -> { ru = u; rv = v; }
                }
                // Screen right is -X.
                p[i * 2] = cx - ru * h;
                p[i * 2 + 1] = cy + rv * h;
            }
            // A turn by a quarter or three quarters keeps the corners counter-clockwise; so does a half turn.
            quad(mesh, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], depth, swatch);
        }
    }
}
