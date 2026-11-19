package editor.gui.view.world;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import editor.core.EditorAssetManager;
import editor.core.Framework;
import editor.logger.LogType;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlNpcType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.metadata.BiomePalette;
import editor.model.metadata.LevelMetadata;
import editor.model.metadata.RoomNodeMetadata;
import editor.model.metadata.WorldMapMetadata;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Responsible for biome palette resolution, per-room minimap preview rendering, and global seamless minimap stitching.
 */
public class MinimapGenerator {

    private static final Color ABYSS_VOID_COLOR = new Color(25, 15, 35, 255);
    private static final BiomePalette DEFAULT_PALETTE = new BiomePalette("#3F5E4D", "#3A5747", "#588069", "#88B89A", "#263B32");

    private static final int ICON_SAVE = 1;
    private static final int ICON_SHOP = 2;
    private static final int ICON_BLACKSMITH = 3;
    private static final int ICON_CRAFTING = 4;
    private static final int ICON_BOSS = 5;
    private static final int ICON_TAVERN = 7;
    private static final int ICON_NPC = 9;

    private Map<String, BiomePalette> biomePalettes = new LinkedHashMap<>();

    public MinimapGenerator() {
        loadBiomes();
    }

    private void loadBiomes() {
        try (InputStream is = EditorAssetManager.getInstance().getResourceAsStream("/meta/blocks/biomes.json");
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, BiomePalette>>(){}.getType();
            Map<String, BiomePalette> loaded = new Gson().fromJson(reader, type);
            if (loaded != null) {
                biomePalettes.putAll(loaded);
            }
        } catch (Exception e) {
            Framework.getInstance().log("Failed to load biomes.json, using defaults.", LogType.WARNING);
        }
    }

    public Set<String> getAvailableBiomes() {
        return biomePalettes.isEmpty() ? Set.of("FOREST") : biomePalettes.keySet();
    }

    /**
     * Resolves the appropriate color palette for a room based on its metadata.
     *
     * @param meta The level metadata containing the biome identifier.
     * @return The resolved BiomePalette, or a default forest palette if none matches.
     */
    public BiomePalette resolveBiomePalette(LevelMetadata meta) {
        String biomeKey = (meta != null && meta.getBiome() != null) ? meta.getBiome().toUpperCase() : "FOREST";
        return biomePalettes.getOrDefault(biomeKey, DEFAULT_PALETTE);
    }

    /**
     * Parses a 3-panel RGB editor image to create a WorldMapRoomEntry.
     * Automatically extracts collision geometry to render a localized minimap preview for the UI.
     *
     * @param id The ID/name of the level.
     * @param combined The full 3-panel composite image exported by the editor.
     * @param mapX The global X coordinate of this room on the world map.
     * @param mapY The global Y coordinate of this room on the world map.
     * @param meta The metadata for this room (controls colors and ambient properties).
     * @return A newly constructed WorldMapRoomEntry containing the preview buffers.
     */
    public WorldMapRoomEntry createEntryFromCombinedImage(String id, BufferedImage combined, int mapX, int mapY, LevelMetadata meta) {
        int w = combined.getWidth() / 3, h = combined.getHeight();
        BufferedImage panel1 = combined.getSubimage(0, 0, w, h);
        List<WorldMapRoomEntry.TriggerMarker> triggers = new ArrayList<>();
        BufferedImage minimapPreview = renderRoomPreviewAndCollectTriggers(combined, meta, triggers);
        return new WorldMapRoomEntry(id, w, h, mapX, mapY, combined, panel1, minimapPreview, triggers);
    }

    /**
     * Re-renders the localized minimap preview for a room.
     * Useful when the user changes a room's biome and the UI needs an immediate color update.
     *
     * @param entry The room entry to update.
     */
    public void rebuildMinimapPreview(WorldMapRoomEntry entry) {
        if (entry == null || entry.getCombinedImage() == null) return;
        BufferedImage preview = renderRoomPreviewAndCollectTriggers(entry.getCombinedImage(), entry.getLevelMetadata(), null);
        entry.setMinimapPreview(preview);
    }

