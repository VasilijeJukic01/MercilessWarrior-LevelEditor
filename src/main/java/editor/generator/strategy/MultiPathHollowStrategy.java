package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.List;
import java.util.Random;

/**
 * Carves a multi-tiered hollow with an upper and lower horizontal gallery separated by a thick subterranean earth slab, stepped floor terraces, and vertical shafts.
 */
public class MultiPathHollowStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        if (h < 22) {
            carveSingleTierSteppedHollow(solid, w, h, rand);
            return;
        }

        int topGalleryY = 4 + rand.nextInt(2), topGalleryH = Math.max(5, (h - 14) / 2);
        int slabThickness = Math.max(4, Math.min(7, (h - 10) - topGalleryH * 2));
        int botGalleryY = topGalleryY + topGalleryH + slabThickness;
        int botGalleryH = Math.max(5, h - botGalleryY - 4);

        carveSteppedCorridor(solid, 4, w - 5, topGalleryY, topGalleryH, w, h, rand);
        carveSteppedCorridor(solid, 4, w - 5, botGalleryY, botGalleryH, w, h, rand);

        int shaft1X = 7 + rand.nextInt(Math.max(3, w / 4));
        int shaft2X = (w * 2) / 3 + rand.nextInt(Math.max(3, w / 5));
        carveVerticalShaftWithSteps(solid, shaft1X, topGalleryY + topGalleryH - 1, botGalleryY + 1, w, h, rand);
        carveVerticalShaftWithSteps(solid, shaft2X, topGalleryY + topGalleryH - 1, botGalleryY + 1, w, h, rand);
    }

    private void carveSteppedCorridor(boolean[][] solid, int startX, int endX, int baseY, int baseH, int w, int h, Random rand) {
        int curY = baseY;
        int curH = baseH;
        int segLen = 7 + rand.nextInt(5);

        for (int x = startX; x <= endX; x++) {
            if ((x - startX) > 0 && (x - startX) % segLen == 0) {
                int stepFloor = rand.nextInt(3) - 1;
                int stepCeil = rand.nextInt(2);
                curY = Math.max(3, Math.min(h - 9, curY + stepCeil));
                curH = Math.max(5, Math.min(8, curH + stepFloor));
                segLen = 6 + rand.nextInt(6);
            }
            for (int y = curY; y < Math.min(h - 3, curY + curH); y++) {
                solid[x][y] = false;
            }
        }
    }

    private void carveVerticalShaftWithSteps(boolean[][] solid, int shaftX, int topY, int botY, int w, int h, Random rand) {
        int sw = 5;
        carveRect(solid, shaftX, topY, sw, botY - topY + 2, w, h);
        boolean leftSide = rand.nextBoolean();
        for (int y = botY - 1; y > topY + 2; y -= 3) {
            int lx = leftSide ? (shaftX - 1) : (shaftX + sw - 2);
            fillRect(solid, lx, y, 3, 2, w, h);
            leftSide = !leftSide;
        }
    }

    private void carveSingleTierSteppedHollow(boolean[][] solid, int w, int h, Random rand) {
        carveSteppedCorridor(solid, 4, w - 5, 4, h - 8, w, h, rand);
    }
}