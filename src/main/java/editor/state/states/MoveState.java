package editor.state.states;

import editor.command.Command;
import editor.command.commands.CompositeCommand;
import editor.command.commands.MoveNodeCommand;
import editor.core.Framework;
import editor.gui.view.tab.TabView;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;
import editor.state.State;
import editor.state.TileHitDetector;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * State responsible for picking and dragging individual objects/tiles or multi-tile selections across the grid.
 */
public class MoveState implements State<TabView> {

    private Point startPoint;
    private List<Tile> selectionSnapshot;
    private List<Point> originalPositions;
    private boolean isDragging = false;

    private int lastClickTileX = -1;
    private int lastClickTileY = -1;
    private int stackCycleIndex = 0;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        List<Tile> selectedTiles = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;

        if (selectedTiles != null && selectedTiles.size() > 1 && isClickInsideSelection(x, y, tileX, tileY, selectedTiles, tabView)) {
            beginDrag(tileX, tileY, selectedTiles);
            return;
        }

        List<Tile> hits = TileHitDetector.findVisibleTilesAt(x, y, tabView);
        if (!hits.isEmpty()) {
            if (tileX == lastClickTileX && tileY == lastClickTileY) {
                stackCycleIndex = (stackCycleIndex + 1) % hits.size();
            } else {
                lastClickTileX = tileX;
                lastClickTileY = tileY;
                stackCycleIndex = 0;
            }

            Tile picked = hits.get(stackCycleIndex);
            if (selectedTiles != null) {
                selectedTiles.clear();
                selectedTiles.add(picked);
            }
            beginDrag(tileX, tileY, List.of(picked));
            tabView.repaint();
        } else {
            if (selectedTiles != null) selectedTiles.clear();
            isDragging = false;
            selectionSnapshot = null;
            originalPositions = null;
            lastClickTileX = -1;
            lastClickTileY = -1;
            tabView.repaint();
        }
    }

    private boolean isClickInsideSelection(int pixelX, int pixelY, int tileX, int tileY, List<Tile> selectedTiles, TabView tabView) {
        for (Tile tile : selectedTiles) {
            if ((tile.getX() == tileX && tile.getY() == tileY) || TileHitDetector.getSpriteBounds(tile, tabView).contains(pixelX, pixelY)) {
                return true;
            }
        }
        return false;
    }

    private void beginDrag(int tileX, int tileY, List<Tile> tilesToMove) {
        isDragging = true;
        startPoint = new Point(tileX, tileY);
        selectionSnapshot = new ArrayList<>(tilesToMove);
        originalPositions = new ArrayList<>(tilesToMove.size());
        for (Tile tile : selectionSnapshot) {
            originalPositions.add(new Point(tile.getX(), tile.getY()));
        }
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        if (!isDragging || startPoint == null || selectionSnapshot == null) return;

        int currentTileX = x / TILE_SIZE;
        int currentTileY = y / TILE_SIZE;
        int dx = currentTileX - startPoint.x;
        int dy = currentTileY - startPoint.y;

        if (dx != 0 || dy != 0) {
            lastClickTileX = -1;
            lastClickTileY = -1;
        }

        for (int i = 0; i < selectionSnapshot.size(); i++) {
            Tile tile = selectionSnapshot.get(i);
            Point originalPos = originalPositions.get(i);
            tile.setX(originalPos.x + dx);
            tile.setY(originalPos.y + dy);
        }
        tabView.repaint();
    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {
        if (!isDragging || startPoint == null || selectionSnapshot == null || originalPositions == null) {
            isDragging = false;
            return;
        }

        int finalTileX = x / TILE_SIZE;
        int finalTileY = y / TILE_SIZE;
        int dx = finalTileX - startPoint.x;
        int dy = finalTileY - startPoint.y;

        if (dx != 0 || dy != 0) {
            List<Command> moveCommands = new ArrayList<>();
            for (int i = 0; i < selectionSnapshot.size(); i++) {
                Tile tile = selectionSnapshot.get(i);
                Point originalPos = originalPositions.get(i);
                tile.setX(originalPos.x);
                tile.setY(originalPos.y);
                moveCommands.add(new MoveNodeCommand(tabView.getLevel(), tile, originalPos.x, originalPos.y, originalPos.x + dx, originalPos.y + dy));
            }
            Framework.getInstance().getGui().getCommandManager().addCommand(new CompositeCommand(moveCommands));
        }

        isDragging = false;
        startPoint = null;
        selectionSnapshot = null;
        originalPositions = null;
    }
}