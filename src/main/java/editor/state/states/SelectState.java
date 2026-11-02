package editor.state.states;

import editor.gui.view.tab.TabView;
import editor.model.repository.Node;
import editor.model.repository.components.Tile;
import editor.settings.SettingsKey;
import editor.state.State;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * State allowing the user to drag a selection marquee over any region of the level, selecting both existing tiles and the grid rectangle itself (for AI).
 */
public class SelectState implements State<TabView> {

    private Rectangle2D dragRect;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        dragRect = new Rectangle2D.Double(x, y, 0, 0);
        if (!tabView.getTabKeyListener().isShiftFlag()) {
            ((List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION)).clear();
            tabView.getSettings().updateParameter(SettingsKey.SELECTED_REGION, null);
        }
        tabView.repaint();
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        if (dragRect != null) {
            dragRect.setRect(dragRect.getX(), dragRect.getY(), x - dragRect.getX(), y - dragRect.getY());
            tabView.repaint();
        }
    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {
        if (dragRect != null) {
            List<Tile> selectedTiles = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);

            double minPxX = Math.min(dragRect.getX(), x);
            double minPxY = Math.min(dragRect.getY(), y);
            double maxPxX = Math.max(dragRect.getX(), x);
            double maxPxY = Math.max(dragRect.getY(), y);

            int levelW = tabView.getLevel().getWidth();
            int levelH = tabView.getLevel().getHeight();

            int startTileX = Math.max(0, Math.min(levelW - 1, (int) (minPxX / TILE_SIZE)));
            int startTileY = Math.max(0, Math.min(levelH - 1, (int) (minPxY / TILE_SIZE)));
            int endTileX = Math.max(0, Math.min(levelW - 1, (int) (maxPxX / TILE_SIZE)));
            int endTileY = Math.max(0, Math.min(levelH - 1, (int) (maxPxY / TILE_SIZE)));

            int tileW = endTileX - startTileX + 1;
            int tileH = endTileY - startTileY + 1;

            if (tileW >= 1 && tileH >= 1 && (maxPxX - minPxX > 8 || maxPxY - minPxY > 8)) {
                Rectangle gridRegion = new Rectangle(startTileX, startTileY, tileW, tileH);
                tabView.getSettings().updateParameter(SettingsKey.SELECTED_REGION, gridRegion);

                for (Node child : tabView.getLevel().getChildren()) {
                    Tile tile = (Tile) child;
                    if (gridRegion.contains(tile.getX(), tile.getY()) && !selectedTiles.contains(tile)) {
                        selectedTiles.add(tile);
                    }
                }
            }

            dragRect = null;
            tabView.repaint();
        }
    }

    /**
     * Renders both the active mouse-drag marquee and the persistent grid-snapped selection region.
     */
    public void renderSelection(Graphics g, TabView tabView) {
        Graphics2D g2d = (Graphics2D) g.create();

        if (dragRect != null) {
            int rx = (int) Math.min(dragRect.getX(), dragRect.getX() + dragRect.getWidth());
            int ry = (int) Math.min(dragRect.getY(), dragRect.getY() + dragRect.getHeight());
            int rw = (int) Math.abs(dragRect.getWidth());
            int rh = (int) Math.abs(dragRect.getHeight());

            g2d.setColor(new Color(0, 140, 255, 70));
            g2d.fillRect(rx, ry, rw, rh);
            g2d.setColor(new Color(0, 140, 255, 220));
            g2d.drawRect(rx, ry, rw, rh);
        }

        Rectangle region = (Rectangle) tabView.getSettings().getParameter(SettingsKey.SELECTED_REGION);
        if (region != null) {
            int px = region.x * TILE_SIZE;
            int py = region.y * TILE_SIZE;
            int pw = region.width * TILE_SIZE;
            int ph = region.height * TILE_SIZE;

            g2d.setColor(new Color(52, 152, 219, 35));
            g2d.fillRect(px, py, pw, ph);

            Stroke dashed = new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{8.0f, 6.0f}, 0.0f);
            g2d.setStroke(dashed);
            g2d.setColor(new Color(41, 128, 185));
            g2d.drawRect(px, py, pw, ph);

            String badge = region.width + "x" + region.height + " Region";
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int bw = fm.stringWidth(badge) + 12;
            int bh = 20;
            g2d.setColor(new Color(41, 128, 185, 220));
            g2d.fillRect(px, Math.max(0, py - bh), bw, bh);
            g2d.setColor(Color.WHITE);
            g2d.drawString(badge, px + 6, Math.max(0, py - bh) + 14);
        }

        g2d.dispose();
    }
}