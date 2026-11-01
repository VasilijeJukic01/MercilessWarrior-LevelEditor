package editor.gui.view.tab;

import editor.settings.Settings;
import editor.settings.SettingsKey;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;

import static editor.constants.Constants.*;

/**
 * Right-hand inspector panel providing controls for layer visibility,
 * ambient fade preview, grid toggling, and the Smart Auto-Tile Brush.
 */
public class SidePanel extends JPanel {

    private final TabView tabView;
    private JCheckBox[] layerCheckBoxes;
    private final String[] layerNames = {
            "Layer 0 [Deco]",
            "Layer 1 [Deco]",
            "Layer 2 [Deco]",
            "Layer 3 [Solid]",
            "Layer 4 [Deco]",
            "Layer 5 [Solid]",
            "All Layers"
    };

    private boolean suppressEvents = false;

    public SidePanel(TabView tabView) {
        this.tabView = tabView;
        init();
    }

    private void init() {
        this.setLayout(new BorderLayout());
        this.setBackground(EXPLORER_COLOR);
        this.setPreferredSize(new Dimension(185, 0));
        this.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, TOOLBAR_COLOR));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(EXPLORER_COLOR);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        this.layerCheckBoxes = new JCheckBox[layerNames.length];
        initLayersSection(content);
        content.add(Box.createVerticalStrut(14));
        initOptionsSection(content);
        content.add(Box.createVerticalGlue());

        this.add(content, BorderLayout.NORTH);
    }

    private void initLayersSection(JPanel container) {
        container.add(createSectionHeader("LAYER VISIBILITY"));
        container.add(Box.createVerticalStrut(6));

        JPanel layersCard = createCardPanel(new GridLayout(layerNames.length, 1, 0, 4));

        for (int i = 0; i < layerNames.length; i++) {
            layerCheckBoxes[i] = createStyledCheckBox(layerNames[i]);
            if (i == layerNames.length - 1) {
                layerCheckBoxes[i].setFont(layerCheckBoxes[i].getFont().deriveFont(Font.BOLD, 12f));
                layerCheckBoxes[i].setSelected(true);
                layerCheckBoxes[i].addItemListener(this::handleAllLayersToggle);
            }
            else layerCheckBoxes[i].addItemListener(this::handleSingleLayerToggle);
            layersCard.add(layerCheckBoxes[i]);
        }

        container.add(layersCard);
    }

    private void initOptionsSection(JPanel container) {
        container.add(createSectionHeader("BRUSH & VIEW"));
        container.add(Box.createVerticalStrut(6));

        JPanel optionsCard = createCardPanel(new GridLayout(3, 1, 0, 6));

        JCheckBox chkSmartBrush = createStyledCheckBox("Smart Auto-Tile");
        chkSmartBrush.setSelected(true);
        chkSmartBrush.setToolTipText("Automatically updates 8-neighbor edges and corners on Layer 3");
        chkSmartBrush.addItemListener(e ->
                tabView.getSettings().updateParameter(SettingsKey.SMART_BRUSH, chkSmartBrush.isSelected())
        );

        JCheckBox chkGrid = createStyledCheckBox("Show Grid");
        chkGrid.setSelected(true);
        chkGrid.addItemListener(e ->
                tabView.getSettings().updateParameter(SettingsKey.GRID, chkGrid.isSelected())
        );

        JCheckBox chkFade = createStyledCheckBox("Ambient Fade");
        chkFade.setSelected(false);
        chkFade.addItemListener(this::changeFade);

        optionsCard.add(chkSmartBrush);
        optionsCard.add(chkGrid);
        optionsCard.add(chkFade);

        container.add(optionsCard);
    }

    private JLabel createSectionHeader(String title) {
        JLabel label = new JLabel(title);
        label.setForeground(new Color(221, 230, 237));
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createCardPanel(LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(TOOLBAR_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 155, 194, 90), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox chk = new JCheckBox(text);
        chk.setOpaque(false);
        chk.setForeground(Color.WHITE);
        chk.setFocusPainted(false);
        chk.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return chk;
    }

    private void handleAllLayersToggle(ItemEvent e) {
        if (suppressEvents) return;
        suppressEvents = true;
        if (e.getStateChange() == ItemEvent.SELECTED) {
            for (int i = 0; i < layerCheckBoxes.length - 1; i++) {
                layerCheckBoxes[i].setSelected(false);
            }
            tabView.getSettings().updateParameter(SettingsKey.LAYERS_INFO, "0000001");
        }
        else {
            tabView.getSettings().updateParameter(SettingsKey.LAYERS_INFO, "0000000");
        }
        suppressEvents = false;
    }

    private void handleSingleLayerToggle(ItemEvent e) {
        if (suppressEvents) return;
        suppressEvents = true;
        layerCheckBoxes[layerCheckBoxes.length - 1].setSelected(false);
        StringBuilder binaryString = new StringBuilder();
        for (int i = 0; i < layerCheckBoxes.length - 1; i++) {
            binaryString.append(layerCheckBoxes[i].isSelected() ? "1" : "0");
        }
        binaryString.append("0");
        tabView.getSettings().updateParameter(SettingsKey.LAYERS_INFO, binaryString.toString());
        suppressEvents = false;
    }

    private void changeFade(ItemEvent e) {
        Settings settings = tabView.getSettings();
        int fade = (e.getStateChange() == ItemEvent.SELECTED) ? 1 : 0;
        settings.updateParameter(SettingsKey.FADE, fade);
    }

}