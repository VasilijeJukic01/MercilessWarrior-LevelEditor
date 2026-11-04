package editor.state.states;

import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.core.Framework;
import editor.generator.AutoTileRuleEngine;
import editor.gui.view.tab.TabView;
import editor.logger.LogType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.settings.SettingsKey;
import editor.state.State;
import editor.state.TileHitDetector;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * State responsible for erasing tiles from the canvas.
 * Supports Shift+Click to cycle stacked tiles (sending the top tile to the back of the stack) so underlying tiles can be deleted without losing the tile on top.
 */
public class DeleteState implements State<TabView> {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();
    private List<Node> strokeStartChildren;
    private boolean strokeModified = false;
    private Point lastDeletedCell = null;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        if (tabView.getTabKeyListener().isShiftFlag()) {
            Tile nextUp = TileHitDetector.rotateTopTileToBack(x, y, tabView);
            if (nextUp != null) {
                List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
                if (selection != null) {
                    selection.clear();
                    selection.add(nextUp);
                }
                Framework.getInstance().log("Cycled stack: brought " + nextUp.getTileType() + " (Layer " + nextUp.getLayer() + ") to top for deletion.", LogType.INFORMATION);
            }
            return;
        }

        this.strokeStartChildren = tabView.getLevel().snapshotChildren();
        this.strokeModified = false;
        this.lastDeletedCell = new Point(x / TILE_SIZE, y / TILE_SIZE);
        deleteTopTileAt(x, y, tabView);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        if (tabView.getTabKeyListener().isShiftFlag()) return;
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        if (lastDeletedCell != null && lastDeletedCell.x == tileX && lastDeletedCell.y == tileY) {
            return;
        }
        this.lastDeletedCell = new Point(tileX, tileY);
        deleteTopTileAt(x, y, tabView);
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
        lastDeletedCell = null;
    }

    private void deleteTopTileAt(int x, int y, TabView tabView) {
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        if (tabView.isTileLockedByAi(tileX, tileY)) return;

        Level level = tabView.getLevel();
        boolean smartBrush = Boolean.TRUE.equals(tabView.getSettings().getParameter(SettingsKey.SMART_BRUSH));

        List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        Tile target = null;
        if (selection != null && selection.size() == 1 && TileHitDetector.getSpriteBounds(selection.get(0), tabView).contains(x, y)) {
            target = selection.get(0);
            selection.clear();
        } else {
            target = TileHitDetector.findTopVisibleTileAt(x, y, tabView);
        }

        if (target != null) {
            int targetX = target.getX();
            int targetY = target.getY();
            level.getChildren().remove(target);
            if (smartBrush && target.getTileType() == TileType.SOLID) {
                if (target.getLayer() == 3) {
                    autoTiler.updateRegionAutoTiles(level, targetX - 1, targetY - 1, targetX + 1, targetY + 1);
                }
                else if (target.getLayer() == 5) {
                    autoTiler.updateLayer5AutoTiles(level, targetX - 1, targetY - 1, targetX + 1, targetY + 1);
                }
            }
            strokeModified = true;
            level.notify(level);
        }
    }
}