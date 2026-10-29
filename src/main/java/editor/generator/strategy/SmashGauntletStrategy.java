package editor.generator.strategy;

import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.List;
import java.util.Random;

public class SmashGauntletStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int topFloorY = h / 2 - 1;
        int botFloorY = h - 4;

        carveRect(solid, 3, 3, w - 6, topFloorY - 3, w, h);
        carveRect(solid, 3, topFloorY + 2, w - 3, botFloorY - (topFloorY + 2), w, h);
        carveRect(solid, 3, 3, 4, botFloorY - 3, w, h);

        for (int x = 8; x < w - 7; x++) {
            extraNodes.add(createObj(parent, x, botFloorY - 1, LvlObjType.SPIKES_UP.ordinal()));
            if ((x - 8) % 3 == 0) {
                extraNodes.add(createObj(parent, x, botFloorY - 3, LvlObjType.SMASH_TRAP.ordinal()));
            }
        }
        for (int x = 8; x < w - 10; x++) {
            extraNodes.add(createObj(parent, x, topFloorY - 1, LvlObjType.SPIKES_UP.ordinal()));
            if ((x - 8) % 3 == 0) {
                extraNodes.add(createObj(parent, x, topFloorY - 3, LvlObjType.SMASH_TRAP.ordinal()));
            }
        }
    }

    private Tile createObj(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.OBJECT, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }

}