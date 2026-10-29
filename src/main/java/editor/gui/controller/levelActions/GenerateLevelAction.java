package editor.gui.controller.levelActions;

import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.GenerateLevelDialog;
import editor.gui.view.tab.TabView;

import java.awt.event.ActionEvent;

public class GenerateLevelAction extends AbstractEditorAction {

    public GenerateLevelAction() {
        super.putValue(NAME, "Generator");
        super.putValue(SHORT_DESCRIPTION, "AI & Procedural Level Generator");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;
        GenerateLevelDialog dialog = new GenerateLevelDialog(EditorFrame.getInstance(), tab);
        dialog.setVisible(true);
    }
}