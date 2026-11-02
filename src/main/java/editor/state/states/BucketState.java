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

import java.awt.Point;
import java.util.*;

import static editor.constants.Constants.TILE_SIZE;

/**
 * Flood-fill tool that fills contiguous empty or matching-tile regions with the currently selected solid tile and optionally auto-tiles the filled area when Smart Brush is enabled.
 */
public class BucketState implements State<TabView> {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        Level level = tabView.getLevel();
        int startX = x / TILE_SIZE;
        int startY = y / TILE_SIZE;
        int w = level.getWidth();
        int h = level.getHeight();
        if (startX < 0 || startX >= w || startY < 0 || startY >= h) return;

        String set = (String) tabView.getSettings().getParameter(SettingsKey.SELECTED_SET);
        if (!set.equals("Solid Tiles")) return;

        int selectedTileId = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_TILE);
        int selectedLayer = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_LAYER);
        boolean smartBrush = Boolean.TRUE.equals(tabView.getSettings().getParameter(SettingsKey.SMART_BRUSH));

        List<Node> oldChildren = level.snapshotChildren();
        Tile[][] grid = new Tile[w][h];
        for (Node n : level.getChildren()) {
            if (n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getLayer() == selectedLayer) {
                if (t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                    grid[t.getX()][t.getY()] = t;
                }
            }
        }

        Tile originTile = grid[startX][startY];
        int targetId = (originTile == null) ? -1 : originTile.getRed();
        if (!smartBrush && targetId == selectedTileId) return;

        boolean[][] visited = new boolean[w][h];
        Queue<Point> queue = new ArrayDeque<>();
        queue.add(new Point(startX, startY));
        visited[startX][startY] = true;

        int minX = startX, maxX = startX, minY = startY, maxY = startY;
        int green = (selectedLayer == 5) ? 255 : 254;
        int blue = (selectedLayer == 5) ? 255 : 254;

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);

            if (grid[p.x][p.y] != null) {
                level.getChildren().remove(grid[p.x][p.y]);
            }
            Tile newTile = new Tile("", level, TileType.SOLID, p.x, p.y, selectedTileId, green, blue);
            newTile.setLayer(selectedLayer);
            level.getChildren().add(newTile);

            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = p.x + d[0], ny = p.y + d[1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[nx][ny]) {
                    int neighborId = (grid[nx][ny] == null) ? -1 : grid[nx][ny].getRed();
                    if (neighborId == targetId) {
                        visited[nx][ny] = true;
                        queue.add(new Point(nx, ny));
                    }
                }
            }
        }
        if (smartBrush && selectedLayer == 3) {
            autoTiler.updateRegionAutoTiles(level, minX - 1, minY - 1, maxX + 1, maxY + 1);
        }

        Command cmd = new BatchLevelCommand(level, oldChildren, new ArrayList<>(level.getChildren()), w, h);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {

    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {

    }
}