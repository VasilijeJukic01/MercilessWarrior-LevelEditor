package editor.state.states;

import editor.gui.view.EditorFrame;
import editor.gui.view.tab.EditDialog;
import editor.gui.view.tab.TabView;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;
import editor.state.State;
import editor.state.TileHitDetector;

import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

public class EditState implements State<TabView> {

    private int lastClickX = -1, lastClickY = -1;
    private int cycleIndex = 0;
    private List<Tile> tilesAtLastLocation = new ArrayList<>();

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;

        if (tileX != lastClickX || tileY != lastClickY) {
            lastClickX = tileX;
            lastClickY = tileY;
            cycleIndex = 0;
            tilesAtLastLocation = TileHitDetector.findVisibleTilesAt(x, y, tabView);
        } else {
            tilesAtLastLocation = TileHitDetector.findVisibleTilesAt(x, y, tabView);
            cycleIndex++;
        }

        if (tilesAtLastLocation.isEmpty()) {
            lastClickX = -1;
            lastClickY = -1;
            return;
        }

        int effectiveIndex = cycleIndex % tilesAtLastLocation.size();
        Tile tileToEdit = tilesAtLastLocation.get(effectiveIndex);
        List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);

        selection.clear();
        selection.add(tileToEdit);
        tabView.getLevel().notify(selection);
        EditDialog editDialog = new EditDialog(EditorFrame.getInstance(), true, tileToEdit);
        editDialog.setVisible(true);
        tabView.getLevel().notify(selection);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {

    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {

    }

}
