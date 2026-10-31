package editor.command.commands;

import editor.command.Command;
import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A composite undoable command that replaces the node list and dimensions of a {@link Level} atomically in a single step.
 */
public class BatchLevelCommand implements Command {

    private final Level level;
    private final List<Node> oldChildren;
    private final List<Node> newChildren;
    private final int oldWidth, oldHeight;
    private final int newWidth, newHeight;

    /**
     * Constructs a batch command using the level's current children as the previous state.
     */
    public BatchLevelCommand(Level level, List<Node> newChildren, int newWidth, int newHeight) {
        this(level, level.getChildren(), newChildren, newWidth, newHeight);
    }

    /**
     * Constructs a batch command with explicit pre-stroke and post-stroke node lists.
     */
    public BatchLevelCommand(Level level, List<Node> oldChildren, List<Node> newChildren, int newWidth, int newHeight) {
        this.level = level;
        this.oldChildren = new ArrayList<>(oldChildren);
        this.newChildren = new ArrayList<>(newChildren);
        this.oldWidth = level.getWidth();
        this.oldHeight = level.getHeight();
        this.newWidth = newWidth;
        this.newHeight = newHeight;
    }

    @Override
    public void execute() {
        if (level == null) return;
        level.getChildren().clear();
        level.getChildren().addAll(newChildren);
        level.setSize(newWidth, newHeight);
        if (level.getParent() != null) level.getParent().setChanged(true);
        level.notify(level);
    }

    @Override
    public void undo() {
        if (level == null) return;
        level.getChildren().clear();
        level.getChildren().addAll(oldChildren);
        level.setSize(oldWidth, oldHeight);
        if (level.getParent() != null) level.getParent().setChanged(true);
        level.notify(level);
    }
}