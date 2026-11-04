package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 * Resolves 8-neighbor bitmask auto-tiling for playable terrain and generates procedural subterranean caverns and connected staircase/hill landscapes inside deep earth.
 */
public class AutoTileRuleEngine {

    private static final int[] INNER_PLANTS = {6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 51, 62, 63};
    private static final int[] INNER_SHROOMS = {55, 56, 57};
    private static final int[] INNER_CRYSTALS = {64, 65};
    private final Random rand = new Random();

    /**
     * Converts a 2D boolean solid grid into auto-tiled Layer 3 {@link Tile} nodes.
     *
     * @param parent The target level.
     * @param solid  The 2D solid occupancy grid.
     * @param w      Grid width in tiles.
     * @param h      Grid height in tiles.
     * @return A list of auto-tiled solid terrain nodes.
     */
    public List<Node> generateSolidTiles(Level parent, boolean[][] solid, int w, int h) {
        cleanupThinVerticalPillars(solid, w, h);
        List<Node> result = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!solid[x][y]) continue;
                int tileId = pickExactTileId(solid, x, y, w, h);
                Tile t = new Tile("", parent, TileType.SOLID, x, y, tileId, 254, 254);
                t.setLayer(3);
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Widens 1-tile-wide vertical columns to 2 tiles so ceilings never intersect single-tile pillars without a valid corner transition.
     */
    private void cleanupThinVerticalPillars(boolean[][] solid, int w, int h) {
        for (int x = 2; x < w - 2; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!solid[x][y]) continue;
                boolean u = solid[x][y - 1];
                boolean d = solid[x][y + 1];
                boolean l = solid[x - 1][y];
                boolean r = solid[x + 1][y];
                if ((u || d) && !l && !r) {
                    solid[x + 1][y] = true;
                }
            }
        }
    }

    /**
     * Carves and decorates layer 5 subterranean caverns, open stepped hills, and floating bridges inside deep interior earth regions using Perlin noise and cellular automata.
     *
     * @param parent   The target level.
     * @param solid    The playable Layer 3 solid grid.
     * @param w        Grid width in tiles.
     * @param h        Grid height in tiles.
     * @param outNodes The node list to populate with layer 5 tiles and layer 4 decorations.
     */
    public void generateWallPockets(Level parent, boolean[][] solid, int w, int h, List<Node> outNodes) {
        boolean[][] deepEarth = new boolean[w][h];
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                deepEarth[x][y] = isDeepEarth(solid, x, y, w, h);
            }
        }

        boolean[][] layer5Solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(layer5Solid[x], true);

        long seed = rand.nextLong();
        for (int x = 2; x < w - 2; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (canCarveAt(deepEarth, x, y)) {
                    double n = interpolatedNoise(x * 0.22, y * 0.38, seed);
                    layer5Solid[x][y] = n > 0.50;
                }
            }
        }

        for (int iter = 0; iter < 2; iter++) {
            boolean[][] next = new boolean[w][h];
            for (int x = 0; x < w; x++) Arrays.fill(next[x], true);

            for (int x = 2; x < w - 2; x++) {
                for (int y = 2; y < h - 2; y++) {
                    if (!canCarveAt(deepEarth, x, y)) {
                        next[x][y] = true;
                        continue;
                    }
                    int neighbors = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            if (layer5Solid[x + dx][y + dy]) neighbors++;
                        }
                    }
                    if (neighbors >= 5) next[x][y] = true;
                    else if (neighbors <= 3) next[x][y] = false;
                    else next[x][y] = layer5Solid[x][y];
                }
            }
            layer5Solid = next;
        }
        cleanupThinVerticalPillars(layer5Solid, w, h);

        int[][] placedL5 = new int[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(placedL5[x], -1);

        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (!deepEarth[x][y] || !layer5Solid[x][y]) continue;
                int tileId = pickExactTileId(layer5Solid, x, y, w, h);
                if (tileId != 8) {
                    placedL5[x][y] = tileId;
                }
            }
        }

        diversifyPocketTypes(layer5Solid, placedL5, deepEarth, w, h);

        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (placedL5[x][y] != -1) {
                    replaceSolidWithLayer5(parent, outNodes, x, y, placedL5[x][y]);
                    addDeco(parent, outNodes, x, y, 2, 34);
                }
            }
        }

        int lastBigBoulderX = -5;
        for (int x = 2; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!deepEarth[x][y] || placedL5[x][y] != -1) continue;

                int floorTileBelow = placedL5[x][y + 1];
                if (isGrassyTopTile(floorTileBelow)) {
                    if (placedL5[x - 1][y + 1] == 0 && x - 2 >= 1 && deepEarth[x - 2][y + 1] && rand.nextDouble() < 0.45) {
                        addDeco(parent, outNodes, x - 2, y + 1, 4, 24);
                    }
                    if (placedL5[x + 1][y + 1] == 2 && x + 2 < w - 1 && deepEarth[x + 2][y + 1] && rand.nextDouble() < 0.45) {
                        addDeco(parent, outNodes, x + 2, y + 1, 4, 37);
                    }

                    if (x - lastBigBoulderX >= 5 && hasConsecutiveGrassyTop(placedL5, x, y + 1, 4, w) && placedL5[x][y - 1] == -1 && rand.nextDouble() < 0.45) {
                        int bigId = rand.nextBoolean() ? rand.nextInt(3) : (48 + rand.nextInt(3));
                        addDeco(parent, outNodes, x, y - 1, 4, bigId);
                        if (rand.nextBoolean()) {
                            addDeco(parent, outNodes, x + 2, y, 4, 30);
                        }
                        lastBigBoulderX = x;
                    }
                    else if (hasConsecutiveGrassyTop(placedL5, x, y + 1, 2, w) && rand.nextDouble() < 0.28) {
                        addDeco(parent, outNodes, x, y, 4, rand.nextBoolean() ? 30 : 33);
                    }
                    else if (rand.nextDouble() < 0.60) {
                        int decoId;
                        double r = rand.nextDouble();
                        if (y > h / 2 && r < 0.25) decoId = INNER_SHROOMS[rand.nextInt(INNER_SHROOMS.length)];
                        else if (y > h * 0.6 && r < 0.40) decoId = INNER_CRYSTALS[rand.nextInt(INNER_CRYSTALS.length)];
                        else decoId = INNER_PLANTS[rand.nextInt(INNER_PLANTS.length)];
                        addDeco(parent, outNodes, x, y, 4, decoId);
                    }
                }

                int ceilTileAbove = placedL5[x][y - 1];
                if ((ceilTileAbove == 15 || ceilTileAbove == 22) && placedL5[x][Math.min(h - 1, y + 1)] == -1 && rand.nextDouble() < 0.38) {
                    addDeco(parent, outNodes, x, y, 4, rand.nextBoolean() ? 22 : 23);
                }
            }
        }
    }

    /**
     * Builds the subterranean staircase landscape inside a given rectangular region:
     * [Open Hill] -> [24:5 Nodule Step] -> [Lower Basin] -> [Stepped Boulder Plateau] -> [Rising 24:5 Nodule Steps]
     */
    public void buildSubterraneanStaircaseInRegion(int[][] placedL5, Rectangle box, int w, int h) {
        int x1 = Math.max(1, box.x + 1);
        int x2 = Math.min(w - 2, box.x + box.width - 2);
        int availW = x2 - x1 + 1;
        if (availW < 3) return;

        // Leave at least 2 rows above topY so 2-tile-tall boulders (anchored at topY - 2) fit on top of the hill!
        int topY = Math.min(h - 3, Math.max(2, box.y + Math.max(2, box.height / 2)));
        int botY = Math.min(h - 2, box.y + box.height - 1);

        // Compact region (availW < 8) -> Build an open hill (0:5, 1:5... 2:5 + 7:5/9:5 legs)
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

        // Medium region (availW 8..12) -> Build a connected stepped hill!
        // Lower left tier at (topY + 1) connects at splitX via inner corner 12:5 directly to upper right tier at (topY)!
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

        // Wide region (availW >= 13) -> Two-Tier stepped hill + rising 24:5 nodules
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

        // Two rising 24:5 stepping nodules to the right of the hill
        if (hillEndX + 2 <= x2 && topY >= 2) {
            placedL5[hillEndX + 2][topY] = 24;
        }
        if (hillEndX + 4 <= x2 && topY - 1 >= 2) {
            placedL5[hillEndX + 4][topY - 1] = 24;
        }
    }

    private void diversifyPocketTypes(boolean[][] layer5Solid, int[][] placedL5, boolean[][] deepEarth, int w, int h) {
        boolean[][] visited = new boolean[w][h];

        for (int x = 2; x < w - 2; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (!layer5Solid[x][y] && !visited[x][y]) {
                    List<Point> airCells = new ArrayList<>();
                    int minX = x, maxX = x, minY = y, maxY = y;
                    Queue<Point> q = new ArrayDeque<>();
                    q.add(new Point(x, y));
                    visited[x][y] = true;

                    while (!q.isEmpty()) {
                        Point p = q.poll();
                        airCells.add(p);
                        minX = Math.min(minX, p.x);
                        maxX = Math.max(maxX, p.x);
                        minY = Math.min(minY, p.y);
                        maxY = Math.max(maxY, p.y);

                        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                        for (int[] d : dirs) {
                            int nx = p.x + d[0], ny = p.y + d[1];
                            if (nx >= 2 && nx < w - 2 && ny >= 2 && ny < h - 2 && !layer5Solid[nx][ny] && !visited[nx][ny]) {
                                visited[nx][ny] = true;
                                q.add(new Point(nx, ny));
                            }
                        }
                    }

                    int pw = maxX - minX + 1;
                    int ph = maxY - minY + 1;
                    double roll = rand.nextDouble();

                    // Keep as organic Multi-Tiered cavern
                    if (roll < 0.45) {
                        if (pw >= 6 && ph >= 5 && rand.nextBoolean()) {
                            int by = minY + ph / 2;
                            int bw = 1 + rand.nextInt(3);
                            int bx = minX + 2 + rand.nextInt(Math.max(1, pw - bw - 3));
                            if (bw == 1) placedL5[bx][by] = 24;
                            else {
                                for (int k = 0; k < bw; k++) {
                                    placedL5[bx + k][by] = (k == 0) ? 21 : (k == bw - 1) ? 23 : 22;
                                }
                            }
                        }
                    }
                    // Convert into a subterranean staircase / hill / nodule landscape!
                    else if (roll < 0.85) {
                        if (pw >= 6 && ph >= 3) {
                            clearPocketBorder(placedL5, airCells, w, h);
                            buildSubterraneanStaircaseInRegion(placedL5, new Rectangle(minX, minY, pw, ph), w, h);
                        }
                        else {
                            clearPocketBorderAndConvertToOpenHill(placedL5, deepEarth, airCells, w, h);
                        }
                    }
                    // Floating mossy bridge (21-22-23) or nodule staircase (24)
                    else {
                        clearPocketBorder(placedL5, airCells, w, h);
                        int by = (minY + maxY) / 2;
                        int bw = Math.min(pw, 1 + rand.nextInt(3));
                        int bx = minX + (pw - bw) / 2;
                        if (bw == 1) placedL5[bx][by] = 24;
                        else {
                            for (int k = 0; k < bw; k++) {
                                placedL5[bx + k][by] = (k == 0) ? 21 : (k == bw - 1) ? 23 : 22;
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearPocketBorder(int[][] placedL5, List<Point> airCells, int w, int h) {
        for (Point p : airCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = p.x + dx, ny = p.y + dy;
                    if (nx >= 1 && nx < w - 1 && ny >= 1 && ny < h - 1) {
                        placedL5[nx][ny] = -1;
                    }
                }
            }
        }
    }

    /**
     * Removes the upper cavern ceiling/walls of a pocket while preserving its organic floor contour and capping the ends with outer hill corners and vertical legs.
     */
    private void clearPocketBorderAndConvertToOpenHill(int[][] placedL5, boolean[][] deepEarth, List<Point> airCells, int w, int h) {
        Map<Integer, Integer> colToFloorY = new TreeMap<>();
        for (Point p : airCells) {
            int fy = p.y + 1;
            if (fy < h - 2 && deepEarth[p.x][fy]) {
                int id = placedL5[p.x][fy];
                if (id == 1 || id == 11 || id == 12 || id == 0 || id == 2) {
                    colToFloorY.merge(p.x, fy, Math::max);
                }
            }
        }

        clearPocketBorder(placedL5, airCells, w, h);
        if (colToFloorY.size() < 3) return;

        List<Integer> cols = new ArrayList<>(colToFloorY.keySet());
        for (int i = 0; i < cols.size(); i++) {
            int cx = cols.get(i);
            int fy = colToFloorY.get(cx);

            boolean hasLeftSame = (i > 0 && cols.get(i - 1) == cx - 1 && colToFloorY.get(cx - 1) == fy);
            boolean hasRightSame = (i < cols.size() - 1 && cols.get(i + 1) == cx + 1 && colToFloorY.get(cx + 1) == fy);
            boolean leftIsHigher = (i > 0 && cols.get(i - 1) == cx - 1 && colToFloorY.get(cx - 1) < fy);
            boolean rightIsHigher = (i < cols.size() - 1 && cols.get(i + 1) == cx + 1 && colToFloorY.get(cx + 1) < fy);

            int topId;
            if (!hasLeftSame && !hasRightSame) topId = 24;
            else if (!hasLeftSame) topId = leftIsHigher ? 11 : 0;
            else if (!hasRightSame) topId = rightIsHigher ? 12 : 2;
            else topId = 1;

            placedL5[cx][fy] = topId;

            if (topId == 0 && fy + 1 < h - 1 && deepEarth[cx][fy + 1]) {
                placedL5[cx][fy + 1] = 7;
            }
            else if (topId == 2 && fy + 1 < h - 1 && deepEarth[cx][fy + 1]) {
                placedL5[cx][fy + 1] = 9;
            }
        }
    }

    private boolean canCarveAt(boolean[][] deepEarth, int x, int y) {
        return deepEarth[x][y] && deepEarth[x][y + 1] && deepEarth[x][y - 1]
                && deepEarth[x - 1][y] && deepEarth[x + 1][y];
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

    private double interpolatedNoise(double x, double y, long seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;

        double v1 = hashNoise(ix, iy, seed);
        double v2 = hashNoise(ix + 1, iy, seed);
        double v3 = hashNoise(ix, iy + 1, seed);
        double v4 = hashNoise(ix + 1, iy + 1, seed);

        // Cosine interpolation for smooth C1-continuous gradients between integer lattice points
        double tX = (1.0 - Math.cos(fx * Math.PI)) * 0.5;
        double tY = (1.0 - Math.cos(fy * Math.PI)) * 0.5;
        double i1 = v1 * (1.0 - tX) + v2 * tX;
        double i2 = v3 * (1.0 - tX) + v4 * tX;
        return i1 * (1.0 - tY) + i2 * tY;
    }

    private double hashNoise(int x, int y, long seed) {
        long n = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return ((n ^ (n >> 16)) & 0x7fffffff) / (double) Integer.MAX_VALUE;
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

    /**
     * Resolves the exact MossyTiles sprite index (0-48) from the 8-directional neighbor occupancy around (x, y).
     */
    public int pickExactTileId(boolean[][] solid, int x, int y, int w, int h) {
        boolean u  = isSolid(solid, x, y - 1, w, h);
        boolean d  = isSolid(solid, x, y + 1, w, h);
        boolean l  = isSolid(solid, x - 1, y, w, h);
        boolean r  = isSolid(solid, x + 1, y, w, h);
        boolean ul = isSolid(solid, x - 1, y - 1, w, h);
        boolean ur = isSolid(solid, x + 1, y - 1, w, h);
        boolean dl = isSolid(solid, x - 1, y + 1, w, h);
        boolean dr = isSolid(solid, x + 1, y + 1, w, h);

        // Isolated 1x1 block (0 cardinal neighbors)
        if (!u && !d && !l && !r) return 24;

        // Thin 1-tile horizontal bridge (!u && !d)
        if (!u && !d) {
            if (!l && r) return 21;
            if (l && !r) return 23;
            return 22;
        }

        // Thin 1-tile vertical pillar (!l && !r)
        if (!l && !r) {
            if (!u && d) return 3;
            if (u && !d) return 17;
            return 10;
        }

        // Outer Convex Corners (2 adjacent cardinal neighbors)
        if (!u && d && !l && r) return dr ? 0 : 32;
        if (!u && d && l && !r) return dl ? 2 : 34;
        if (u && !d && !l && r) return ur ? 14 : 46;
        if (u && !d && l && !r) return ul ? 16 : 48;

        // Top Surface (!u && d && l && r)
        if (!u && d && l && r) {
            if (!dl && !dr) return 33;
            if (!dr) return 30;
            if (!dl) return 31;
            return 1;
        }

        // Bottom Ceiling Surface (u && !d && l && r)
        if (u && !d && l && r) {
            if (!ul && !ur) return 47;
            if (!ur) return 37;
            if (!ul) return 38;
            return 15;
        }

        // Left Wall Surface (u && d && !l && r)
        if (u && d && !l && r) {
            if (!ur && !dr) return 39;
            if (!dr) return 28;
            if (!ur) return 35;
            return 7;
        }

        // Right Wall Surface (u && d && l && !r)
        if (u && d && l && !r) {
            if (!ul && !dl) return 41;
            if (!dl) return 29;
            if (!ul) return 36;
            return 9;
        }

        // All 4 cardinal neighbors are solid (u && d && l && r) -> 16 combinations of (ul, ur, dl, dr)
        if (!ul && !ur && !dl && !dr) return 40;

        // 3 diagonal air cells
        if (!ul && ur && !dl && !dr) return 18;
        if (ul && !ur && !dl && !dr) return 19;
        if (!ul && !ur && !dl && dr) return 25;
        if (!ul && !ur && dl && !dr) return 26;

        // 2 diagonal air cells
        if (!ul && !ur) return 6;
        if (!dl && !dr) return 27;
        if (!ul && !dl) return 13;
        if (!ur && !dr) return 20;
        if (!ur && !dl) return 44;
        if (!ul && !dr) return 45;

        // 1 diagonal air cell
        if (!dr) return 4;
        if (!dl) return 5;
        if (!ur) return 11;
        if (!ul) return 12;

        // 0 diagonal air cells (solid interior center)
        return 8;
    }

    /**
     * Re-evaluates and updates the 8-neighbor auto-tile IDs for all Layer 3 solid tiles within the specified bounding box (clamped to level bounds).
     *
     * @param level The level being edited.
     * @param minX  Minimum X coordinate of the dirty region.
     * @param minY  Minimum Y coordinate of the dirty region.
     * @param maxX  Maximum X coordinate of the dirty region.
     * @param maxY  Maximum Y coordinate of the dirty region.
     */
    public void updateRegionAutoTiles(Level level, int minX, int minY, int maxX, int maxY) {
        int w = level.getWidth();
        int h = level.getHeight();
        boolean[][] solid = new boolean[w][h];
        Tile[][] solidTileMap = new Tile[w][h];

        for (Node node : level.getChildren()) {
            if (node instanceof Tile t && t.getTileType() == TileType.SOLID) {
                if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                    solid[t.getX()][t.getY()] = true;
                    if (t.getLayer() == 3) solidTileMap[t.getX()][t.getY()] = t;
                }
            }
        }

        int startX = Math.max(0, minX);
        int endX = Math.min(w - 1, maxX);
        int startY = Math.max(0, minY);
        int endY = Math.min(h - 1, maxY);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                Tile existing = solidTileMap[x][y];
                if (existing != null) {
                    int newId = pickExactTileId(solid, x, y, w, h);
                    existing.setRed(newId);
                }
            }
        }
    }

    /**
     * Auto-tiles Layer 5 solid structures (bridges, nodules, hills) within the specified bounding box,
     * ensuring each Layer 5 tile has a 34:2 BLACK backing decoration and no conflicting Layer 3 tile.
     */
    public void updateLayer5AutoTiles(Level level, int minX, int minY, int maxX, int maxY) {
        int w = level.getWidth();
        int h = level.getHeight();
        boolean[][] l5Solid = new boolean[w][h];
        Tile[][] l5TileMap = new Tile[w][h];

        for (Node node : level.getChildren()) {
            if (node instanceof Tile t && t.getTileType() == TileType.SOLID && t.getLayer() == 5) {
                if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                    l5Solid[t.getX()][t.getY()] = true;
                    l5TileMap[t.getX()][t.getY()] = t;
                }
            }
        }

        int startX = Math.max(0, minX);
        int endX = Math.min(w - 1, maxX);
        int startY = Math.max(0, minY);
        int endY = Math.min(h - 1, maxY);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                Tile existing = l5TileMap[x][y];
                if (existing != null) {
                    int newId = pickExactTileId(l5Solid, x, y, w, h);
                    existing.setRed(newId);
                    ensureBlackBackingAndClearL3Interior(level, x, y);
                }
            }
        }
    }

    /**
     * Carves a subterranean cavern inside solid earth within [minX..maxX, minY..maxY], placing BLACK backing inside the hollow cavity and converting the 1-tile perimeter into cavern walls/floors/ceilings.
     */
    public void carveLayer5CavernRegion(Level level, int minX, int minY, int maxX, int maxY) {
        int w = level.getWidth();
        int h = level.getHeight();

        boolean[][] isOuterSurfaceL3 = new boolean[w][h];
        boolean[][] hasSolid = new boolean[w][h];
        boolean[][] hasBlackDeco = new boolean[w][h];

        for (Node node : level.getChildren()) {
            if (node instanceof Tile t) {
                int tx = t.getX(), ty = t.getY();
                if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                if (t.getTileType() == TileType.SOLID) {
                    hasSolid[tx][ty] = true;
                    if (t.getLayer() == 3 && t.getRed() != 8) {
                        isOuterSurfaceL3[tx][ty] = true;
                    }
                } else if (t.getTileType() == TileType.DECO && t.getLayer() == 2 && t.getBlue() == 34) {
                    hasBlackDeco[tx][ty] = true;
                }
            }
        }

        boolean[][] cavity = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                cavity[x][y] = hasBlackDeco[x][y] && !hasSolid[x][y];
            }
        }

        int cMinX = Math.max(1, minX), cMaxX = Math.min(w - 2, maxX);
        int cMinY = Math.max(1, minY), cMaxY = Math.min(h - 2, maxY);

        for (int x = cMinX; x <= cMaxX; x++) {
            for (int y = cMinY; y <= cMaxY; y++) {
                if (!canPlaceCaveCavityAt(isOuterSurfaceL3, hasSolid, hasBlackDeco, x, y, w, h)) {
                    continue;
                }
                cavity[x][y] = true;
                final int cx = x, cy = y;
                level.getChildren().removeIf(n -> n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getX() == cx && t.getY() == cy);
                ensureBlackBackingDeco(level, x, y);
            }
        }

        boolean[][] surroundingSolid = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                surroundingSolid[x][y] = !cavity[x][y];
            }
        }

        int bx1 = Math.max(1, cMinX - 1), bx2 = Math.min(w - 2, cMaxX + 1);
        int by1 = Math.max(1, cMinY - 1), by2 = Math.min(h - 2, cMaxY + 1);

        for (int x = bx1; x <= bx2; x++) {
            for (int y = by1; y <= by2; y++) {
                if (cavity[x][y] || isOuterSurfaceL3[x][y]) continue;
                if (!hasSolid[x][y] && !hasBlackDeco[x][y]) continue;

                int id = pickExactTileId(surroundingSolid, x, y, w, h);
                final int px = x, py = y;
                if (id != 8) {
                    level.getChildren().removeIf(n -> n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getX() == px && t.getY() == py);
                    Tile t5 = new Tile("", level, TileType.SOLID, x, y, id, 255, 255);
                    t5.setLayer(5);
                    level.getChildren().add(t5);
                    ensureBlackBackingDeco(level, x, y);
                }
            }
        }
    }

    private boolean canPlaceCaveCavityAt(boolean[][] isOuterSurfaceL3, boolean[][] hasSolid, boolean[][] hasBlackDeco, int cx, int cy, int w, int h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) return false;
                if (isOuterSurfaceL3[nx][ny]) return false;
                if (!hasSolid[nx][ny] && !hasBlackDeco[nx][ny]) return false;
            }
        }
        return true;
    }

    private void ensureBlackBackingAndClearL3Interior(Level level, int x, int y) {
        level.getChildren().removeIf(n -> n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getLayer() == 3 && t.getRed() == 8 && t.getX() == x && t.getY() == y);
        ensureBlackBackingDeco(level, x, y);
    }

    private void ensureBlackBackingDeco(Level level, int x, int y) {
        for (Node n : level.getChildren()) {
            if (n instanceof Tile t && t.getTileType() == TileType.DECO && t.getLayer() == 2 && t.getX() == x && t.getY() == y) {
                return;
            }
        }
        Tile black = new Tile("", level, TileType.DECO, x, y, 254, 2, 34);
        black.setLayer(2);
        level.getChildren().add(black);
    }

    private boolean isSolid(boolean[][] solid, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return true;
        return solid[x][y];
    }
}