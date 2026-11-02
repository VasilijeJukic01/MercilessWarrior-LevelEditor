package editor.gui;

import editor.command.CommandManager;
import editor.core.Gui;
import editor.gui.view.EditorFrame;
import editor.gui.view.ProjectView;
import editor.gui.view.tab.TabView;

public class SwingGui implements Gui {

    private final CommandManager defaultCommandManager;

    public SwingGui() {
        this.defaultCommandManager = new CommandManager();
    }

    @Override
    public CommandManager getCommandManager() {
        TabView currentTab = EditorFrame.getInstance().getCurrentTab();
        return (currentTab != null) ? currentTab.getCommandManager() : defaultCommandManager;
    }

    @Override
    public ProjectView getProjectView() {
        return EditorFrame.getInstance().getProjectView();
    }

    @Override
    public void start() {
        EditorFrame.getInstance().setVisible(true);
    }
}
