package editor.generator;

import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Populates panel 2 decoration layers (layers 0, 1, 2, 4) across a level based on surface geometry, vertical air clearance, and hazard avoidance.
 */
public class AutoDecoratorEngine {

    private static final int[] SMALL_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 47, 51, 62, 63};
    private static final int[] SMALL_SHROOMS = {55, 56, 57};
    private static final int[] CRYSTALS = {64, 65};

    private static final int[] LOGS_AND_BIG_SHROOM = {44, 48, 49, 50, 58};
    private static final int[] BUSHES_AND_ROCKS = {25, 42, 43, 54};
    private static final int[] BIG_BOULDERS = {0, 1, 2, 4, 40, 41};
    private static final int[] TALL_FLOWERS = {5, 45, 46};

    private static final int[] TREES = {59, 60};
    private static final int DECO_TREE3 = 61;
    private static final int DECO_PILLAR1 = 53;

    private static final int[] OPEN_AIR_HANGING_MOSS = {22, 23};
    private static final int[] SHORT_PILLAR_VINES = {17, 19, 20, 21};
    private static final int DECO_MOSS1_BIG = 28;
    private static final int DECO_VINES5_WALL = 29;

    private static final int DECO_LEFT_END = 35;
    private static final int DECO_RIGHT_END = 36;
    private static final int DECO_UPPER_END = 38;
    private static final int DECO_BOTTOM_END = 39;
    private static final int DECO_LEAF_LEFT = 24;
    private static final int DECO_LEAF_RIGHT = 37;

    private final Random rand = new Random();

    /**
     * Generates decoration tiles for the given solid terrain grid without hazard filtering.
     */
    public List<Node> generateDecorations(Level parent, boolean[][] solid, int w, int h, double density) {
        return generateDecorations(parent, solid, new ArrayList<>(), w, h, density);
    }

    /**
     * Generates multi-layer decoration tiles for the level while avoiding placed hazards.
     *
     * @param parent        The target level.
     * @param solid         The 2D solid terrain occupancy grid.
     * @param existingNodes Existing nodes in the level used to detect spike/lava hazards.
     * @param w             Grid width in tiles.
     * @param h             Grid height in tiles.
     * @param density       Decoration density factor in the range {@code [0.0, 1.0]}.
     * @return A list of generated decoration nodes.
     */
    public List<Node> generateDecorations(Level parent, boolean[][] solid, List<Node> existingNodes, int w, int h, double density) {
        List<Node> decos = new ArrayList<>();
        boolean[][] usedFloor = new boolean[w][h];
        boolean[][] hazard = extractHazardGrid(existingNodes, w, h);

        // Extend border fade caps 1 tile into adj solid ceiling/floor tiles on layer 2 so seams tuck behind layer 3 terrain
        for (int y = 0; y < h; y++) {
            if (!solid[0][y] || (y > 0 && !solid[0][y - 1]) || (y + 1 < h && !solid[0][y + 1])) {
                decos.add(createDeco(parent, 0, y, 2, DECO_LEFT_END));
            }
            if (!solid[w - 1][y] || (y > 0 && !solid[w - 1][y - 1]) || (y + 1 < h && !solid[w - 1][y + 1])) {
                decos.add(createDeco(parent, w - 1, y, 2, DECO_RIGHT_END));
            }
        }
        for (int x = 0; x < w; x++) {
            if (!solid[x][0] || (x > 0 && !solid[x - 1][0]) || (x + 1 < w && !solid[x + 1][0])) {
                decos.add(createDeco(parent, x, 0, 2, DECO_UPPER_END));
            }
            if (!solid[x][h - 1] || (x > 0 && !solid[x - 1][h - 1]) || (x + 1 < w && !solid[x + 1][h - 1])) {
                decos.add(createDeco(parent, x, h - 1, 2, DECO_BOTTOM_END));
            }
        }

        for (int x = 2; x < w - 2; x++) {
            for (int y = 3; y < h - 4; y++) {
                if (!solid[x][y]) continue;

                if (!solid[x][y - 1] && solid[x][y + 1]) {
                    if (!solid[x - 1][y] && solid[x + 1][y] && rand.nextDouble() < 0.5) {
                        decos.add(createDeco(parent, x - 1, y, 0, DECO_LEAF_LEFT));
                    }
                    else if (solid[x - 1][y] && !solid[x + 1][y] && rand.nextDouble() < 0.5) {
                        decos.add(createDeco(parent, x + 1, y, 0, DECO_LEAF_RIGHT));
                    }
                }
                else if (solid[x][y - 1] && solid[x][y + 1]) {
                    if (!solid[x - 1][y] && solid[x + 1][y] && rand.nextDouble() < density * 0.22) {
                        decos.add(createDeco(parent, x - 1, y, 0, DECO_LEAF_LEFT));
                    }
                    else if (solid[x - 1][y] && !solid[x + 1][y] && rand.nextDouble() < density * 0.22) {
                        int id = (rand.nextInt(5) == 0) ? DECO_VINES5_WALL : DECO_LEAF_RIGHT;
                        int layer = (id == DECO_VINES5_WALL) ? 4 : 0;
                        decos.add(createDeco(parent, (id == DECO_VINES5_WALL) ? x : x + 1, y, layer, id));
                    }
                }
            }
        }

        // Multi-tile background structures require a vertical Y-offset matching their sprite height in tiles
        int nextFeatureX = 4;
        for (int x = 4; x < w - 7; x++) {
            if (x < nextFeatureX) continue;
            for (int y = 5; y < h - 2; y++) {
                if (!solid[x][y] && solid[x][y + 1] && y + 2 < h && solid[x][y + 2]) {
                    if (rand.nextDouble() > density * 0.55) continue;

                    double roll = rand.nextDouble();
                    if (roll < 0.14 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 6, w)) {
                        int treeId = TREES[rand.nextInt(TREES.length)];
                        decos.add(createDeco(parent, x, y - 4, 0, treeId));
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 5;
                        break;
                    }
                    else if (roll < 0.26 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 5, w)) {
                        int id = rand.nextBoolean() ? DECO_TREE3 : DECO_PILLAR1;
                        decos.add(createDeco(parent, x, y - 3, 0, id));
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (roll < 0.40 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 4, 4, w)) {
                        int id = TALL_FLOWERS[rand.nextInt(TALL_FLOWERS.length)];
                        decos.add(createDeco(parent, x, y - 2, 0, id));
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (roll < 0.62 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 5, 3, w)) {
                        int id = BIG_BOULDERS[rand.nextInt(BIG_BOULDERS.length)];
                        decos.add(createDeco(parent, x, y - 1, 0, id));
                        markFloorUsed(usedFloor, x, y, 4);
                        nextFeatureX = x + 5;
                        break;
                    }
                    else if (roll < 0.82 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 4, 3, w)) {
                        int id = BUSHES_AND_ROCKS[rand.nextInt(BUSHES_AND_ROCKS.length)];
                        int layer = (id != 54 && rand.nextInt(3) == 0) ? 2 : 0;
                        decos.add(createDeco(parent, x, y - 1, layer, id));
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 3, w)) {
                        int id = LOGS_AND_BIG_SHROOM[rand.nextInt(LOGS_AND_BIG_SHROOM.length)];
                        decos.add(createDeco(parent, x, y - 1, 0, id));
                        markFloorUsed(usedFloor, x, y, 2);
                        nextFeatureX = x + 3;
                        break;
                    }
                }
            }
        }

        for (int x = 3; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!solid[x][y] && solid[x][y + 1] && !hazard[x][y]) {
                    if (rand.nextDouble() <= density * 0.65 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 1, 2, w)) {
                        int id;
                        double r = rand.nextDouble();
                        if (y > h / 2 && r < 0.18) id = SMALL_SHROOMS[rand.nextInt(SMALL_SHROOMS.length)];
                        else if (y > h * 0.65 && r < 0.28) id = CRYSTALS[rand.nextInt(CRYSTALS.length)];
                        else id = SMALL_PLANTS[rand.nextInt(SMALL_PLANTS.length)];
                        int layer = usedFloor[x][y] ? 4 : (rand.nextBoolean() ? 0 : 4);
                        decos.add(createDeco(parent, x, y, layer, id));
                    }
                }
            }
        }

        int nextAllowedMossX = 3;
        int nextAllowedVineX = 3;
        for (int x = 3; x < w - 4; x++) {
            for (int y = 2; y < h - 4; y++) {
                // Require at least a 2-tile-thick ceiling (solid[x][y-1] && solid[x][y-2]) so 1-tile bridges never get ceiling moss
                if (!solid[x][y] && solid[x][y - 1] && solid[x][y - 2]) {
                    if (x >= nextAllowedMossX && rand.nextDouble() < density * 0.45) {
                        int layer = rand.nextBoolean() ? 0 : 1;
                        if (isThickFlatCeiling(solid, x, y - 1, 9, w) && hasAirBelow(solid, x, y, 9, 2, w, h)) {
                            decos.add(createDeco(parent, x, y - 1, layer, 32));
                            nextAllowedMossX = x + 9;
                        }
                        else if (isThickFlatCeiling(solid, x, y - 1, 6, w) && hasAirBelow(solid, x, y, 6, 2, w, h)) {
                            decos.add(createDeco(parent, x, y - 1, layer, 31));
                            nextAllowedMossX = x + 6;
                        }
                    }

                    int airHeight = measureAirColumnHeight(solid, x, y, h);
                    if (x >= nextAllowedVineX) {
                        // Flat-bottomed vine pillars (17, 19, 20, 21, 28) are only placed when a solid floor hides their base
                        if ((airHeight == 2 || airHeight == 3) && isThickFlatCeiling(solid, x, y - 1, 2, w) && solid[x][y + airHeight] && rand.nextDouble() < density * 0.45) {
                            int id = SHORT_PILLAR_VINES[rand.nextInt(SHORT_PILLAR_VINES.length)];
                            decos.add(createDeco(parent, x, y, 0, id));
                            nextAllowedVineX = x + 5;
                        }
                        else if ((airHeight == 7 || airHeight == 8) && isThickFlatCeiling(solid, x, y - 1, 3, w) && isFlatFloorWithClearance(solid, hazard, x, y + airHeight, 3, 2, w) && rand.nextDouble() < density * 0.35) {
                            decos.add(createDeco(parent, x, y, 0, DECO_MOSS1_BIG));
                            nextAllowedVineX = x + 7;
                        }
                        else if (airHeight >= 4 && isThickFlatCeiling(solid, x, y - 1, 2, w) && rand.nextDouble() < density * 0.30) {
                            int id = OPEN_AIR_HANGING_MOSS[rand.nextInt(OPEN_AIR_HANGING_MOSS.length)];
                            decos.add(createDeco(parent, x, y, 0, id));
                            nextAllowedVineX = x + 4;
                        }
                    }
                }
            }
        }
        return decos;
    }

    private void markFloorUsed(boolean[][] usedFloor, int x, int y, int span) {
        for (int k = 0; k < span && x + k < usedFloor.length; k++) {
            usedFloor[x + k][y] = true;
        }
    }

    private boolean[][] extractHazardGrid(List<Node> nodes, int w, int h) {
        boolean[][] hazard = new boolean[w][h];
        if (nodes == null) return hazard;
        for (Node n : nodes) {
            if (n instanceof Tile t && t.getTileType() == TileType.OBJECT) {
                int id = t.getBlue();
                if (id == LvlObjType.SPIKES_UP.ordinal() || id == LvlObjType.LAVA.ordinal() || id == LvlObjType.SMASH_TRAP.ordinal()) {
                    if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                        hazard[t.getX()][t.getY()] = true;
                    }
                }
            }
        }
        return hazard;
    }

    private int measureAirColumnHeight(boolean[][] solid, int x, int startY, int h) {
        int count = 0;
        for (int y = startY; y < h; y++) {
            if (solid[x][y]) return count;
            count++;
        }
        return -1;
    }

    private Tile createDeco(Level parent, int x, int y, int layer, int decoIndex) {
        Tile t = new Tile("", parent, TileType.DECO, x, y, 254, layer, decoIndex);
        t.setLayer(layer);
        return t;
    }

    private boolean isFlatFloorWithClearance(boolean[][] solid, boolean[][] hazard, int x, int floorY, int spanX, int clearanceY, int w) {
        if (floorY >= solid[0].length || x + spanX > w) return false;
        for (int i = 0; i < spanX; i++) {
            if (!solid[x + i][floorY] || hazard[x + i][floorY - 1]) return false;
            for (int dy = 1; dy <= clearanceY; dy++) {
                if (floorY - dy < 0 || solid[x + i][floorY - dy]) return false;
            }
        }
        return true;
    }

    private boolean isThickFlatCeiling(boolean[][] solid, int x, int ceilY, int spanX, int w) {
        if (ceilY < 1 || ceilY + 1 >= solid[0].length || x + spanX > w) return false;
        for (int i = 0; i < spanX; i++) {
            if (!solid[x + i][ceilY] || !solid[x + i][ceilY - 1] || solid[x + i][ceilY + 1]) return false;
        }
        return true;
    }

    private boolean hasAirBelow(boolean[][] solid, int x, int startY, int spanX, int depthY, int w, int h) {
        if (x + spanX > w || startY + depthY > h) return false;
        for (int i = 0; i < spanX; i++) {
            for (int dy = 0; dy < depthY; dy++) {
                if (solid[x + i][startY + dy]) return false;
            }
        }
        return true;
    }
}