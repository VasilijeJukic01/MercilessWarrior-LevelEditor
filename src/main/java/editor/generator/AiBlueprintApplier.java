package editor.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import editor.core.Framework;
import editor.generator.strategy.ExitSide;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlObjType;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Parses full-level and regional Co-Op AI JSON blueprints and applies strict geometric validation, safe enemy patrol-floor snapping, and subterranean Layer 5 safety guardrails.
 */
public class AiBlueprintApplier {

    private final AutoTileRuleEngine tileEngine;
    private final SubterraneanPocketGenerator pocketGenerator;
    private final AutoDecoratorEngine decoEngine;
    private final PlayabilityValidator validator;
    private final EntityPopulator entityPopulator;
    private final Random rand = new Random();

    public AiBlueprintApplier(AutoTileRuleEngine tileEngine, SubterraneanPocketGenerator pocketGenerator, AutoDecoratorEngine decoEngine, PlayabilityValidator validator, EntityPopulator entityPopulator) {
        this.tileEngine = tileEngine;
        this.pocketGenerator = pocketGenerator;
        this.decoEngine = decoEngine;
        this.validator = validator;
        this.entityPopulator = entityPopulator;
    }

    /**
     * Parses a full-level AI JSON or ASCII blueprint, validates jump playability across the selected exits,
     * snaps all items to legal floors/patrol zones, and populates subterranean pockets and decorations.
     */
    public List<Node> buildFullLevelNodesFromAi(Level level, int w, int h, String aiOutput, double decoDensity, Set<ExitSide> exits) {
        String cleaned = extractJsonOrAscii(aiOutput);

        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);
        List<Node> extraNodes = new ArrayList<>();

        if (cleaned.startsWith("{")) {
            JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

            if (root.has("carve") && root.get("carve").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("carve")) {
                    JsonObject r = el.getAsJsonObject();
                    int rx = r.get("x").getAsInt(), ry = r.get("y").getAsInt();
                    int rw = r.get("w").getAsInt(), rh = r.get("h").getAsInt();
                    for (int x = Math.max(3, rx); x < Math.min(w - 3, rx + rw); x++) {
                        for (int y = Math.max(3, ry); y < Math.min(h - 3, ry + rh); y++) {
                            solid[x][y] = false;
                        }
                    }
                }
            }

