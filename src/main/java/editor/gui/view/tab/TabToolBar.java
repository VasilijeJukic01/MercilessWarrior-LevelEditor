package editor.gui.view.tab;

import editor.gui.controller.ActionManager;
import editor.gui.controller.ActionType;
import editor.gui.view.EditorFrame;

import javax.swing.*;

import static editor.constants.Constants.TAB_TOOLBAR_COLOR;

/**
 * Secondary toolbar displaying level-specific editing, generation, navigation, and export actions.
 */
public class TabToolBar extends JToolBar {

    public TabToolBar() {
        super(SwingConstants.HORIZONTAL);
        super.setFloatable(false);
        this.setBackground(TAB_TOOLBAR_COLOR);

        ActionManager am = EditorFrame.getInstance().getActionManager();

        // Import / Export / Generator
        this.add(am.getAction(ActionType.IMPORT).createButton());
        this.add(am.getAction(ActionType.EXPORT).createButton());
        this.add(am.getAction(ActionType.GENERATE_LEVEL).createButton());
        this.addSeparator();

        // Drawing & Editing Tools
        this.add(am.getAction(ActionType.ADD_NODE).createButton());
        this.add(am.getAction(ActionType.DELETE_NODE).createButton());
        this.add(am.getAction(ActionType.EDIT_NODE).createButton());
        this.add(am.getAction(ActionType.BUCKET_FILL).createButton());
        this.add(am.getAction(ActionType.RECT_FILL).createButton());
        this.addSeparator();

        // Selection & Clipboard
        this.add(am.getAction(ActionType.SELECT).createButton());
        this.add(am.getAction(ActionType.MOVE).createButton());
        this.add(am.getAction(ActionType.COPY).createButton());
        this.add(am.getAction(ActionType.PASTE).createButton());
        this.addSeparator();

        // History & Zoom
        this.add(am.getAction(ActionType.UNDO).createButton());
        this.add(am.getAction(ActionType.REDO).createButton());
        this.add(am.getAction(ActionType.ZOOM_IN).createButton());
        this.add(am.getAction(ActionType.ZOOM_OUT).createButton());

        this.add(Box.createHorizontalGlue());

        // Level Configuration & Utilities
        this.add(am.getAction(ActionType.OPTIONS).createButton());
        this.add(am.getAction(ActionType.LOAD_TILESET).createButton());
        this.add(am.getAction(ActionType.BACKGROUND).createButton());
        this.add(am.getAction(ActionType.SCREENSHOT).createButton());
    }
}