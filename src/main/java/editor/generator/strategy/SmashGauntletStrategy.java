package editor.generator.strategy;

import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.List;
import java.util.Random;

/**
 * Carves a two-tier hazard gauntlet where ceiling-mounted smash traps alternate with jumpable floor spike pits.
 */
public class SmashGauntletStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int topCeilY = Math.max(3, h / 2 - 6);
        int topFloorY = topCeilY + 4;

        int botCeilY = topFloorY + 3;
        int botFloorY = botCeilY + 4;

        carveRect(solid, 4, topCeilY + 1, w - 8, 3, w, h);
        carveRect(solid, 4, botCeilY + 1, w - 4, 3, w, h);

        carveRect(solid, 0, topCeilY + 1, 6, 3, w, h);
        carveRect(solid, 4, topCeilY + 1, 4, botFloorY - topCeilY - 1, w, h);

        populateGauntletCorridor(parent, extraNodes, 9, w - 6, topCeilY, topFloorY, rand);
        populateGauntletCorridor(parent, extraNodes, 9, w - 6, botCeilY, botFloorY, rand);
    }

    private void populateGauntletCorridor(Level parent, List<Node> extraNodes,
                                          int startX, int endX, int ceilY, int floorY, Random rand) {
        for (int x = startX; x < endX; x++) {
            int step = (x - startX) % 4;
            if (step == 0) {
                extraNodes.add(createObj(parent, x, ceilY + 1, LvlObjType.SMASH_TRAP.ordinal()));
            }
            else if (step == 2 && x + 1 < endX && rand.nextDouble() < 0.65) {
                extraNodes.add(createObj(parent, x, floorY - 1, LvlObjType.SPIKES_UP.ordinal()));
            }
        }
    }

    private Tile createObj(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.OBJECT, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }
}