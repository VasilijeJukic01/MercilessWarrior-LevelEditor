package editor.gui.view.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import editor.core.Framework;
import editor.gui.controller.levelActions.ExportLevelAction;
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
import editor.model.metadata.RoomExitMetadata;
import editor.model.metadata.RoomNodeMetadata;
import editor.model.metadata.WorldMapMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.repository.components.ProjectExplorer;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.model.tree.EditorTree;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.List;

import static editor.constants.Constants.DECO_NUM;

/**
 * Controller handling all business logic for the World Map Assembler.
 * Loading rooms, SAD minimap alignment, exit auto-wiring, gap room creation, mega-level world preview generation, and exporting.
 */
public class WorldMapController {

    private static final Color WALKABLE_COLOR = new Color(254, 254, 254);
    private static final Color SOLID_COLOR = new Color(0, 255, 255);
    private static final String PREVIEW_LEVEL_NAME = "WORLD_PREVIEW";

    private static final Map<String, WorldMapRoomEntry> SESSION_ROOMS = new LinkedHashMap<>();
    private static String sessionStartLevelId = "begin";

    private Project activeProject;
    private final Map<String, WorldMapRoomEntry> rooms = SESSION_ROOMS;
    private String startLevelId = sessionStartLevelId;
    private WorldMapRoomEntry selectedRoom = null;
    private Rectangle measuredGap = null;

    private static File lastResourcesRoot = null;
    private static File loadedMinimapFile = null;
    private static File loadedWorldMapJsonFile = null;

    public WorldMapController(Project activeProject) {
        this.activeProject = activeProject;
        loadFromActiveProject();
        if (!rooms.isEmpty() && selectedRoom == null) {
            selectedRoom = rooms.values().iterator().next();
        }
    }

    public void loadFromActiveProject() {
        if (activeProject == null) return;
        int cursorX = 0;
        for (Node n : activeProject.getChildren()) {
            if (n instanceof Level lvl) {
                if (PREVIEW_LEVEL_NAME.equals(lvl.getName())) continue;

                WorldMapRoomEntry existing = rooms.get(lvl.getName());
                int mx = (existing != null) ? existing.getMapX() : cursorX;
                int my = (existing != null) ? existing.getMapY() : 0;

                BufferedImage combined = ExportLevelAction.buildExportImage(lvl, "Both");
                WorldMapRoomEntry entry = createEntryFromCombinedImage(lvl.getName(), combined, mx, my);
                entry.setProjectLevel(lvl);
                if (existing != null) {
                    entry.setExits(existing.getExits());
                    entry.setSourceImageFile(existing.getSourceImageFile());
                    entry.setLevelMetadata(existing.getLevelMetadata());
                }
                rooms.put(lvl.getName(), entry);
                cursorX += lvl.getWidth() + 4;
            }
        }
    }

