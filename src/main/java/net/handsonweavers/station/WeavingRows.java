package net.handsonweavers.station;

import java.util.Random;

/**
 * The rows of arrows a weaving asks for, the same on the server and every client: row {@code n} of a weaving follows from its seed
 * alone, so the server checks what the weaver typed without ever sending the rows.
 */
public final class WeavingRows {
    /** Arrows: up, left, down, right, as Weavers Paradise numbers them. */
    public static final int UP = 0;
    public static final int LEFT = 1;
    public static final int DOWN = 2;
    public static final int RIGHT = 3;
    public static final int LENGTH = 5;

    private WeavingRows() {
    }

    public static int[] row(long seed, int index) {
        Random random = new Random(seed * 0x9E3779B97F4A7C15L + index * 31L + 7L);
        int[] row = new int[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            row[i] = random.nextInt(4);
        }
        return row;
    }
}
