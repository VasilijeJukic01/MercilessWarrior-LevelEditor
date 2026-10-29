package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

/**
 * Strategy interface for carving room geometry and placing archetype-specific objects.
 */
public interface RoomCarverStrategy {

    void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand);

    default void carveRect(boolean[][] solid, int rx, int ry, int rw, int rh, int w, int h) {
        for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
            for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
                solid[x][y] = false;
            }
        }
    }

    default void fillRect(boolean[][] solid, int rx, int ry, int rw, int rh, int w, int h) {
        for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
            for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
                solid[x][y] = true;
            }
        }
    }
}