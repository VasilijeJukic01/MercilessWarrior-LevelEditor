package editor.gui.view.world;

import editor.model.loader.LvlTriggerType;
import editor.model.metadata.LevelMetadata;
import editor.model.metadata.RoomExitMetadata;
import editor.model.repository.components.Level;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a single room positioned on the World Map grid.
 */
public class WorldMapRoomEntry {

    public record TriggerMarker(int x, int y, LvlTriggerType type) { }

    private final String id;
    private final int width;
    private final int height;
    private int mapX;
    private int mapY;

    private final BufferedImage combinedImage;
    private final BufferedImage panel1Image;
    private final BufferedImage minimapPreview;
    private final List<TriggerMarker> triggers;
    private List<RoomExitMetadata> exits = new ArrayList<>();

    private File sourceImageFile;
    private LevelMetadata levelMetadata;
    private Level projectLevel;

    public WorldMapRoomEntry(String id, int width, int height, int mapX, int mapY, BufferedImage combinedImage, BufferedImage panel1Image, BufferedImage minimapPreview, List<TriggerMarker> triggers) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.mapX = mapX;
        this.mapY = mapY;
        this.combinedImage = combinedImage;
        this.panel1Image = panel1Image;
        this.minimapPreview = minimapPreview;
        this.triggers = triggers;
    }

    public Rectangle getBounds() {
        return new Rectangle(mapX, mapY, width, height);
    }

    public String getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMapX() {
        return mapX;
    }

    public void setMapX(int mapX) {
        this.mapX = mapX;
    }

    public int getMapY() {
        return mapY;
    }

    public void setMapY(int mapY) {
        this.mapY = mapY;
    }

    public BufferedImage getCombinedImage() {
        return combinedImage;
    }

    public BufferedImage getPanel1Image() {
        return panel1Image;
    }

    public BufferedImage getMinimapPreview() {
        return minimapPreview;
    }

    public List<TriggerMarker> getTriggers() {
        return triggers;
    }

    public List<RoomExitMetadata> getExits() {
        return exits;
    }

    public void setExits(List<RoomExitMetadata> exits) {
        this.exits = exits;
    }

    public File getSourceImageFile() {
        return sourceImageFile;
    }

    public void setSourceImageFile(File sourceImageFile) {
        this.sourceImageFile = sourceImageFile;
    }

    public LevelMetadata getLevelMetadata() {
        return levelMetadata;
    }

    public void setLevelMetadata(LevelMetadata levelMetadata) {
        this.levelMetadata = levelMetadata;
    }

    public Level getProjectLevel() {
        return projectLevel;
    }

    public void setProjectLevel(Level projectLevel) {
        this.projectLevel = projectLevel;
    }
}