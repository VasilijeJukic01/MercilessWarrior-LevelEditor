package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

public class ChamberArenaStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        carveRect(solid, 4, 4, w - 8, h - 8, w, h);
        carveRect(solid, 0, h - 8, 6, 4, w, h);
        carveRect(solid, w - 6, h / 2 - 2, 6, 4, w, h);

        fillRect(solid, 10, h - 9, 8, 5, w, h);
        fillRect(solid, w - 18, h / 2 + 2, 10, h / 2 - 4, w, h);

        for (int x = w / 2 - 2; x <= w / 2 + 1; x++) {
            solid[x][h / 2 - 2] = true;
        }
    }

}