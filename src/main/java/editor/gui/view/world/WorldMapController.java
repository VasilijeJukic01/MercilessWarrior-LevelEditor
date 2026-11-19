package editor.gui.view.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import editor.core.Framework;
import editor.gui.controller.levelActions.ExportLevelAction;
import editor.logger.LogType;
import editor.model.metadata.LevelMetadata;
import editor.model.metadata.RoomNodeMetadata;
import editor.model.metadata.WorldMapMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/**
 * Coordinator for the World Map Assembler, delegating minimap generation, routing/alignment, and tab creation to dedicated helper classes.
 */
public class WorldMapController {

    private static final Map<String, WorldMapRoomEntry> SESSION_ROOMS = new LinkedHashMap<>();
    private static String sessionStartLevelId = "begin";
    private static File lastResourcesRoot = null;
    private static File loadedMinimapFile = null;
    private static File loadedWorldMapJsonFile = null;

    private Project activeProject;
    private final Map<String, WorldMapRoomEntry> rooms = SESSION_ROOMS;
    private String startLevelId = sessionStartLevelId;
    private WorldMapRoomEntry selectedRoom = null;
    private Rectangle measuredGap = null;

    private final MinimapGenerator minimapGenerator = new MinimapGenerator();
    private final WorldMapRouter router = new WorldMapRouter();
    private final WorldMapTabBridge tabBridge = new WorldMapTabBridge();

    public WorldMapController(Project activeProject) {
        this.activeProject = activeProject;
        loadFromActiveProject();
        if (!rooms.isEmpty() && selectedRoom == null) {
            selectedRoom = rooms.values().iterator().next();
        }
    }

    /**
     * Parses all unsaved level nodes currently active in the Editor's Tree view and pushes them into the map assembly memory so they can be positioned.
     */
    public void loadFromActiveProject() {
        if (activeProject == null) return;
        int cursorX = 0;
        for (Node n : activeProject.getChildren()) {
            if (n instanceof Level lvl) {
                if (WorldMapTabBridge.PREVIEW_LEVEL_NAME.equals(lvl.getName())) continue;

                WorldMapRoomEntry existing = rooms.get(lvl.getName());
                int mx = (existing != null) ? existing.getMapX() : cursorX;
                int my = (existing != null) ? existing.getMapY() : 0;

                BufferedImage combined = ExportLevelAction.buildExportImage(lvl, "Both");
                LevelMetadata lvlMeta = lvl.getMetadata();
                boolean lvlHasMeta = lvlMeta != null && (lvlMeta.getBiome() != null || lvlMeta.getBackgroundId() != null || lvlMeta.getTileset() != null || lvlMeta.getAmbientParticlesEnabled() != null);
                LevelMetadata meta = lvlHasMeta ? lvlMeta : ((existing != null && existing.getLevelMetadata() != null) ? existing.getLevelMetadata() : lvlMeta);

                lvl.setMetadata(meta);
                WorldMapRoomEntry entry = minimapGenerator.createEntryFromCombinedImage(lvl.getName(), combined, mx, my, meta);
                entry.setProjectLevel(lvl);
                entry.setLevelMetadata(meta);
                if (existing != null) {
                    entry.setExits(existing.getExits());
                    entry.setTotemId(existing.getTotemId());
                    entry.setTotemName(existing.getTotemName());
                    entry.setSourceImageFile(existing.getSourceImageFile());
                    entry.setMetaFile(existing.getMetaFile());
                }
                rooms.put(lvl.getName(), entry);
                cursorX += lvl.getWidth() + 4;
            }
        }
    }

    /**
     * Mass imports levels directly from a target OS directory, bypassing the Project Tree.
     * Looks for companion metadata files (rotations, scales, biomes) to fully reconstruct visual state.
     *
     * @param dir The OS directory containing level PNGs and metadata.
     */
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
                if (id.endsWith("_restored") || id.equals("minimap") || id.equals("menu") || id.equals("tavern") || id.startsWith("levelarena")) {
                    continue;
                }

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
                if (meta == null) meta = new LevelMetadata();

                WorldMapRoomEntry entry = minimapGenerator.createEntryFromCombinedImage(id, img, mx, my, meta);
                entry.setSourceImageFile(f);
                entry.setMetaFile(metaFile);
                entry.setLevelMetadata(meta);

