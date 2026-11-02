package editor.gui.view.tab;

import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.core.Framework;
import editor.logger.LogType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.*;

/**
 * Options dialog supporting undoable resizing, content shifting (X/Y offset), live color swatches, ambient fade opacity control, and layer-specific clearing.
 */
public class OptionsDialog extends JDialog {

    private final TabView tab;
    private final Level level;

    private final JSpinner spWid;
    private final JSpinner spHei;
    private final JSpinner spShiftX;
    private final JSpinner spShiftY;
    private final JSlider slFadeAlpha;
    private final JCheckBox chkClearBackground;

    private Color selectedGridColor;
    private Color selectedFadeColor;

    private final JPanel gridColorPreview = new JPanel();
    private final JPanel fadeColorPreview = new JPanel();

    public OptionsDialog(Frame owner, boolean modal, TabView tab) {
        super(owner, "Level Options & Utilities", modal);
        this.tab = tab;
        this.level = tab.getLevel();

        this.spWid = new JSpinner(new SpinnerNumberModel(level.getWidth(), 10, 250, 1));
        this.spHei = new JSpinner(new SpinnerNumberModel(level.getHeight(), 10, 250, 1));
        this.spShiftX = new JSpinner(new SpinnerNumberModel(0, -100, 100, 1));
        this.spShiftY = new JSpinner(new SpinnerNumberModel(0, -100, 100, 1));
        this.chkClearBackground = new JCheckBox("Remove custom background image");

        this.selectedGridColor = (Color) tab.getSettings().getParameter(SettingsKey.GRID_COLOR);
        this.selectedFadeColor = (Color) tab.getSettings().getParameter(SettingsKey.FADE_COLOR);

        int initialAlphaPct = (int) Math.round((selectedFadeColor.getAlpha() / 255.0) * 100);
        this.slFadeAlpha = new JSlider(0, 100, initialAlphaPct);

        initUI();
    }

