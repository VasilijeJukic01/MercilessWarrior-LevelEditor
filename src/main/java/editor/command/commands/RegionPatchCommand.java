package editor.command.commands;

import editor.command.Command;
import editor.generator.AutoTileRuleEngine;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Undoable command that replaces only the nodes within a specific grid {@link Rectangle} and stitches the 1-tile perimeter seam using auto-tiler.
 * Preserves all user edits made outside the region during asynchronous AI gen.
 */
public class RegionPatchCommand implements Command {

    private final Level level;
    private final Rectangle region;
    private final List<Node> removedNodes = new ArrayList<>();
    private final List<Node> addedNodes;
    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();

    /**
     * Constructs a regional patch command for the specified grid rectangle.
     *
     * @param level      The target level.
     * @param region     The grid rectangle (in tile coordinates) to patch.
     * @param addedNodes The new nodes generated for this region.
     */
    public RegionPatchCommand(Level level, Rectangle region, List<Node> addedNodes) {
        this.level = level;
        this.region = new Rectangle(region);
        this.addedNodes = new ArrayList<>(addedNodes);
    }

    @Override
    public void execute() {
        if (level == null) return;
        removedNodes.clear();
        level.getChildren().removeIf(node -> {
            if (node instanceof Tile t && region.contains(t.getX(), t.getY())) {
                removedNodes.add(t);
                return true;
            }
            return false;
        });

        level.getChildren().addAll(addedNodes);
        autoTiler.updateRegionAutoTiles(level, region.x - 1, region.y - 1, region.x + region.width, region.y + region.height);

        if (level.getParent() != null) level.getParent().setChanged(true);
        level.notify(level);
    }

    @Override
    public void undo() {
        if (level == null) return;
        level.getChildren().removeAll(addedNodes);
        level.getChildren().addAll(removedNodes);
        autoTiler.updateRegionAutoTiles(level, region.x - 1, region.y - 1, region.x + region.width, region.y + region.height);

        if (level.getParent() != null) level.getParent().setChanged(true);
        level.notify(level);
    }
}