package editor.gui.view.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static editor.constants.Constants.DECO_NUM;

/**
 * Controller handling all business logic for the World Map Assembler.
 * Loading rooms, SAD minimap alignment, exit auto-wiring, gap room creation, mega-level world preview generation, and exporting.
 */
// TODO: Break this class
public class WorldMapController {

    private static final Color ABYSS_VOID_COLOR = new Color(25, 15, 35, 255);
    private static final String PREVIEW_LEVEL_NAME = "WORLD_PREVIEW";

    private static final int ICON_SAVE = 1;
    private static final int ICON_SHOP = 2;
    private static final int ICON_BLACKSMITH = 3;
    private static final int ICON_CRAFTING = 4;
    private static final int ICON_BOSS = 5;
    private static final int ICON_TAVERN = 7;
    private static final int ICON_NPC = 9;

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

    private Map<String, BiomePalette> biomePalettes = new HashMap<>();

    private record BiomePalette(String walkableFill, String walkableGridAlt, String floorHighlight, String wallContourRim, String wallOuterShell) {
        public Color getWalkableFill() {
            return applyAlpha(walkableFill != null ? walkableFill : "#3F5E4D", 254);
        }
        public Color getWalkableGridAlt() {
            return applyAlpha(walkableGridAlt != null ? walkableGridAlt : "#3A5747", 254);
        }
        public Color getFloorHighlight() { return applyAlpha(floorHighlight != null ? floorHighlight : "#588069", 254);
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

    public WorldMapController(Project activeProject) {
        this.activeProject = activeProject;
        loadBiomes();
        loadFromActiveProject();
        if (!rooms.isEmpty() && selectedRoom == null) {
            selectedRoom = rooms.values().iterator().next();
        }
    }

    private void loadBiomes() {
        try (InputStream is = getClass().getResourceAsStream("/meta/blocks/biomes.json");
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, BiomePalette>>(){}.getType();
            Map<String, BiomePalette> loaded = new Gson().fromJson(reader, type);
            if (loaded != null) {
                biomePalettes = loaded;
            }
        } catch (Exception e) {
            Framework.getInstance().log("Failed to load biomes.json, using defaults.", LogType.WARNING);
        }
    }

    private BiomePalette resolveBiomePalette(LevelMetadata meta) {
        String biomeKey = "FOREST";
        if (meta != null && meta.getBiome() != null) biomeKey = meta.getBiome().toUpperCase();
        BiomePalette palette = biomePalettes.get(biomeKey);
        if (palette != null) return palette;
        return new BiomePalette("#3F5E4D", "#3A5747", "#588069", "#88B89A", "#263B32");
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
                LevelMetadata meta = (existing != null) ? existing.getLevelMetadata() : null;
                WorldMapRoomEntry entry = createEntryFromCombinedImage(lvl.getName(), combined, mx, my, meta);
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

                if (id.endsWith("_restored") || id.equals("minimap")) continue;

                WorldMapRoomEntry existing = rooms.get(id);
                int mx = (existing != null) ? existing.getMapX() : cursorX;
                int my = (existing != null) ? existing.getMapY() : 0;

                LevelMetadata meta = null;
                File metaFile = detectCompanionMetaFile(f, id);
                if (metaFile != null && metaFile.exists()) {
                    try (FileReader reader = new FileReader(metaFile)) {
                        meta = new Gson().fromJson(reader, LevelMetadata.class);
                    } catch (Exception ignored) {}
                }

                WorldMapRoomEntry entry = createEntryFromCombinedImage(id, img, mx, my, meta);
                entry.setSourceImageFile(f);
                entry.setLevelMetadata(meta);

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
        File levelsDir = pngFile.getParentFile();
        if (levelsDir != null) {
            File imagesDir = levelsDir.getParentFile();
            if (imagesDir != null) {
                File resourcesDir = imagesDir.getParentFile();
                if (resourcesDir != null) {
                    File metaCandidate = new File(new File(resourcesDir, "meta"), id + ".json");
                    if (metaCandidate.exists()) return metaCandidate;
                }
            }
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
        WorldMapRoomEntry entry = createEntryFromCombinedImage(name, combined, measuredGap.x, measuredGap.y, null);
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

        boolean[][] globalAir = new boolean[totalW][totalH];
        boolean[][] globalPlatform = new boolean[totalW][totalH];
        int[][] globalObj = new int[totalW][totalH];
        BiomePalette[][] globalPalette = new BiomePalette[totalW][totalH];
        List<int[]> globalIcons = new ArrayList<>();

        for (int x = 0; x < totalW; x++) {
            Arrays.fill(globalObj[x], -1);
        }

        WorldMapMetadata worldMeta = new WorldMapMetadata();
        worldMeta.setStartLevel(startLvl);

        for (WorldMapRoomEntry r : rooms.values()) {
            r.setMapX(r.getMapX() + shiftX);
            r.setMapY(r.getMapY() + shiftY);

            BufferedImage combined = r.getCombinedImage();
            int rw = r.getWidth(), rh = r.getHeight();
            BiomePalette palette = resolveBiomePalette(r.getLevelMetadata());

            boolean[][] roomSolid = new boolean[rw][rh];
            for (int x = 0; x < rw; x++) {
                for (int y = 0; y < rh; y++) {
                    Color c1 = new Color(combined.getRGB(x, y));
                    Color c2 = new Color(combined.getRGB(rw + x, y));
                    boolean isSolidTerrain = (c1.getRed() < 49);
                    boolean isBlackBacking = (c2.getBlue() == 34 && c2.getGreen() <= 5);
                    roomSolid[x][y] = isSolidTerrain || isBlackBacking;
                }
            }
            boolean[][] cleanRoomSolid = removeTinySolidSpecs(roomSolid, rw, rh);

            for (int x = 0; x < rw; x++) {
                for (int y = 0; y < rh; y++) {
                    int gx = r.getMapX() + x;
                    int gy = r.getMapY() + y;
                    if (gx < 0 || gx >= totalW || gy < 0 || gy >= totalH) continue;

                    if (globalPalette[gx][gy] == null) {
                        globalPalette[gx][gy] = palette;
                    }

                    if (!cleanRoomSolid[x][y]) {
                        globalAir[gx][gy] = true;
                        globalPalette[gx][gy] = palette;
                        if (roomSolid[x][y]) {
                            globalPlatform[gx][gy] = true;
                        }
                    }

                    Color c1 = new Color(combined.getRGB(x, y));
                    Color c3 = new Color(combined.getRGB(rw * 2 + x, y));
                    int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();

                    if (r1 == 254 && g1 == 254 && b1 < LvlObjType.MAX.ordinal()) {
                        globalObj[gx][gy] = b1;
                    }

                    if (r1 == 254 && g1 == 254 && b1 == LvlObjType.DOOR.ordinal() && c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() == LvlTriggerType.TELEPORT_TAVERN.ordinal()) {
                        globalIcons.add(new int[]{gx, gy, ICON_TAVERN});
                    }
                    else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TOTEM.ordinal()) {
                        globalIcons.add(new int[]{gx, gy, ICON_SAVE});
                    }
                    else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.SHOP.ordinal()) {
                        globalIcons.add(new int[]{Math.min(totalW - 1, gx + 1), Math.min(totalH - 1, gy + 3), ICON_SHOP});
                    }
                    else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.BLACKSMITH.ordinal()) {
                        globalIcons.add(new int[]{gx, Math.min(totalH - 1, gy + 1), ICON_BLACKSMITH});
                    }
                    else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TABLE.ordinal()) {
                        globalIcons.add(new int[]{gx, gy, ICON_CRAFTING});
                    }
                    else if (r1 == 254 && b1 == 254 && g1 == LvlEnemyType.SPEAR_WOMAN.ordinal()) {
                        globalIcons.add(new int[]{gx, gy, ICON_BOSS});
                    }
                    else if (c3.getRed() == 0 && c3.getGreen() == 0 && c3.getBlue() < LvlNpcType.MAX.ordinal()) {
                        globalIcons.add(new int[]{gx, gy, ICON_NPC});
                    }
                }
            }

