package editor.gui.view.world;

import editor.core.Framework;
import editor.gui.view.EditorFrame;
import editor.gui.view.tab.TabView;
import editor.gui.view.tab.TabbedPane;
import editor.logger.LogType;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlNpcType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.metadata.LevelMetadata;
import editor.model.metadata.ObjectMetadata;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.repository.components.ProjectExplorer;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.model.tree.EditorTree;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static editor.constants.Constants.DECO_NUM;

/**
 * Bridges WorldMapRoomEntry models with the main editor's Project, Level, and TabbedPane views.
 */
public class WorldMapTabBridge {

    public static final String PREVIEW_LEVEL_NAME = "WORLD_PREVIEW";

    /**
     * Verifies that an active project node exists in the tree. If none exists, creates a fresh one.
     */
    public Project ensureProjectExists(Project activeProject) {
        if (activeProject != null) return activeProject;
        ProjectExplorer explorer = Framework.getInstance().getRepository().getProjectExplorer();
        Project resolved;
        if (!explorer.getChildren().isEmpty() && explorer.getChildren().get(0) instanceof Project existing) resolved = existing;
        else {
            resolved = new Project("World Project", explorer);
            explorer.addChild(resolved);
            EditorFrame.getInstance().getEditorTree().deserialize(resolved);
        }
        TabbedPane tabbedPane = EditorFrame.getInstance().getProjectView().getTabbedPane();
        tabbedPane.setProject(resolved);
        EditorFrame.getInstance().getProjectView().getProjectViewTop().setProject(resolved);
        return resolved;
    }

    /**
     * Attaches a newly generated level to the active project tree and immediately opens a Swing tab for it.
     */
    public void addLevelToProjectAndOpenTab(Project project, Level level) {
        project.addChild(level);
        EditorTree tree = (EditorTree) EditorFrame.getInstance().getEditorTree();
        tree.refreshView();
        focusOrOpenLevelTab(project, level);
    }

    /**
     * Brings an existing level tab to the front. If the tab doesn't exist yet, it instantiates a new TabView.
     */
    public void focusOrOpenLevelTab(Project project, Level level) {
        TabbedPane tabbedPane = EditorFrame.getInstance().getProjectView().getTabbedPane();
        tabbedPane.setProject(project);
        for (Component comp : tabbedPane.getComponents()) {
            if (comp instanceof TabView tv && tv.getLevel() == level) {
                tabbedPane.setSelectedComponent(tv);
                return;
            }
        }
        TabView newTab = new TabView(level);
        tabbedPane.addTab(level.getName(), newTab);
        tabbedPane.setSelectedComponent(newTab);
    }

    /**
     * Opens a specific room from the map canvas directly into a full-featured level editing tab.
     * Reconstructs the Node tree from the compressed 3-panel image data if it hasn't been opened yet.
     */
    public void openRoomInEditorTab(Project project, WorldMapRoomEntry room) {
        if (room == null) return;
        Level targetLevel = room.getProjectLevel();
        if (targetLevel == null) {
            targetLevel = new Level(room.getId(), project, room.getWidth(), room.getHeight());
            populateLevelNodesFromCombinedImage(room.getCombinedImage(), room.getLevelMetadata(), targetLevel, 0, 0);
            room.setProjectLevel(targetLevel);
            addLevelToProjectAndOpenTab(project, targetLevel);
        } else {
            focusOrOpenLevelTab(project, targetLevel);
        }
        Framework.getInstance().log("Opened room '" + room.getId() + "' in editor tab.", LogType.NOTIFICATION);
    }

    /**
     * Stitches every room on the map into one massive composite level and opens it in an editor tab.
     * Allows inspecting all tunnel alignments simultaneously at full resolution.
     */
    public Level buildAndOpenMegaWorldPreviewTab(Project project, Collection<WorldMapRoomEntry> rooms) {
        if (rooms.isEmpty()) return null;
        project.getChildren().removeIf(n -> n instanceof Level && PREVIEW_LEVEL_NAME.equals(n.getName()));

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (WorldMapRoomEntry r : rooms) {
            if (PREVIEW_LEVEL_NAME.equals(r.getId())) continue;
            minX = Math.min(minX, r.getMapX());
            minY = Math.min(minY, r.getMapY());
            maxX = Math.max(maxX, r.getMapX() + r.getWidth());
            maxY = Math.max(maxY, r.getMapY() + r.getHeight());
        }

        int shiftX = -minX;
        int shiftY = -minY;
        int totalW = Math.max(26, maxX - minX);
        int totalH = Math.max(14, maxY - minY);

        Level worldPreview = new Level(PREVIEW_LEVEL_NAME, project, totalW, totalH);
        for (WorldMapRoomEntry r : rooms) {
            if (PREVIEW_LEVEL_NAME.equals(r.getId())) continue;
            int offsetX = r.getMapX() + shiftX;
            int offsetY = r.getMapY() + shiftY;
            populateLevelNodesFromCombinedImage(r.getCombinedImage(), r.getLevelMetadata(), worldPreview, offsetX, offsetY);
        }

        addLevelToProjectAndOpenTab(project, worldPreview);
        Framework.getInstance().log("Created full WORLD_PREVIEW level (" + totalW + "x" + totalH + " tiles)!", LogType.NOTIFICATION);
        return worldPreview;
    }

