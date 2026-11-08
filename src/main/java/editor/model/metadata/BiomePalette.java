package editor.model.metadata;

import java.awt.Color;

public class BiomePalette {
    private String walkableFill;
    private String walkableGridAlt;
    private String floorHighlight;
    private String wallContourRim;
    private String wallOuterShell;

    public Color getWalkableFill() {
        return applyAlpha(walkableFill, 254);
    }
    public Color getWalkableGridAlt() {
        return applyAlpha(walkableGridAlt, 254);
    }
    public Color getFloorHighlight() {
        return applyAlpha(floorHighlight, 254);
    }
    public Color getWallContourRim() {
        return applyAlpha(wallContourRim, 255);
    }
    public Color getWallOuterShell() {
        return applyAlpha(wallOuterShell, 255);
    }

    private Color applyAlpha(String hex, int alpha) {
        if (hex == null) return Color.BLACK;
        Color base = Color.decode(hex);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }
}