package editor.state;

import editor.core.Framework;
import editor.gui.view.tab.TabView;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlObjType;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;
import static editor.constants.ObjectConstants.*;

/**
 * Utility for picking visible tiles, multi-tile decorations, objects, and enemies at a canvas pixel coordinate.
 */
public final class TileHitDetector {

    private TileHitDetector() {}

    /**
     * Returns all visible tiles at the given canvas pixel coordinate in stack order (last added = first), prioritizing tiles on the currently selected Target Layer if present.
     */
    public static List<Tile> findVisibleTilesAt(int pixelX, int pixelY, TabView tabView) {
        int tileX = pixelX / TILE_SIZE;
        int tileY = pixelY / TILE_SIZE;
        int activeLayer = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_LAYER);
        List<Node> children = tabView.getLevel().getChildren();

        List<Tile> activeLayerExact = new ArrayList<>();
        List<Tile> otherExact = new ArrayList<>();
        List<Tile> activeLayerBounds = new ArrayList<>();
        List<Tile> otherBounds = new ArrayList<>();

        for (int i = children.size() - 1; i >= 0; i--) {
            if (!(children.get(i) instanceof Tile t)) continue;
            if (!isLayerVisible(t, tabView)) continue;

            if (t.getX() == tileX && t.getY() == tileY) {
                if (t.getLayer() == activeLayer) activeLayerExact.add(t);
                else otherExact.add(t);
            } else if (getSpriteBounds(t, tabView).contains(pixelX, pixelY)) {
                if (t.getLayer() == activeLayer) activeLayerBounds.add(t);
                else otherBounds.add(t);
            }
        }

        List<Tile> result = new ArrayList<>();
        result.addAll(activeLayerExact);
        result.addAll(otherExact);
        result.addAll(activeLayerBounds);
        result.addAll(otherBounds);
        return result;
    }

    /**
     * Returns the single topmost visible tile at the given canvas pixel coordinate, or null if none.
     */
    public static Tile findTopVisibleTileAt(int pixelX, int pixelY, TabView tabView) {
        List<Tile> matches = findVisibleTilesAt(pixelX, pixelY, tabView);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Rotates the topmost visible tile at (pixelX, pixelY) to the bottom of the level's child list (deque rotation), bringing the underlying tile to the top so it can be deleted or edited next.
     */
    public static Tile rotateTopTileToBack(int pixelX, int pixelY, TabView tabView) {
        List<Tile> matches = findVisibleTilesAt(pixelX, pixelY, tabView);
        if (matches.size() <= 1) return null;

        Tile top = matches.get(0);
        List<Node> children = tabView.getLevel().getChildren();
        if (children.remove(top)) {
            children.add(0, top);
            tabView.getLevel().notify(tabView.getLevel());
        }
        return matches.get(1);
    }

    /**
     * Checks whether the tile's layer is currently enabled in the SidePanel layer visibility checkboxes.
     */
    public static boolean isLayerVisible(Tile tile, TabView tabView) {
        String layersFlags = (String) tabView.getSettings().getParameter(SettingsKey.LAYERS_INFO);
        if (layersFlags == null || layersFlags.length() < 7) return true;
        if (layersFlags.charAt(6) == '1') return true;
        int layer = tile.getLayer();
        return layer >= 0 && layer < 6 && layersFlags.charAt(layer) == '1';
    }

    /**
     * Computes the rendered pixel bounding box for any tile, including multi-tile objects, enemies, and decorations.
     */
    public static Rectangle getSpriteBounds(Tile tile, TabView tabView) {
        int baseX = tile.getX() * TILE_SIZE;
        int baseY = tile.getY() * TILE_SIZE;

        switch (tile.getTileType()) {
            case ENEMY -> {
                int val = tile.getGreen();
                if (val >= 0 && val < LvlEnemyType.MAX.ordinal()) {
                    LvlEnemyType et = LvlEnemyType.values()[val];
                    return new Rectangle(
                            baseX + et.getXOffset(),
                            (tile.getY() - 1) * TILE_SIZE + et.getYOffset(),
                            et.getWid(),
                            et.getHei()
                    );
                }
            }
            case OBJECT -> {
                int val = tile.getBlue();
                if (val >= 0 && val < LvlObjType.MAX.ordinal()) {
                    LvlObjType ot = LvlObjType.values()[val];
                    return new Rectangle(
                            baseX + ot.getXOffset(),
                            baseY + ot.getYOffset(),
                            Math.max(TILE_SIZE, ot.getWid()),
                            Math.max(TILE_SIZE, ot.getHei())
                    );
                }
            }
            case DECO -> {
                int val = tile.getBlue();
                String set = (String) tabView.getSettings().getParameter(SettingsKey.TILE_SET);
                List<DecoMetadata> metaList = Framework.getInstance().getStorage().getDecoMetadataMap().get(set);
                if (metaList != null && val >= 0 && val < metaList.size()) {
                    DecoMetadata dm = metaList.get(val);
                    return new Rectangle(
                            baseX + dm.getxOffset(),
                            baseY + dm.getyOffset(),
                            dm.getWid(),
                            dm.getHei()
                    );
                }
            }
            case PLAYER -> {
                return new Rectangle(baseX + PLAYER_OFFSET_X, baseY + PLAYER_OFFSET_Y, PLAYER_WID, PLAYER_HEI);
            }
        }
        return new Rectangle(baseX, baseY, TILE_SIZE, TILE_SIZE);
    }
}