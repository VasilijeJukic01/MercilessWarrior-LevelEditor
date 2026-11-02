package editor.gui.view.tab;

import editor.gui.controller.levelActions.ExportLevelAction;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static editor.constants.Constants.*;

public class ExportDialog extends JDialog {

    private final TabView tabView;
    private final Level level;

    private final JComboBox<String> cbExportType = new JComboBox<>(new String[]{
            "Both (Full 3-Panel Engine Image)",
            "Solid & Entities only (Panel 1)",
            "Decoration tiles only (Panel 2)",
            "Triggers & NPCs only (Panel 3)"
    });
    private final JTextField tfOutputPath = new JTextField();
    private final JCheckBox chkExportMetadata = new JCheckBox("Export rotation/scale metadata (.json) alongside PNG");
    private final PixelPreviewPanel previewPanel = new PixelPreviewPanel();

    private boolean confirmed = false;

    public ExportDialog(Frame owner, boolean modal, TabView tabView) {
        super(owner, "Export Level Pixel Image (PI)", modal);
        this.tabView = tabView;
        this.level = tabView.getLevel();

        String safeName = level.getName().trim().replaceAll("\\s+", "_");
        tfOutputPath.setText(new File(safeName + ".png").getAbsolutePath());

        boolean hasTransformedDecos = hasRotatedOrScaledDecos();
        chkExportMetadata.setSelected(hasTransformedDecos);

        initUI();
        updatePreview();
    }

    private boolean hasRotatedOrScaledDecos() {
        for (Node n : level.getChildren()) {
            if (n instanceof Tile t && t.getTileType() == TileType.DECO) {
                if (t.getRotation() != 0.0 || t.getScaleX() != 1.0 || t.getScaleY() != 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void initUI() {
        setSize(560, 480);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setBackground(EXPLORER_COLOR);
        main.setBorder(new EmptyBorder(14, 16, 14, 16));

        main.add(createPreviewSection(), BorderLayout.CENTER);
        main.add(createOptionsSection(), BorderLayout.SOUTH);

        add(main, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
    }

    private JPanel createPreviewSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        JLabel lbTitle = new JLabel(String.format("LIVE PIXEL IMAGE PREVIEW (%dx%d per panel)", level.getWidth(), level.getHeight()));
        lbTitle.setForeground(new Color(221, 230, 237));
        lbTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        wrapper.add(lbTitle, BorderLayout.NORTH);

        previewPanel.setBackground(TOOLBAR_COLOR);
        previewPanel.setBorder(BorderFactory.createLineBorder(new Color(116, 155, 194, 90), 1));
        wrapper.add(previewPanel, BorderLayout.CENTER);

        return wrapper;
    }

    private JPanel createOptionsSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        JLabel lbTitle = new JLabel("EXPORT CONFIGURATION");
        lbTitle.setForeground(new Color(221, 230, 237));
        lbTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        wrapper.add(lbTitle, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(TOOLBAR_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 155, 194, 90), 1),
                new EmptyBorder(10, 12, 10, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        cbExportType.addActionListener(e -> updatePreview());

        JPanel pathPanel = new JPanel(new BorderLayout(6, 0));
        pathPanel.setOpaque(false);
        pathPanel.add(tfOutputPath, BorderLayout.CENTER);
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> browseOutputFile());
        pathPanel.add(btnBrowse, BorderLayout.EAST);

        chkExportMetadata.setOpaque(false);
        chkExportMetadata.setForeground(Color.WHITE);
        chkExportMetadata.setFocusPainted(false);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.32;
        JLabel lbMode = new JLabel("Export Mode:");
        lbMode.setForeground(Color.WHITE);
        card.add(lbMode, gbc);

        gbc.gridx = 1; gbc.weightx = 0.68;
        card.add(cbExportType, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.32;
        JLabel lbOut = new JLabel("Output PNG File:");
        lbOut.setForeground(Color.WHITE);
        card.add(lbOut, gbc);

        gbc.gridx = 1; gbc.weightx = 0.68;
        card.add(pathPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        card.add(chkExportMetadata, gbc);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(TOOLBAR_COLOR);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(116, 155, 194, 80)));

        JButton btnExport = new JButton("Export Now");
        btnExport.setBackground(BUTTON_COLOR);
        btnExport.setForeground(Color.WHITE);
        btnExport.setFocusPainted(false);
        btnExport.setFont(btnExport.getFont().deriveFont(Font.BOLD, 12f));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setFocusPainted(false);

        btnExport.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        btnCancel.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        footer.add(btnCancel);
        footer.add(btnExport);
        return footer;
    }

    private void browseOutputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Exported Pixel Image (.png)");
        chooser.setSelectedFile(new File(tfOutputPath.getText()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) path += ".png";
            tfOutputPath.setText(path);
        }
    }

    private void updatePreview() {
        String mode = (String) cbExportType.getSelectedItem();
        BufferedImage img = ExportLevelAction.buildExportImage(level, mode);
        previewPanel.setImage(img);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getSelectedExportMode() {
        return (String) cbExportType.getSelectedItem();
    }

    public File getOutputPngFile() {
        String path = tfOutputPath.getText().trim();
        if (!path.toLowerCase().endsWith(".png")) path += ".png";
        return new File(path);
    }

    public File getOutputJsonFile() {
        String pngPath = getOutputPngFile().getAbsolutePath();
        String jsonPath = pngPath.substring(0, pngPath.length() - 4) + ".json";
        return new File(jsonPath);
    }

    public boolean isExportMetadataSelected() {
        return chkExportMetadata.isSelected();
    }

    private static class PixelPreviewPanel extends JPanel {
        private BufferedImage image;

        public void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            int pad = 14;
            int availW = Math.max(1, getWidth() - pad * 2);
            int availH = Math.max(1, getHeight() - pad * 2);

            double scale = Math.min((double) availW / image.getWidth(), (double) availH / image.getHeight());
            int drawW = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int drawX = (getWidth() - drawW) / 2;
            int drawY = (getHeight() - drawH) / 2;

            g2d.drawImage(image, drawX, drawY, drawW, drawH, null);
            g2d.setColor(new Color(157, 178, 191));
            g2d.drawRect(drawX - 1, drawY - 1, drawW + 1, drawH + 1);
            g2d.dispose();
        }
    }
}