package editor.gui.controller.projectActions;

import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.TabView;
import editor.gui.view.world.WorldMapDialog;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.tree.mvc.TreeItem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Opens the visual World Map & Routing Assembler dialog.
 */
public class WorldMapAction extends AbstractEditorAction {

    public WorldMapAction() {
        super.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
        super.putValue(NAME, "World Map");
        super.putValue(SHORT_DESCRIPTION, "Open Visual World Map & Routing Assembler (Ctrl+M)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = null;
        TreeItem selected = EditorFrame.getInstance().getEditorTree().getSelectedNode();
        if (selected != null) {
            if (selected.getNode() instanceof Project p) project = p;
            else if (selected.getNode() instanceof Level l) project = (Project) l.getParent();
        }
        if (project == null) {
            TabView tab = EditorFrame.getInstance().getCurrentTab();
            if (tab != null && tab.getLevel() != null) {
                project = (Project) tab.getLevel().getParent();
            }
        }

        WorldMapDialog dialog = new WorldMapDialog(EditorFrame.getInstance(), project);
        dialog.setVisible(true);
    }
}