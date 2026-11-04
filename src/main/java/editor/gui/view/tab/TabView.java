package editor.gui.view.tab;

import editor.command.CommandManager;
import editor.core.Framework;
import editor.gui.controller.tabController.TabKeyListener;
import editor.gui.controller.tabController.TabMouseListener;
import editor.gui.controller.tabController.TabWheelListener;
import editor.gui.view.renderer.LevelRenderer;
import editor.gui.view.renderer.Renderer;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlNpcType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.nodeObserver.NodeSubscriber;
import editor.settings.EditorSettings;
import editor.settings.Settings;
import editor.settings.SettingsKey;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static editor.constants.Constants.*;

/**
 * Represents a single level editing tab containing the workspace canvas, layer side panel, bottom tile picker, and coordinate/tile-inspector status bar.
 */
public class TabView extends JPanel implements AdjustmentListener, NodeSubscriber {

    private Level level;
    private final Settings settings;

    private double scale = 1.0;
    private double dx = 0.0, dy = 0.0;
    private Point hoverTile = null;

    private JPanel mainPanel, workspacePanel;
    private BottomPanel bottomPanel;
    private JScrollBar hScrollBar, vScrollBar;
    private JLabel lbStatusBar;

    private final Renderer renderer;
    private TabKeyListener tabKeyListener;

    private final CommandManager commandManager = new CommandManager();
    private final List<ActiveAiZone> activeAiZones = new CopyOnWriteArrayList<>();
    private Timer aiRepaintTimer;

    public TabView(Level level) {
        this.setLayout(new BorderLayout());
        this.settings = new EditorSettings(this);
        initSettings();
        this.level = level;
        this.level.addSubscriber(this);
        initBars();
        initCentralPanels();
        initEastPanel();
        this.renderer = new LevelRenderer(this);
        initSouthPanel();
        initStatusBar();
        refreshBars();
    }

    private void initSettings() {
        this.settings.addParameter(SettingsKey.GRID, true);
        this.settings.addParameter(SettingsKey.GRID_COLOR, Color.BLUE);
        this.settings.addParameter(SettingsKey.LAYERS_INFO, "0000001");
        this.settings.addParameter(SettingsKey.FADE, 0);
        this.settings.addParameter(SettingsKey.FADE_COLOR, DEFAULT_FADE_COLOR);
        this.settings.addParameter(SettingsKey.SELECTED_SET, "Solid Tiles");
        this.settings.addParameter(SettingsKey.SELECTED_TILE, 0);
        this.settings.addParameter(SettingsKey.SELECTED_LAYER, 3);
        this.settings.addParameter(SettingsKey.EDIT_SELECTION, new ArrayList<Tile>());
        this.settings.addParameter(SettingsKey.BACKGROUND, null);
        this.settings.addParameter(SettingsKey.TILE_SET, "Forest");
        this.settings.addParameter(SettingsKey.EXPORT_TYPE, "Both");
        this.settings.addParameter(SettingsKey.SMART_BRUSH, true);
        this.settings.addParameter(SettingsKey.CAVE_BRUSH, false);
        this.settings.addParameter(SettingsKey.SELECTED_REGION, null);
    }

    private void initBars() {
        this.hScrollBar = new JScrollBar(JScrollBar.HORIZONTAL, 0, 100, 0, H_SCROLLBAR_MAX);
        this.vScrollBar = new JScrollBar(JScrollBar.VERTICAL, 0, 100, 0, V_SCROLLBAR_MAX);
        hScrollBar.addAdjustmentListener(this);
        vScrollBar.addAdjustmentListener(this);
    }

    private void initCentralPanels() {
        this.mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(hScrollBar, BorderLayout.SOUTH);
        this.workspacePanel = new Workspace();
        workspacePanel.setLayout(new BorderLayout());
        workspacePanel.setBackground(VIEW_COLOR);
        workspacePanel.setFocusable(true);
        workspacePanel.requestFocus();
        mainPanel.add(workspacePanel);
        initListeners();
    }

