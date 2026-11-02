package editor.gui.controller.levelActions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import editor.core.Framework;
import editor.gui.controller.AbstractEditorAction;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.ExportDialog;
import editor.gui.view.tab.TabView;
import editor.logger.LogType;
import editor.model.metadata.LevelMetadata;
import editor.model.metadata.ObjectMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ExportLevelAction extends AbstractEditorAction {

    public ExportLevelAction() {
        super.putValue(NAME, "Export PI");
        super.putValue(SHORT_DESCRIPTION, "Export Pixel Image & Metadata");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabView tab = EditorFrame.getInstance().getCurrentTab();
        if (tab == null) return;

        ExportDialog dialog = new ExportDialog(EditorFrame.getInstance(), true, tab);
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) return;

        File pngFile = dialog.getOutputPngFile();
        File jsonFile = dialog.getOutputJsonFile();
        String mode = dialog.getSelectedExportMode();
        boolean saveMeta = dialog.isExportMetadataSelected();

        exportLevelToFiles(tab.getLevel(), mode, saveMeta, pngFile, jsonFile);
    }

    /**
     * Builds the pixel image for the given level and export mode, writes the PNG, and optionally writes the companion rotation/scale JSON metadata file.
     */
    public static void exportLevelToFiles(Level level, String exportMode, boolean saveMetadata, File pngFile, File jsonFile) {
        try {
            BufferedImage exportImg = buildExportImage(level, exportMode);
            ImageIO.write(exportImg, "png", pngFile);
            Framework.getInstance().log("Level exported to: " + pngFile.getAbsolutePath(), LogType.NOTIFICATION);
            if (saveMetadata && jsonFile != null) saveMetadataAsJson(level, jsonFile);
        } catch (Exception ex) {
            Framework.getInstance().log("Failed to export level: " + ex.getMessage(), LogType.ERROR);
            JOptionPane.showMessageDialog(EditorFrame.getInstance(), "Failed to export level:\n" + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Generates the composite 3-panel (or single-panel) RGB pixel image for a level.
     */
    public static BufferedImage buildExportImage(Level level, String exportMode) {
        int w = level.getWidth();
        int h = level.getHeight();

        BufferedImage panel1 = createPanelImage(level, w, h, TileType.SOLID);
        BufferedImage panel2 = createPanelImage(level, w, h, TileType.DECO);
        BufferedImage panel3 = createPanelImage(level, w, h, TileType.TRIGGER);

        if (exportMode != null) {
            if (exportMode.startsWith("Solid")) return panel1;
            if (exportMode.startsWith("Decoration")) return panel2;
            if (exportMode.startsWith("Triggers")) return panel3;
        }

        BufferedImage combined = new BufferedImage(w * 3, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = combined.createGraphics();
        g2d.drawImage(panel1, 0, 0, null);
        g2d.drawImage(panel2, w, 0, null);
        g2d.drawImage(panel3, w * 2, 0, null);
        g2d.dispose();
        return combined;
    }

    private static BufferedImage createPanelImage(Level level, int width, int height, TileType panelType) {
        BufferedImage pixelImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = pixelImage.createGraphics();

        g2d.setColor(new Color(254, 254, 254));
        g2d.fillRect(0, 0, width, height);

        for (Node child : level.getChildren()) {
            Tile t = (Tile) child;
            if (t.getX() < 0 || t.getX() >= width || t.getY() < 0 || t.getY() >= height) continue;

            boolean belongsToPanel1 = (panelType == TileType.SOLID) && (t.getTileType() == TileType.SOLID || t.getTileType() == TileType.ENEMY || t.getTileType() == TileType.OBJECT || t.getTileType() == TileType.PLAYER);
            boolean belongsToPanel2 = (panelType == TileType.DECO) && (t.getTileType() == TileType.DECO);
            boolean belongsToPanel3 = (panelType == TileType.TRIGGER) && (t.getTileType() == TileType.TRIGGER || t.getTileType() == TileType.NPC);

            if (!belongsToPanel1 && !belongsToPanel2 && !belongsToPanel3) continue;

            int r = Math.max(0, Math.min(255, t.getRed()));
            int g = Math.max(0, Math.min(255, t.getGreen()));
            int b = Math.max(0, Math.min(255, t.getBlue()));
            int pixelColor = (r << 16) | (g << 8) | b;
            g2d.setColor(new Color(pixelColor));
            g2d.fillRect(t.getX(), t.getY(), 1, 1);
        }
        g2d.dispose();
        return pixelImage;
    }

    private static void saveMetadataAsJson(Level level, File targetFile) {
        LevelMetadata levelMetadata = new LevelMetadata();

        if (targetFile.exists()) {
            try (FileReader reader = new FileReader(targetFile)) {
                LevelMetadata existing = new Gson().fromJson(reader, LevelMetadata.class);
                if (existing != null) {
                    levelMetadata = existing;
                    levelMetadata.getDecorations().clear();
                }
            } catch (Exception ex) {
                Framework.getInstance().log("Failed to read existing metadata: " + ex.getMessage(), LogType.WARNING);
            }
        }

        for (Node child : level.getChildren()) {
            Tile tile = (Tile) child;
            if (tile.getTileType() == TileType.DECO && (tile.getRotation() != 0.0 || tile.getScaleX() != 1.0 || tile.getScaleY() != 1.0)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setX(tile.getX());
                metadata.setY(tile.getY());
                metadata.setLayer(tile.getLayer());
                metadata.setRotation(tile.getRotation());
                metadata.setScaleX(tile.getScaleX());
                metadata.setScaleY(tile.getScaleY());
                levelMetadata.getDecorations().add(metadata);
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(targetFile)) {
            gson.toJson(levelMetadata, writer);
            Framework.getInstance().log("Metadata saved to " + targetFile.getAbsolutePath(), LogType.NOTIFICATION);
        } catch (Exception ex) {
            Framework.getInstance().log("Failed to save metadata JSON: " + ex.getMessage(), LogType.ERROR);
        }
    }
}