            RoomNodeMetadata nodeMeta = new RoomNodeMetadata();
            nodeMeta.setMapX(r.getMapX());
            nodeMeta.setMapY(r.getMapY());
            nodeMeta.setExits(new ArrayList<>(r.getExits()));
            worldMeta.getRooms().put(r.getId(), nodeMeta);
        }

        bridgeRoomExitSeamGaps(globalAir, globalPalette, totalW, totalH);

        boolean[][] globalSolid = new boolean[totalW][totalH];
        for (int x = 0; x < totalW; x++) {
            for (int y = 0; y < totalH; y++) {
                globalSolid[x][y] = !globalAir[x][y];
            }
        }

        BiomePalette defaultPalette = resolveBiomePalette(null);
        int[] globalPixels = new int[totalW * totalH];
        Arrays.fill(globalPixels, ABYSS_VOID_COLOR.getRGB());

        for (int x = 0; x < totalW; x++) {
            for (int y = 0; y < totalH; y++) {
                BiomePalette pal = findNearestPalette(globalPalette, x, y, totalW, totalH, defaultPalette);
                if (globalAir[x][y]) {
                    Color c;
                    if (globalObj[x][y] == LvlObjType.LAVA.ordinal()) {
                        c = new Color(235, 95, 25, 254);
                    }
                    else if (globalObj[x][y] == LvlObjType.BRICK.ordinal()) {
                        c = new Color(212, 163, 89, 254);
                    }
                    else if (globalPlatform[x][y] || (y + 1 < totalH && globalSolid[x][y + 1])) {
                        c = pal.getFloorHighlight();
                    }
                    else {
                        boolean nearCeilingOrWall = (y > 0 && globalSolid[x][y - 1]) || (x > 0 && globalSolid[x - 1][y]) || (x + 1 < totalW && globalSolid[x + 1][y]);
                        c = nearCeilingOrWall ? pal.getWalkableGridAlt() : pal.getWalkableFill();
                    }
                    globalPixels[y * totalW + x] = c.getRGB();
                }
                else {
                    int dist = findCardinalThenDiagonalDistanceToAir(globalSolid, x, y, totalW, totalH);
                    if (dist == 1) globalPixels[y * totalW + x] = pal.getWallContourRim().getRGB();
                    else if (dist == 2) globalPixels[y * totalW + x] = pal.getWallOuterShell().getRGB();
                }
            }
        }

