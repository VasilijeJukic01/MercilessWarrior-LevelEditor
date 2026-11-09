package editor.gui.view.tab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import static editor.constants.Constants.*;

/**
 * Interactive Mini-Paint canvas allowing the user to sketch level geometry and semantic markers with adjustable brush sizes.
 */
public class LevelSketchPanel extends JPanel {

    public static final int CELL_SOLID  = 0;
    public static final int CELL_AIR    = 1;
    public static final int CELL_ENEMY  = 2;
    public static final int CELL_HAZARD = 3;
    public static final int CELL_LOOT   = 4;

    private static final Color COLOR_SOLID  = new Color(22, 34, 28);
    private static final Color COLOR_BORDER = new Color(75, 120, 90);
    private static final Color COLOR_AIR    = new Color(138, 198, 209);
    private static final Color COLOR_ENEMY  = new Color(231, 76, 60);
    private static final Color COLOR_HAZARD = new Color(230, 126, 34);
    private static final Color COLOR_LOOT   = new Color(241, 196, 15);

    private int gridW;
    private int gridH;
    private int[][] grid;

    private int activeBrushType = CELL_AIR;
    private int brushRadius = 1;
    private Point hoverCell = null;
    private final JCheckBox chkOrganicEdges = new JCheckBox("Organic Cave Edges", true);