            if (root.has("fill") && root.get("fill").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("fill")) {
                    JsonObject r = el.getAsJsonObject();
                    int rx = r.get("x").getAsInt(), ry = r.get("y").getAsInt();
                    int rw = r.get("w").getAsInt(), rh = r.get("h").getAsInt();
                    for (int x = Math.max(3, rx); x < Math.min(w - 3, rx + rw); x++) {
                        for (int y = Math.max(3, ry); y < Math.min(h - 3, ry + rh); y++) {
                            solid[x][y] = true;
                        }
                    }
                }
            }

            validator.ensurePlayable(solid, w, h, exits);

            if (root.has("items") && root.get("items").isJsonArray()) {
                Set<String> occupied = new HashSet<>();
                for (JsonElement el : root.getAsJsonArray("items")) {
                    JsonObject item = el.getAsJsonObject();
                    if (!item.has("type")) continue;
                    String type = normalizeAssetType(item.get("type").getAsString());
                    if (type.startsWith("SPAWN") || type.startsWith("LOAD")) continue;

                    int rawX = item.has("x") ? item.get("x").getAsInt() : (w / 2);
                    int rawY = item.has("y") ? item.get("y").getAsInt() : (h / 2);
                    int x = Math.max(4, Math.min(w - 5, rawX));
                    int y = Math.max(3, Math.min(h - 4, rawY));

                    if (isEnemyType(type)) {
                        Point safePt = entityPopulator.findNearestSafeEnemyFloor(solid, x, y, 4, w - 5, 3, h - 3, w, h);
                        if (safePt != null && occupied.add(safePt.x + "," + safePt.y)) {
                            extraNodes.add(createEnemyByType(level, safePt.x, safePt.y, type));
                        }
                        continue;
                    }

                    int floorY = entityPopulator.snapToFloorY(solid, x, y, h);
                    if (floorY == -1 || !occupied.add(x + "," + floorY)) continue;

                    Node placed = createGameplayObjectByType(level, solid, x, floorY, type);
                    if (placed != null) extraNodes.add(placed);
                }
            }
        }
        else {
            String[] lines = Arrays.stream(cleaned.split("\r?\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && (s.contains("#") || s.contains(".")))
                    .toArray(String[]::new);
            int parsedH = Math.min(h, lines.length);
            for (int y = 0; y < parsedH; y++) {
                String row = lines[y];
                for (int x = 0; x < w; x++) {
                    char c = (x < row.length()) ? row.charAt(x) : '#';
                    solid[x][y] = (c == '#');
                }
            }
            validator.ensurePlayable(solid, w, h, exits);
        }

        List<Node> newNodes = new ArrayList<>(tileEngine.generateSolidTiles(level, solid, w, h));
        pocketGenerator.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(entityPopulator.generateSpawnsAndTriggers(level, solid, w, h, exits));
        newNodes.addAll(extraNodes);
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));
        return newNodes;
    }

    /**
     * Parses a regional Co-Op AI JSON response and applies validated terrain edits, Layer 5 structures, and snapped items/decorations.
     */
    public List<Node> buildRegionalAiNodes(Level level, Rectangle region, boolean[][] solidSnapshot, List<Node> nodesSnapshot, String prompt, String aiJson) {
        int lvlW = solidSnapshot.length, lvlH = solidSnapshot[0].length;
        String lowerPrompt = prompt.toLowerCase();
        String cleaned = extractJsonOrAscii(aiJson);
        JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

        boolean clearExistingDecos = (root.has("clearExistingDecorations") && root.get("clearExistingDecorations").getAsBoolean()) || lowerPrompt.contains("remove") || lowerPrompt.contains("clear");

        JsonArray l3Carves = new JsonArray();
        if (root.has("layer3Carve") && root.get("layer3Carve").isJsonArray()) l3Carves.addAll(root.getAsJsonArray("layer3Carve"));
        if (root.has("carve") && root.get("carve").isJsonArray() && (!root.has("subterraneanPocket") || !root.get("subterraneanPocket").getAsBoolean())) {
            l3Carves.addAll(root.getAsJsonArray("carve"));
        }

        for (JsonElement el : l3Carves) {
            JsonObject r = el.getAsJsonObject();
            int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
            int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
            int rw = r.get("w").getAsInt();
            int rh = r.get("h").getAsInt();
            for (int x = Math.max(region.x, rx); x < Math.min(region.x + region.width, rx + rw); x++) {
                for (int y = Math.max(region.y, ry); y < Math.min(region.y + region.height, ry + rh); y++) {
                    if (x >= 1 && x < lvlW - 1 && y >= 1 && y < lvlH - 1) solidSnapshot[x][y] = false;
                }
            }
        }

        JsonArray l3Fills = new JsonArray();
        if (root.has("layer3Fill") && root.get("layer3Fill").isJsonArray()) l3Fills.addAll(root.getAsJsonArray("layer3Fill"));
        if (root.has("fill") && root.get("fill").isJsonArray()) l3Fills.addAll(root.getAsJsonArray("fill"));

        for (JsonElement el : l3Fills) {
            JsonObject r = el.getAsJsonObject();
            int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
            int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
            int rw = Math.max(1, r.get("w").getAsInt());
            int rh = Math.max(1, r.get("h").getAsInt());
            for (int x = Math.max(region.x, rx); x < Math.min(region.x + region.width, rx + rw); x++) {
                for (int y = Math.max(region.y, ry); y < Math.min(region.y + region.height, ry + rh); y++) {
                    if (x >= 0 && x < lvlW && y >= 0 && y < lvlH) solidSnapshot[x][y] = true;
                }
            }
        }

        if (!l3Carves.isEmpty() || !l3Fills.isEmpty()) {
            eliminateUndergroundAndSealedAir(solidSnapshot, region, lvlW, lvlH, lowerPrompt);
            healRegionSeams(solidSnapshot, region, lvlW, lvlH);
        }

        int[][] placedL5 = new int[lvlW][lvlH];
        for (int x = 0; x < lvlW; x++) Arrays.fill(placedL5[x], -1);

        for (Node n : nodesSnapshot) {
            if (n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getLayer() == 5 && region.contains(t.getX(), t.getY())) {
                if (solidSnapshot[t.getX()][t.getY()] && !clearExistingDecos) {
                    placedL5[t.getX()][t.getY()] = t.getRed();
                }
            }
        }

        applyAiLayer5Structures(root, region, solidSnapshot, placedL5, lvlW, lvlH);

        List<Node> regionNodes = new ArrayList<>();
        for (int x = region.x; x < region.x + region.width; x++) {
            for (int y = region.y; y < region.y + region.height; y++) {
                if (x < 0 || x >= lvlW || y < 0 || y >= lvlH || !solidSnapshot[x][y]) continue;

                if (placedL5[x][y] != -1) {
                    Tile t5 = new Tile("", level, TileType.SOLID, x, y, placedL5[x][y], 255, 255);
                    t5.setLayer(5);
                    regionNodes.add(t5);

                    Tile black = new Tile("", level, TileType.DECO, x, y, 254, 2, 34);
                    black.setLayer(2);
                    regionNodes.add(black);
                }
                else {
                    int l3Id = tileEngine.pickExactTileId(solidSnapshot, x, y, lvlW, lvlH);
                    Tile t3 = new Tile("", level, TileType.SOLID, x, y, l3Id, 255, 254);
                    t3.setGreen(254);
                    t3.setLayer(3);
                    regionNodes.add(t3);
                }
            }
        }

        Set<String> occupiedPanel1Cells = new HashSet<>();
        List<DecoMetadata> decoCatalog = Framework.getInstance().getStorage().getDecoMetadataMap().get("Forest");
        for (Node n : nodesSnapshot) {
            if (!(n instanceof Tile t) || !region.contains(t.getX(), t.getY())) continue;
            int tx = t.getX(), ty = t.getY();

            if (t.getTileType() == TileType.DECO && t.getBlue() != 34 && !clearExistingDecos) {
                if (isExistingDecoStillValid(t, solidSnapshot, placedL5, decoCatalog, lvlW, lvlH)) {
                    regionNodes.add(t);
                }
            }
            else if ((t.getTileType() == TileType.OBJECT || t.getTileType() == TileType.ENEMY || t.getTileType() == TileType.TRIGGER || t.getTileType() == TileType.NPC) && !clearExistingDecos && !solidSnapshot[tx][ty] && ty + 1 < lvlH && solidSnapshot[tx][ty + 1]) {
                regionNodes.add(t);
                occupiedPanel1Cells.add(tx + "," + ty);
            }
        }

        regionNodes.addAll(parseExplicitAiPlacements(level, region, solidSnapshot, placedL5, occupiedPanel1Cells, root, lvlW, lvlH));
        return regionNodes;
    }

    private String extractJsonOrAscii(String raw) {
        String cleaned = raw.replace("```json", "").replace("```", "").trim();
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned;
    }

    private void applyAiLayer5Structures(JsonObject root, Rectangle region, boolean[][] solidSnapshot, int[][] placedL5, int lvlW, int lvlH) {
        JsonArray caverns = new JsonArray();
        if (root.has("layer5Caverns") && root.get("layer5Caverns").isJsonArray()) {
            caverns.addAll(root.getAsJsonArray("layer5Caverns"));
        }
        if (root.has("subterraneanPocket") && root.get("subterraneanPocket").getAsBoolean() && root.has("carve") && root.get("carve").isJsonArray()) {
            caverns.addAll(root.getAsJsonArray("carve"));
        }

        if (!caverns.isEmpty()) {
            boolean[][] l5Grid = new boolean[lvlW][lvlH];
            for (int x = 0; x < lvlW; x++) Arrays.fill(l5Grid[x], true);

            for (JsonElement el : caverns) {
                JsonObject r = el.getAsJsonObject();
                int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
                int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
                int rw = Math.max(2, r.get("w").getAsInt());
                int rh = Math.max(2, r.get("h").getAsInt());
                int x1 = Math.max(region.x + 1, rx);
                int x2 = Math.min(region.x + region.width - 2, rx + rw - 1);
                int y1 = Math.max(region.y + 1, ry);
                int y2 = Math.min(region.y + region.height - 2, ry + rh - 1);
                for (int x = x1; x <= x2; x++) {
                    for (int y = y1; y <= y2; y++) {
                        if (isDeepSolidCell(solidSnapshot, x, y, lvlW, lvlH)) {
                            l5Grid[x][y] = false;
                        }
                    }
                }
            }

            for (int x = region.x; x < region.x + region.width; x++) {
                for (int y = region.y; y < region.y + region.height; y++) {
                    if (isDeepSolidCell(solidSnapshot, x, y, lvlW, lvlH) && l5Grid[x][y]) {
                        int id = tileEngine.pickExactTileId(l5Grid, x, y, lvlW, lvlH);
                        if (id != 8) placedL5[x][y] = id;
                    }
                }
            }
        }

        if (root.has("layer5Hills") && root.get("layer5Hills").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("layer5Hills")) {
                JsonObject r = el.getAsJsonObject();
                int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
                int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
                int rw = Math.max(2, r.get("w").getAsInt());
                int rh = Math.max(1, r.get("h").getAsInt());

                int x1 = Math.max(region.x, rx);
                int x2 = Math.min(region.x + region.width - 1, rx + rw - 1);
                int topY = Math.max(region.y, Math.min(region.y + region.height - 1, ry));
                int botY = Math.min(region.y + region.height - 1, topY + rh);
                if (x2 <= x1 || !isDeepSolidCell(solidSnapshot, x1, topY, lvlW, lvlH)) continue;

                for (int x = x1; x <= x2; x++) {
                    if (!isDeepSolidCell(solidSnapshot, x, topY, lvlW, lvlH)) continue;
                    if (x == x1) {
                        boolean higherLeft = (topY > 1 && placedL5[x1][topY - 1] != -1);
                        placedL5[x][topY] = higherLeft ? 11 : 0;
                    }
                    else if (x == x2) {
                        boolean higherRight = (topY > 1 && placedL5[x2][topY - 1] != -1);
                        placedL5[x][topY] = higherRight ? 12 : 2;
                    }
                    else {
                        placedL5[x][topY] = 1;
                    }
                }
                for (int y = topY + 1; y <= botY; y++) {
                    if (isDeepSolidCell(solidSnapshot, x1, y, lvlW, lvlH) && placedL5[x1][y] == -1) placedL5[x1][y] = 7;
                    if (isDeepSolidCell(solidSnapshot, x2, y, lvlW, lvlH) && placedL5[x2][y] == -1) placedL5[x2][y] = 9;
                }
            }
        }

        if (root.has("layer5Bridges") && root.get("layer5Bridges").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("layer5Bridges")) {
                JsonObject r = el.getAsJsonObject();
                int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
                int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
                int rw = r.has("w") ? Math.max(1, r.get("w").getAsInt()) : 1;

                int x1 = Math.max(region.x, rx);
                int x2 = Math.min(region.x + region.width - 1, rx + rw - 1);
                int by = Math.max(region.y, Math.min(region.y + region.height - 1, ry));
                if (!isDeepSolidCell(solidSnapshot, x1, by, lvlW, lvlH)) continue;

                if (x1 == x2) placedL5[x1][by] = 24;
                else {
                    for (int x = x1; x <= x2; x++) {
                        if (!isDeepSolidCell(solidSnapshot, x, by, lvlW, lvlH)) continue;
                        placedL5[x][by] = (x == x1) ? 21 : (x == x2) ? 23 : 22;
                    }
                }
            }
        }
    }

    private List<Node> parseExplicitAiPlacements(Level level, Rectangle region, boolean[][] solidSnapshot, int[][] placedL5, Set<String> occupiedPanel1Cells, JsonObject root, int lvlW, int lvlH) {
        JsonArray combinedPlacements = new JsonArray();
        for (String key : new String[]{"items", "enemies", "objects", "decorations"}) {
            if (root.has(key) && root.get(key).isJsonArray()) {
                combinedPlacements.addAll(root.getAsJsonArray(key));
            }
        }

        List<Node> explicitAiPlacements = new ArrayList<>();
        List<DecoMetadata> decoCatalog = Framework.getInstance().getStorage().getDecoMetadataMap().get("Forest");

        for (JsonElement el : combinedPlacements) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("type")) continue;

            String type = normalizeAssetType(obj.get("type").getAsString());
            int rawX = obj.has("x") ? obj.get("x").getAsInt() : (region.x + region.width / 2);
            int rawY = obj.has("y") ? obj.get("y").getAsInt() : (region.y + region.height / 2);
            int gx = resolveGlobalCoord(rawX, region.x, region.width);
            int gyInput = resolveGlobalCoord(rawY, region.y, region.height);

            if (isEnemyType(type)) {
                Point safePt = entityPopulator.findNearestSafeEnemyFloor(solidSnapshot, gx, gyInput, region.x, region.x + region.width - 1, region.y, region.y + region.height - 1, lvlW, lvlH);
                if (safePt != null && occupiedPanel1Cells.add(safePt.x + "," + safePt.y)) {
                    explicitAiPlacements.add(createEnemyByType(level, safePt.x, safePt.y, type));
                }
                continue;
            }

            int l3FloorAirY = snapToRegionFloorY(solidSnapshot, region, gx);
            if (l3FloorAirY != -1 && occupiedPanel1Cells.contains(gx + "," + l3FloorAirY)) {
                int altX = findNearestFreeFloorX(solidSnapshot, occupiedPanel1Cells, region, gx, l3FloorAirY);
                if (altX != -1) {
                    gx = altX;
                    l3FloorAirY = snapToRegionFloorY(solidSnapshot, region, gx);
                }
            }

            if (l3FloorAirY != -1) {
                Node placedObj = createGameplayObjectByType(level, solidSnapshot, gx, l3FloorAirY, type);
                if (placedObj != null) {
                    explicitAiPlacements.add(placedObj);
                    occupiedPanel1Cells.add(gx + "," + l3FloorAirY);
                    continue;
                }
            }

            if (decoCatalog != null) {
                int decoIdx = findDecoIndexByName(decoCatalog, type);
                if (decoIdx != -1) {
                    if (decoIdx >= 122 && decoIdx <= 131 && l3FloorAirY != -1) {
                        Tile dt = new Tile("", level, TileType.DECO, gx, l3FloorAirY, 254, 4, decoIdx);
                        dt.setLayer(4);
                        dt.setScaleX(1.04);
                        explicitAiPlacements.add(dt);
                        continue;
                    }

                    DecoMetadata meta = decoCatalog.get(decoIdx);
                    int tilesTall = Math.max(1, meta.getHei() / 64);
                    int l5SurfaceY = findNearestLayer5SurfaceY(placedL5, region, gx, gyInput, type.startsWith("VINE") || type.startsWith("MOSS"));
                    if (l5SurfaceY != -1) {
                        int anchorY = (type.startsWith("VINE") || type.startsWith("MOSS")) ? (l5SurfaceY + 1) : Math.max(0, (l5SurfaceY - 1) - (tilesTall - 1));
                        Tile dt = new Tile("", level, TileType.DECO, gx, anchorY, 254, 4, decoIdx);
                        dt.setLayer(4);
                        explicitAiPlacements.add(dt);
                    } else if (l3FloorAirY != -1) {
                        int ceilY = entityPopulator.snapToCeilingY(solidSnapshot, gx, gyInput);
                        int anchorY = (type.startsWith("VINE") || type.startsWith("MOSS")) ? Math.max(0, ceilY != -1 ? ceilY : gyInput) : Math.max(0, l3FloorAirY - (tilesTall - 1));
                        int layer = (tilesTall == 1) ? 4 : 0;
                        Tile dt = new Tile("", level, TileType.DECO, gx, anchorY, 254, layer, decoIdx);
                        dt.setLayer(layer);
                        explicitAiPlacements.add(dt);
                    }
                }
            }
        }
        return explicitAiPlacements;
    }

    private boolean isEnemyType(String type) {
        return switch (type) {
            case "SKELETON", "E0", "GHOUL", "E1", "SPEAR_WOMAN", "LANCER", "E2", "KNIGHT", "E3", "WRAITH", "E4" -> true;
            default -> false;
        };
    }

    private Node createEnemyByType(Level level, int x, int y, String type) {
        return switch (type) {
            case "GHOUL", "E1" -> entityPopulator.createEnemy(level, x, y, LvlEnemyType.GHOUL.ordinal());
            case "SPEAR_WOMAN", "LANCER", "E2" -> entityPopulator.createEnemy(level, x, y, LvlEnemyType.SPEAR_WOMAN.ordinal());
            case "KNIGHT", "E3" -> entityPopulator.createEnemy(level, x, y, LvlEnemyType.KNIGHT.ordinal());
            case "WRAITH", "E4" -> entityPopulator.createEnemy(level, x, y, LvlEnemyType.WRAITH.ordinal());
            default -> entityPopulator.createEnemy(level, x, y, LvlEnemyType.SKELETON.ordinal());
        };
    }

    private Node createGameplayObjectByType(Level level, boolean[][] solid, int x, int floorAirY, String type) {
        return switch (type) {
            case "BLUE_POTION", "MANA_POTION", "O0" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.BLUE_POTION.ordinal());
            case "RED_POTION", "HEALTH_POTION", "POTION", "O1" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.RED_POTION.ordinal());
            case "CRATE", "BOX", "O2" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.CRATE.ordinal());
            case "BARREL", "O3" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.BARREL.ordinal());
            case "SPIKES", "SPIKE", "SPIKES_UP", "O4" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.SPIKES_UP.ordinal());
            case "ARROW_TRAP_LEFT", "ARROW_TRAP", "O5" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.ARROW_TRAP_LEFT.ordinal());
            case "ARROW_TRAP_RIGHT", "O6" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.ARROW_TRAP_RIGHT.ordinal());
            case "SHOP", "O8" -> entityPopulator.createObj(level, x, floorAirY - 3, LvlObjType.SHOP.ordinal());
            case "BLOCKER", "O9" -> entityPopulator.createObj(level, x, floorAirY - 2, LvlObjType.BLOCKER.ordinal());
            case "BLACKSMITH", "O10" -> entityPopulator.createObj(level, x, floorAirY - 1, LvlObjType.BLACKSMITH.ordinal());
            case "DOG", "O11" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.DOG.ordinal());
            case "TOTEM", "SAVE_TOTEM", "O12" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.TOTEM.ordinal());
            case "SMASH_TRAP", "O13" -> {
                int ceilY = entityPopulator.snapToCeilingY(solid, x, floorAirY);
                yield (ceilY != -1) ? entityPopulator.createObj(level, x, ceilY, LvlObjType.SMASH_TRAP.ordinal()) : null;
            }
            case "CANDLE", "LAMP", "LANTERN", "O14" -> entityPopulator.createObj(level, x, floorAirY - 1, LvlObjType.CANDLE.ordinal());
            case "LOOT", "CHEST", "O15" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.LOOT.ordinal());
            case "TABLE", "CRAFTING_TABLE", "O16" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.TABLE.ordinal());
            case "BOARD", "SIGN", "O17" -> entityPopulator.createObj(level, x, floorAirY - 1, LvlObjType.BOARD.ordinal());
            case "LAVA", "O19" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.LAVA.ordinal());
            case "BRICK", "O20" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.BRICK.ordinal());
            case "JUMP_PAD", "JUMPPAD", "O21" -> entityPopulator.createObj(level, x, floorAirY, LvlObjType.JUMP_PAD.ordinal());
            case "DOOR", "O27" -> entityPopulator.createObj(level, x, floorAirY - 1, LvlObjType.DOOR.ordinal());
            default -> null;
        };
    }

    private int findNearestLayer5SurfaceY(int[][] placedL5, Rectangle region, int x, int targetY, boolean ceiling) {
        int bestY = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int y = Math.max(1, region.y); y < Math.min(placedL5[0].length - 1, region.y + region.height); y++) {
            int id = placedL5[x][y];
            boolean match = ceiling ? (id == 14 || id == 15 || id == 16 || id == 22) : (id == 0 || id == 1 || id == 2 || id == 11 || id == 12 || id == 21 || id == 22 || id == 23 || id == 24);
            if (match) {
                int dist = Math.abs(y - targetY);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestY = y;
                }
            }
        }
        return bestY;
    }

    private void eliminateUndergroundAndSealedAir(boolean[][] solid, Rectangle region, int w, int h, String lowerPrompt) {
        boolean fillDownward = lowerPrompt.contains("remove hole") || lowerPrompt.contains("fill hole") || lowerPrompt.contains("fill pit") || lowerPrompt.contains("connect") || lowerPrompt.contains("flat");

        if (fillDownward) {
            for (int x = Math.max(0, region.x); x < Math.min(w, region.x + region.width); x++) {
                boolean seenFloorInRegion = false;
                for (int y = Math.max(1, region.y); y < Math.min(h, region.y + region.height); y++) {
                    if (solid[x][y] && !solid[x][y - 1] && isWideFloorSpan(solid, x, y, 5, w)) {
                        seenFloorInRegion = true;
                    }
                    if (seenFloorInRegion) solid[x][y] = true;
                }
            }
        }

        boolean[][] reachableAir = new boolean[w][h];
        Queue<Point> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!solid[x][y] && !region.contains(x, y)) {
                    reachableAir[x][y] = true;
                    queue.add(new Point(x, y));
                }
            }
        }
        if (queue.isEmpty()) {
            for (int x = region.x; x < region.x + region.width; x++) {
                for (int y = region.y; y < region.y + region.height; y++) {
                    if (!solid[x][y]) {
                        reachableAir[x][y] = true;
                        queue.add(new Point(x, y));
                        break;
                    }
                }
                if (!queue.isEmpty()) break;
            }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (int[] d : dirs) {
                int nx = p.x + d[0], ny = p.y + d[1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny] && !reachableAir[nx][ny]) {
                    reachableAir[nx][ny] = true;
                    queue.add(new Point(nx, ny));
                }
            }
        }

        for (int x = Math.max(0, region.x); x < Math.min(w, region.x + region.width); x++) {
            for (int y = Math.max(0, region.y); y < Math.min(h, region.y + region.height); y++) {
                if (!solid[x][y] && !reachableAir[x][y]) {
                    solid[x][y] = true;
                }
            }
        }
    }

    private boolean isWideFloorSpan(boolean[][] solid, int cx, int y, int minSpan, int w) {
        int count = 1;
        for (int x = cx - 1; x >= 0 && solid[x][y]; x--) count++;
        for (int x = cx + 1; x < w && solid[x][y]; x++) count++;
        return count >= minSpan;
    }

    private boolean isExistingDecoStillValid(Tile t, boolean[][] solid, int[][] placedL5, List<DecoMetadata> catalog, int w, int h) {
        int tx = t.getX(), ty = t.getY();
        int decoId = t.getBlue();
        if (decoId == 35 || decoId == 36 || decoId == 38 || decoId == 39) return !solid[tx][ty];
        if (t.getLayer() == 4 && ty + 1 < h && placedL5[tx][ty + 1] != -1) return true;

        int tilesTall = 1;
        int tilesWide = 1;
        if (catalog != null && decoId >= 0 && decoId < catalog.size()) {
            DecoMetadata meta = catalog.get(decoId);
            tilesTall = Math.max(1, meta.getHei() / 64);
            tilesWide = Math.max(1, meta.getWid() / 64);
        }

        for (int dx = 0; dx < tilesWide; dx++) {
            for (int dy = 0; dy < tilesTall; dy++) {
                int cx = tx + dx, cy = ty + dy;
                if (cx >= w || cy >= h || solid[cx][cy]) return false;
            }
        }

        int baseRowBelow = ty + tilesTall;
        return baseRowBelow < h && solid[tx][baseRowBelow];
    }

    private boolean isDeepSolidCell(boolean[][] solid, int cx, int cy, int w, int h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h || !solid[nx][ny]) return false;
            }
        }
        return true;
    }

    private int resolveGlobalCoord(int val, int regionStart, int span) {
        if (val >= regionStart && val < regionStart + span) return val;
        if (val >= 0 && val < span) return regionStart + val;
        return Math.max(regionStart, Math.min(regionStart + span - 1, val));
    }

    private String normalizeAssetType(String raw) {
        String s = raw.toUpperCase().trim();
        if (s.contains("(")) s = s.substring(0, s.indexOf('(')).trim();
        if (s.contains("=")) s = s.substring(s.indexOf('=') + 1).trim();
        if (s.contains(":")) s = s.substring(0, s.indexOf(':')).trim();
        return s;
    }

    private int findNearestFreeFloorX(boolean[][] solid, Set<String> occupied, Rectangle region, int startX, int startY) {
        for (int dist = 1; dist < region.width; dist++) {
            for (int sign : new int[]{1, -1}) {
                int cx = startX + dist * sign;
                if (cx >= region.x && cx < region.x + region.width) {
                    int fy = snapToRegionFloorY(solid, region, cx);
                    if (fy != -1 && !occupied.contains(cx + "," + fy)) {
                        return cx;
                    }
                }
            }
        }
        return -1;
    }

    private void healRegionSeams(boolean[][] solid, Rectangle region, int w, int h) {
        int[] borderCols = {region.x, region.x + region.width - 1};
        for (int bx : borderCols) {
            if (bx <= 1 || bx >= w - 2) continue;
            for (int y = Math.max(1, region.y); y <= Math.min(h - 2, region.y + region.height); y++) {
                if (!solid[bx][y] && solid[bx - 1][y] && solid[bx + 1][y] && solid[bx][y + 1]) {
                    solid[bx][y] = true;
                }
                if (solid[bx][y] && !solid[bx - 1][y] && !solid[bx + 1][y]) {
                    solid[bx][y] = false;
                }
            }
        }
    }

    private int findDecoIndexByName(List<DecoMetadata> catalog, String name) {
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).getId().equalsIgnoreCase(name)) return i;
        }
        return switch (name) {
            case "UNDER_GRASS", "UNDER_GRASS_STRIP", "MOSS_STRIP", "LEDGE_STRIP" -> 122 + rand.nextInt(10);
            case "TREE", "TREES" -> {
                int[] pool = {59, 60, 61, 81, 82, 84};
                yield pool[rand.nextInt(pool.length)];
            }
            case "BUSH", "BUSHES" -> (rand.nextBoolean()) ? 25 : (42 + rand.nextInt(3));
            case "ROCK", "STONE", "BOULDER", "BIG_STONE" -> rand.nextInt(3);
            case "SHROOM", "MUSHROOM", "MUSHROOMS", "SHROOMS" -> 55 + rand.nextInt(4);
            case "CRYSTAL", "CRYSTALS" -> 64 + rand.nextInt(2);
            case "CRYSTAL_MOUND", "FISSURE" -> rand.nextBoolean() ? 89 : 106;
            case "FLOWER", "FLOWERS" -> 45 + rand.nextInt(2);
            case "LOG", "LOGS", "HOLLOW_LOG" -> {
                int[] pool = {48, 49, 50, 85, 94, 95, 110};
                yield pool[rand.nextInt(pool.length)];
            }
            case "STATUE", "SHRINE", "RUIN", "CRYPT", "TOMB" -> {
                int[] pool = {83, 86, 87, 88};
                yield pool[rand.nextInt(pool.length)];
            }
            case "COCOON", "HANGING_SHROOMS", "HANGING_PODS" -> {
                int[] pool = {73, 74, 75, 80};
                yield pool[rand.nextInt(pool.length)];
            }
            case "PILLAR", "COLUMN", "TAPROOT" -> {
                int[] pool = {53, 96, 104, 114};
                yield pool[rand.nextInt(pool.length)];
            }
            case "TAVERN", "BUILDING", "HOUSE" -> 66;
            case "THORN", "THORNS" -> 3 + rand.nextInt(3);
            case "PLANT", "GRASS" -> 6 + rand.nextInt(11);
            case "VINE", "VINES" -> 17 + rand.nextInt(5);
            case "MOSS" -> 22 + rand.nextInt(2);
            default -> -1;
        };
    }

    private int snapToRegionFloorY(boolean[][] solid, Rectangle region, int x) {
        int maxY = Math.min(solid[0].length - 2, region.y + region.height + 1);
        int minY = Math.max(1, region.y);
        for (int y = minY; y <= maxY; y++) {
            if (!solid[x][y] && !solid[x][Math.max(0, y - 1)] && solid[x][y + 1]) {
                return y;
            }
        }
        return -1;
    }
}