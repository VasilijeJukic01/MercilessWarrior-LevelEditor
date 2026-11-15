package editor.command.commands;

import editor.command.Command;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

/**
 * Undoable command that records a free-transform operation (rotation, scaleX, scaleY, offsetX, offsetY, and layer) on a tile.
 */
public class TransformNodeCommand implements Command {

    private final Level level;
    private final Tile tile;
    private final double oldRotation, oldScaleX, oldScaleY;
    private final int oldOffsetX, oldOffsetY, oldLayer;
    private final double newRotation, newScaleX, newScaleY;
    private final int newOffsetX, newOffsetY, newLayer;

    public TransformNodeCommand(Level level, Tile tile, double oldRotation, double oldScaleX, double oldScaleY, int oldOffsetX, int oldOffsetY, int oldLayer, double newRotation, double newScaleX, double newScaleY, int newOffsetX, int newOffsetY, int newLayer) {
        this.level = level;
        this.tile = tile;
        this.oldRotation = oldRotation;
        this.oldScaleX = oldScaleX;
        this.oldScaleY = oldScaleY;
        this.oldOffsetX = oldOffsetX;
        this.oldOffsetY = oldOffsetY;
        this.oldLayer = oldLayer;
        this.newRotation = newRotation;
        this.newScaleX = newScaleX;
        this.newScaleY = newScaleY;
        this.newOffsetX = newOffsetX;
        this.newOffsetY = newOffsetY;
        this.newLayer = newLayer;
    }

    @Override
    public void execute() {
        if (tile == null || level == null) return;
        apply(newRotation, newScaleX, newScaleY, newOffsetX, newOffsetY, newLayer);
    }

    @Override
    public void undo() {
        if (tile == null || level == null) return;
        apply(oldRotation, oldScaleX, oldScaleY, oldOffsetX, oldOffsetY, oldLayer);
    }

    private void apply(double rot, double sx, double sy, int ox, int oy, int layer) {
        tile.setRotation(rot);
        tile.setScaleX(sx);
        tile.setScaleY(sy);
        tile.setOffsetX(ox);
        tile.setOffsetY(oy);
        tile.setLayer(layer);
        if (tile.getTileType() == TileType.DECO) {
            tile.setGreen(layer);
        }
        if (level.getParent() != null) {
            level.getParent().setChanged(true);
        }
        level.notify(tile);
    }
}