package editor.generator;

import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.*;

/**
 * Populates panel 2 decoration layers (layers 0, 1, 2, 4) across a level based on surface geometry, vertical air clearance, and hazard avoidance.
 */
public class AutoDecoratorEngine {

    private static final int[] SMALL_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 47, 62, 63};
    private static final int[] SMALL_SHROOMS = {55, 56, 57};
    private static final int[] CRYSTALS = {64, 65};

    private static final int[] LOGS_AND_BIG_SHROOM = {44, 48, 49, 50, 58, 85};
    private static final int[] BUSHES_AND_ROCKS = {25, 42, 43, 54};
    private static final int[] BIG_BOULDERS = {0, 1, 2, 40, 41};
    private static final int[] TALL_FLOWERS = {45, 46};

    private static final int[] TALL_TREES = {59, 60, 84};
    private static final int[] MEDIUM_TREES_AND_PILLARS = {53, 61, 81};

    private static final int[] WIDE_CEILING_MOSS = {31, 32, 75, 76, 77};
    private static final int[] CEILING_HANGERS = {22, 23, 73, 74, 78, 80};
    private static final int[] SHORT_PILLAR_VINES = {17, 19, 20, 21};
    private static final int DECO_MOSS1_BIG = 28;
    private static final int DECO_VINES5_WALL = 29;
    private static final int[] UNDER_GRASS_STRIPS = {123, 125, 126, 127, 128, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149};

    private static final int DECO_LEFT_END = 35;
    private static final int DECO_RIGHT_END = 36;
    private static final int DECO_UPPER_END = 38;
    private static final int DECO_BOTTOM_END = 39;
    private static final int DECO_LEAF_LEFT = 24;
    private static final int DECO_LEAF_RIGHT = 37;

    private final Random rand = new Random();
    private int lastPlatformStripId = -1;

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
        boolean[][] occupiedDecoCell = new boolean[w][h];
        boolean[][] usedFloor = new boolean[w][h];
        boolean[][] hazard = extractHazardGrid(existingNodes, w, h);

        if (existingNodes != null) {
            for (Node n : existingNodes) {
                if (n instanceof Tile t && t.getTileType() == TileType.DECO) {
                    if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                        occupiedDecoCell[t.getX()][t.getY()] = true;
                    }
                }
            }
        }

        // Border fade caps
        for (int y = 0; y < h; y++) {
            if ((!solid[0][y] || (y > 0 && !solid[0][y - 1]) || (y + 1 < h && !solid[0][y + 1])) && !occupiedDecoCell[0][y]) {
                decos.add(createDeco(parent, 0, y, 2, DECO_LEFT_END));
                occupiedDecoCell[0][y] = true;
            }
            if ((!solid[w - 1][y] || (y > 0 && !solid[w - 1][y - 1]) || (y + 1 < h && !solid[w - 1][y + 1])) && !occupiedDecoCell[w - 1][y]) {
                decos.add(createDeco(parent, w - 1, y, 2, DECO_RIGHT_END));
                occupiedDecoCell[w - 1][y] = true;
            }
        }
        for (int x = 0; x < w; x++) {
            if ((!solid[x][0] || (x > 0 && !solid[x - 1][0]) || (x + 1 < w && !solid[x + 1][0])) && !occupiedDecoCell[x][0]) {
                decos.add(createDeco(parent, x, 0, 2, DECO_UPPER_END));
                occupiedDecoCell[x][0] = true;
            }
            if ((!solid[x][h - 1] || (x > 0 && !solid[x - 1][h - 1]) || (x + 1 < w && !solid[x + 1][h - 1])) && !occupiedDecoCell[x][h - 1]) {
                decos.add(createDeco(parent, x, h - 1, 2, DECO_BOTTOM_END));
                occupiedDecoCell[x][h - 1] = true;
            }
        }

        placeUnderGrassFloorRuns(parent, solid, occupiedDecoCell, hazard, decos, w, h, density);

        // Wall leaves and vertical rotated moss strips
        for (int x = 2; x < w - 2; x++) {
            for (int y = 3; y < h - 4; y++) {
                if (!solid[x][y]) continue;
                if (!solid[x][y - 1] && solid[x][y + 1]) {
                    if (!solid[x - 1][y] && solid[x + 1][y] && !occupiedDecoCell[x - 1][y] && rand.nextDouble() < 0.5) {
                        decos.add(createDeco(parent, x - 1, y, 0, DECO_LEAF_LEFT));
                        occupiedDecoCell[x - 1][y] = true;
                    }
                    else if (solid[x - 1][y] && !solid[x + 1][y] && !occupiedDecoCell[x + 1][y] && rand.nextDouble() < 0.5) {
                        decos.add(createDeco(parent, x + 1, y, 0, DECO_LEAF_RIGHT));
                        occupiedDecoCell[x + 1][y] = true;
                    }
                }
                else if (solid[x][y - 1] && solid[x][y + 1]) {
                    if (!solid[x - 1][y] && solid[x + 1][y] && !occupiedDecoCell[x - 1][y] && rand.nextDouble() < density * 0.22) {
                        decos.add(createDeco(parent, x - 1, y, 0, DECO_LEAF_LEFT));
                        occupiedDecoCell[x - 1][y] = true;
                    }
                    else if (solid[x - 1][y] && !solid[x + 1][y] && rand.nextDouble() < density * 0.22) {
                        int id = (rand.nextInt(5) == 0) ? DECO_VINES5_WALL : DECO_LEAF_RIGHT;
                        int targetX = (id == DECO_VINES5_WALL) ? x : x + 1;
                        int layer = (id == DECO_VINES5_WALL) ? 4 : 0;
                        if (!occupiedDecoCell[targetX][y]) {
                            decos.add(createDeco(parent, targetX, y, layer, id));
                            occupiedDecoCell[targetX][y] = true;
                        }
                    }
                }
            }
        }

        // Multi-tile background structures
        int nextFeatureX = 4;
        for (int x = 4; x < w - 7; x++) {
            if (x < nextFeatureX) continue;
            for (int y = 5; y < h - 2; y++) {
                if (!solid[x][y] && solid[x][y + 1] && y + 2 < h && solid[x][y + 2]) {
                    if (rand.nextDouble() > density * 0.55) continue;

                    double roll = rand.nextDouble();
                    if (roll < 0.16 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 6, w) && !occupiedDecoCell[x][y - 4]) {
                        int treeId = TALL_TREES[rand.nextInt(TALL_TREES.length)];
                        decos.add(createDeco(parent, x, y - 4, 0, treeId));
                        occupiedDecoCell[x][y - 4] = true;
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 5;
                        break;
                    }
                    else if (roll < 0.30 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 5, w) && !occupiedDecoCell[x][y - 3]) {
                        int id = MEDIUM_TREES_AND_PILLARS[rand.nextInt(MEDIUM_TREES_AND_PILLARS.length)];
                        int layer = (id == 81 || id == 53) ? 2 : 0;
                        decos.add(createDeco(parent, x, y - 3, layer, id));
                        occupiedDecoCell[x][y - 3] = true;
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (roll < 0.42 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 4, 4, w) && !occupiedDecoCell[x][y - 2]) {
                        int id = TALL_FLOWERS[rand.nextInt(TALL_FLOWERS.length)];
                        decos.add(createDeco(parent, x, y - 2, 0, id));
                        occupiedDecoCell[x][y - 2] = true;
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (roll < 0.62 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 5, 3, w) && !occupiedDecoCell[x][y - 1]) {
                        int id = BIG_BOULDERS[rand.nextInt(BIG_BOULDERS.length)];
                        decos.add(createDeco(parent, x, y - 1, 0, id));
                        occupiedDecoCell[x][y - 1] = true;
                        markFloorUsed(usedFloor, x, y, 4);
                        nextFeatureX = x + 5;
                        break;
                    }
                    else if (roll < 0.82 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 4, 3, w) && !occupiedDecoCell[x][y - 1]) {
                        int id = BUSHES_AND_ROCKS[rand.nextInt(BUSHES_AND_ROCKS.length)];
                        int layer = (id != 54 && rand.nextInt(3) == 0) ? 2 : 0;
                        decos.add(createDeco(parent, x, y - 1, layer, id));
                        occupiedDecoCell[x][y - 1] = true;
                        markFloorUsed(usedFloor, x, y, 3);
                        nextFeatureX = x + 4;
                        break;
                    }
                    else if (isFlatFloorWithClearance(solid, hazard, x, y + 1, 3, 3, w) && !occupiedDecoCell[x][y - 1]) {
                        int id = LOGS_AND_BIG_SHROOM[rand.nextInt(LOGS_AND_BIG_SHROOM.length)];
                        int layer = (id == 85) ? 2 : 0;
                        int anchorY = (id == 85) ? y : (y - 1);
                        if (!occupiedDecoCell[x][anchorY]) {
                            decos.add(createDeco(parent, x, anchorY, layer, id));
                            occupiedDecoCell[x][anchorY] = true;
                            markFloorUsed(usedFloor, x, y, 3);
                            nextFeatureX = x + 4;
                            break;
                        }
                    }
                }
            }
        }

        // Small 1x1 plants, mushrooms, and crystals on unoccupied air-above-floor cells
        for (int x = 3; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!solid[x][y] && solid[x][y + 1] && !hazard[x][y] && !occupiedDecoCell[x][y]) {
                    if (rand.nextDouble() <= density * 0.65 && isFlatFloorWithClearance(solid, hazard, x, y + 1, 1, 2, w)) {
                        int id;
                        double r = rand.nextDouble();
                        if (y > h / 2 && r < 0.18) id = SMALL_SHROOMS[rand.nextInt(SMALL_SHROOMS.length)];
                        else if (y > h * 0.65 && r < 0.28) id = CRYSTALS[rand.nextInt(CRYSTALS.length)];
                        else id = SMALL_PLANTS[rand.nextInt(SMALL_PLANTS.length)];
                        int layer = usedFloor[x][y] ? 4 : (rand.nextBoolean() ? 0 : 4);
                        decos.add(createDeco(parent, x, y, layer, id));
                        occupiedDecoCell[x][y] = true;
                    }
                }
            }
        }

        // Ceiling moss, ferns, hanging pods/shrooms/cocoons anchored at ceilY (y - 1)
        int nextAllowedMossX = 3;
        int nextAllowedVineX = 3;
        for (int x = 3; x < w - 4; x++) {
            for (int y = 2; y < h - 4; y++) {
                if (!solid[x][y] && solid[x][y - 1] && solid[x][y - 2]) {
                    int airHeight = measureAirColumnHeight(solid, x, y, h);

                    if (x >= nextAllowedMossX && airHeight >= 4 && !occupiedDecoCell[x][y - 1] && rand.nextDouble() < density * 0.48) {
                        if (isThickFlatCeiling(solid, x, y - 1, 6, w) && hasAirBelow(solid, x, y, 6, 3, w, h)) {
                            int id = WIDE_CEILING_MOSS[rand.nextInt(WIDE_CEILING_MOSS.length)];
                            int layer = (id >= 75) ? 2 : (rand.nextBoolean() ? 0 : 1);
                            decos.add(createDeco(parent, x, y - 1, layer, id));
                            occupiedDecoCell[x][y - 1] = true;
                            nextAllowedMossX = x + 6;
                        }
                    }

                    if (x >= nextAllowedVineX) {
                        if ((airHeight == 2 || airHeight == 3) && isThickFlatCeiling(solid, x, y - 1, 2, w) && solid[x][y + airHeight] && !occupiedDecoCell[x][y] && rand.nextDouble() < density * 0.40) {
                            int id = SHORT_PILLAR_VINES[rand.nextInt(SHORT_PILLAR_VINES.length)];
                            decos.add(createDeco(parent, x, y, 0, id));
                            occupiedDecoCell[x][y] = true;
                            nextAllowedVineX = x + 5;
                        }
                        else if (airHeight >= 7 && isThickFlatCeiling(solid, x, y - 1, 3, w) && hasAirBelow(solid, x, y, 3, 7, w, h) && !occupiedDecoCell[x][y] && rand.nextDouble() < density * 0.25) {
                            decos.add(createDeco(parent, x, y, 0, DECO_MOSS1_BIG));
                            occupiedDecoCell[x][y] = true;
                            nextAllowedVineX = x + 14;
                        }
                        else if (airHeight >= 5 && isThickFlatCeiling(solid, x, y - 1, 3, w) && !occupiedDecoCell[x][y - 1] && rand.nextDouble() < density * 0.35) {
                            int id = CEILING_HANGERS[rand.nextInt(CEILING_HANGERS.length)];
                            int layer = (id >= 73) ? 2 : 0;
                            decos.add(createDeco(parent, x, y - 1, layer, id));
                            occupiedDecoCell[x][y - 1] = true;
                            nextAllowedVineX = x + 5;
                        }
                    }
                }
            }
        }

        return decos;
    }

    /**
     * - Finds contiguous flat floor runs of length >= 4 tiles with at least 2 tiles of solid earth thickness.
     * - Picks strip style for that platform ledge (different from the previous ledge).
     * - Places strips at AIR_ABOVE_FLOOR (y = floorY - 1) on Layer 4 in steps of 3 tiles (so 4-tile strips overlap by 1 tile).
     * - Fits the final strip on the right edge of the ledge using scaleX / offsetX so it never sticks out past the corner!
     */
    private void placeUnderGrassFloorRuns(Level parent, boolean[][] solid, boolean[][] occupiedDecoCell, boolean[][] hazard, List<Node> decos, int w, int h, double density) {
        if (density <= 0.05) return;

        for (int floorY = 4; floorY < h - 2; floorY++) {
            int x = 2;
            while (x < w - 4) {
                if (!isValidUnderGrassFloorTile(solid, hazard, x, floorY, w, h)) {
                    x++;
                    continue;
                }
                int runStart = x;
                while (x < w - 2 && isValidUnderGrassFloorTile(solid, hazard, x, floorY, w, h)) {
                    x++;
                }
                int runEnd = x - 1;
                int runLen = runEnd - runStart + 1;
                if (runLen < 3 || rand.nextDouble() > Math.min(0.92, density + 0.25)) {
                    continue;
                }

                int stripId = UNDER_GRASS_STRIPS[rand.nextInt(UNDER_GRASS_STRIPS.length)];
                if (stripId == lastPlatformStripId) {
                    stripId = UNDER_GRASS_STRIPS[(stripId - 122 + 1 + rand.nextInt(UNDER_GRASS_STRIPS.length - 1)) % UNDER_GRASS_STRIPS.length];
                }
                lastPlatformStripId = stripId;

                int airY = floorY - 1;
                int lastStepStart = Math.max(runStart, runEnd - 2);

                for (int cx = runStart; cx <= lastStepStart; cx += 3) {
                    if (occupiedDecoCell[cx][airY]) continue;
                    int remainingTiles = (runEnd + 1) - cx;
                    Tile strip = createDeco(parent, cx, airY, 4, stripId);
                    if (remainingTiles < 4) {
                        double fitScale = Math.max(0.76, (remainingTiles / 4.0) + 0.04);
                        int shiftLeft = -((int) Math.round((1.0 - fitScale) * 128));
                        strip.setScaleX(Math.round(fitScale * 100.0) / 100.0);
                        strip.setOffsetX(shiftLeft);
                    }
                    else if (cx == runStart) {
                        strip.setScaleX(1.04);
                        strip.setOffsetX(-4);
                    }

                    decos.add(strip);
                    occupiedDecoCell[cx][airY] = true;
                }
            }
        }
    }

    private boolean isValidUnderGrassFloorTile(boolean[][] solid, boolean[][] hazard, int x, int floorY, int w, int h) {
        if (x < 1 || x >= w - 1 || floorY < 2 || floorY + 1 >= h) return false;
        if (solid[x][floorY - 1] || solid[x][floorY - 2] || hazard[x][floorY - 1]) return false;
        return solid[x][floorY] && solid[x][floorY + 1];
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