        for (int[] icon : globalIcons) {
            int ix = Math.max(0, Math.min(totalW - 1, icon[0]));
            int iy = Math.max(0, Math.min(totalH - 1, icon[1]));
            globalPixels[iy * totalW + ix] = new Color(254, 254, icon[2], 254).getRGB();
        }

        BufferedImage minimapOut = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        minimapOut.setRGB(0, 0, totalW, totalH, globalPixels, 0, totalW);

        ImageIO.write(minimapOut, "png", minimapFile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(worldMeta, writer);
        }

        Framework.getInstance().log("Exported minimap.png (" + totalW + "x" + totalH + ") and world_map.json!", LogType.NOTIFICATION);
        return new Dimension(totalW, totalH);
    }

    /**
     * Ensures that any exit trigger at the edge of a room carves a clean 1-2 tile bridge into the neighboring room's tunnel if there is a tiny 1-2 tile gap between their bounds.
     */
    private void bridgeRoomExitSeamGaps(boolean[][] globalAir, BiomePalette[][] globalPalette, int totalW, int totalH) {
        for (WorldMapRoomEntry r : rooms.values()) {
            BiomePalette pal = resolveBiomePalette(r.getLevelMetadata());
            for (WorldMapRoomEntry.TriggerMarker tm : r.getTriggers()) {
                String name = tm.type().name();
                if (!name.startsWith("LOAD_")) continue;

                int gx = r.getMapX() + tm.x();
                int gy = r.getMapY() + tm.y();
                int dx = name.equals("LOAD_RIGHT_LEVEL") ? 1 : name.equals("LOAD_LEFT_LEVEL") ? -1 : 0;
                int dy = name.equals("LOAD_DOWN_LEVEL") ? 1 : name.equals("LOAD_UP_LEVEL") ? -1 : 0;

                for (int step = 1; step <= 3; step++) {
                    int tx = gx + dx * step;
                    int ty = gy + dy * step;
                    if (tx >= 0 && tx < totalW && ty >= 0 && ty < totalH && globalAir[tx][ty]) {
                        for (int fill = 0; fill < step; fill++) {
                            int fx = gx + dx * fill;
                            int fy = gy + dy * fill;
                            globalAir[fx][fy] = true;
                            if (globalPalette[fx][fy] == null) globalPalette[fx][fy] = pal;
                        }
                        break;
                    }
                }
            }
        }
    }

    private BiomePalette findNearestPalette(BiomePalette[][] grid, int cx, int cy, int w, int h, BiomePalette fallback) {
        if (grid[cx][cy] != null) return grid[cx][cy];
        for (int r = 1; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    int nx = cx + dx, ny = cy + dy;
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && grid[nx][ny] != null) {
                        return grid[nx][ny];
                    }
                }
            }
        }
        return fallback;
    }

    public WorldMapRoomEntry createEntryFromCombinedImage(String id, BufferedImage combined, int mapX, int mapY, LevelMetadata meta) {
        int w = combined.getWidth() / 3, h = combined.getHeight();
        BufferedImage panel1 = combined.getSubimage(0, 0, w, h);
        BufferedImage minimapPreview = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        List<WorldMapRoomEntry.TriggerMarker> triggers = new ArrayList<>();
        List<int[]> pendingIcons = new ArrayList<>();

        BiomePalette palette = resolveBiomePalette(meta);
        boolean[][] solid = new boolean[w][h];
        int[][] objGrid = new int[w][h];

        for (int x = 0; x < w; x++) {
            Arrays.fill(objGrid[x], -1);
            for (int y = 0; y < h; y++) {
                Color c1 = new Color(combined.getRGB(x, y));
                Color c2 = new Color(combined.getRGB(w + x, y));
                Color c3 = new Color(combined.getRGB(w * 2 + x, y));
                int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();

                boolean isSolidTerrain = (r1 < 49);
                boolean isSubterraneanBlackBacking = (c2.getBlue() == 34 && c2.getGreen() <= 5);
                solid[x][y] = isSolidTerrain || isSubterraneanBlackBacking;

                if (r1 == 254 && g1 == 254 && b1 < LvlObjType.MAX.ordinal()) {
                    objGrid[x][y] = b1;
                }

                if (r1 == 254 && g1 == 254 && b1 == LvlObjType.DOOR.ordinal() && c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() == LvlTriggerType.TELEPORT_TAVERN.ordinal()) {
                    pendingIcons.add(new int[]{x, y, ICON_TAVERN});
                }
                else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TOTEM.ordinal()) {
                    pendingIcons.add(new int[]{x, y, ICON_SAVE});
                }
                else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.SHOP.ordinal()) {
                    pendingIcons.add(new int[]{x + 1, Math.min(h - 1, y + 3), ICON_SHOP});
                }
                else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.BLACKSMITH.ordinal()) {
                    pendingIcons.add(new int[]{x, Math.min(h - 1, y + 1), ICON_BLACKSMITH});
                }
                else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TABLE.ordinal()) {
                    pendingIcons.add(new int[]{x, y, ICON_CRAFTING});
                }
                else if (r1 == 254 && b1 == 254 && g1 == LvlEnemyType.SPEAR_WOMAN.ordinal()) {
                    pendingIcons.add(new int[]{x, y, ICON_BOSS});
                }

                if (c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() < LvlTriggerType.MAX.ordinal()) {
                    LvlTriggerType trigType = LvlTriggerType.values()[c3.getBlue()];
                    triggers.add(new WorldMapRoomEntry.TriggerMarker(x, y, trigType));
                }
                else if (c3.getRed() == 0 && c3.getGreen() == 0 && c3.getBlue() < LvlNpcType.MAX.ordinal()) {
                    pendingIcons.add(new int[]{x, y, ICON_NPC});
                }
            }
        }

        boolean[][] cleanSolid = removeTinySolidSpecs(solid, w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!cleanSolid[x][y]) {
                    Color pixelColor = computeSmoothWalkableColor(cleanSolid, solid, objGrid[x][y], x, y, w, h, palette);
                    minimapPreview.setRGB(x, y, pixelColor.getRGB());
                }
                else {
                    int distToAir = findCardinalThenDiagonalDistanceToAir(cleanSolid, x, y, w, h);
                    if (distToAir == 1) minimapPreview.setRGB(x, y, palette.getWallContourRim().getRGB());
                    else if (distToAir == 2) minimapPreview.setRGB(x, y, palette.getWallOuterShell().getRGB());
                    else minimapPreview.setRGB(x, y, new Color(0, 0, 0, 0).getRGB());
                }
            }
        }

        for (int[] icon : pendingIcons) {
            int ix = Math.max(0, Math.min(w - 1, icon[0]));
            int iy = Math.max(0, Math.min(h - 1, icon[1]));
            int iconType = icon[2];
            minimapPreview.setRGB(ix, iy, new Color(254, 254, iconType, 254).getRGB());
        }

        return new WorldMapRoomEntry(id, w, h, mapX, mapY, combined, panel1, minimapPreview, triggers);
    }

    /**
     * Strips isolated 1-tile floating platforms from the solid wall mask so they don't get wrapped
     * in a bulky 3x3 wall rim; instead they are drawn as a sleek 1px platform line inside the room.
     */
    private boolean[][] removeTinySolidSpecs(boolean[][] solid, int w, int h) {
        boolean[][] result = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            System.arraycopy(solid[x], 0, result[x], 0, h);
        }
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (!solid[x][y]) continue;
                boolean thinHorizontal = !solid[x][y - 1] && !solid[x][y + 1];
                if (thinHorizontal) {
                    result[x][y] = false;
                }
            }
        }
        return result;
    }

    /**
     * Computes a clean, non-checkerboard walkable color with soft depth shading and 1px platform ledges.
     */
    private Color computeSmoothWalkableColor(boolean[][] cleanSolid, boolean[][] rawSolid, int objId,
                                             int x, int y, int w, int h, BiomePalette palette) {
        if (objId == LvlObjType.LAVA.ordinal()) {
            return new Color(235, 95, 25, 254);
        }
        if (objId == LvlObjType.BRICK.ordinal()) {
            return new Color(212, 163, 89, 254);
        }
        if (rawSolid[x][y] && !cleanSolid[x][y]) {
            return palette.getFloorHighlight();
        }
        if (y + 1 < h && cleanSolid[x][y + 1]) {
            return palette.getFloorHighlight();
        }
        boolean nearCeilingOrWall = (y > 0 && cleanSolid[x][y - 1]) || (x > 0 && cleanSolid[x - 1][y]) || (x + 1 < w && cleanSolid[x + 1][y]);
        return nearCeilingOrWall ? palette.getWalkableGridAlt() : palette.getWalkableFill();
    }

    private int findCardinalThenDiagonalDistanceToAir(boolean[][] solid, int cx, int cy, int w, int h) {
        int[][] cardinals = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : cardinals) {
            int nx = cx + d[0], ny = cy + d[1];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny]) {
                return 1;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny]) {
                    return 2;
                }
            }
        }
        return 3;
    }

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