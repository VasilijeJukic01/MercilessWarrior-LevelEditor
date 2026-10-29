package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

public class MultiPathHollowStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int leftY = 6 + rand.nextInt(3);
        int rightY = h - 7;
        carveRect(solid, 0, leftY - 3, 8, 4, w, h);
        carveRect(solid, w - 8, rightY - 3, 8, 4, w, h);

        carveRect(solid, 5, 4, w - 10, h / 2 - 3, w, h);
        carveRect(solid, 5, h / 2 + 2, w - 10, h / 2 - 5, w, h);

        carveRect(solid, 7, 4, 5, h - 8, w, h);
        carveRect(solid, w / 2 - 2, 4, 5, h - 8, w, h);
        carveRect(solid, w - 12, 4, 5, h - 8, w, h);

        for (int x = 10; x < w - 12; x += 9) {
            int py = h / 2 + (rand.nextBoolean() ? -2 : 3);
            for (int bx = x; bx < x + 3; bx++) solid[bx][py] = true;
        }
    }

}