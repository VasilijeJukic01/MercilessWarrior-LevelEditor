package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Generates procedural Layer 5 subterranean cutaways (L-hooks, U-alcoves, Z-steps, irregular caverns, stepped hills, and 3x3 rings) inside deep interior earth and populates them with Layer 4 decorations.
 */
public class SubterraneanPocketGenerator {

    private static final int[] INNER_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 51, 62, 63};
    private static final int[] INNER_SHROOMS = {55, 56, 57};
    private static final int[] INNER_CRYSTALS = {64, 65};

    private final AutoTileRuleEngine tileEngine;
    private final Random rand = new Random();

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

        List<Point> candidates = new ArrayList<>();
        for (int y = 2; y < h - 3; y += 2) {
            for (int x = 2; x < w - 4; x += 2) {
                int jx = Math.min(w - 4, Math.max(2, x + rand.nextInt(3) - 1));
                int jy = Math.min(h - 3, Math.max(2, y + rand.nextInt(3) - 1));
                if (deepEarth[jx][jy]) candidates.add(new Point(jx, jy));
            }
        }
        Collections.shuffle(candidates, rand);

        boolean placedRareRing = false;
        int lastMotifType = -1;

        for (Point pt : candidates) {
            int x = pt.x, y = pt.y;
            if (used[x][y] || !deepEarth[x][y]) continue;

            int motif = rand.nextInt(7);
            if (motif == lastMotifType) motif = (motif + 1 + rand.nextInt(5)) % 7;

            boolean placed = false;
            switch (motif) {
                case 0 -> {
                    int cw = 6 + rand.nextInt(4);
                    int ch = 4 + rand.nextInt(3);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, cw, ch, w, h)) {
                        stampIrregularLayer5Cavern(placedL5, used, x, y, cw, ch);
                        placed = true;
                    }
                }
                case 1 -> {
                    int lw = 4 + rand.nextInt(4);
                    int lh = 3 + rand.nextInt(2);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, lw, lh, w, h)) {
                        stampOpenLHook(placedL5, used, x, y, lw, lh, rand.nextBoolean());
                        placed = true;
                    }
                }
                case 2 -> {
                    int uw = 4 + rand.nextInt(4);
                    int uh = 3 + rand.nextInt(2);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, uw, uh, w, h)) {
                        stampOpenUAlcove(placedL5, used, x, y, uw, uh);
                        placed = true;
                    }
                }
                case 3 -> {
                    int zw = 5 + rand.nextInt(4);
                    int zh = 3 + rand.nextInt(2);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, zw, zh, w, h)) {
                        stampZStepContour(placedL5, used, x, y, zw, zh, rand.nextBoolean());
                        placed = true;
                    }
                }
                case 4 -> {
                    int hw = 5 + rand.nextInt(4);
                    int hh = 3 + rand.nextInt(2);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, hw, hh, w, h)) {
                        buildSubterraneanStaircaseInRegion(placedL5, new Rectangle(x, y, hw, hh), w, h);
                        markBoxUsed(used, x - 2, y - 2, hw + 4, hh + 4, w, h);
                        placed = true;
                    }
                }
                case 5 -> {
                    if (!placedRareRing && isFreeDeepEarthBox(deepEarth, used, x, y, 3, 3, w, h)) {
                        stampHollow3x3Ring(placedL5, used, x, y, w, h);
                        placedRareRing = true;
                        placed = true;
                    }
                }
                case 6 -> {
                    int sw = 3 + rand.nextInt(3);
                    if (isFreeDeepEarthBox(deepEarth, used, x, y, sw, 2, w, h)) {
                        for (int k = 0; k < sw; k++) {
                            placedL5[x + k][y + 1] = (k == 0) ? 21 : (k == sw - 1) ? 23 : 22;
                        }
                        markBoxUsed(used, x - 2, y - 2, sw + 4, 5, w, h);
                        placed = true;
                    }
                }
            }

            if (placed) lastMotifType = motif;
        }

        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (placedL5[x][y] != -1) {
                    replaceSolidWithLayer5(parent, outNodes, x, y, placedL5[x][y]);
                    addDeco(parent, outNodes, x, y, 2, 34);
                }
            }
        }
        decorateSubterraneanFeatures(parent, deepEarth, used, placedL5, w, h, outNodes);
    }

    /**
     * Builds a connected subterranean staircase/hill terrace inside the specified rectangular region.
     */
    public void buildSubterraneanStaircaseInRegion(int[][] placedL5, Rectangle box, int w, int h) {
        int x1 = Math.max(1, box.x + 1);
        int x2 = Math.min(w - 2, box.x + box.width - 2);
        int availW = x2 - x1 + 1;
        if (availW < 3) return;

        int topY = Math.min(h - 3, Math.max(2, box.y + Math.max(2, box.height / 2)));
        int botY = Math.min(h - 2, box.y + box.height - 1);

        if (availW < 8 || topY + 1 >= botY) {
            for (int x = x1; x <= x2; x++) {
                placedL5[x][topY] = (x == x1) ? 0 : (x == x2) ? 2 : 1;
            }
            for (int y = topY + 1; y <= botY; y++) {
                placedL5[x1][y] = 7;
                placedL5[x2][y] = 9;
            }
            return;
        }

        if (availW < 13) {
            int splitX = x1 + Math.max(2, availW / 3);
            int lowY = topY + 1;

            for (int x = x1; x <= splitX; x++) {
                placedL5[x][lowY] = (x == x1) ? 0 : (x == splitX) ? 12 : 1;
            }
            for (int y = lowY + 1; y <= botY; y++) {
                placedL5[x1][y] = 7;
            }

            for (int x = splitX; x <= x2; x++) {
                placedL5[x][topY] = (x == splitX) ? 0 : (x == x2) ? 2 : 1;
            }
            for (int y = topY + 1; y <= botY; y++) {
                placedL5[x2][y] = 9;
            }
            return;
        }

        int hillEndX = x2 - 4;
        int splitX = x1 + Math.max(2, (hillEndX - x1) / 3);
        int lowY = topY + 1;

        for (int x = x1; x <= splitX; x++) {
            placedL5[x][lowY] = (x == x1) ? 0 : (x == splitX) ? 12 : 1;
        }
        for (int y = lowY + 1; y <= botY; y++) {
            placedL5[x1][y] = 7;
        }

        for (int x = splitX; x <= hillEndX; x++) {
            placedL5[x][topY] = (x == splitX) ? 0 : (x == hillEndX) ? 2 : 1;
        }
        for (int y = topY + 1; y <= botY; y++) {
            placedL5[hillEndX][y] = 9;
        }

        if (hillEndX + 2 <= x2 && topY >= 2) {
            placedL5[hillEndX + 2][topY] = 24;
        }
        if (hillEndX + 4 <= x2 && topY - 1 >= 2) {
            placedL5[hillEndX + 4][topY - 1] = 24;
        }
    }

    private void stampIrregularLayer5Cavern(int[][] placedL5, boolean[][] used, int rx, int ry, int rw, int rh) {
        boolean[][] localSolid = new boolean[rw + 2][rh + 2];
        for (int x = 0; x < rw + 2; x++) Arrays.fill(localSolid[x], true);

        int splitX = 2 + rw / 2;
        int topY1 = rand.nextBoolean() ? 2 : 3;
        int topY2 = (topY1 == 2 && rh >= 5) ? 3 : 2;
        int botY1 = rh - 1;
        int botY2 = (rh >= 5 && rand.nextBoolean()) ? (rh - 2) : (rh - 1);

        for (int x = 2; x < rw; x++) {
            int yStart = (x < splitX) ? topY1 : topY2;
            int yEnd = (x < splitX) ? botY1 : botY2;
            for (int y = yStart; y <= yEnd; y++) {
                localSolid[x][y] = false;
            }
        }

        for (int x = 1; x <= rw; x++) {
            for (int y = 1; y <= rh; y++) {
                if (localSolid[x][y]) {
                    int id = tileEngine.pickExactTileId(localSolid, x, y, rw + 2, rh + 2);
                    if (id != 8) {
                        placedL5[rx + x - 1][ry + y - 1] = id;
                    }
                }
            }
        }
        markBoxUsed(used, rx - 2, ry - 2, rw + 4, rh + 4, used.length, used[0].length);
    }

    private void stampOpenLHook(int[][] placedL5, boolean[][] used, int rx, int ry, int lw, int lh, boolean wallOnLeft) {
        int floorY = ry + lh - 1;
        if (wallOnLeft) {
            placedL5[rx][ry] = 3;
            for (int y = ry + 1; y < floorY; y++) placedL5[rx][y] = 9;
            placedL5[rx][floorY] = 11;
            for (int x = rx + 1; x < rx + lw - 1; x++) placedL5[x][floorY] = 1;
            placedL5[rx + lw - 1][floorY] = 2;
        }
        else {
            int wallX = rx + lw - 1;
            placedL5[wallX][ry] = 3;
            for (int y = ry + 1; y < floorY; y++) placedL5[wallX][y] = 7;
            placedL5[wallX][floorY] = 12;
            for (int x = rx + 1; x < wallX; x++) placedL5[x][floorY] = 1;
            placedL5[rx][floorY] = 0;
        }
        markBoxUsed(used, rx - 2, ry - 2, lw + 4, lh + 4, used.length, used[0].length);
    }

    private void stampOpenUAlcove(int[][] placedL5, boolean[][] used, int rx, int ry, int uw, int uh) {
        int floorY = ry + uh - 1;
        int rightX = rx + uw - 1;

        placedL5[rx][ry] = 0;
        placedL5[rightX][ry] = 2;
        for (int y = ry + 1; y < floorY; y++) {
            placedL5[rx][y] = 9;
            placedL5[rightX][y] = 7;
        }
        placedL5[rx][floorY] = 11;
        placedL5[rightX][floorY] = 12;
        for (int x = rx + 1; x < rightX; x++) {
            placedL5[x][floorY] = 1;
        }
        markBoxUsed(used, rx - 2, ry - 2, uw + 4, uh + 4, used.length, used[0].length);
    }

    private void stampZStepContour(int[][] placedL5, boolean[][] used, int rx, int ry, int zw, int zh, boolean stepDownRight) {
        int midX = rx + zw / 2;
        int topY = ry + 1;
        int botY = ry + zh - 1;

        if (stepDownRight) {
            placedL5[rx][topY] = 0;
            for (int x = rx + 1; x < midX; x++) placedL5[x][topY] = 1;
            placedL5[midX][topY] = 2;
            for (int y = topY + 1; y < botY; y++) placedL5[midX][y] = 9;
            placedL5[midX][botY] = 11;
            for (int x = midX + 1; x < rx + zw - 1; x++) placedL5[x][botY] = 1;
            placedL5[rx + zw - 1][botY] = 2;
        }
        else {
            placedL5[rx][botY] = 0;
            for (int x = rx + 1; x < midX; x++) placedL5[x][botY] = 1;
            placedL5[midX][botY] = 12;
            for (int y = topY + 1; y < botY; y++) placedL5[midX][y] = 7;
            placedL5[midX][topY] = 0;
            for (int x = midX + 1; x < rx + zw - 1; x++) placedL5[x][topY] = 1;
            placedL5[rx + zw - 1][topY] = 2;
        }
        markBoxUsed(used, rx - 2, ry - 2, zw + 4, zh + 4, used.length, used[0].length);
    }

    private void stampHollow3x3Ring(int[][] placedL5, boolean[][] used, int rx, int ry, int w, int h) {
        placedL5[rx][ry] = 0;      placedL5[rx + 1][ry] = 1;      placedL5[rx + 2][ry] = 2;
        placedL5[rx][ry + 1] = 7;  placedL5[rx + 1][ry + 1] = 24; placedL5[rx + 2][ry + 1] = 9;
        placedL5[rx][ry + 2] = 14; placedL5[rx + 1][ry + 2] = 15; placedL5[rx + 2][ry + 2] = 16;
        markBoxUsed(used, rx - 2, ry - 2, 7, 7, w, h);
    }

    private void decorateSubterraneanFeatures(Level parent, boolean[][] deepEarth, boolean[][] used, int[][] placedL5, int w, int h, List<Node> outNodes) {
        int lastBigPropX = -5;
        for (int x = 2; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!deepEarth[x][y] || placedL5[x][y] != -1) continue;

                int floorTileBelow = placedL5[x][y + 1];
                if (isGrassyTopTile(floorTileBelow) || floorTileBelow == 21 || floorTileBelow == 23) {
                    if (placedL5[x - 1][y + 1] == 0 && x - 2 >= 1 && deepEarth[x - 2][y + 1] && rand.nextDouble() < 0.50) {
                        addDeco(parent, outNodes, x - 2, y + 1, 4, 24);
                    }
                    if (placedL5[x + 1][y + 1] == 2 && x + 2 < w - 1 && deepEarth[x + 2][y + 1] && rand.nextDouble() < 0.50) {
                        addDeco(parent, outNodes, x + 2, y + 1, 4, 37);
                    }

                    if (x - lastBigPropX >= 5 && hasConsecutiveGrassyTop(placedL5, x, y + 1, 3, w) && placedL5[x][y - 1] == -1 && rand.nextDouble() < 0.45) {
                        double roll = rand.nextDouble();
                        int bigId = (roll < 0.45) ? rand.nextInt(3) : (roll < 0.75) ? (48 + rand.nextInt(3)) : 58;
                        addDeco(parent, outNodes, x, y - 1, 4, bigId);
                        lastBigPropX = x;
                    }
                    else if (hasConsecutiveGrassyTop(placedL5, x, y + 1, 2, w) && rand.nextDouble() < 0.35) {
                        addDeco(parent, outNodes, x, y, 4, rand.nextBoolean() ? 30 : 33);
                    }
                    else if (rand.nextDouble() < 0.65) {
                        int decoId;
                        double r = rand.nextDouble();
                        if (r < 0.28) decoId = INNER_SHROOMS[rand.nextInt(INNER_SHROOMS.length)];
                        else if (r < 0.48) decoId = INNER_CRYSTALS[rand.nextInt(INNER_CRYSTALS.length)];
                        else decoId = INNER_PLANTS[rand.nextInt(INNER_PLANTS.length)];
                        addDeco(parent, outNodes, x, y, 4, decoId);
                    }
                }

                int ceilTileAbove = placedL5[x][y - 1];
                if ((ceilTileAbove == 15 || ceilTileAbove == 22) && placedL5[x][Math.min(h - 1, y + 1)] == -1 && rand.nextDouble() < 0.45) {
                    addDeco(parent, outNodes, x, y, 4, rand.nextBoolean() ? 22 : 23);
                }

                if (!used[x][y] && y + 3 < h - 1 && deepEarth[x][y + 3] && rand.nextDouble() < 0.03) {
                    int[] rootVines = {19, 20, 21};
                    addDeco(parent, outNodes, x, y, 4, rootVines[rand.nextInt(rootVines.length)]);
                    markBoxUsed(used, x - 2, y - 1, 5, 5, w, h);
                }
            }
        }
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
        return tileId == 1 || tileId == 11 || tileId == 12 || tileId == 22 || tileId == 24;
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