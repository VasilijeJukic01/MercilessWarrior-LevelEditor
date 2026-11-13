package editor.state.states;

import editor.command.Command;
import editor.command.commands.TransformNodeCommand;
import editor.core.Framework;
import editor.gui.view.tab.TabView;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.settings.SettingsKey;
import editor.state.State;
import editor.state.TileHitDetector;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

/**
 * Interactive Free Transform state allowing on-canvas non-uniform stretching (scaleX/scaleY), corner scaling, and rotation of decoration sprites.
 */
public class EditState implements State<TabView> {

    public enum Handle {
        NONE,
        NW, N, NE, E, SE, S, SW, W,
        ROTATE
    }

    private static final Color GIZMO_LINE_COLOR = new Color(255, 255, 255, 235);
    private static final Color GIZMO_HANDLE_FILL = new Color(0, 162, 232);
    private static final Color GIZMO_HANDLE_HOVER = new Color(75, 210, 255);

    private Tile activeTile;
    private Handle activeHandle = Handle.NONE;
    private Handle hoveredHandle = Handle.NONE;

    private double startRotation, startScaleX, startScaleY;
    private int startLayer;
    private double dragStartAngleRad;

    private int lastClickTileX = -1, lastClickTileY = -1;
    private int cycleIndex = 0;

    @Override
    public void clickPerform(int x, int y, TabView tabView) {
        if (activeTile != null && activeTile.getTileType() == TileType.DECO) {
            Handle hit = hitTestHandles(x, y, activeTile, tabView);
            if (hit != Handle.NONE) {
                beginTransformDrag(hit, x, y, tabView);
                return;
            }
        }

        int tileX = x / TILE_SIZE;
        int tileY = y / TILE_SIZE;
        List<Tile> hits = TileHitDetector.findVisibleTilesAt(x, y, tabView);

        if (hits.isEmpty()) {
            clearSelection(tabView);
            return;
        }

        if (tileX == lastClickTileX && tileY == lastClickTileY && activeTile != null && hits.size() > 1) {
            cycleIndex = (cycleIndex + 1) % hits.size();
        } else {
            lastClickTileX = tileX;
            lastClickTileY = tileY;
            cycleIndex = 0;
        }

        activeTile = hits.get(cycleIndex % hits.size());
        List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        selection.clear();
        selection.add(activeTile);

        if (activeTile.getTileType() != TileType.DECO) {
            promptLayerOrResetPopup(activeTile, tabView);
        }

        tabView.getLevel().notify(selection);
    }

    @Override
    public void dragPerform(int x, int y, TabView tabView) {
        if (activeTile == null || activeHandle == Handle.NONE) return;

        DecoMetadata meta = getDecoMeta(activeTile, tabView);
        if (meta == null) return;

        Point2D.Double center = getTileCenter(activeTile, meta);
        boolean shiftDown = tabView.getTabKeyListener() != null && tabView.getTabKeyListener().isShiftFlag();
        if (activeHandle == Handle.ROTATE) {
            double currentAngle = Math.atan2(y - center.y, x - center.x);
            double deltaDeg = Math.toDegrees(currentAngle - dragStartAngleRad);
            double newRot = (startRotation + deltaDeg) % 360.0;
            if (newRot < 0) newRot += 360.0;
            if (shiftDown) {
                newRot = Math.round(newRot / 15.0) * 15.0;
            }
            activeTile.setRotation(newRot);
            return;
        }

        Point2D.Double localPt = toUnscaledRotatedLocal(x, y, center, activeTile.getRotation());
        double halfW = Math.max(8.0, meta.getWid() / 2.0);
        double halfH = Math.max(8.0, meta.getHei() / 2.0);

        double newScaleX = activeTile.getScaleX();
        double newScaleY = activeTile.getScaleY();

        if (activeHandle == Handle.E || activeHandle == Handle.W) {
            newScaleX = clampScale(Math.abs(localPt.x) / halfW);
        }
        else if (activeHandle == Handle.N || activeHandle == Handle.S) {
            newScaleY = clampScale(Math.abs(localPt.y) / halfH);
        }
        else {
            newScaleX = clampScale(Math.abs(localPt.x) / halfW);
            newScaleY = clampScale(Math.abs(localPt.y) / halfH);
            if (shiftDown) {
                double ratio = (startScaleY != 0) ? (startScaleX / startScaleY) : 1.0;
                double uniform = Math.max(newScaleX, newScaleY);
                newScaleX = clampScale(uniform * (ratio >= 1.0 ? 1.0 : ratio));
                newScaleY = clampScale(uniform / (ratio >= 1.0 ? ratio : 1.0));
            }
        }

        activeTile.setScaleX(roundTwoDecimals(newScaleX));
        activeTile.setScaleY(roundTwoDecimals(newScaleY));
    }

