package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves 8-neighbor bitmask auto-tiling for playable Layer 3 terrain and interactive Layer 5 brush strokes.
 */
public class AutoTileRuleEngine {

    /**
     * Converts a 2D boolean solid grid into auto-tiled Layer 3 {@link Tile} nodes.
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
     * Re-evaluates and updates the 8-neighbor auto-tile IDs for all Layer 3 solid tiles within the specified bounding box.
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
     * Auto-tiles Layer 5 solid structures (bridges, nodules, hills) within the specified bounding box, ensuring each Layer 5 tile has a 34:2 BLACK backing decoration and no conflicting Layer 3 tile.
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