    private void initUI() {
        setSize(460, 540);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(EXPLORER_COLOR);
        mainContent.setBorder(new EmptyBorder(14, 16, 14, 16));

        mainContent.add(createDimensionsSection());
        mainContent.add(Box.createVerticalStrut(12));
        mainContent.add(createVisualsSection());
        mainContent.add(Box.createVerticalStrut(12));
        mainContent.add(createUtilitiesSection());

        add(mainContent, BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createDimensionsSection() {
        JPanel card = createCard("DIMENSIONS & SHIFT CONTENT");
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        addFormRow(card, gbc, 0, "Level Width (tiles):", spWid);
        addFormRow(card, gbc, 1, "Level Height (tiles):", spHei);
        addFormRow(card, gbc, 2, "Shift All Tiles X (+/-):", spShiftX);
        addFormRow(card, gbc, 3, "Shift All Tiles Y (+/-):", spShiftY);

        return card;
    }

    private JPanel createVisualsSection() {
        JPanel card = createCard("GRID, FADE & BACKGROUND");
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        JButton btnGrid = new JButton("Choose...");
        setupColorSwatch(gridColorPreview, selectedGridColor);
        btnGrid.addActionListener(e -> pickColor(selectedGridColor, "Select Grid Color", c -> {
            selectedGridColor = c;
            gridColorPreview.setBackground(c);
        }));

        JButton btnFade = new JButton("Choose...");
        setupColorSwatch(fadeColorPreview, new Color(selectedFadeColor.getRed(), selectedFadeColor.getGreen(), selectedFadeColor.getBlue()));
        btnFade.addActionListener(e -> pickColor(selectedFadeColor, "Select Fade Color", c -> {
            selectedFadeColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), selectedFadeColor.getAlpha());
            fadeColorPreview.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue()));
        }));

        slFadeAlpha.setOpaque(false);
        slFadeAlpha.setForeground(Color.WHITE);
        slFadeAlpha.setMajorTickSpacing(25);
        slFadeAlpha.setPaintTicks(true);

        chkClearBackground.setOpaque(false);
        chkClearBackground.setForeground(Color.WHITE);
        chkClearBackground.setFocusPainted(false);
        chkClearBackground.setEnabled(tab.getSettings().getParameter(SettingsKey.BACKGROUND) != null);

        addFormRow(card, gbc, 0, "Grid Line Color:", createColorRow(gridColorPreview, btnGrid));
        addFormRow(card, gbc, 1, "Ambient Fade Color:", createColorRow(fadeColorPreview, btnFade));
        addFormRow(card, gbc, 2, "Fade Opacity (%):", slFadeAlpha);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        card.add(chkClearBackground, gbc);

        return card;
    }

    private JPanel createUtilitiesSection() {
        JPanel card = createCard("QUICK LAYER UTILITIES (UNDOABLE)");
        card.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));

        String[] layerOptions = {
                "Layer 0 [Deco]", "Layer 1 [Deco]", "Layer 2 [Deco]",
                "Layer 3 [Solid]", "Layer 4 [Deco]", "Layer 5 [Entities/Solid]"
        };
        JComboBox<String> cbClearLayer = new JComboBox<>(layerOptions);
        JButton btnClearLayer = new JButton("Clear Layer");
        JButton btnClearAll = new JButton("Clear All");
        btnClearAll.setBackground(new Color(192, 57, 43));
        btnClearAll.setForeground(Color.WHITE);

        btnClearLayer.addActionListener(e -> {
            int targetLayer = cbClearLayer.getSelectedIndex();
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove all tiles on Layer " + targetLayer + "?",
                    "Confirm Clear Layer", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                List<Node> remaining = new ArrayList<>();
                for (Node n : level.getChildren()) {
                    if (n instanceof Tile t && t.getLayer() != targetLayer) {
                        remaining.add(t);
                    }
                }
                executeUndoableChange(remaining, level.getWidth(), level.getHeight(), "Cleared Layer " + targetLayer);
            }
        });

        btnClearAll.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear the ENTIRE level? (Undoable with Ctrl+Z)",
                    "Confirm Clear Level", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                executeUndoableChange(new ArrayList<>(), level.getWidth(), level.getHeight(), "Cleared entire level");
            }
        });

        card.add(cbClearLayer);
        card.add(btnClearLayer);
        card.add(btnClearAll);
        return card;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(TOOLBAR_COLOR);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(116, 155, 194, 80)));

        JButton btnApply = new JButton("Apply & Close");
        btnApply.setBackground(BUTTON_COLOR);
        btnApply.setForeground(Color.WHITE);
        btnApply.setFocusPainted(false);
        btnApply.setFont(btnApply.getFont().deriveFont(Font.BOLD, 12f));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setFocusPainted(false);

        btnApply.addActionListener(e -> applyAndClose());
        btnCancel.addActionListener(e -> dispose());

        footer.add(btnCancel);
        footer.add(btnApply);
        return footer;
    }

    private void applyAndClose() {
        int newWid = (Integer) spWid.getValue();
        int newHei = (Integer) spHei.getValue();
        int shiftX = (Integer) spShiftX.getValue();
        int shiftY = (Integer) spShiftY.getValue();

        int alpha = (int) Math.round((slFadeAlpha.getValue() / 100.0) * 255);
        Color finalFadeColor = new Color(
                selectedFadeColor.getRed(),
                selectedFadeColor.getGreen(),
                selectedFadeColor.getBlue(),
                Math.max(0, Math.min(255, alpha))
        );

        tab.getSettings().updateParameter(SettingsKey.GRID_COLOR, selectedGridColor);
        tab.getSettings().updateParameter(SettingsKey.FADE_COLOR, finalFadeColor);
        if (chkClearBackground.isSelected()) {
            tab.getSettings().updateParameter(SettingsKey.BACKGROUND, null);
        }

        if (newWid != level.getWidth() || newHei != level.getHeight() || shiftX != 0 || shiftY != 0) {
            List<Node> updatedNodes = new ArrayList<>();
            for (Node n : level.getChildren()) {
                if (n instanceof Tile orig) {
                    int nx = orig.getX() + shiftX;
                    int ny = orig.getY() + shiftY;
                    if (nx >= 0 && nx < newWid && ny >= 0 && ny < newHei) {
                        Tile copy = new Tile(orig.getName(), level, orig.getTileType(), nx, ny, orig.getRed(), orig.getGreen(), orig.getBlue());
                        copy.setLayer(orig.getLayer());
                        copy.setRotation(orig.getRotation());
                        copy.setScaleX(orig.getScaleX());
                        copy.setScaleY(orig.getScaleY());
                        updatedNodes.add(copy);
                    }
                }
            }
            executeUndoableChange(updatedNodes, newWid, newHei, String.format("Updated level size to %dx%d (shift %+d, %+d)", newWid, newHei, shiftX, shiftY));
        }

        dispose();
    }

    private void executeUndoableChange(List<Node> newNodes, int w, int h, String logMsg) {
        Command cmd = new BatchLevelCommand(level, new ArrayList<>(level.getChildren()), newNodes, w, h);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        Framework.getInstance().log(logMsg, LogType.NOTIFICATION);
    }

    private JPanel createCard(String title) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        JLabel lbTitle = new JLabel(title);
        lbTitle.setForeground(new Color(221, 230, 237));
        lbTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        wrapper.add(lbTitle, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setBackground(TOOLBAR_COLOR);
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 155, 194, 90), 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        wrapper.add(body, BorderLayout.CENTER);
        return body;
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.55;
        gbc.gridwidth = 1;
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.45;
        panel.add(field, gbc);
    }

    private void setupColorSwatch(JPanel swatch, Color color) {
        swatch.setPreferredSize(new Dimension(28, 22));
        swatch.setBackground(color);
        swatch.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
    }

    private JPanel createColorRow(JPanel swatch, JButton button) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        row.setOpaque(false);
        row.add(swatch);
        row.add(button);
        return row;
    }

    private void pickColor(Color initial, String title, java.util.function.Consumer<Color> onPicked) {
        Color chosen = JColorChooser.showDialog(this, title, initial);
        if (chosen != null) onPicked.accept(chosen);
    }
}