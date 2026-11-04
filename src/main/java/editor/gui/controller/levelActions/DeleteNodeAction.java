package editor.gui.controller.levelActions;

import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.core.Framework;
import editor.generator.AutoTileRuleEngine;
import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.TabView;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.settings.SettingsKey;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class DeleteNodeAction extends AbstractEditorAction {

    private final AutoTileRuleEngine autoTiler = new AutoTileRuleEngine();

    public DeleteNodeAction() {
        super.putValue(NAME, "Delete");
        super.putValue(SHORT_DESCRIPTION, "Delete selected tile(s) or switch to Delete tool (Shift+Click cycles stacked tiles)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;

        List<Tile> selection = (List<Tile>) tab.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        if (selection != null && !selection.isEmpty()) {
            Level level = tab.getLevel();
            List<Node> oldChildren = level.snapshotChildren();
            boolean smartBrush = Boolean.TRUE.equals(tab.getSettings().getParameter(SettingsKey.SMART_BRUSH));

            for (Tile t : new ArrayList<>(selection)) {
                level.getChildren().remove(t);
                if (smartBrush && t.getTileType() == TileType.SOLID && t.getLayer() == 3) {
                    autoTiler.updateRegionAutoTiles(level, t.getX() - 1, t.getY() - 1, t.getX() + 1, t.getY() + 1);
                }
            }
            selection.clear();
            tab.getSettings().updateParameter(SettingsKey.SELECTED_REGION, null);

            Command cmd = new BatchLevelCommand(level, oldChildren, new ArrayList<>(level.getChildren()), level.getWidth(), level.getHeight());
            Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        }

        EditorFrame.getInstance().getProjectView().startDeleteState();
    }

}
