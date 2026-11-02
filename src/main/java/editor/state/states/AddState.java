package editor.state.states;

import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.core.Framework;
import editor.generator.AutoTileRuleEngine;
import editor.gui.view.tab.TabView;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.settings.SettingsKey;
import editor.state.State;

import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * State responsible for placing tiles on the canvas.
 * Groups continuous drag strokes into a single undoable command and supports real-time Smart Brush auto-tiling.
 */
public class AddState implements State<TabView> {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();
    private List<Node> strokeStartChildren;
    private boolean strokeModified = false;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        this.strokeStartChildren = new ArrayList<>(tabView.getLevel().getChildren());
        this.strokeModified = false;
        placeTile(x, y, tabView);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        placeTile(x, y, tabView);
    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {
        if (strokeModified && strokeStartChildren != null) {
            Level level = tabView.getLevel();
            Command cmd = new BatchLevelCommand(
                    level,
                    strokeStartChildren,
                    new ArrayList<>(level.getChildren()),
                    level.getWidth(),
                    level.getHeight()
            );
            Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        }
        strokeStartChildren = null;
        strokeModified = false;
    }

    private void placeTile(int x, int y, TabView tabView) {
        Level level = tabView.getLevel();
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        if (tabView.isTileLockedByAi(tileX, tileY)) return;
        if (tileX < 0 || tileX >= level.getWidth() || tileY < 0 || tileY >= level.getHeight()) return;

        String set = (String) tabView.getSettings().getParameter(SettingsKey.SELECTED_SET);
        int index = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_TILE);
        int layer = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_LAYER);
        boolean smartBrush = Boolean.TRUE.equals(tabView.getSettings().getParameter(SettingsKey.SMART_BRUSH));

        List<Tile> tiles = getTileSet(set, tabView);
        if (tiles == null) return;

        Tile newTile;
        if (set.equals("Solid Tiles")) newTile = getSolidTile(tabView, tiles, index, layer, tileX, tileY);
        else if (set.equals("Objects")) newTile = getObjectTile(tabView, tiles, index, tileX, tileY);
        else if (set.equals("Enemies")) newTile = getEnemyTile(tabView, tiles, index, tileX, tileY);
        else if (set.equals("Player")) newTile = getPlayerTile(tabView, tiles, tileX, tileY);
        else if (set.equals("Triggers")) newTile = getTriggerTile(tabView, tiles, index, tileX, tileY);
        else if (set.equals("NPCs")) newTile = getNpcTile(tabView, tiles, index, tileX, tileY);
        else newTile = getDecoTile(tabView, tiles, index, layer, tileX, tileY);

        if (newTile != null) {
            newTile.setLayer(layer);
            level.getChildren().add(newTile);
            if (smartBrush && newTile.getTileType() == TileType.SOLID && layer == 3) {
                autoTiler.updateRegionAutoTiles(level, tileX - 1, tileY - 1, tileX + 1, tileY + 1);
            }
            strokeModified = true;
            level.notify(level);
        }
    }

    private List<Tile> getTileSet(String name, TabView tab) {
        String set = (String) tab.getSettings().getParameter(SettingsKey.TILE_SET);
        if ("Solid Tiles".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get(set+"Tiles");
        else if ("Decorations".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get(set+"Deco");
        else if ("Objects".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get("Objects");
        else if ("Enemies".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get("Enemies");
        else if ("Player".equals(name))
            return List.of(Framework.getInstance().getStorage().getPlayerTile());
        else if ("Triggers".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get("Triggers");
        else if ("NPCs".equals(name))
            return Framework.getInstance().getStorage().getTileMap().get("NPCs");
        return null;
    }

    private boolean isFree(int tileX, int tileY, Level level, List<TileType> types) {
        for (Node node : level.getChildren()) {
            Tile tile = (Tile) node;
            if (tile.getX() == tileX && tile.getY() == tileY && types.contains(tile.getTileType())) return false;
        }
        return true;
    }

    private Tile getSolidTile(TabView tabView, List<Tile> tiles, int index, int layer, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getRed() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.SOLID, TileType.OBJECT, TileType.ENEMY, TileType.PLAYER))) return null;
                int green = (layer == 5) ? 255 : 254;
                int blue = (layer == 5) ? 255 : 254;
                return new Tile("", tabView.getLevel(), TileType.SOLID, tileX, tileY, index, green, blue);
            }
        }
        return null;
    }

    private Tile getObjectTile(TabView tabView, List<Tile> tiles, int index, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getBlue() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.SOLID, TileType.OBJECT, TileType.ENEMY, TileType.PLAYER))) return null;
                return new Tile("", tabView.getLevel(), TileType.OBJECT, tileX, tileY, 254, 254, index);
            }
        }
        return null;
    }

    private Tile getEnemyTile(TabView tabView, List<Tile> tiles, int index, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getGreen() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.SOLID, TileType.OBJECT, TileType.ENEMY, TileType.PLAYER))) return null;
                return new Tile("", tabView.getLevel(), TileType.ENEMY, tileX, tileY, 254, index, 254);
            }
        }
        return null;
    }

    private Tile getDecoTile(TabView tabView, List<Tile> tiles, int index, int layer, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getBlue() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.DECO))) return null;
                return new Tile("", tabView.getLevel(), TileType.DECO, tileX, tileY, 254, layer, index);
            }
        }
        return null;
    }

    private Tile getPlayerTile(TabView tabView, List<Tile> tiles, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getTileType() == TileType.PLAYER) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.SOLID, TileType.OBJECT, TileType.ENEMY, TileType.PLAYER))) return null;
                return new Tile("", tabView.getLevel(), TileType.PLAYER, tileX, tileY, 100, 100, 100);
            }
        }
        return null;
    }

    private Tile getTriggerTile(TabView tabView, List<Tile> tiles, int index, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getBlue() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.TRIGGER))) return null;
                return new Tile("", tabView.getLevel(), TileType.TRIGGER, tileX, tileY, 254, 254, index);
            }
        }
        return null;
    }

    private Tile getNpcTile(TabView tabView, List<Tile> tiles, int index, int tileX, int tileY) {
        for (Tile tile : tiles) {
            if (tile.getBlue() == index) {
                if (!isFree(tileX, tileY, tabView.getLevel(), List.of(TileType.NPC, TileType.SOLID, TileType.ENEMY, TileType.OBJECT, TileType.PLAYER))) return null;
                return new Tile("", tabView.getLevel(), TileType.NPC, tileX, tileY, 0, 0, index);
            }
        }
        return null;
    }
}