    /**
     * Core renderer that translates the 3-panel RGB data into a flat, colored minimap image.
     * It parses solid blocks, applies the biome palette, removes tiny 1-tile specs to prevent UI clutter, and draws interactive icons (Save, Shop, NPC, etc.).
     */
    private BufferedImage renderRoomPreviewAndCollectTriggers(BufferedImage combined, LevelMetadata meta, List<WorldMapRoomEntry.TriggerMarker> outTriggers) {
        int w = combined.getWidth() / 3, h = combined.getHeight();
        BufferedImage minimapPreview = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
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

                solid[x][y] = (r1 < 49) || (c2.getBlue() == 34 && c2.getGreen() <= 5);
                if (r1 == 254 && g1 == 254 && b1 < LvlObjType.MAX.ordinal()) {
                    objGrid[x][y] = b1;
                }

                collectIconIfPresent(r1, g1, b1, c3, x, y, w, h, pendingIcons);

                if (outTriggers != null && c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() < LvlTriggerType.MAX.ordinal()) {
                    outTriggers.add(new WorldMapRoomEntry.TriggerMarker(x, y, LvlTriggerType.values()[c3.getBlue()]));
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
            minimapPreview.setRGB(ix, iy, new Color(254, 254, icon[2], 254).getRGB());
        }
        return minimapPreview;
    }

    /**
     * Stitches every individual room on the map canvas into one massive global minimap.png.
     * Automatically patches small 1-3 tile gaps between room exits so the player sees a unified world map in the game engine without pixel seams.
     *
     * @param rooms All placed rooms on the world map canvas.
     * @param worldMeta The root JSON object to populate with final coordinates.
     * @return The final global minimap.png image ready for export.
     */
    public BufferedImage buildGlobalMinimapAndPopulateMetadata(Collection<WorldMapRoomEntry> rooms, WorldMapMetadata worldMeta) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (WorldMapRoomEntry r : rooms) {
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

        for (int x = 0; x < totalW; x++) Arrays.fill(globalObj[x], -1);

        for (WorldMapRoomEntry r : rooms) {
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
                    roomSolid[x][y] = (c1.getRed() < 49) || (c2.getBlue() == 34 && c2.getGreen() <= 5);
                }
            }
            boolean[][] cleanRoomSolid = removeTinySolidSpecs(roomSolid, rw, rh);

            for (int x = 0; x < rw; x++) {
                for (int y = 0; y < rh; y++) {
                    int gx = r.getMapX() + x;
                    int gy = r.getMapY() + y;
                    if (gx < 0 || gx >= totalW || gy < 0 || gy >= totalH) continue;

                    if (globalPalette[gx][gy] == null) globalPalette[gx][gy] = palette;
                    if (!cleanRoomSolid[x][y]) {
                        globalAir[gx][gy] = true;
                        globalPalette[gx][gy] = palette;
                        if (roomSolid[x][y]) globalPlatform[gx][gy] = true;
                    }

                    Color c1 = new Color(combined.getRGB(x, y));
                    Color c3 = new Color(combined.getRGB(rw * 2 + x, y));
                    int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();

                    if (r1 == 254 && g1 == 254 && b1 < LvlObjType.MAX.ordinal()) {
                        globalObj[gx][gy] = b1;
                    }
                    collectIconIfPresent(r1, g1, b1, c3, gx, gy, totalW, totalH, globalIcons);
                }
            }

