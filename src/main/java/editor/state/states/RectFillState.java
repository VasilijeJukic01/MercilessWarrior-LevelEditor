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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * State allowing the user to drag a rectangular marquee to fill an entire box of tiles at once (or erase a box when holding Shift), with automatic Smart Brush edge tiling.
 */
public class RectFillState implements State<TabView> {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();
    private Point startTile;
    private Point currentTile;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        int tx = x / TILE_SIZE;
        int ty = y / TILE_SIZE;
        this.startTile = new Point(tx, ty);
        this.currentTile = new Point(tx, ty);
        tabView.repaint();
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        if (startTile != null) {
            this.currentTile = new Point(x / TILE_SIZE, y / TILE_SIZE);
            tabView.repaint();
        }
    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {
        if (startTile == null) return;
        Level level = tabView.getLevel();
        int w = level.getWidth();
        int h = level.getHeight();

        int endX = x / TILE_SIZE;
        int endY = y / TILE_SIZE;

        int minX = Math.max(0, Math.min(startTile.x, endX));
        int maxX = Math.min(w - 1, Math.max(startTile.x, endX));
        int minY = Math.max(0, Math.min(startTile.y, endY));
        int maxY = Math.min(h - 1, Math.max(startTile.y, endY));

        startTile = null;
        currentTile = null;

        if (minX > maxX || minY > maxY) {
            tabView.repaint();
            return;
        }

        List<Node> oldChildren = level.snapshotChildren();
        int selectedTileId = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_TILE);
        int selectedLayer = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_LAYER);
        boolean smartBrush = Boolean.TRUE.equals(tabView.getSettings().getParameter(SettingsKey.SMART_BRUSH));
        boolean eraseMode = tabView.getTabKeyListener().isShiftFlag();

        int fMinX = minX, fMaxX = maxX, fMinY = minY, fMaxY = maxY;
        level.getChildren().removeIf(n -> {
            if (n instanceof Tile t && t.getX() >= fMinX && t.getX() <= fMaxX && t.getY() >= fMinY && t.getY() <= fMaxY) {
                return eraseMode || (t.getTileType() == TileType.SOLID && t.getLayer() == selectedLayer);
            }
            return false;
        });

        if (!eraseMode) {
            int green = (selectedLayer == 5) ? 255 : 254;
            int blue = (selectedLayer == 5) ? 255 : 254;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cy = minY; cy <= maxY; cy++) {
                    Tile t = new Tile("", level, TileType.SOLID, cx, cy, selectedTileId, green, blue);
                    t.setLayer(selectedLayer);
                    level.getChildren().add(t);
                }
            }
        }

        if (smartBrush && selectedLayer == 3) {
            autoTiler.updateRegionAutoTiles(level, minX - 1, minY - 1, maxX + 1, maxY + 1);
        }

        Command cmd = new BatchLevelCommand(level, oldChildren, new ArrayList<>(level.getChildren()), w, h);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
    }

    /**
     * Renders the grid-snapped rectangular fill preview marquee on the canvas.
     */
    public void renderPreview(Graphics g) {
        if (startTile != null && currentTile != null) {
            int minX = Math.min(startTile.x, currentTile.x) * TILE_SIZE;
            int minY = Math.min(startTile.y, currentTile.y) * TILE_SIZE;
            int width = (Math.abs(currentTile.x - startTile.x) + 1) * TILE_SIZE;
            int height = (Math.abs(currentTile.y - startTile.y) + 1) * TILE_SIZE;

            g.setColor(new Color(46, 204, 113, 90));
            g.fillRect(minX, minY, width, height);
            g.setColor(new Color(39, 174, 96));
            g.drawRect(minX, minY, width, height);
        }
    }
}