                if (existing != null) {
                    entry.setExits(existing.getExits());
                    entry.setTotemId(existing.getTotemId());
                    entry.setTotemName(existing.getTotemName());
                }
                rooms.put(id, entry);
                cursorX += entry.getWidth() + 4;
            } catch (Exception ignored) {}
        }

        if (!rooms.isEmpty() && selectedRoom == null) {
            selectedRoom = rooms.values().iterator().next();
        }
    }

    /**
     * Resolves the location of a level's JSON companion file based on standard engine folder structures.
     */
    private File detectCompanionMetaFile(File pngFile, String id) {
        File sameDir = new File(pngFile.getParentFile(), id + ".json");
        if (sameDir.exists()) return sameDir;

        File curr = pngFile.getParentFile();
        while (curr != null) {
            File candidateMetaDir = new File(curr, "meta");
            if (candidateMetaDir.exists() && candidateMetaDir.isDirectory()) {
                return new File(candidateMetaDir, id + ".json");
            }
            curr = curr.getParentFile();
        }
        return sameDir;
    }

    /**
     * Applies routing, totem metadata, and layout positions from an existing world_map.json file over the currently loaded rooms.
     */
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
                        if (node.getTotemId() != null) room.setTotemId(node.getTotemId());
                        if (node.getTotemName() != null) room.setTotemName(node.getTotemName());
                        if (node.getExits() != null) room.setExits(new ArrayList<>(node.getExits()));
                    }
                }
            }
            Framework.getInstance().log("Loaded world_map.json into World Map Assembler.", LogType.NOTIFICATION);
        }
    }

    /**
     * Triggers the SAD template matching algorithm to automatically align isolated rooms to an existing minimap snapshot.
     */
    public void autoAlignRoomsToMinimap(File minimapFile) throws Exception {
        router.autoAlignRoomsToMinimap(minimapFile, rooms.values(), Collections.emptySet());
        Framework.getInstance().log("Auto-aligned all rooms to minimap.png!", LogType.NOTIFICATION);
    }

    /**
     * Uses proximity calculations to automatically draw directional routing connections (exits) between rooms that are physically adjacent on the canvas.
     */
    public int autoWireTouchingExits() {
        return router.autoWireTouchingExits(rooms.values());
    }

    /**
     * Spawns a brand new, empty level based on the rectangular dimensions dragged on the map canvas.
     */
    public WorldMapRoomEntry createNewLevelInGap(String name) {
        if (measuredGap == null) return null;
        activeProject = tabBridge.ensureProjectExists(activeProject);

        LevelMetadata meta = new LevelMetadata();
        meta.ensureDefaults(null);
        Level newLvl = new Level(name, activeProject, measuredGap.width, measuredGap.height);
        newLvl.setMetadata(meta);
        tabBridge.addLevelToProjectAndOpenTab(activeProject, newLvl);

        BufferedImage combined = ExportLevelAction.buildExportImage(newLvl, "Both");
        WorldMapRoomEntry entry = minimapGenerator.createEntryFromCombinedImage(name, combined, measuredGap.x, measuredGap.y, meta);
        entry.setProjectLevel(newLvl);
        entry.setLevelMetadata(meta);
        rooms.put(name, entry);
        selectedRoom = entry;
        return entry;
    }

    public void openRoomInEditorTab(WorldMapRoomEntry room) {
        activeProject = tabBridge.ensureProjectExists(activeProject);
        tabBridge.openRoomInEditorTab(activeProject, room);
    }

    public Level buildAndOpenMegaWorldPreviewTab() {
        activeProject = tabBridge.ensureProjectExists(activeProject);
        return tabBridge.buildAndOpenMegaWorldPreviewTab(activeProject, rooms.values());
    }

    /**
     * Stitches the current canvas arrangement into the final game-ready minimap.png, applies visual patches, and dumps the world_map.json routing file while preserving any existing totem metadata.
     *
     * @param minimapFile Output destination for minimap.png.
     * @param jsonFile Output destination for world_map.json.
     * @param startLvl The name of the room players spawn into on a fresh save.
     */
    public Dimension exportWorldMapAndMinimap(File minimapFile, File jsonFile, String startLvl) throws Exception {
        if (jsonFile != null && jsonFile.exists()) {
            try (FileReader reader = new FileReader(jsonFile)) {
                WorldMapMetadata onDisk = new Gson().fromJson(reader, WorldMapMetadata.class);
                if (onDisk != null && onDisk.getRooms() != null) {
                    for (Map.Entry<String, RoomNodeMetadata> entry : onDisk.getRooms().entrySet()) {
                        WorldMapRoomEntry r = rooms.get(entry.getKey());
                        RoomNodeMetadata diskNode = entry.getValue();
                        if (r != null && diskNode != null) {
                            if (r.getTotemId() == null && diskNode.getTotemId() != null) {
                                r.setTotemId(diskNode.getTotemId());
                            }
                            if ((r.getTotemName() == null || r.getTotemName().isEmpty()) && diskNode.getTotemName() != null) {
                                r.setTotemName(diskNode.getTotemName());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        WorldMapMetadata worldMeta = new WorldMapMetadata();
        worldMeta.setStartLevel(startLvl);

        BufferedImage minimapOut = minimapGenerator.buildGlobalMinimapAndPopulateMetadata(rooms.values(), worldMeta);
        if (minimapFile.getParentFile() != null && !minimapFile.getParentFile().exists()) {
            minimapFile.getParentFile().mkdirs();
        }
        ImageIO.write(minimapOut, "png", minimapFile);

        if (jsonFile.getParentFile() != null && !jsonFile.getParentFile().exists()) {
            jsonFile.getParentFile().mkdirs();
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(worldMeta, writer);
        }

        File metaDir = jsonFile.getParentFile();
        for (WorldMapRoomEntry r : rooms.values()) {
            if (WorldMapTabBridge.PREVIEW_LEVEL_NAME.equals(r.getId())) continue;
            File roomMetaFile = r.getMetaFile();
            if (roomMetaFile == null && metaDir != null) {
                roomMetaFile = new File(metaDir, r.getId() + ".json");
                r.setMetaFile(roomMetaFile);
            }
            if (roomMetaFile != null && r.getLevelMetadata() != null) {
                ExportLevelAction.saveRoomMetadataFile(r.getLevelMetadata(), roomMetaFile);
            }
        }

        Framework.getInstance().log("Exported minimap.png, world_map.json, and room metadata JSONs!", LogType.NOTIFICATION);
        return new Dimension(minimapOut.getWidth(), minimapOut.getHeight());
    }

    /**
     * Updates the selected room's env metadata and immediately re-renders its minimap preview on the map canvas using the new biome palette.
     */
    public void updateSelectedRoomEnvironment(String biome, String backgroundId, String tileset, boolean ambientParticles) {
        if (selectedRoom == null) return;
        LevelMetadata meta = selectedRoom.getLevelMetadata();
        if (meta == null) {
            meta = new LevelMetadata();
            selectedRoom.setLevelMetadata(meta);
        }

        String oldBiome = meta.getBiome();
        meta.setBiome(biome);
        meta.setBackgroundId(backgroundId);
        meta.setTileset(tileset);
        meta.setAmbientParticlesEnabled(ambientParticles);

        if (selectedRoom.getProjectLevel() != null) selectedRoom.getProjectLevel().setMetadata(meta);
        if (!Objects.equals(oldBiome, biome)) minimapGenerator.rebuildMinimapPreview(selectedRoom);
    }

    /**
     * One-Click Import that auto-discovers all structural assets and assembles the world view completely automatically from any root project folder.
     * Supports the 'images/levels/rooms' + 'images/levels/minimap/minimap.png' structure.
     *
     * @param selectedDir The root OS directory (e.g. /resources/ or /levels/) to initiate the scan from.
     */
    public void importEntireGameWorld(File selectedDir) throws Exception {
        File resourcesRoot = resolveResourcesRoot(selectedDir);
        lastResourcesRoot = resourcesRoot;

        File roomsDir = new File(resourcesRoot, "images/levels/rooms");
        if (!roomsDir.exists()) roomsDir = new File(resourcesRoot, "images/levels");
        if (!roomsDir.exists()) roomsDir = selectedDir;

        File metaDir = new File(resourcesRoot, "meta");
        File newMinimapFile = new File(resourcesRoot, "images/levels/minimap/minimap.png");
        File legacyMinimapFile = new File(resourcesRoot, "images/levels/minimap.png");
        loadedMinimapFile = (newMinimapFile.exists() || newMinimapFile.getParentFile().exists()) ? newMinimapFile : legacyMinimapFile;
        loadedWorldMapJsonFile = new File(metaDir, "world_map.json");

        rooms.clear();
        loadLevelsFromDirectory(roomsDir);

        Set<String> roomsWithSavedCoords = new HashSet<>();
        if (loadedWorldMapJsonFile.exists()) {
            loadWorldMapJson(loadedWorldMapJsonFile);
            for (WorldMapRoomEntry r : rooms.values()) {
                roomsWithSavedCoords.add(r.getId());
            }
        }

        if (loadedMinimapFile.exists()) {
            router.autoAlignRoomsToMinimap(loadedMinimapFile, rooms.values(), roomsWithSavedCoords);
        }

        if (!rooms.isEmpty()) selectedRoom = rooms.values().iterator().next();
        Framework.getInstance().log("Imported entire game world (" + rooms.size() + " rooms) from: " + resourcesRoot.getAbsolutePath(), LogType.NOTIFICATION);
    }

    private File resolveResourcesRoot(File dir) {
        if (new File(dir, "images/levels").exists() && new File(dir, "meta").exists()) return dir;
        if (dir.getName().equalsIgnoreCase("rooms") && dir.getParentFile() != null && dir.getParentFile().getParentFile() != null && dir.getParentFile().getParentFile().getParentFile() != null) {
            return dir.getParentFile().getParentFile().getParentFile();
        }
        if (dir.getName().equalsIgnoreCase("levels") && dir.getParentFile() != null && dir.getParentFile().getParentFile() != null) {
            return dir.getParentFile().getParentFile();
        }
        if (dir.getName().equalsIgnoreCase("images") && dir.getParentFile() != null) return dir.getParentFile();
        if (dir.getName().equalsIgnoreCase("meta") && dir.getParentFile() != null) return dir.getParentFile();
        return dir;
    }

    public MinimapGenerator getMinimapGenerator() {
        return minimapGenerator;
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

    public void setSelectedRoom(WorldMapRoomEntry selectedRoom) {
        this.selectedRoom = selectedRoom;
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

    public Rectangle getMeasuredGap() {
        return measuredGap;
    }

    public void setMeasuredGap(Rectangle measuredGap) {
        this.measuredGap = measuredGap;
    }
}