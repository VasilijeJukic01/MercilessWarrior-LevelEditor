package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Exact decoration placement rules extracted from Merciless Warrior's Panel 2 pixel dumps.
 */
public class AutoDecoratorEngine {

    private static final int[] FLOOR_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final int[] HANGING_VINES = {18, 20, 21, 22, 23, 27, 28};
    private static final int[] CEILING_MOSS_ON_TILE = {31, 32};
    private static final int[] TALL_BG_OBJECTS = {0, 1, 2, 5, 25};

    private static final int DECO_BLACK = 34;
    private static final int DECO_LEFT_END = 35;
    private static final int DECO_RIGHT_END = 36;
    private static final int DECO_UPPER_END = 38;
    private static final int DECO_BOTTOM_END = 39;

    private final Random rand = new Random();

    public List<Node> generateDecorations(Level parent, boolean[][] solid, int w, int h, double density) {
        List<Node> decos = new ArrayList<>();

        // 1. Border fade caps at tunnel entrances/exits on Layer 2 (35:2, 36:2, 38:2, 39:2)
        for (int y = 0; y < h; y++) {
            if (!solid[0][y]) decos.add(createDeco(parent, 0, y, 2, DECO_LEFT_END));
            if (!solid[w - 1][y]) decos.add(createDeco(parent, w - 1, y, 2, DECO_RIGHT_END));
        }
        for (int x = 0; x < w; x++) {
            if (!solid[x][0]) decos.add(createDeco(parent, x, 0, 2, DECO_UPPER_END));
            if (!solid[x][h - 1]) decos.add(createDeco(parent, x, h - 1, 2, DECO_BOTTOM_END));
        }

        // 2. Floor Plants, Tall Background Rocks/Bushes, Ceiling Moss, and Hanging Vines
        int lastTallBgX = -5;
        int lastCeilingMossX = -5;

        for (int x = 2; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {

                // A. Floor surface: (x, y) is AIR, (x, y+1) is SOLID FLOOR
                if (!solid[x][y] && solid[x][y + 1]) {
                    if (rand.nextDouble() <= density) {
                        // Tall 2-tile-high background stones/bushes (0, 1, 2, 5, 25) go at (x, y - 1) on Layer 0!
                        if (x - lastTallBgX >= 5 && rand.nextDouble() < 0.22 && isFlatFloor(solid, x, y + 1, 4, w)) {
                            int bgId = TALL_BG_OBJECTS[rand.nextInt(TALL_BG_OBJECTS.length)];
                            decos.add(createDeco(parent, x, y - 1, 0, bgId));
                            lastTallBgX = x;
                        }
                        // Small 1-tile plants (6..16) go directly at (x, y) on Layer 0 or Layer 4
                        else if (rand.nextDouble() < 0.65 && isFlatFloor(solid, x, y + 1, 2, w)) {
                            int plantId = FLOOR_PLANTS[rand.nextInt(FLOOR_PLANTS.length)];
                            int layer = rand.nextBoolean() ? 0 : 4;
                            decos.add(createDeco(parent, x, y, layer, plantId));
                        }
                    }
                }

                // B. Ceiling surface: (x, y) is AIR, (x, y-1) is SOLID CEILING
                if (!solid[x][y] && solid[x][y - 1] && isFlatCeiling(solid, x, y - 1, 3, w)) {
                    // Wide ceiling moss (31, 32) is placed ON the ceiling tile (y - 1) on Layer 0 or 4!
                    if (x - lastCeilingMossX >= 6 && rand.nextDouble() < density * 0.35) {
                        int mossId = CEILING_MOSS_ON_TILE[rand.nextInt(CEILING_MOSS_ON_TILE.length)];
                        int layer = rand.nextBoolean() ? 0 : 4;
                        decos.add(createDeco(parent, x, y - 1, layer, mossId));
                        lastCeilingMossX = x;
                    }
                    // Hanging vines (18, 20..23, 27, 28) are placed on the air tile (y) on Layer 0!
                    else if (rand.nextDouble() < density * 0.25 && !solid[x][Math.min(h - 1, y + 2)]) {
                        int vineId = HANGING_VINES[rand.nextInt(HANGING_VINES.length)];
                        decos.add(createDeco(parent, x, y, 0, vineId));
                    }
                }
            }
        }

        return decos;
    }

    private Tile createDeco(Level parent, int x, int y, int layer, int decoIndex) {
        Tile t = new Tile("", parent, TileType.DECO, x, y, 254, layer, decoIndex);
        t.setLayer(layer);
        return t;
    }

    private boolean isFlatFloor(boolean[][] solid, int x, int floorY, int span, int width) {
        if (floorY >= solid[0].length) return false;
        for (int i = 0; i < span; i++) {
            if (x + i >= width || !solid[x + i][floorY] || solid[x + i][floorY - 1]) return false;
        }
        return true;
    }

    private boolean isFlatCeiling(boolean[][] solid, int x, int ceilY, int span, int width) {
        if (ceilY < 0 || ceilY + 1 >= solid[0].length) return false;
        for (int i = 0; i < span; i++) {
            if (x + i >= width || !solid[x + i][ceilY] || solid[x + i][ceilY + 1]) return false;
        }
        return true;
    }
}