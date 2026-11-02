package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

/**
 * Carves an upper gallery and lower combat chamber connected by vertical shafts, populated with randomized stepped plateaus and floating bridges.
 */
public class ChamberArenaStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int topH = 5 + rand.nextInt(3);
        int botH = 6 + rand.nextInt(4);
        int topY = 4 + rand.nextInt(2);
        int botY = h - botH - 4;

        carveRect(solid, 4, topY, w - 8, topH, w, h);
        carveRect(solid, 4, botY, w - 8, botH, w, h);

        int leftTunnelY = rand.nextBoolean() ? (topY + topH - 4) : (botY + botH - 4);
        int rightTunnelY = rand.nextBoolean() ? (botY + botH - 4) : (topY + topH - 4);
        carveRect(solid, 0, leftTunnelY, 6, 4, w, h);
        carveRect(solid, w - 6, rightTunnelY, 6, 4, w, h);

        int shaft1X = 6 + rand.nextInt(Math.max(2, w / 3));
        int shaft2X = w / 2 + rand.nextInt(Math.max(2, w / 3 - 6));
        carveRect(solid, shaft1X, topY, 5, botY - topY + botH, w, h);
        if (rand.nextBoolean()) {
            carveRect(solid, shaft2X, topY, 5, botY - topY + botH, w, h);
        }

        int numPlateaus = 1 + rand.nextInt(3);
        for (int i = 0; i < numPlateaus; i++) {
            int pw = 4 + rand.nextInt(6);
            int ph = 2 + rand.nextInt(2);
            int px = 6 + rand.nextInt(Math.max(2, w - pw - 12));
            fillRect(solid, px, botY + botH - ph, pw, ph, w, h);
        }

        int numBridges = 2 + rand.nextInt(4);
        for (int i = 0; i < numBridges; i++) {
            int bw = 1 + rand.nextInt(4);
            int bx = 6 + rand.nextInt(Math.max(2, w - bw - 12));
            int by = botY + 1 + rand.nextInt(Math.max(2, botH - 3));
            fillRect(solid, bx, by, bw, 1, w, h);
        }
    }
}