            RoomNodeMetadata nodeMeta = new RoomNodeMetadata();
            nodeMeta.setMapX(r.getMapX());
            nodeMeta.setMapY(r.getMapY());
            nodeMeta.setTotemId(r.getTotemId());
            nodeMeta.setTotemName((r.getTotemName() != null && !r.getTotemName().trim().isEmpty()) ? r.getTotemName().trim() : null);
            nodeMeta.setExits(new ArrayList<>(r.getExits()));
            worldMeta.getRooms().put(r.getId(), nodeMeta);
        }

        bridgeRoomExitSeamGaps(rooms, globalAir, globalPalette, totalW, totalH);

        boolean[][] globalSolid = new boolean[totalW][totalH];
        for (int x = 0; x < totalW; x++) {
            for (int y = 0; y < totalH; y++) {
                globalSolid[x][y] = !globalAir[x][y];
            }
        }

        int[] globalPixels = new int[totalW * totalH];
        Arrays.fill(globalPixels, ABYSS_VOID_COLOR.getRGB());

        for (int x = 0; x < totalW; x++) {
            for (int y = 0; y < totalH; y++) {
                BiomePalette pal = findNearestPalette(globalPalette, x, y, totalW, totalH, DEFAULT_PALETTE);
                if (globalAir[x][y]) {
                    Color c;
                    if (globalObj[x][y] == LvlObjType.LAVA.ordinal()) c = new Color(235, 95, 25, 254);
                    else if (globalObj[x][y] == LvlObjType.WATER.ordinal()) c = new Color(40, 165, 210, 254);
                    else if (globalObj[x][y] == LvlObjType.BRICK.ordinal()) c = new Color(212, 163, 89, 254);
                    else if (globalPlatform[x][y] || (y + 1 < totalH && globalSolid[x][y + 1])) c = pal.getFloorHighlight();
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
        return minimapOut;
    }

    private void collectIconIfPresent(int r1, int g1, int b1, Color c3, int x, int y, int maxW, int maxH, List<int[]> icons) {
        if (r1 == 254 && g1 == 254 && b1 == LvlObjType.DOOR.ordinal() && c3.getRed() == 254 && c3.getGreen() == 254 && c3.getBlue() == LvlTriggerType.TELEPORT_TAVERN.ordinal()) {
            icons.add(new int[]{x, y, ICON_TAVERN});
        }
        else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TOTEM.ordinal()) {
            icons.add(new int[]{x, y, ICON_SAVE});
        }
        else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.SHOP.ordinal()) {
            icons.add(new int[]{Math.min(maxW - 1, x + 1), Math.min(maxH - 1, y + 2), ICON_SHOP});
        }
        else if (r1 == 254 && g1 == 254 && b1 == LvlObjType.TABLE.ordinal()) {
            icons.add(new int[]{x, Math.min(maxH - 1, y + 1), ICON_CRAFTING});
        }
        else if (r1 == 254 && b1 == 254 && g1 == LvlEnemyType.SPEAR_WOMAN.ordinal()) {
            icons.add(new int[]{x, y, ICON_BOSS});
        }
        else if (c3.getRed() == 0 && c3.getGreen() == 0 && c3.getBlue() < LvlNpcType.MAX.ordinal()) {
            int npcId = c3.getBlue();
            if (npcId == LvlNpcType.BLACKSMITH.ordinal()) {
                icons.add(new int[]{x, y, ICON_BLACKSMITH});
            }
        }
    }

    private void bridgeRoomExitSeamGaps(Collection<WorldMapRoomEntry> rooms, boolean[][] globalAir, BiomePalette[][] globalPalette, int totalW, int totalH) {
        for (WorldMapRoomEntry r : rooms) {
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

    private boolean[][] removeTinySolidSpecs(boolean[][] solid, int w, int h) {
        boolean[][] result = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            System.arraycopy(solid[x], 0, result[x], 0, h);
        }
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                if (!solid[x][y]) continue;
                if (!solid[x][y - 1] && !solid[x][y + 1]) {
                    result[x][y] = false;
                }
            }
        }
        return result;
    }

    private Color computeSmoothWalkableColor(boolean[][] cleanSolid, boolean[][] rawSolid, int objId, int x, int y, int w, int h, BiomePalette palette) {
        if (objId == LvlObjType.LAVA.ordinal()) return new Color(235, 95, 25, 254);
        else if (objId == LvlObjType.WATER.ordinal()) return new Color(40, 165, 210, 254);
        if (objId == LvlObjType.BRICK.ordinal()) return new Color(212, 163, 89, 254);
        if (rawSolid[x][y] && !cleanSolid[x][y]) return palette.getFloorHighlight();
        if (y + 1 < h && cleanSolid[x][y + 1]) return palette.getFloorHighlight();
        boolean nearCeilingOrWall = (y > 0 && cleanSolid[x][y - 1]) || (x > 0 && cleanSolid[x - 1][y]) || (x + 1 < w && cleanSolid[x + 1][y]);
        return nearCeilingOrWall ? palette.getWalkableGridAlt() : palette.getWalkableFill();
    }

    private int findCardinalThenDiagonalDistanceToAir(boolean[][] solid, int cx, int cy, int w, int h) {
        int[][] cardinals = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : cardinals) {
            int nx = cx + d[0], ny = cy + d[1];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny]) return 1;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny]) return 2;
            }
        }
        return 3;
    }
}