    public LevelSketchPanel(int width, int height) {
        this.gridW = width;
        this.gridH = height;
        this.grid = new int[width][height];
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        setBackground(TOOLBAR_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        SketchCanvas canvas = new SketchCanvas();
        add(createToolbar(canvas), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(createBottomBar(canvas), BorderLayout.SOUTH);
    }

    private JPanel createToolbar(SketchCanvas canvas) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setBackground(TOOLBAR_COLOR);

        ButtonGroup toolGroup = new ButtonGroup();
        bar.add(createBrushButton("Carve Air", CELL_AIR, COLOR_AIR, true, toolGroup));
        bar.add(createBrushButton("Solid / Platform", CELL_SOLID, new Color(88, 145, 110), false, toolGroup));
        bar.add(createBrushButton("Enemy (E)", CELL_ENEMY, COLOR_ENEMY, false, toolGroup));
        bar.add(createBrushButton("Spikes (H)", CELL_HAZARD, COLOR_HAZARD, false, toolGroup));
        bar.add(createBrushButton("Loot/Totem (L)", CELL_LOOT, COLOR_LOOT, false, toolGroup));

        bar.add(Box.createHorizontalStrut(8));
        JLabel lbSize = new JLabel("Size:");
        lbSize.setForeground(Color.WHITE);
        bar.add(lbSize);

        JComboBox<String> cbSize = new JComboBox<>(new String[]{"1x1 (Platform)", "3x3 (Tunnel)", "5x5 (Room)", "7x7 (Cavern)"});
        cbSize.setSelectedIndex(1);
        cbSize.addActionListener(e -> {
            brushRadius = cbSize.getSelectedIndex();
            canvas.repaint();
        });
        bar.add(cbSize);

        return bar;
    }

    private JToggleButton createBrushButton(String text, int type, Color swatchColor, boolean selected, ButtonGroup group) {
        JToggleButton btn = new JToggleButton(text, selected);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(Color.BLACK);
        btn.setBackground(swatchColor);
        btn.addActionListener(e -> activeBrushType = type);
        group.add(btn);
        return btn;
    }

    private JPanel createBottomBar(SketchCanvas canvas) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bar.setBackground(TOOLBAR_COLOR);

        JButton btnFillSolid = new JButton("Fill All Solid");
        btnFillSolid.setFocusPainted(false);
        btnFillSolid.addActionListener(e -> {
            fillAll(CELL_SOLID);
            canvas.repaint();
        });

        JButton btnClearAir = new JButton("Carve Center Chamber");
        btnClearAir.setFocusPainted(false);
        btnClearAir.addActionListener(e -> {
            fillAll(CELL_SOLID);
            for (int x = 4; x < gridW - 4; x++) {
                for (int y = 4; y < gridH - 4; y++) {
                    grid[x][y] = CELL_AIR;
                }
            }
            canvas.repaint();
        });

        chkOrganicEdges.setOpaque(false);
        chkOrganicEdges.setForeground(Color.WHITE);
        chkOrganicEdges.setFocusPainted(false);
        chkOrganicEdges.setToolTipText("Adds subtle rocky cave variation to your brush strokes while keeping platforms flat");

        JLabel hint = new JLabel("Tip: Left-Drag = Paint Active Brush  |  Right-Drag = Quick Toggle Air/Solid");
        hint.setForeground(new Color(180, 200, 220));
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));

        bar.add(btnFillSolid);
        bar.add(btnClearAir);
        bar.add(chkOrganicEdges);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(hint);
        return bar;
    }

    public void resizeSketchGrid(int newW, int newH) {
        if (newW == gridW && newH == gridH) return;
        int[][] next = new int[newW][newH];
        for (int x = 0; x < Math.min(gridW, newW); x++) {
            System.arraycopy(grid[x], 0, next[x], 0, Math.min(gridH, newH));
        }
        this.gridW = newW;
        this.gridH = newH;
        this.grid = next;
        repaint();
    }

    public void loadFromSolidMatrix(boolean[][] solid) {
        this.gridW = solid.length;
        this.gridH = solid[0].length;
        this.grid = new int[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                grid[x][y] = solid[x][y] ? CELL_SOLID : CELL_AIR;
            }
        }
        repaint();
    }

    public void fillAll(int cellValue) {
        for (int x = 0; x < gridW; x++) {
            Arrays.fill(grid[x], cellValue);
        }
    }

    public int[][] getGridCopy() {
        int[][] copy = new int[gridW][gridH];
        for (int x = 0; x < gridW; x++) {
            System.arraycopy(grid[x], 0, copy[x], 0, gridH);
        }
        return copy;
    }

    public boolean isOrganicEdgesSelected() {
        return chkOrganicEdges.isSelected();
    }

    /**
     * Converts the user's sketch into an ASCII blueprint ('#' = solid, '.' = air, 'E' = enemy, 'H' = hazard, 'L' = loot) so Gemini AI can read the exact drawn shape and refine it.
     */
    public String toAsciiBlueprint() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                char c = switch (grid[x][y]) {
                    case CELL_SOLID -> '#';
                    case CELL_ENEMY -> 'E';
                    case CELL_HAZARD -> 'H';
                    case CELL_LOOT -> 'L';
                    default -> '.';
                };
                sb.append(c);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private class SketchCanvas extends JPanel {

        public SketchCanvas() {
            setBackground( new Color(15, 22, 28));
            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    paintAtScreen(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    paintAtScreen(e);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverCell = screenToCell(e.getX(), e.getY());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverCell = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void paintAtScreen(MouseEvent e) {
            Point pt = screenToCell(e.getX(), e.getY());
            hoverCell = pt;
            if (pt == null) return;

            int brushToApply = activeBrushType;
            if (SwingUtilities.isRightMouseButton(e)) {
                brushToApply = (activeBrushType == CELL_AIR) ? CELL_SOLID : CELL_AIR;
            }

            // Markers use a 1x1 precision brush so they don't flood the screen
            int r = (brushToApply >= CELL_ENEMY) ? 0 : brushRadius;

            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int nx = pt.x + dx;
                    int ny = pt.y + dy;
                    if (nx >= 0 && nx < gridW && ny >= 0 && ny < gridH) {
                        grid[nx][ny] = brushToApply;
                    }
                }
            }
            repaint();
        }

        private Point screenToCell(int sx, int sy) {
            Rectangle bounds = computeGridBounds();
            if (!bounds.contains(sx, sy)) return null;
            int cx = (int) (((sx - bounds.x) / (double) bounds.width) * gridW);
            int cy = (int) (((sy - bounds.y) / (double) bounds.height) * gridH);
            return new Point(Math.max(0, Math.min(gridW - 1, cx)), Math.max(0, Math.min(gridH - 1, cy)));
        }

        private Rectangle computeGridBounds() {
            int pad = 8;
            int availW = Math.max(1, getWidth() - pad * 2);
            int availH = Math.max(1, getHeight() - pad * 2);
            double cellSize = Math.min((double) availW / gridW, (double) availH / gridH);
            int drawW = (int) Math.round(cellSize * gridW);
            int drawH = (int) Math.round(cellSize * gridH);
            int startX = (getWidth() - drawW) / 2;
            int startY = (getHeight() - drawH) / 2;
            return new Rectangle(startX, startY, drawW, drawH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            Rectangle b = computeGridBounds();
            double cw = (double) b.width / gridW;
            double ch = (double) b.height / gridH;

            for (int x = 0; x < gridW; x++) {
                for (int y = 0; y < gridH; y++) {
                    int px = (int) Math.round(b.x + x * cw);
                    int py = (int) Math.round(b.y + y * ch);
                    int pw = (int) Math.ceil(cw);
                    int ph = (int) Math.ceil(ch);

                    int val = grid[x][y];
                    if (val == CELL_SOLID) {
                        boolean nearAir = (y > 0 && grid[x][y - 1] != CELL_SOLID) || (y < gridH - 1 && grid[x][y + 1] != CELL_SOLID) || (x > 0 && grid[x - 1][y] != CELL_SOLID) || (x < gridW - 1 && grid[x + 1][y] != CELL_SOLID);
                        g2d.setColor(nearAir ? COLOR_BORDER : COLOR_SOLID);
                        g2d.fillRect(px, py, pw, ph);
                    }
                    else {
                        g2d.setColor(COLOR_AIR);
                        g2d.fillRect(px, py, pw, ph);
                        if (val == CELL_ENEMY || val == CELL_HAZARD || val == CELL_LOOT) {
                            g2d.setColor(val == CELL_ENEMY ? COLOR_ENEMY : val == CELL_HAZARD ? COLOR_HAZARD : COLOR_LOOT);
                            g2d.fillRoundRect(px + 1, py + 1, Math.max(2, pw - 2), Math.max(2, ph - 2), 4, 4);
                        }
                    }

                    g2d.setColor(new Color(255, 255, 255, 22));
                    g2d.drawRect(px, py, pw, ph);
                }
            }

            // Draw brush cursor preview
            if (hoverCell != null) {
                int r = (activeBrushType >= CELL_ENEMY) ? 0 : brushRadius;
                int minX = Math.max(0, hoverCell.x - r), maxX = Math.min(gridW - 1, hoverCell.x + r);
                int minY = Math.max(0, hoverCell.y - r), maxY = Math.min(gridH - 1, hoverCell.y + r);

                int rx = (int) Math.round(b.x + minX * cw), ry = (int) Math.round(b.y + minY * ch);
                int rw = (int) Math.round((maxX - minX + 1) * cw), rh = (int) Math.round((maxY - minY + 1) * ch);

                g2d.setColor(new Color(255, 255, 255, 190));
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawRect(rx, ry, rw, rh);
            }

            g2d.setColor(new Color(116, 155, 194));
            g2d.drawRect(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
            g2d.dispose();
        }
    }
}