package editor.gui.view.world;

import editor.model.metadata.RoomExitMetadata;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interactive 2D canvas view for rendering and manipulating rooms on the World Map.
 */
public class WorldMapCanvas extends JPanel {

    private static final Color CANVAS_BG = new Color(24, 32, 44);

    private final WorldMapController controller;
    private final Runnable onSelectionOrPositionChanged;
    private final Consumer<WorldMapRoomEntry> onRoomDoubleClicked;

    private double zoom = 3.0;
    private double panX = 40;
    private double panY = 40;
    private Point lastMousePt;
    private Point dragRoomOffset;
    private Point gapStartTile;

    public WorldMapCanvas(WorldMapController controller, Runnable onSelectionOrPositionChanged, Consumer<WorldMapRoomEntry> onRoomDoubleClicked) {
        this.controller = controller;
        this.onSelectionOrPositionChanged = onSelectionOrPositionChanged;
        this.onRoomDoubleClicked = onRoomDoubleClicked;

        setBackground(CANVAS_BG);
        initListeners();
    }

    private void initListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    Point tilePt = screenToTile(e.getPoint());
                    WorldMapRoomEntry clicked = findRoomAtTile(tilePt.x, tilePt.y);
                    if (clicked != null && onRoomDoubleClicked != null) {
                        onRoomDoubleClicked.accept(clicked);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                Point tilePt = screenToTile(e.getPoint());
                if (SwingUtilities.isRightMouseButton(e)) {
                    lastMousePt = e.getPoint();
                } else if (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown()) {
                    gapStartTile = tilePt;
                    controller.setMeasuredGap(new Rectangle(tilePt.x, tilePt.y, 1, 1));
                    onSelectionOrPositionChanged.run();
                    repaint();
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    WorldMapRoomEntry room = findRoomAtTile(tilePt.x, tilePt.y);
                    controller.setSelectedRoom(room);
                    if (room != null) {
                        dragRoomOffset = new Point(tilePt.x - room.getMapX(), tilePt.y - room.getMapY());
                    }
                    onSelectionOrPositionChanged.run();
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point tilePt = screenToTile(e.getPoint());
                if (SwingUtilities.isRightMouseButton(e) && lastMousePt != null) {
                    panX += e.getX() - lastMousePt.x;
                    panY += e.getY() - lastMousePt.y;
                    lastMousePt = e.getPoint();
                    repaint();
                } else if (SwingUtilities.isLeftMouseButton(e) && gapStartTile != null) {
                    int minX = Math.min(gapStartTile.x, tilePt.x);
                    int minY = Math.min(gapStartTile.y, tilePt.y);
                    int w = Math.abs(tilePt.x - gapStartTile.x) + 1;
                    int h = Math.abs(tilePt.y - gapStartTile.y) + 1;
                    controller.setMeasuredGap(new Rectangle(minX, minY, w, h));
                    onSelectionOrPositionChanged.run();
                    repaint();
                } else if (SwingUtilities.isLeftMouseButton(e) && controller.getSelectedRoom() != null && dragRoomOffset != null) {
                    WorldMapRoomEntry selected = controller.getSelectedRoom();
                    selected.setMapX(tilePt.x - dragRoomOffset.x);
                    selected.setMapY(tilePt.y - dragRoomOffset.y);
                    onSelectionOrPositionChanged.run();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePt = null;
                dragRoomOffset = null;
                gapStartTile = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = (e.getWheelRotation() < 0) ? 1.15 : (1.0 / 1.15);
                zoom = Math.max(1.0, Math.min(12.0, zoom * factor));
                repaint();
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    private Point screenToTile(Point screenPt) {
        int tx = (int) Math.floor((screenPt.x - panX) / zoom);
        int ty = (int) Math.floor((screenPt.y - panY) / zoom);
        return new Point(tx, ty);
    }

    private WorldMapRoomEntry findRoomAtTile(int tx, int ty) {
        List<WorldMapRoomEntry> list = new ArrayList<>(controller.getRooms().values());
        for (int i = list.size() - 1; i >= 0; i--) {
            WorldMapRoomEntry r = list.get(i);
            if (r.getBounds().contains(tx, ty)) return r;
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform oldTx = g2d.getTransform();
        g2d.translate(panX, panY);
        g2d.scale(zoom, zoom);

        WorldMapRoomEntry selectedRoom = controller.getSelectedRoom();

        for (WorldMapRoomEntry r : controller.getRooms().values()) {
            g2d.drawImage(r.getMinimapPreview(), r.getMapX(), r.getMapY(), r.getWidth(), r.getHeight(), null);

            for (WorldMapRoomEntry.TriggerMarker tm : r.getTriggers()) {
                g2d.setColor(tm.type().getColor());
                g2d.fillRect(r.getMapX() + tm.x(), r.getMapY() + tm.y(), 1, 1);
            }

            g2d.setStroke(new BasicStroke((float) (1.5 / zoom)));
            g2d.setColor(r == selectedRoom ? new Color(241, 196, 15) : new Color(41, 128, 185, 200));
            g2d.drawRect(r.getMapX(), r.getMapY(), r.getWidth(), r.getHeight());
        }

        List<WorldMapRoomEntry> list = new ArrayList<>(controller.getRooms().values());
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Rectangle inter = list.get(i).getBounds().intersection(list.get(j).getBounds());
                if (!inter.isEmpty()) {
                    g2d.setColor(new Color(231, 76, 60, 165));
                    g2d.fillRect(inter.x, inter.y, inter.width, inter.height);
                }
            }
        }

        Rectangle measuredGap = controller.getMeasuredGap();
        if (measuredGap != null) {
            g2d.setColor(new Color(46, 204, 113, 65));
            g2d.fillRect(measuredGap.x, measuredGap.y, measuredGap.width, measuredGap.height);
            g2d.setStroke(new BasicStroke((float) (2.0 / zoom)));
            g2d.setColor(new Color(46, 204, 113));
            g2d.drawRect(measuredGap.x, measuredGap.y, measuredGap.width, measuredGap.height);
        }

        g2d.setTransform(oldTx);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        for (WorldMapRoomEntry r : controller.getRooms().values()) {
            int sx = (int) (panX + r.getMapX() * zoom);
            int sy = (int) (panY + r.getMapY() * zoom);
            String label = r.getId() + " (" + r.getWidth() + "x" + r.getHeight() + ")";
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(sx + 2, sy + 2, g2d.getFontMetrics().stringWidth(label) + 8, 18, 6, 6);
            g2d.setColor(r == selectedRoom ? new Color(241, 196, 15) : Color.WHITE);
            g2d.drawString(label, sx + 6, sy + 15);
        }

        if (selectedRoom != null) {
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.setColor(new Color(241, 196, 15, 220));
            int x1 = (int) (panX + (selectedRoom.getMapX() + selectedRoom.getWidth() / 2.0) * zoom);
            int y1 = (int) (panY + (selectedRoom.getMapY() + selectedRoom.getHeight() / 2.0) * zoom);
            for (RoomExitMetadata ex : selectedRoom.getExits()) {
                WorldMapRoomEntry target = controller.getRooms().get(ex.getTargetLevel());
                if (target != null) {
                    int x2 = (int) (panX + (target.getMapX() + target.getWidth() / 2.0) * zoom);
                    int y2 = (int) (panY + (target.getMapY() + target.getHeight() / 2.0) * zoom);
                    g2d.drawLine(x1, y1, x2, y2);
                    g2d.fillOval(x2 - 4, y2 - 4, 8, 8);
                }
            }
        }

        g2d.dispose();
    }
}