    private void initListeners() {
        TabMouseListener tabMouseListener = new TabMouseListener(this);
        workspacePanel.addMouseListener(tabMouseListener);
        workspacePanel.addMouseMotionListener(tabMouseListener);
        workspacePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshBars();
            }
        });

        this.tabKeyListener = new TabKeyListener();
        TabWheelListener tabWheelListener = new TabWheelListener(vScrollBar, tabKeyListener, this);
        workspacePanel.addMouseWheelListener(tabWheelListener);
        workspacePanel.addKeyListener(tabKeyListener);
    }

    private void initSouthPanel() {
        this.bottomPanel = new BottomPanel(this);
        JScrollPane scrollPane = new JScrollPane(bottomPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPanel, scrollPane);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerLocation(0.65);
        this.add(splitPane, BorderLayout.CENTER);
    }

    private void initEastPanel() {
        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(vScrollBar, BorderLayout.WEST);
        SidePanel sidePanel = new SidePanel(this);
        eastPanel.add(sidePanel, BorderLayout.EAST);
        mainPanel.add(eastPanel, BorderLayout.EAST);
    }

    private void initStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(TOOLBAR_COLOR);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        this.lbStatusBar = new JLabel();
        this.lbStatusBar.setForeground(Color.WHITE);
        this.lbStatusBar.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusPanel.add(lbStatusBar, BorderLayout.WEST);

        this.add(statusPanel, BorderLayout.SOUTH);
        updateStatusBarText();
    }

    /**
     * Updates the hovered grid cell coordinate, refreshes the status bar inspector, and repaints the ghost preview.
     */
    public void updateHoverTile(int tileX, int tileY) {
        if (hoverTile == null || hoverTile.x != tileX || hoverTile.y != tileY) {
            this.hoverTile = new Point(tileX, tileY);
            updateStatusBarText();
            workspacePanel.repaint();
        }
    }

    /**
     * Clears the hovered grid coordinate when the cursor exits the workspace canvas.
     */
    public void clearHoverTile() {
        if (hoverTile != null) {
            this.hoverTile = null;
            updateStatusBarText();
            workspacePanel.repaint();
        }
    }

    private void updateStatusBarText() {
        if (lbStatusBar == null || level == null) return;

        int zoomPct = (int) Math.round(scale * 100);
        String coordStr = (hoverTile != null) ? String.format("Grid: (%d, %d)", hoverTile.x, hoverTile.y) : "Grid: (-, -)";
        String sizeStr = String.format("Level Size: %dx%d", level.getWidth(), level.getHeight());
        String zoomStr = String.format("Zoom: %d%%", zoomPct);
        String inspectStr = buildTileInspectorString();

        lbStatusBar.setText(String.format("%s   |   %s   |   %s   |   %s", coordStr, sizeStr, zoomStr, inspectStr));
    }

    private String buildTileInspectorString() {
        if (hoverTile == null || hoverTile.x < 0 || hoverTile.x >= level.getWidth() || hoverTile.y < 0 || hoverTile.y >= level.getHeight()) {
            return "Hover: Out of bounds";
        }

        String tileset = (String) settings.getParameter(SettingsKey.TILE_SET);
        List<DecoMetadata> decoMeta = Framework.getInstance().getStorage().getDecoMetadataMap().get(tileset);
        List<String> items = new ArrayList<>();

        for (Node n : level.getChildren()) {
            if (n instanceof Tile t && t.getX() == hoverTile.x && t.getY() == hoverTile.y) {
                switch (t.getTileType()) {
                    case SOLID -> items.add("SOLID[ID:" + t.getRed() + ", L" + t.getLayer() + "]");
                    case DECO -> {
                        String decoName = (decoMeta != null && t.getBlue() >= 0 && t.getBlue() < decoMeta.size()) ? decoMeta.get(t.getBlue()).getId() : ("ID:" + t.getBlue());
                        items.add("DECO[" + decoName + ", L" + t.getLayer() + "]");
                    }
                    case OBJECT -> {
                        String objName = (t.getBlue() >= 0 && t.getBlue() < LvlObjType.MAX.ordinal()) ? LvlObjType.values()[t.getBlue()].name() : ("ID:" + t.getBlue());
                        items.add("OBJ[" + objName + "]");
                    }
                    case ENEMY -> {
                        String enemyName = (t.getGreen() >= 0 && t.getGreen() < LvlEnemyType.MAX.ordinal()) ? LvlEnemyType.values()[t.getGreen()].name() : ("ID:" + t.getGreen());
                        items.add("ENEMY[" + enemyName + "]");
                    }
                    case TRIGGER -> {
                        String trigName = (t.getBlue() >= 0 && t.getBlue() < LvlTriggerType.MAX.ordinal()) ? LvlTriggerType.values()[t.getBlue()].getLabel() : ("ID:" + t.getBlue());
                        items.add("TRIGGER[" + trigName + "]");
                    }
                    case NPC -> {
                        String npcName = (t.getBlue() >= 0 && t.getBlue() < LvlNpcType.MAX.ordinal()) ? LvlNpcType.values()[t.getBlue()].getLabel() : ("ID:" + t.getBlue());
                        items.add("NPC[" + npcName + "]");
                    }
                    case PLAYER -> items.add("PLAYER");
                }
            }
        }

        return items.isEmpty() ? "Inspect: [Empty]" : "Inspect: " + String.join("  •  ", items);
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent e) {
        JScrollBar jScrollBar = (JScrollBar) e.getSource();
        if (jScrollBar.getOrientation() == JScrollBar.HORIZONTAL) dx = e.getValue();
        else dy = e.getValue();
        this.repaint();
    }

    private void refreshBars() {
        this.vScrollBar.setMaximum((int) ((level.getHeight() * TILE_SIZE) * scale));
        this.hScrollBar.setMaximum((int) ((level.getWidth() * TILE_SIZE) * scale));
        updateStatusBarText();
        this.repaint();
    }

    public void reload() {
        this.bottomPanel.reload(true);
        this.level.notify(this);
    }

    // AI
    public void addActiveAiZone(ActiveAiZone zone) {
        activeAiZones.add(zone);
        if (aiRepaintTimer == null) {
            aiRepaintTimer = new Timer(100, e -> workspacePanel.repaint());
        }
        if (!aiRepaintTimer.isRunning()) aiRepaintTimer.start();
        workspacePanel.repaint();
    }

    public void removeActiveAiZone(ActiveAiZone zone) {
        activeAiZones.remove(zone);
        if (activeAiZones.isEmpty() && aiRepaintTimer != null) aiRepaintTimer.stop();
        workspacePanel.repaint();
    }

    public List<ActiveAiZone> getActiveAiZones() {
        return activeAiZones;
    }

    public boolean isTileLockedByAi(int tileX, int tileY) {
        for (ActiveAiZone zone : activeAiZones) {
            if (zone.region().contains(tileX, tileY)) return true;
        }
        return false;
    }

    // Getters & Setters
    public Level getLevel() {
        return level;
    }

    public Settings getSettings() {
        return settings;
    }

    public JPanel getWorkspacePanel() {
        return workspacePanel;
    }

    public BottomPanel getBottomPanel() {
        return bottomPanel;
    }

    public Point getHoverTile() {
        return hoverTile;
    }

    public double getScale() {
        return scale;
    }

    public double getDx() {
        return dx;
    }

    public double getDy() {
        return dy;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public void setDx(double dx) {
        int max_dx = hScrollBar.getMaximum() - hScrollBar.getVisibleAmount();
        this.dx = Math.max(0, Math.min(dx, max_dx));
        hScrollBar.setValue((int) this.dx);
        repaint();
    }

    public void setDy(double dy) {
        int max_dy = vScrollBar.getMaximum() - vScrollBar.getVisibleAmount();
        this.dy = Math.max(0, Math.min(dy, max_dy));
        vScrollBar.setValue((int) this.dy);
        repaint();
    }

    public void setScale(double scale) {
        this.scale = scale;
        this.vScrollBar.setMaximum((int) (level.getHeight() * TILE_SIZE * scale));
        this.hScrollBar.setMaximum((int) (level.getWidth() * TILE_SIZE * scale));
        updateStatusBarText();
        this.repaint();
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public TabKeyListener getTabKeyListener() {
        return tabKeyListener;
    }

    // Observer
    @Override
    public <T> void updateNode(T t) {
        refreshBars();
        repaint();
    }

    // Workspace Panel
    private class Workspace extends JPanel {

        public Workspace() {
            this.setDoubleBuffered(true);
        }

        protected void paintComponent(Graphics g) {
            this.requestFocus(true);
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform tx = new AffineTransform();
            tx.translate(-dx,-dy);
            tx.scale(scale, scale);
            g2.transform(tx);

            renderer.render(g);
        }

    }

}