    /**
     * Converts a 3-panel RGB composite image into a hierarchy of tiles.
     *
     * @param levelImg The raw 3-panel RGB export.
     * @param metadata Supplemental JSON data used to re-apply transformations (rotations/scale) to specific decorations.
     * @param targetLevel The target Level object to populate with Tile nodes.
     * @param offsetX A global coordinate shift X to apply to all instantiated tiles.
     * @param offsetY A global coordinate shift Y to apply to all instantiated tiles.
     */
    public void populateLevelNodesFromCombinedImage(BufferedImage levelImg, LevelMetadata metadata, Level targetLevel, int offsetX, int offsetY) {
        if (levelImg == null) return;
        int panelWidth = levelImg.getWidth() / 3;
        int height = levelImg.getHeight();

        Map<String, ObjectMetadata> rotScaleMap = new HashMap<>();
        if (metadata != null && metadata.getDecorations() != null) {
            for (ObjectMetadata m : metadata.getDecorations()) {
                rotScaleMap.put(m.getX() + "," + m.getY() + "," + m.getLayer(), m);
            }
        }

        for (int i = 0; i < panelWidth; i++) {
            for (int j = 0; j < height; j++) {
                int gx = i + offsetX;
                int gy = j + offsetY;

                Color c1 = new Color(levelImg.getRGB(i, j));
                int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();

                if (r1 < 49) {
                    Tile solid = new Tile("", targetLevel, TileType.SOLID, gx, gy, r1, 254, 254);
                    if (g1 == 255 && b1 == 255) {
                        solid.setLayer(5);
                        solid.setGreen(255);
                        solid.setBlue(255);
                    }
                    else {
                        solid.setLayer(3);
                    }
                    targetLevel.getChildren().add(solid);
                }
                if (b1 < LvlObjType.MAX.ordinal()) {
                    Tile obj = new Tile("", targetLevel, TileType.OBJECT, gx, gy, 254, 254, b1);
                    obj.setLayer(5);
                    targetLevel.getChildren().add(obj);
                }
                if (g1 < LvlEnemyType.MAX.ordinal()) {
                    Tile enemy = new Tile("", targetLevel, TileType.ENEMY, gx, gy, 254, g1, 254);
                    enemy.setLayer(5);
                    targetLevel.getChildren().add(enemy);
                }
                if (r1 == 100 && g1 == 100 && b1 == 100) {
                    Tile player = new Tile("", targetLevel, TileType.PLAYER, gx, gy, 100, 100, 100);
                    player.setLayer(5);
                    targetLevel.getChildren().add(player);
                }

                Color c2 = new Color(levelImg.getRGB(panelWidth + i, j));
                int decoVal = c2.getBlue();
                int layerVal = c2.getGreen();
                if (decoVal < DECO_NUM && layerVal <= 5) {
                    Tile deco = new Tile("", targetLevel, TileType.DECO, gx, gy, 254, layerVal, decoVal);
                    deco.setLayer(layerVal);
                    ObjectMetadata om = rotScaleMap.get(i + "," + j + "," + layerVal);
                    if (om != null) {
                        deco.setRotation(om.getRotation());
                        deco.setScaleX(om.getScaleX());
                        deco.setScaleY(om.getScaleY());
                    }
                    targetLevel.getChildren().add(deco);
                }

                Color c3 = new Color(levelImg.getRGB(panelWidth * 2 + i, j));
                int b3 = c3.getBlue();
                if (c3.getRed() == 254 && c3.getGreen() == 254 && b3 < LvlTriggerType.MAX.ordinal()) {
                    Tile trig = new Tile("", targetLevel, TileType.TRIGGER, gx, gy, 254, 254, b3);
                    trig.setLayer(5);
                    targetLevel.getChildren().add(trig);
                }
                else if (c3.getRed() == 0 && c3.getGreen() == 0 && b3 < LvlNpcType.MAX.ordinal()) {
                    Tile npc = new Tile("", targetLevel, TileType.NPC, gx, gy, 0, 0, b3);
                    npc.setLayer(5);
                    targetLevel.getChildren().add(npc);
                }
            }
        }
    }
}