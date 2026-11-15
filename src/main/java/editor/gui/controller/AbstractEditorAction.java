package editor.gui.controller;

import editor.gui.view.EditorFrame;
import editor.gui.view.style.CustomButton;
import editor.gui.view.tab.TabView;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;
import editor.utils.Utils;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public abstract class AbstractEditorAction extends AbstractAction {

    public Optional<Icon> loadIcon(String file) {
        BufferedImage img = Utils.getInstance().importImage(file, -1, -1);
        return img != null ? Optional.of(new ImageIcon(img)) : Optional.empty();
    }

    private static Icon createImageIcon(URL url) {
        return new ImageIcon(url);
    }

    public JButton createButton() {
        JButton button = new JButton(this);
        button.setUI(new CustomButton());
        return button;
    }

    protected void reset() {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;

        tab.getSettings().updateParameter(SettingsKey.SELECTED_REGION, null);
        List<Tile> selection = (List<Tile>) tab.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        if (selection != null && !selection.isEmpty()) {
            selection.clear();
            tab.getLevel().notify(selection);
        }
    }

}
