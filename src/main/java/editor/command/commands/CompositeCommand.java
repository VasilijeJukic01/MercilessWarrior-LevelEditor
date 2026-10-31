package editor.command.commands;

import editor.command.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups multiple {@link Command} instances into a single atomic undo/redo operation.
 */
public class CompositeCommand implements Command {

    private final List<Command> commands;

    public CompositeCommand(List<Command> commands) {
        this.commands = new ArrayList<>(commands);
    }

    @Override
    public void execute() {
        for (Command cmd : commands) {
            cmd.execute();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }
}