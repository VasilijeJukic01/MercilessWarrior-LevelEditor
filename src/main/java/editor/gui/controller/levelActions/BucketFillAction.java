package editor.gui.controller.levelActions;

import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.TabView;

import java.awt.event.ActionEvent;

/**
 * Switches the active editor tool to the Flood-Fill (Bucket) state.
 */
public class BucketFillAction extends AbstractEditorAction {

    public BucketFillAction() {
        super.putValue(NAME, "Bucket");
        super.putValue(SHORT_DESCRIPTION, "Flood-fill contiguous solid tiles");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;
        EditorFrame.getInstance().getProjectView().getStateManager().setBucketState();
        super.reset();
    }
}