    @Override
    public void releasePerform(int x, int y, TabView tabView) {
        if (activeTile != null && activeHandle != Handle.NONE) {
            boolean changed = activeTile.getRotation() != startRotation || activeTile.getScaleX() != startScaleX || activeTile.getScaleY() != startScaleY || activeTile.getLayer() != startLayer;

            if (changed) {
                Command cmd = new TransformNodeCommand(
                        tabView.getLevel(), activeTile,
                        startRotation, startScaleX, startScaleY, startLayer,
                        activeTile.getRotation(), activeTile.getScaleX(), activeTile.getScaleY(), activeTile.getLayer()
                );
                Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
            }
        }
        activeHandle = Handle.NONE;
    }

    /**
     * Updates the hovered handle state and mouse cursor when moving over the canvas in Edit mode.
     */
    public void updateHover(int worldX, int worldY, TabView tabView) {
        if (activeTile == null || activeTile.getTileType() != TileType.DECO) {
            hoveredHandle = Handle.NONE;
            return;
        }
        Handle hit = hitTestHandles(worldX, worldY, activeTile, tabView);
        if (hit != hoveredHandle) {
            hoveredHandle = hit;
            tabView.getWorkspacePanel().setCursor(getCursorForHandle(hit));
            tabView.getWorkspacePanel().repaint();
        }
    }

    /**
     * Renders the Photoshop-style bounding box, 8 cyan handles, rotation stem, and live dimensions badge.
     */
    public void renderGizmo(Graphics g, TabView tabView) {
        if (activeTile == null || activeTile.getTileType() != TileType.DECO) return;
        if (!tabView.getLevel().getChildren().contains(activeTile)) {
            activeTile = null;
            return;
        }

        DecoMetadata meta = getDecoMeta(activeTile, tabView);
        if (meta == null) return;

        double zoom = Math.max(0.25, tabView.getScale());
        Point2D.Double center = getTileCenter(activeTile, meta);
        double halfW = (meta.getWid() * activeTile.getScaleX()) / 2.0;
        double halfH = (meta.getHei() * activeTile.getScaleY()) / 2.0;

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform oldTx = g2d.getTransform();
        g2d.translate(center.x, center.y);
        g2d.rotate(Math.toRadians(activeTile.getRotation()));

        float strokeW = (float) (1.6 / zoom);
        g2d.setStroke(new BasicStroke(strokeW));
        g2d.setColor(GIZMO_LINE_COLOR);
        g2d.draw(new Rectangle2D.Double(-halfW, -halfH, halfW * 2.0, halfH * 2.0));

        double rotStemLen = 22.0 / zoom;
        g2d.drawLine(0, (int) -halfH, 0, (int) (-halfH - rotStemLen));

        double cornerSize = 9.0 / zoom;
        double pillLong = 18.0 / zoom;
        double pillShort = 6.0 / zoom;

        drawHandleRect(g2d, -halfW, -halfH, cornerSize, cornerSize, strokeW, Handle.NW);
        drawHandleRect(g2d, halfW, -halfH, cornerSize, cornerSize, strokeW, Handle.NE);
        drawHandleRect(g2d, halfW, halfH, cornerSize, cornerSize, strokeW, Handle.SE);
        drawHandleRect(g2d, -halfW, halfH, cornerSize, cornerSize, strokeW, Handle.SW);

        drawHandleRect(g2d, 0, -halfH, pillLong, pillShort, strokeW, Handle.N);
        drawHandleRect(g2d, 0, halfH, pillLong, pillShort, strokeW, Handle.S);
        drawHandleRect(g2d, -halfW, 0, pillShort, pillLong, strokeW, Handle.W);
        drawHandleRect(g2d, halfW, 0, pillShort, pillLong, strokeW, Handle.E);

        double rotRadius = 5.5 / zoom;
        Ellipse2D.Double rotKnob = new Ellipse2D.Double(-rotRadius, -halfH - rotStemLen - rotRadius, rotRadius * 2, rotRadius * 2);
        g2d.setColor(isHandleHighlighted(Handle.ROTATE) ? GIZMO_HANDLE_HOVER : GIZMO_HANDLE_FILL);
        g2d.fill(rotKnob);
        g2d.setColor(Color.WHITE);
        g2d.draw(rotKnob);

        g2d.setTransform(oldTx);
        drawTransformBadge(g2d, center, halfH, zoom, activeTile);
        g2d.dispose();
    }

