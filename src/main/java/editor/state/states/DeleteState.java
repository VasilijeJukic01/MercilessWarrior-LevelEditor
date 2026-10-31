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
 * State responsible for erasing tiles from the canvas.
 * Groups continuous drag erasures into a single undoable command and re-tiles adjacent terrain when Smart Brush is active.
 */
public class DeleteState implements State<TabView> {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();
    private List<Node> strokeStartChildren;
    private boolean strokeModified = false;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        this.strokeStartChildren = new ArrayList<>(tabView.getLevel().getChildren());
        this.strokeModified = false;
        deleteTile(x, y, tabView);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        deleteTile(x, y, tabView);
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

    private void deleteTile(int x, int y, TabView tabView) {
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        Level level = tabView.getLevel();
        boolean smartBrush = Boolean.TRUE.equals(tabView.getSettings().getParameter(SettingsKey.SMART_BRUSH));

        Tile target = findTopTileAtPosition(tileX, tileY, level);
        if (target != null) {
            level.getChildren().remove(target);
            if (smartBrush && target.getTileType() == TileType.SOLID && target.getLayer() == 3) {
                autoTiler.updateRegionAutoTiles(level, tileX - 1, tileY - 1, tileX + 1, tileY + 1);
            }
            strokeModified = true;
            level.notify(level);
        }
    }

    private Tile findTopTileAtPosition(int tileX, int tileY, Level level) {
        List<Node> children = level.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof Tile c && c.getX() == tileX && c.getY() == tileY) {
                return c;
            }
        }
        return null;
    }
}