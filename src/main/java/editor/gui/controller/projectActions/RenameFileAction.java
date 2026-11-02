package editor.gui.controller.projectActions;

import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.ProjectOptionsDialog;
import editor.gui.view.tab.TabView;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.tree.mvc.TreeItem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class RenameFileAction extends AbstractEditorAction {

    public RenameFileAction() {
        super.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        super.putValue(NAME, "Project Options");
        super.putValue(SHORT_DESCRIPTION, "Edit Project & Level Properties (Ctrl+R)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreeItem treeItem = EditorFrame.getInstance().getEditorTree().getSelectedNode();
        Project project = null;
        Level selectedLevel = null;

        if (treeItem != null) {
            if (treeItem.getNode() instanceof Project p) project = p;
            else if (treeItem.getNode() instanceof Level l) {
                selectedLevel = l;
                project = (Project) l.getParent();
            }
        }

        if (project == null) {
            TabView currentTab = EditorFrame.getInstance().getCurrentTab();
            if (currentTab != null && currentTab.getLevel() != null) {
                selectedLevel = currentTab.getLevel();
                project = (Project) selectedLevel.getParent();
            }
        }

        if (project != null) {
            ProjectOptionsDialog dialog = new ProjectOptionsDialog(EditorFrame.getInstance(), project, selectedLevel);
            dialog.setVisible(true);
        }
        else {
            JOptionPane.showMessageDialog(EditorFrame.getInstance(), "Please select or open a Project first!", "No Project Selected", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}