    private void drawHandleRect(Graphics2D g2d, double cx, double cy, double w, double h, float strokeW, Handle handle) {
        Rectangle2D.Double rect = new Rectangle2D.Double(cx - w / 2.0, cy - h / 2.0, w, h);
        g2d.setColor(isHandleHighlighted(handle) ? GIZMO_HANDLE_HOVER : GIZMO_HANDLE_FILL);
        g2d.fill(rect);
        g2d.setStroke(new BasicStroke(strokeW));
        g2d.setColor(Color.WHITE);
        g2d.draw(rect);
    }

    private void drawTransformBadge(Graphics2D g2d, Point2D.Double center, double halfH, double zoom, Tile tile) {
        String info = String.format("W: %.2fx  H: %.2fx  Rot: %.1f°  [L%d]", tile.getScaleX(), tile.getScaleY(), tile.getRotation(), tile.getLayer());
        Font font = new Font("SansSerif", Font.BOLD, Math.max(10, (int) Math.round(11 / zoom)));
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int padX = (int) Math.round(6 / zoom);
        int padY = (int) Math.round(4 / zoom);
        int bw = fm.stringWidth(info) + padX * 2;
        int bh = fm.getHeight() + padY;
        int bx = (int) Math.round(center.x - bw / 2.0);
        int by = (int) Math.round(center.y + halfH + (14 / zoom));

        g2d.setColor(new Color(25, 35, 48, 220));
        g2d.fillRoundRect(bx, by, bw, bh, (int) (6 / zoom), (int) (6 / zoom));
        g2d.setColor(GIZMO_HANDLE_FILL);
        g2d.drawRoundRect(bx, by, bw, bh, (int) (6 / zoom), (int) (6 / zoom));
        g2d.setColor(Color.WHITE);
        g2d.drawString(info, bx + padX, by + fm.getAscent() + padY / 2);
    }

    private boolean isHandleHighlighted(Handle h) {
        return activeHandle == h || hoveredHandle == h;
    }

    private void beginTransformDrag(Handle handle, int x, int y, TabView tabView) {
        this.activeHandle = handle;
        this.startRotation = activeTile.getRotation();
        this.startScaleX = activeTile.getScaleX();
        this.startScaleY = activeTile.getScaleY();
        this.startLayer = activeTile.getLayer();

        DecoMetadata meta = getDecoMeta(activeTile, tabView);
        if (meta != null) {
            Point2D.Double center = getTileCenter(activeTile, meta);
            this.dragStartAngleRad = Math.atan2(y - center.y, x - center.x);
        }
    }

    private Handle hitTestHandles(int worldX, int worldY, Tile tile, TabView tabView) {
        DecoMetadata meta = getDecoMeta(tile, tabView);
        if (meta == null) return Handle.NONE;

        double zoom = Math.max(0.25, tabView.getScale());
        Point2D.Double center = getTileCenter(tile, meta);
        Point2D.Double local = toUnscaledRotatedLocal(worldX, worldY, center, tile.getRotation());

        double halfW = (meta.getWid() * tile.getScaleX()) / 2.0;
        double halfH = (meta.getHei() * tile.getScaleY()) / 2.0;
        double tol = 10.0 / zoom;
        double rotStemLen = 22.0 / zoom;

        if (Point2D.distance(local.x, local.y, 0, -halfH - rotStemLen) <= tol * 1.2) return Handle.ROTATE;

        if (isNear(local.x, local.y, -halfW, -halfH, tol)) return Handle.NW;
        if (isNear(local.x, local.y, halfW, -halfH, tol)) return Handle.NE;
        if (isNear(local.x, local.y, halfW, halfH, tol)) return Handle.SE;
        if (isNear(local.x, local.y, -halfW, halfH, tol)) return Handle.SW;

        if (isNear(local.x, local.y, 0, -halfH, tol * 1.2)) return Handle.N;
        if (isNear(local.x, local.y, 0, halfH, tol * 1.2)) return Handle.S;
        if (isNear(local.x, local.y, -halfW, 0, tol * 1.2)) return Handle.W;
        if (isNear(local.x, local.y, halfW, 0, tol * 1.2)) return Handle.E;

        boolean outsideBox = Math.abs(local.x) > halfW + tol * 0.5 || Math.abs(local.y) > halfH + tol * 0.5;
        boolean nearOuterCorners = Math.abs(local.x) <= halfW + tol * 2.4 && Math.abs(local.y) <= halfH + tol * 2.4;
        if (outsideBox && nearOuterCorners) {
            return Handle.ROTATE;
        }

        return Handle.NONE;
    }

