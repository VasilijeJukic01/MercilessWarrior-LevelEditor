package editor.model.metadata;

import java.awt.Color;

public class BiomePalette {
    private String walkableFill;
    private String walkableGridAlt;
    private String floorHighlight;
    private String wallContourRim;
    private String wallOuterShell;

    public BiomePalette() {}

    public BiomePalette(String walkableFill, String walkableGridAlt, String floorHighlight, String wallContourRim, String wallOuterShell) {
        this.walkableFill = walkableFill;
        this.walkableGridAlt = walkableGridAlt;
        this.floorHighlight = floorHighlight;
        this.wallContourRim = wallContourRim;
        this.wallOuterShell = wallOuterShell;
    }

    public Color getWalkableFill() {
        return applyAlpha(walkableFill != null ? walkableFill : "#3F5E4D", 254);
    }

    public Color getWalkableGridAlt() {
        return applyAlpha(walkableGridAlt != null ? walkableGridAlt : "#3A5747", 254);
    }

    public Color getFloorHighlight() {
        return applyAlpha(floorHighlight != null ? floorHighlight : "#588069", 254);
    }

    public Color getWallContourRim() {
        return applyAlpha(wallContourRim != null ? wallContourRim : "#88B89A", 255);
    }

    public Color getWallOuterShell() {
        return applyAlpha(wallOuterShell != null ? wallOuterShell : "#263B32", 255);
    }

    private Color applyAlpha(String hex, int alpha) {
        try {
            Color base = Color.decode(hex);
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
}