package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.*;

/**
 * Generates procedural Layer 5 subterranean cutaways (L-hooks, U-alcoves, Z-steps, irregular caverns, stepped hills, and 3x3 rings) inside deep interior earth and populates them with Layer 4 decorations.
 */
public class SubterraneanPocketGenerator {

    private static final int[] SMALL_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 62, 63};
    private static final int[] SMALL_SHROOMS = {55, 56, 57};
    private static final int[] CRYSTALS = {64, 65};

    private static final int[] CLASSIC_FLOOR_PROPS = {0, 1, 2, 25, 40, 41, 44, 45, 46, 48, 49, 54, 58};
    private static final int[] CRADLE_NOOK_SETPIECES = {89, 91, 94, 95, 110, 116, 118, 119, 120};
    private static final int[] DEEP_VOID_SETPIECES = {92, 93, 106, 116, 121};
    private static final int[] CAVERN_CEILING_MOSS = {21, 22, 23, 73, 76};
    private static final int[] VERTICAL_ROOT_COLUMNS = {96, 104, 114};

    private final AutoTileRuleEngine tileEngine;
    private final Random rand = new Random();
    private final Deque<Integer> recentSetpieces = new ArrayDeque<>();

    public SubterraneanPocketGenerator(AutoTileRuleEngine tileEngine) {
        this.tileEngine = tileEngine;
    }

    /**
     * Populates deep interior black earth (Layer 3 tile 8) with varied, non-repetitive Layer 5 motifs and decorates their surfaces with Layer 4 foliage, boulders, logs, mushrooms, crystals, and hanging moss.
     */
    public void generateWallPockets(Level parent, boolean[][] solid, int w, int h, List<Node> outNodes) {
        boolean[][] deepEarth = new boolean[w][h];
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                deepEarth[x][y] = isDeepEarth(solid, x, y, w, h);
            }
        }

        boolean[][] used = new boolean[w][h];
        int[][] placedL5 = new int[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(placedL5[x], -1);
        recentSetpieces.clear();

        carveEnclosedSubterraneanCaverns(parent, deepEarth, used, placedL5, w, h, outNodes);
        carveLongSteppedFloorContours(deepEarth, used, placedL5, w, h);
        carveMidLevelCradlesAndShelves(parent, deepEarth, used, placedL5, w, h, outNodes);
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (placedL5[x][y] != -1) {
                    replaceSolidWithLayer5(parent, outNodes, x, y, placedL5[x][y]);
                    addDeco(parent, outNodes, x, y, 2, 34);
                }
            }
        }
        decorateSubterraneanEcosystem(parent, deepEarth, used, placedL5, w, h, outNodes);
    }

    /**
     * Builds a connected subterranean staircase/hill terrace inside the specified rectangular region.
     */
    private void carveEnclosedSubterraneanCaverns(Level parent, boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int w, int h, List<Node> outNodes) {
        for (int y = 3; y < h - 6; y += 3) {
            for (int x = 3; x < w - 9; x += 4) {
                int cw = 6 + rand.nextInt(4);
                int ch = 4 + rand.nextInt(3);
                if (!isFreeDeepEarthBox(deepEarth, used, x, y, cw, ch, w, h)) continue;
                if (rand.nextDouble() > 0.65) continue;

                boolean[][] localSolid = new boolean[cw + 2][ch + 2];
                for (int lx = 0; lx < cw + 2; lx++) Arrays.fill(localSolid[lx], true);

                int splitX = 2 + cw / 2;
                int topY1 = 2;
                int topY2 = (ch >= 5 && rand.nextBoolean()) ? 3 : 2;
                int botY1 = ch - 1;
                int botY2 = (ch >= 5 && rand.nextBoolean()) ? (ch - 2) : (ch - 1);

                for (int lx = 2; lx < cw; lx++) {
                    int yStart = (lx < splitX) ? topY1 : topY2;
                    int yEnd = (lx < splitX) ? botY1 : botY2;
                    for (int ly = yStart; ly <= yEnd; ly++) {
                        localSolid[lx][ly] = false;
                    }
                }

                for (int lx = 1; lx <= cw; lx++) {
                    for (int ly = 1; ly <= ch; ly++) {
                        if (localSolid[lx][ly]) {
                            int id = tileEngine.pickExactTileId(localSolid, lx, ly, cw + 2, ch + 2);
                            if (id != 8) {
                                placedL5[x + lx - 1][y + ly - 1] = id;
                            }
                        }
                    }
                }
                markBoxUsed(used, x - 2, y - 1, cw + 4, ch + 2, w, h);
            }
        }
    }

    /**
     * Scans the lower and middle bands of deep earth to build long, continuous stepped subterranean floor contours (width 6..16) so the bottom of the level is never a blank black void.
     */
    private void carveLongSteppedFloorContours(boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int w, int h) {
        for (int baseY = h - 2; baseY >= 5; baseY -= 4) {
            int x = 2;
            while (x < w - 5) {
                if (!deepEarth[x][baseY] || used[x][baseY] || !deepEarth[x][baseY - 1]) {
                    x++;
                    continue;
                }

                int runStart = x;
                int maxRun = 7 + rand.nextInt(9);
                while (x < w - 2 && (x - runStart) < maxRun && deepEarth[x][baseY] && !used[x][baseY] && deepEarth[x][baseY - 1] && !used[x][baseY - 1]) {
                    x++;
                }
                int runEnd = x - 1;
                int runLen = runEnd - runStart + 1;
                if (runLen < 4) {
                    x++;
                    continue;
                }

                stampMultiStepContour(deepEarth, used, placedL5, runStart, runEnd, baseY, w, h);
                x = runEnd + 3;
            }
        }
    }

    private void stampMultiStepContour(boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int x1, int x2, int baseY, int w, int h) {
        int span = x2 - x1 + 1;
        int curY = baseY;

        placedL5[x1][curY] = 0;
        for (int wy = curY + 1; wy < h - 1 && deepEarth[x1][wy]; wy++) {
            placedL5[x1][wy] = 7;
        }

        int stepPoint1 = (span >= 7) ? (x1 + span / 3 + rand.nextInt(2)) : -1;
        int stepPoint2 = (span >= 11) ? (x1 + (span * 2) / 3 + rand.nextInt(2)) : -1;
        for (int x = x1 + 1; x < x2; x++) {
            if ((x == stepPoint1 || x == stepPoint2) && curY - 1 >= 3 && deepEarth[x][curY - 1] && !used[x][curY - 1]) {
                placedL5[x][curY] = 12;
                curY = curY - 1;
                placedL5[x][curY] = 0;
            }
            else if ((x == stepPoint1 || x == stepPoint2) && curY + 1 < h - 1 && deepEarth[x][curY + 1] && !used[x][curY + 1]) {
                placedL5[x][curY] = 2;
                curY = curY + 1;
                placedL5[x][curY] = 11;
            }
            else placedL5[x][curY] = 1;
        }

        placedL5[x2][curY] = 2;
        for (int wy = curY + 1; wy < h - 1 && deepEarth[x2][wy]; wy++) {
            placedL5[x2][wy] = 9;
        }

        markBoxUsed(used, x1 - 1, Math.max(1, curY - 2), span + 2, 5, w, h);
    }

    /**
     * Fills remaining mid-level black earth pockets with U-cradles, L-hooks, and compact shelves.
     */
    private void carveMidLevelCradlesAndShelves(Level parent, boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int w, int h, List<Node> outNodes) {
        for (int y = 2; y < h - 3; y += 2) {
            for (int x = 2; x < w - 4; x += 2) {
                if (used[x][y] || !deepEarth[x][y]) continue;

                int uw = 4 + rand.nextInt(2);
                int uh = 3;
                if (isFreeDeepEarthBox(deepEarth, used, x, y, uw, uh, w, h)) {
                    int floorY = y + uh - 1;
                    int rightX = x + uw - 1;
                    placedL5[x][y] = 0;
                    placedL5[rightX][y] = 2;
                    for (int wy = y + 1; wy < floorY; wy++) {
                        placedL5[x][wy] = 9;
                        placedL5[rightX][wy] = 7;
                    }
                    placedL5[x][floorY] = 11;
                    placedL5[rightX][floorY] = 12;
                    for (int fx = x + 1; fx < rightX; fx++) placedL5[fx][floorY] = 1;

                    if (uw >= 4 && rand.nextDouble() < 0.65) {
                        int setpiece = pickNonRepeatingSetpiece(CRADLE_NOOK_SETPIECES);
                        Tile d = new Tile("", parent, TileType.DECO, x + 1, floorY - 2, 254, 4, setpiece);
                        d.setLayer(4);
                        outNodes.add(d);
                    }
                    markBoxUsed(used, x - 1, y - 1, uw + 2, uh + 2, w, h);
                    continue;
                }

                int lw = 3 + rand.nextInt(2);
                if (isFreeDeepEarthBox(deepEarth, used, x, y, lw, 2, w, h)) {
                    boolean wallLeft = rand.nextBoolean();
                    int floorY = y + 1;
                    if (wallLeft) {
                        placedL5[x][y] = 3;
                        placedL5[x][floorY] = 11;
                        for (int fx = x + 1; fx < x + lw - 1; fx++) placedL5[fx][floorY] = 1;
                        placedL5[x + lw - 1][floorY] = 2;
                    }
                    else {
                        int wx = x + lw - 1;
                        placedL5[wx][y] = 3;
                        placedL5[wx][floorY] = 12;
                        for (int fx = x + 1; fx < wx; fx++) placedL5[fx][floorY] = 1;
                        placedL5[x][floorY] = 0;
                    }
                    markBoxUsed(used, x - 1, y - 1, lw + 2, 4, w, h);
                }
            }
        }
    }

    /**
     * Populates all surfaces, cavern ceilings, vertical drops, and remaining black earth pockets with set-pieces.
     */
    private void decorateSubterraneanEcosystem(Level parent, boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int w, int h, List<Node> outNodes) {
        boolean[][] decoOccupied = new boolean[w][h];
        for (Node n : outNodes) {
            if (n instanceof Tile t && t.getTileType() == TileType.DECO && t.getBlue() != 34) {
                if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                    decoOccupied[t.getX()][t.getY()] = true;
                }
            }
        }

        int lastBigPropX = -4;
        for (int y = 2; y < h - 1; y++) {
            lastBigPropX = -4;
            for (int x = 2; x < w - 3; x++) {
                if (!deepEarth[x][y] || placedL5[x][y] != -1 || decoOccupied[x][y]) continue;

                int floorTileBelow = (y + 1 < h) ? placedL5[x][y + 1] : -1;
                if (isGrassyTopTile(floorTileBelow) || floorTileBelow == 0 || floorTileBelow == 2) {
                    if (x - 2 >= 1 && placedL5[x - 1][y + 1] == 0 && deepEarth[x - 2][y + 1] && !decoOccupied[x - 2][y + 1] && rand.nextDouble() < 0.65) {
                        addDeco(parent, outNodes, x - 2, y + 1, 4, 24);
                        decoOccupied[x - 2][y + 1] = true;
                    }
                    if (x + 2 < w - 1 && placedL5[x + 1][y + 1] == 2 && deepEarth[x + 2][y + 1] && !decoOccupied[x + 2][y + 1] && rand.nextDouble() < 0.65) {
                        addDeco(parent, outNodes, x + 2, y + 1, 4, 37);
                        decoOccupied[x + 2][y + 1] = true;
                    }
                    if (x - lastBigPropX >= 4 && hasConsecutiveGrassyTop(placedL5, x, y + 1, 3, w) && placedL5[x][y - 1] == -1 && rand.nextDouble() < 0.62) {
                        int propId = (rand.nextDouble() < 0.45) ? pickNonRepeatingSetpiece(CRADLE_NOOK_SETPIECES) : CLASSIC_FLOOR_PROPS[rand.nextInt(CLASSIC_FLOOR_PROPS.length)];
                        int anchorY = (propId == 44 || propId == 116) ? y : (y - 1);
                        addDeco(parent, outNodes, x, anchorY, 4, propId);
                        decoOccupied[x][y] = true;
                        decoOccupied[x + 1][y] = true;
                        lastBigPropX = x;
                        continue;
                    }

                    if (hasConsecutiveGrassyTop(placedL5, x, y + 1, 2, w) && rand.nextDouble() < 0.48) {
                        int id = (rand.nextInt(4) == 0) ? 116 : (rand.nextBoolean() ? 30 : 33);
                        addDeco(parent, outNodes, x, y, 4, id);
                        decoOccupied[x][y] = true;
                        decoOccupied[x + 1][y] = true;
                        continue;
                    }

                    if (rand.nextDouble() < 0.82) {
                        double r = rand.nextDouble();
                        int decoId = (r < 0.35) ? SMALL_SHROOMS[rand.nextInt(SMALL_SHROOMS.length)] : (r < 0.55) ? CRYSTALS[rand.nextInt(CRYSTALS.length)] : SMALL_PLANTS[rand.nextInt(SMALL_PLANTS.length)];
                        addDeco(parent, outNodes, x, y, 4, decoId);
                        decoOccupied[x][y] = true;
                    }
                }

                int ceilTileAbove = placedL5[x][y - 1];
                if ((ceilTileAbove == 15 || ceilTileAbove == 14 || ceilTileAbove == 16) && (y + 2 < h && placedL5[x][y + 1] == -1 && placedL5[x][y + 2] == -1) && rand.nextDouble() < 0.58) {
                    int hangerId = CAVERN_CEILING_MOSS[rand.nextInt(CAVERN_CEILING_MOSS.length)];
                    addDeco(parent, outNodes, x, y, 4, hangerId);
                    decoOccupied[x][y] = true;
                }
            }
        }

        for (int y = 3; y < h - 4; y += 3) {
            for (int x = 3; x < w - 5; x += 4) {
                if (isClearForVoidSetpiece(deepEarth, placedL5, decoOccupied, x, y, 4, 3, w, h) && rand.nextDouble() < 0.72) {
                    int id = (y + 4 < h && deepEarth[x][y + 3] && rand.nextInt(4) == 0) ? VERTICAL_ROOT_COLUMNS[rand.nextInt(VERTICAL_ROOT_COLUMNS.length)] : pickNonRepeatingSetpiece(DEEP_VOID_SETPIECES);
                    addDeco(parent, outNodes, x, y, 4, id);
                    for (int dx = -1; dx <= 4; dx++) {
                        for (int dy = -1; dy <= 3; dy++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) decoOccupied[nx][ny] = true;
                        }
                    }
                }
            }
        }
    }

    private boolean isClearForVoidSetpiece(boolean[][] deepEarth, int[][] placedL5, boolean[][] decoOccupied, int sx, int sy, int bw, int bh, int w, int h) {
        if (sx + bw >= w - 1 || sy + bh >= h - 1) return false;
        for (int x = sx; x < sx + bw; x++) {
            for (int y = sy; y < sy + bh; y++) {
                if (!deepEarth[x][y] || placedL5[x][y] != -1 || decoOccupied[x][y]) return false;
            }
        }
        return true;
    }

    private int pickNonRepeatingSetpiece(int[] pool) {
        int chosen = pool[rand.nextInt(pool.length)];
        for (int i = 0; i < 6 && recentSetpieces.contains(chosen); i++) {
            chosen = pool[rand.nextInt(pool.length)];
        }
        recentSetpieces.addLast(chosen);
        while (recentSetpieces.size() > 4) recentSetpieces.removeFirst();
        return chosen;
    }

    private boolean isFreeDeepEarthBox(boolean[][] deepEarth, boolean[][] used, int sx, int sy, int bw, int bh, int w, int h) {
        if (sx < 1 || sy < 1 || sx + bw >= w - 1 || sy + bh >= h - 1) return false;
        for (int x = sx; x < sx + bw; x++) {
            for (int y = sy; y < sy + bh; y++) {
                if (!deepEarth[x][y] || used[x][y]) return false;
            }
        }
        return true;
    }

    private void markBoxUsed(boolean[][] used, int sx, int sy, int bw, int bh, int w, int h) {
        for (int x = Math.max(0, sx); x < Math.min(w, sx + bw); x++) {
            for (int y = Math.max(0, sy); y < Math.min(h, sy + bh); y++) {
                used[x][y] = true;
            }
        }
    }

    private boolean isGrassyTopTile(int tileId) {
        return tileId == 1 || tileId == 11 || tileId == 12 || tileId == 22;
    }

    private boolean hasConsecutiveGrassyTop(int[][] placedL5, int x, int floorY, int span, int w) {
        if (x + span >= w) return false;
        for (int i = 0; i < span; i++) {
            int id = placedL5[x + i][floorY];
            if (!isGrassyTopTile(id) && id != 0 && id != 2 && id != 21 && id != 23) return false;
        }
        return true;
    }

    private boolean isDeepEarth(boolean[][] solid, int cx, int cy, int w, int h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h || !solid[nx][ny]) return false;
            }
        }
        return true;
    }

    private void replaceSolidWithLayer5(Level parent, List<Node> outNodes, int x, int y, int tileId) {
        outNodes.removeIf(n -> n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getX() == x && t.getY() == y);
        Tile t5 = new Tile("", parent, TileType.SOLID, x, y, tileId, 255, 255);
        t5.setLayer(5);
        outNodes.add(t5);
    }

    private void addDeco(Level parent, List<Node> outNodes, int x, int y, int layer, int decoId) {
        outNodes.removeIf(n -> n instanceof Tile t && t.getTileType() == TileType.DECO && t.getX() == x && t.getY() == y);
        Tile d = new Tile("", parent, TileType.DECO, x, y, 254, layer, decoId);
        d.setLayer(layer);
        outNodes.add(d);
    }
}