    public void loadLevelsFromDirectory(File dir) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparing(File::getName));

        int cursorX = 0;
        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null || img.getWidth() < 78 || img.getWidth() % 3 != 0) continue;
                String id = f.getName().substring(0, f.getName().lastIndexOf('.'));
                WorldMapRoomEntry existing = rooms.get(id);
                int mx = (existing != null) ? existing.getMapX() : cursorX;
                int my = (existing != null) ? existing.getMapY() : 0;

                WorldMapRoomEntry entry = createEntryFromCombinedImage(id, img, mx, my);
                entry.setSourceImageFile(f);

                File metaFile = detectCompanionMetaFile(f, id);
                if (metaFile != null && metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        entry.setLevelMetadata(new Gson().fromJson(reader, LevelMetadata.class));
                    } catch (Exception ignored) {}
                }

                if (existing != null) entry.setExits(existing.getExits());
                rooms.put(id, entry);
                cursorX += entry.getWidth() + 4;
            } catch (Exception ignored) {}
        }

        if (!rooms.isEmpty() && selectedRoom == null) {
            selectedRoom = rooms.values().iterator().next();
        }
    }

    private File detectCompanionMetaFile(File pngFile, String id) {
        File sameDir = new File(pngFile.getParentFile(), id + ".json");
        if (sameDir.exists()) return sameDir;
        File parent = pngFile.getParentFile();
        if (parent != null && parent.getParentFile() != null) {
            File resourcesDir = parent.getParentFile();
            File metaCandidate = new File(new File(resourcesDir, "meta"), id + ".json");
            if (metaCandidate.exists()) return metaCandidate;
        }
        return null;
    }

    public void loadWorldMapJson(File jsonFile) throws Exception {
        try (FileReader reader = new FileReader(jsonFile)) {
            WorldMapMetadata meta = new Gson().fromJson(reader, WorldMapMetadata.class);
            if (meta == null) return;
            if (meta.getStartLevel() != null) {
                this.startLevelId = meta.getStartLevel();
                sessionStartLevelId = this.startLevelId;
            }
            if (meta.getRooms() != null) {
                for (Map.Entry<String, RoomNodeMetadata> e : meta.getRooms().entrySet()) {
                    WorldMapRoomEntry room = rooms.get(e.getKey());
                    if (room != null) {
                        RoomNodeMetadata node = e.getValue();
                        if (node.getMapX() != null) room.setMapX(node.getMapX());
                        if (node.getMapY() != null) room.setMapY(node.getMapY());
                        if (node.getExits() != null) room.setExits(new ArrayList<>(node.getExits()));
                    }
                }
            }
            Framework.getInstance().log("Loaded world_map.json into World Map Assembler.", LogType.NOTIFICATION);
        }
    }

    public void autoAlignFromMinimapPng(File minimapFile) throws Exception {
        BufferedImage minimapImg = ImageIO.read(minimapFile);
        if (minimapImg == null) return;
        int[][] minimapGray = toGrayscale(minimapImg);

        for (WorldMapRoomEntry room : rooms.values()) {
            Point match = findRoomOnMinimapSAD(minimapGray, room.getPanel1Image());
            if (match != null) {
                room.setMapX(match.x);
                room.setMapY(match.y);
            }
        }
        Framework.getInstance().log("Auto-aligned all rooms to minimap.png!", LogType.NOTIFICATION);
    }

    private Point findRoomOnMinimapSAD(int[][] minimapGray, BufferedImage roomPanel1) {
        int rw = roomPanel1.getWidth(), rh = roomPanel1.getHeight();
        int mh = minimapGray.length, mw = minimapGray[0].length;
        if (rw > mw || rh > mh) return null;

        int[][] roomGray = toGrayscale(roomPanel1);
        int bestSAD = Integer.MAX_VALUE;
        Point bestPt = null;

        for (int y = 0; y <= mh - rh; y++) {
            for (int x = 0; x <= mw - rw; x++) {
                int sad = 0;
                for (int j = 0; j < rh; j++) {
                    for (int i = 0; i < rw; i++) {
                        sad += Math.abs(minimapGray[y + j][x + i] - roomGray[j][i]);
                        if (sad >= bestSAD) break;
                    }
                    if (sad >= bestSAD) break;
                }
                if (sad < bestSAD) {
                    bestSAD = sad;
                    bestPt = new Point(x, y);
                }
            }
        }
        return bestPt;
    }

    private int[][] toGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = (r + g + b) / 3;
            }
        }
        return gray;
    }

    public int autoWireTouchingExits() {
        int wiredCount = 0;
        for (WorldMapRoomEntry room : rooms.values()) {
            Map<String, List<Point>> groupedTriggers = new LinkedHashMap<>();
            for (WorldMapRoomEntry.TriggerMarker tm : room.getTriggers()) {
                if (tm.type().name().startsWith("LOAD_")) {
                    groupedTriggers.computeIfAbsent(tm.type().name(), k -> new ArrayList<>()).add(new Point(tm.x(), tm.y()));
                }
            }

            List<RoomExitMetadata> newExits = new ArrayList<>();
            for (Map.Entry<String, List<Point>> entry : groupedTriggers.entrySet()) {
                String trigName = entry.getKey();
                List<Point> clusters = clusterTriggerPoints(entry.getValue());

                for (Point pt : clusters) {
                    int globalX = room.getMapX() + pt.x;
                    int globalY = room.getMapY() + pt.y;
                    int probeX = globalX + (trigName.equals("LOAD_RIGHT_LEVEL") ? 2 : trigName.equals("LOAD_LEFT_LEVEL") ? -2 : 0);
                    int probeY = globalY + (trigName.equals("LOAD_DOWN_LEVEL") ? 2 : trigName.equals("LOAD_UP_LEVEL") ? -2 : 0);

                    WorldMapRoomEntry neighbor = findRoomContainingGlobalPoint(probeX, probeY, room);
                    if (neighbor != null) {
                        String bestSpawn = findClosestSpawnInNeighbor(neighbor, globalX, globalY, trigName);
                        RoomExitMetadata exit = new RoomExitMetadata(trigName, neighbor.getId(), bestSpawn);
                        if (clusters.size() > 1) {
                            exit.setMinX(Math.max(0, pt.x - 4));
                            exit.setMaxX(pt.x + 4);
                            exit.setMinY(Math.max(0, pt.y - 4));
                            exit.setMaxY(pt.y + 4);
                        }
                        newExits.add(exit);
                        wiredCount++;
                    }
                }
            }
            if (!newExits.isEmpty()) {
                room.setExits(newExits);
            }
        }
        return wiredCount;
    }

    private List<Point> clusterTriggerPoints(List<Point> points) {
        List<Point> reps = new ArrayList<>();
        for (Point p : points) {
            boolean nearExisting = false;
            for (Point r : reps) {
                if (p.distance(r) <= 5.0) {
                    nearExisting = true;
                    break;
                }
            }
            if (!nearExisting) reps.add(p);
        }
        return reps;
    }

    private WorldMapRoomEntry findRoomContainingGlobalPoint(int gx, int gy, WorldMapRoomEntry exclude) {
        for (WorldMapRoomEntry candidate : rooms.values()) {
            if (candidate == exclude) continue;
            if (candidate.getBounds().contains(gx, gy)) return candidate;
        }
        return null;
    }

    private String findClosestSpawnInNeighbor(WorldMapRoomEntry neighbor, int globalExitX, int globalExitY, String trigName) {
        String bestSpawn = switch (trigName) {
            case "LOAD_LEFT_LEVEL" -> "SPAWN_A";
            case "LOAD_RIGHT_LEVEL" -> "SPAWN_B";
            case "LOAD_UP_LEVEL" -> "SPAWN_C";
            default -> "SPAWN_D";
        };
        double bestDist = Double.MAX_VALUE;
        for (WorldMapRoomEntry.TriggerMarker tm : neighbor.getTriggers()) {
            if (tm.type().name().startsWith("SPAWN_")) {
                double d = Point.distance(globalExitX, globalExitY, neighbor.getMapX() + tm.x(), neighbor.getMapY() + tm.y());
                if (d < bestDist) {
                    bestDist = d;
                    bestSpawn = tm.type().name();
                }
            }
        }
        return bestSpawn;
    }

    public WorldMapRoomEntry createNewLevelInGap(String name) {
        if (measuredGap == null) return null;
        ensureProjectExists();

        Level newLvl = new Level(name, activeProject, measuredGap.width, measuredGap.height);
        addLevelToActiveProjectAndOpenTab(newLvl);

        BufferedImage combined = ExportLevelAction.buildExportImage(newLvl, "Both");
        WorldMapRoomEntry entry = createEntryFromCombinedImage(name, combined, measuredGap.x, measuredGap.y);
        entry.setProjectLevel(newLvl);
        rooms.put(name, entry);
        selectedRoom = entry;
        return entry;
    }

    /**
     * Opens a single room from the World Map directly as a Level tab in the editor.
     */
    public void openRoomInEditorTab(WorldMapRoomEntry room) {
        if (room == null) return;
        ensureProjectExists();

        Level targetLevel = room.getProjectLevel();
        if (targetLevel == null) {
            targetLevel = new Level(room.getId(), activeProject, room.getWidth(), room.getHeight());
            populateLevelNodesFromCombinedImage(room.getCombinedImage(), room.getLevelMetadata(), targetLevel, 0, 0);
            room.setProjectLevel(targetLevel);
            addLevelToActiveProjectAndOpenTab(targetLevel);
        } else {
            focusOrOpenLevelTab(targetLevel);
        }
        Framework.getInstance().log("Opened room '" + room.getId() + "' in editor tab.", LogType.NOTIFICATION);
    }

    /**
     * Stitches every room on the World Map into one giant composite Level ("WORLD_PREVIEW") and opens it in an editor tab so the entire interconnected world can be viewed at full resolution!
     */
    public Level buildAndOpenMegaWorldPreviewTab() {
        if (rooms.isEmpty()) return null;
        ensureProjectExists();

        activeProject.getChildren().removeIf(n -> n instanceof Level && PREVIEW_LEVEL_NAME.equals(n.getName()));

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (WorldMapRoomEntry r : rooms.values()) {
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

        Level worldPreview = new Level(PREVIEW_LEVEL_NAME, activeProject, totalW, totalH);
        for (WorldMapRoomEntry r : rooms.values()) {
            if (PREVIEW_LEVEL_NAME.equals(r.getId())) continue;
            int offsetX = r.getMapX() + shiftX;
            int offsetY = r.getMapY() + shiftY;
            populateLevelNodesFromCombinedImage(r.getCombinedImage(), r.getLevelMetadata(), worldPreview, offsetX, offsetY);
        }

        addLevelToActiveProjectAndOpenTab(worldPreview);
        Framework.getInstance().log("Created full WORLD_PREVIEW level (" + totalW + "x" + totalH + " tiles)!", LogType.NOTIFICATION);
        return worldPreview;
    }

    private void populateLevelNodesFromCombinedImage(BufferedImage levelImg, LevelMetadata metadata,
                                                     Level targetLevel, int offsetX, int offsetY) {
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

                // Panel 1: Solid, Object, Enemy, Player
                Color c1 = new Color(levelImg.getRGB(i, j));
                int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();

                if (r1 < 49) {
                    Tile solid = new Tile("", targetLevel, TileType.SOLID, gx, gy, r1, 254, 254);
                    if (g1 == 255 && b1 == 255) {
                        solid.setLayer(5);
                        solid.setGreen(255);
                        solid.setBlue(255);
                    } else {
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

                // Panel 2: Decorations + Rotation/Scale Metadata
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

                // Panel 3: Triggers & NPCs
                Color c3 = new Color(levelImg.getRGB(panelWidth * 2 + i, j));
                int b3 = c3.getBlue();
                if (c3.getRed() == 254 && c3.getGreen() == 254 && b3 < LvlTriggerType.MAX.ordinal()) {
                    Tile trig = new Tile("", targetLevel, TileType.TRIGGER, gx, gy, 254, 254, b3);
                    trig.setLayer(5);
                    targetLevel.getChildren().add(trig);
                } else if (c3.getRed() == 0 && c3.getGreen() == 0 && b3 < LvlNpcType.MAX.ordinal()) {
                    Tile npc = new Tile("", targetLevel, TileType.NPC, gx, gy, 0, 0, b3);
                    npc.setLayer(5);
                    targetLevel.getChildren().add(npc);
                }
            }
        }
    }

    private void ensureProjectExists() {
        if (activeProject != null) return;
        ProjectExplorer explorer = Framework.getInstance().getRepository().getProjectExplorer();
        if (!explorer.getChildren().isEmpty() && explorer.getChildren().get(0) instanceof Project existing) {
            activeProject = existing;
        } else {
            activeProject = new Project("World Project", explorer);
            explorer.addChild(activeProject);
            EditorFrame.getInstance().getEditorTree().deserialize(activeProject);
        }
        TabbedPane tabbedPane = EditorFrame.getInstance().getProjectView().getTabbedPane();
        tabbedPane.setProject(activeProject);
        EditorFrame.getInstance().getProjectView().getProjectViewTop().setProject(activeProject);
    }

    private void addLevelToActiveProjectAndOpenTab(Level level) {
        activeProject.addChild(level);
        EditorTree tree = (EditorTree) EditorFrame.getInstance().getEditorTree();
        tree.refreshView();
        focusOrOpenLevelTab(level);
    }

    private void focusOrOpenLevelTab(Level level) {
        TabbedPane tabbedPane = EditorFrame.getInstance().getProjectView().getTabbedPane();
        tabbedPane.setProject(activeProject);
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

    public Dimension exportWorldMapAndMinimap(File minimapFile, File jsonFile, String startLvl) throws Exception {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (WorldMapRoomEntry r : rooms.values()) {
            minX = Math.min(minX, r.getMapX());
            minY = Math.min(minY, r.getMapY());
            maxX = Math.max(maxX, r.getMapX() + r.getWidth());
            maxY = Math.max(maxY, r.getMapY() + r.getHeight());
        }

        int shiftX = (minX < 0) ? -minX : 0;
        int shiftY = (minY < 0) ? -minY : 0;
        int totalW = Math.max(1, maxX + shiftX);
        int totalH = Math.max(1, maxY + shiftY);

        BufferedImage minimapOut = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        WorldMapMetadata worldMeta = new WorldMapMetadata();
        worldMeta.setStartLevel(startLvl);

        for (WorldMapRoomEntry r : rooms.values()) {
            r.setMapX(r.getMapX() + shiftX);
            r.setMapY(r.getMapY() + shiftY);

            Graphics2D g = minimapOut.createGraphics();
            g.drawImage(r.getMinimapPreview(), r.getMapX(), r.getMapY(), null);
            g.dispose();

            RoomNodeMetadata nodeMeta = new RoomNodeMetadata();
            nodeMeta.setMapX(r.getMapX());
            nodeMeta.setMapY(r.getMapY());
            nodeMeta.setExits(new ArrayList<>(r.getExits()));
            worldMeta.getRooms().put(r.getId(), nodeMeta);
        }

        ImageIO.write(minimapOut, "png", minimapFile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(worldMeta, writer);
        }

        Framework.getInstance().log("Exported minimap.png (" + totalW + "x" + totalH + ") and world_map.json!", LogType.NOTIFICATION);
        return new Dimension(totalW, totalH);
    }

    public WorldMapRoomEntry createEntryFromCombinedImage(String id, BufferedImage combined, int mapX, int mapY) {
        int w = combined.getWidth() / 3;
        int h = combined.getHeight();
        BufferedImage panel1 = combined.getSubimage(0, 0, w, h);
        BufferedImage minimapPreview = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        List<WorldMapRoomEntry.TriggerMarker> triggers = new ArrayList<>();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Color c1 = new Color(combined.getRGB(x, y));
                int r = c1.getRed(), g = c1.getGreen(), b = c1.getBlue();

                if (b == LvlObjType.TOTEM.ordinal() && r == 254 && g == 254) {
                    minimapPreview.setRGB(x, y, new Color(254, 254, 1).getRGB());
                } else if (b == LvlObjType.SHOP.ordinal() && r == 254 && g == 254) {
                    minimapPreview.setRGB(x, y, new Color(254, 254, 2).getRGB());
                } else if (b == LvlObjType.BLACKSMITH.ordinal() && r == 254 && g == 254) {
                    minimapPreview.setRGB(x, y, new Color(254, 254, 3).getRGB());
                } else if (b == LvlObjType.TABLE.ordinal() && r == 254 && g == 254) {
                    minimapPreview.setRGB(x, y, new Color(254, 254, 4).getRGB());
                } else if (g == LvlEnemyType.SPEAR_WOMAN.ordinal() && r == 254 && b == 254) {
                    minimapPreview.setRGB(x, y, new Color(254, 254, 5).getRGB());
                } else if (r < 49) {
                    minimapPreview.setRGB(x, y, SOLID_COLOR.getRGB());
                } else {
                    minimapPreview.setRGB(x, y, WALKABLE_COLOR.getRGB());
                }

                Color c3 = new Color(combined.getRGB(w * 2 + x, y));
                if (c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() < LvlTriggerType.MAX.ordinal()) {
                    triggers.add(new WorldMapRoomEntry.TriggerMarker(x, y, LvlTriggerType.values()[c3.getBlue()]));
                }
            }
        }

        return new WorldMapRoomEntry(id, w, h, mapX, mapY, combined, panel1, minimapPreview, triggers);
    }

    /**
     * One-click importer: accepts the game's 'resources' folder, 'images/levels' folder, or 'meta' folder.
     * Automatically loads all level PNGs, companion metadata, world_map.json, and minimap.png alignment!
     */
    public void importEntireGameWorld(File selectedDir) throws Exception {
        File resourcesRoot = resolveResourcesRoot(selectedDir);
        lastResourcesRoot = resourcesRoot;

        File levelsDir = new File(resourcesRoot, "images/levels");
        if (!levelsDir.exists()) levelsDir = selectedDir;

        File metaDir = new File(resourcesRoot, "meta");
        loadedMinimapFile = new File(levelsDir, "minimap.png");
        loadedWorldMapJsonFile = new File(metaDir, "world_map.json");

        rooms.clear();
        loadLevelsFromDirectory(levelsDir);

        Set<String> roomsWithSavedCoords = new HashSet<>();
        if (loadedWorldMapJsonFile.exists()) {
            try (FileReader reader = new FileReader(loadedWorldMapJsonFile)) {
                WorldMapMetadata meta = new Gson().fromJson(reader, WorldMapMetadata.class);
                if (meta != null) {
                    if (meta.getStartLevel() != null) {
                        this.startLevelId = meta.getStartLevel();
                        sessionStartLevelId = this.startLevelId;
                    }
                    if (meta.getRooms() != null) {
                        for (Map.Entry<String, RoomNodeMetadata> e : meta.getRooms().entrySet()) {
                            WorldMapRoomEntry room = rooms.get(e.getKey());
                            if (room != null) {
                                RoomNodeMetadata node = e.getValue();
                                if (node.getMapX() != null && node.getMapY() != null) {
                                    room.setMapX(node.getMapX());
                                    room.setMapY(node.getMapY());
                                    roomsWithSavedCoords.add(room.getId());
                                }
                                if (node.getExits() != null) {
                                    room.setExits(new ArrayList<>(node.getExits()));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (loadedMinimapFile.exists()) {
            BufferedImage minimapImg = ImageIO.read(loadedMinimapFile);
            if (minimapImg != null) {
                int[][] minimapGray = toGrayscale(minimapImg);
                for (WorldMapRoomEntry room : rooms.values()) {
                    if (!roomsWithSavedCoords.contains(room.getId())) {
                        Point match = findRoomOnMinimapSAD(minimapGray, room.getPanel1Image());
                        if (match != null) {
                            room.setMapX(match.x);
                            room.setMapY(match.y);
                        }
                    }
                }
            }
        }

        if (!rooms.isEmpty()) {
            selectedRoom = rooms.values().iterator().next();
        }
        Framework.getInstance().log("Imported entire game world (" + rooms.size() + " rooms) from: " + resourcesRoot.getAbsolutePath(), LogType.NOTIFICATION);
    }

    private File resolveResourcesRoot(File dir) {
        if (new File(dir, "images/levels").exists() && new File(dir, "meta").exists()) {
            return dir;
        }
        if (dir.getName().equalsIgnoreCase("levels") && dir.getParentFile() != null && dir.getParentFile().getParentFile() != null) {
            return dir.getParentFile().getParentFile();
        }
        if (dir.getName().equalsIgnoreCase("images") && dir.getParentFile() != null) {
            return dir.getParentFile();
        }
        if (dir.getName().equalsIgnoreCase("meta") && dir.getParentFile() != null) {
            return dir.getParentFile();
        }
        return dir;
    }

    public Map<String, WorldMapRoomEntry> getRooms() {
        return rooms;
    }

    public String getStartLevelId() {
        return startLevelId;
    }

    public WorldMapRoomEntry getSelectedRoom() {
        return selectedRoom;
    }

    public File getLastResourcesRoot() {
        return lastResourcesRoot;
    }

    public File getLoadedMinimapFile() {
        return loadedMinimapFile;
    }

    public File getLoadedWorldMapJsonFile() {
        return loadedWorldMapJsonFile;
    }

    public void setSelectedRoom(WorldMapRoomEntry selectedRoom) {
        this.selectedRoom = selectedRoom;
    }

    public Rectangle getMeasuredGap() {
        return measuredGap;
    }

    public void setMeasuredGap(Rectangle measuredGap) {
        this.measuredGap = measuredGap;
    }
}