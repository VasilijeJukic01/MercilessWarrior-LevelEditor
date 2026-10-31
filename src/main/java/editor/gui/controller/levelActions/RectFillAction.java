package editor.gui.controller.levelActions;

import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.TabView;

import java.awt.event.ActionEvent;

/**
 * Switches the active editor tool to the Rectangle Drag-Fill state (Shift+Drag erases).
 */
public class RectFillAction extends AbstractEditorAction {

    public RectFillAction() {
        super.putValue(NAME, "Rect Fill");
        super.putValue(SHORT_DESCRIPTION, "Drag a rectangle to fill (Hold Shift to erase)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;
        EditorFrame.getInstance().getProjectView().getStateManager().setRectFillState();
        super.reset();
    }
}