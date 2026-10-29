package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

public class VerticalSerpentStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int tiers = 4;
        int tierHeight = Math.max(4, (h - 6) / tiers);
        boolean dropRight = true;

        carveRect(solid, 5, 0, 4, 6, w, h);
        for (int t = 0; t < tiers; t++) {
            int yStart = 3 + t * tierHeight;
            carveRect(solid, 4, yStart, w - 8, tierHeight - 2, w, h);
            if (t < tiers - 1) {
                int shaftX = dropRight ? (w - 9) : 4;
                carveRect(solid, shaftX, yStart, 5, tierHeight + 2, w, h);
                dropRight = !dropRight;
            }
        }
        carveRect(solid, w - 6, h - 7, 6, 4, w, h);
    }

}