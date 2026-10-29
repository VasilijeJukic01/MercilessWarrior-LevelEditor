package editor.generator;

import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact 8-neighbor auto-tiling rules extracted directly from Merciless Warrior's
 * level00, level01, level02, level03, level10, level11, level12, level13, and level23 pixel dumps.
 */
public class AutoTileRuleEngine {
    public List<Node> generateSolidTiles(Level parent, boolean[][] solid, int w, int h) {
        List<Node> result = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!solid[x][y]) continue;
                int tileId = pickExactTileId(solid, x, y, w, h);
                // Playable terrain is always on Layer 3 (G=254, B=254)
                Tile t = new Tile("", parent, TileType.SOLID, x, y, tileId, 254, 254);
                t.setLayer(3);
                result.add(t);
            }
        }
        return result;
    }

    private int pickExactTileId(boolean[][] solid, int x, int y, int w, int h) {
        boolean u  = isSolid(solid, x, y - 1, w, h);
        boolean d  = isSolid(solid, x, y + 1, w, h);
        boolean l  = isSolid(solid, x - 1, y, w, h);
        boolean r  = isSolid(solid, x + 1, y, w, h);
        boolean ul = isSolid(solid, x - 1, y - 1, w, h);
        boolean ur = isSolid(solid, x + 1, y - 1, w, h);
        boolean dl = isSolid(solid, x - 1, y + 1, w, h);
        boolean dr = isSolid(solid, x + 1, y + 1, w, h);

        // 1. Single 1x1 isolated stepping stone (!u && !d && !l && !r) -> Tile 24
        if (!u && !d && !l && !r) return 24;

        // 2. Thin 1-tile horizontal floating platform (!u && !d) -> 21 (Left), 22 (Mid), 23 (Right)
        if (!u && !d) {
            if (!l && r) return 21;
            if (l && !r) return 23;
            return 22;
        }

        // 3. Thin 1-tile vertical pillar (!l && !r) -> 3 (Top), 10 (Mid), 17 (Bottom)
        if (!l && !r) {
            if (!u && d) return 3;
            if (u && !d) return 17;
            return 10;
        }

        // 4. Outer Convex Corners (sticking out into air) -> 0 (TL), 2 (TR), 14 (BL), 16 (BR)
        if (!u && d && !l && r) return 0;  // Top-Left outer corner
        if (!u && d && l && !r) return 2;  // Top-Right outer corner
        if (u && !d && !l && r) return 14; // Bottom-Left outer corner
        if (u && !d && l && !r) return 16; // Bottom-Right outer corner

        // 5. Flat Cardinal Surfaces -> 1 (Top Floor), 15 (Bottom Ceiling), 7 (Left Edge), 9 (Right Edge)
        if (!u && d && l && r) return 1;   // Flat grassy floor (air above)
        if (u && !d && l && r) return 15;  // Flat ceiling (air below)
        if (u && d && !l && r) return 7;   // Left-facing wall (air to the left)
        if (u && d && l && !r) return 9;   // Right-facing wall (air to the right)

        // 6. All 4 cardinal neighbors are solid -> Check diagonals for Inner Concave Corners!
        if (u && d && l && r) {
            if (!ur && !ul) return 1;
            if (!dl && !dr) return 15;
            if (!ur) return 11; // Bottom-Left inner corner (wall on left meets floor on right)
            if (!ul) return 12; // Bottom-Right inner corner (floor on left meets wall on right)
            if (!dr) return 4;  // Top-Left inner corner (wall on left meets ceiling on right)
            if (!dl) return 5;  // Top-Right inner corner (ceiling on left meets wall on right)
            return 8;           // Solid Black Center Fill!
        }

        return 8;
    }

    private boolean isSolid(boolean[][] solid, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return true;
        return solid[x][y];
    }
}