    /**
     * Opens a compact context popup to change layer or reset transform when right-clicking or double-clicking.
     */
    public void promptLayerOrResetPopup(Tile tile, TabView tabView) {
        if (tile == null) return;
        Integer[] layers = (tile.getTileType() == TileType.DECO) ? new Integer[]{0, 1, 2, 4} : new Integer[]{3, 5};
        JComboBox<Integer> cbLayer = new JComboBox<>(layers);
        cbLayer.setSelectedItem(tile.getLayer());

        JCheckBox chkReset = new JCheckBox("Reset Rotation (0°) & Scale (1.0x)");
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Assign Z-Layer:"));
        panel.add(cbLayer);
        if (tile.getTileType() == TileType.DECO) {
            panel.add(chkReset);
        }

        int res = JOptionPane.showConfirmDialog(tabView, panel, "Tile Layer & Transform Reset", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            double oldRot = tile.getRotation();
            double oldSx = tile.getScaleX();
            double oldSy = tile.getScaleY();
            int oldLayer = tile.getLayer();

            int newLayer = (Integer) cbLayer.getSelectedItem();
            double newRot = chkReset.isSelected() ? 0.0 : oldRot;
            double newSx = chkReset.isSelected() ? 1.0 : oldSx;
            double newSy = chkReset.isSelected() ? 1.0 : oldSy;

            Command cmd = new TransformNodeCommand(tabView.getLevel(), tile, oldRot, oldSx, oldSy, oldLayer, newRot, newSx, newSy, newLayer);
            Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        }
    }

    public void clearSelection(TabView tabView) {
        activeTile = null;
        activeHandle = Handle.NONE;
        hoveredHandle = Handle.NONE;
        if (tabView != null) {
            List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
            if (selection != null) selection.clear();
            tabView.getWorkspacePanel().setCursor(Cursor.getDefaultCursor());
            tabView.getWorkspacePanel().repaint();
        }
    }

    private DecoMetadata getDecoMeta(Tile tile, TabView tabView) {
        String set = (String) tabView.getSettings().getParameter(SettingsKey.TILE_SET);
        List<DecoMetadata> list = Framework.getInstance().getStorage().getDecoMetadataMap().get(set);
        int idx = tile.getBlue();
        if (list == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx);
    }

    private Point2D.Double getTileCenter(Tile tile, DecoMetadata meta) {
        boolean isDefault = tile.getRotation() == 0.0 && tile.getScaleX() == 1.0 && tile.getScaleY() == 1.0;
        double ox = isDefault ? meta.getxOffset() : 0.0;
        double oy = isDefault ? meta.getyOffset() : 0.0;
        return new Point2D.Double(
                tile.getX() * TILE_SIZE + ox + meta.getWid() / 2.0,
                tile.getY() * TILE_SIZE + oy + meta.getHei() / 2.0
        );
    }

    private Point2D.Double toUnscaledRotatedLocal(double worldX, double worldY, Point2D.Double center, double rotationDeg) {
        double dx = worldX - center.x;
        double dy = worldY - center.y;
        double rad = Math.toRadians(-rotationDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Point2D.Double(dx * cos - dy * sin, dx * sin + dy * cos);
    }

    private boolean isNear(double x, double y, double tx, double ty, double tol) {
        return Math.abs(x - tx) <= tol && Math.abs(y - ty) <= tol;
    }

    private double clampScale(double s) {
        return Math.max(0.15, Math.min(4.0, s));
    }

    private double roundTwoDecimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private Cursor getCursorForHandle(Handle h) {
        return switch (h) {
            case N, S -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case E, W -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case NW, SE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case NE, SW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case ROTATE -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    public Tile getActiveTile() {
        return activeTile;
    }
}