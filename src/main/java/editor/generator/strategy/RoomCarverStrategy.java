package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

/**
 * Strategy interface for carving playable room geometry and placing archetype-specific objects.
 */
public interface RoomCarverStrategy {

    /**
     * Carves air passages into the solid terrain matrix and optionally appends archetype objects.
     *
     * @param parent     The target level.
     * @param solid      The 2D solid occupancy grid.
     * @param extraNodes Output list for archetype-specific object nodes.
     * @param w          Grid width in tiles.
     * @param h          Grid height in tiles.
     * @param rand       Random number generator.
     */
    void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand);

    /**
     * Clears a rectangular region in the solid matrix to air ({@code false}).
     */
    default void carveRect(boolean[][] solid, int rx, int ry, int rw, int rh, int w, int h) {
        for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
            for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
                solid[x][y] = false;
            }
        }
    }

    /**
     * Fills a rectangular region in the solid matrix with terrain ({@code true}).
     */
    default void fillRect(boolean[][] solid, int rx, int ry, int rw, int rh, int w, int h) {
        for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
            for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
                solid[x][y] = true;
            }
        }
    }
}