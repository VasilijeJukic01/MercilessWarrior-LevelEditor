package editor.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.command.commands.RegionPatchCommand;
import editor.core.Framework;
import editor.generator.strategy.RoomArchetype;
import editor.logger.LogType;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Facade that coordinates room carving strategies, playability validation, auto-tiling, auto-decoration, AI blueprint parsing, and undoable command execution.
 */
public class LevelGeneratorFacade {

    private final AutoTileRuleEngine tileEngine = new AutoTileRuleEngine();
    private final AutoDecoratorEngine decoEngine = new AutoDecoratorEngine();
    private final AiPromptService aiService = new AiPromptService();
    private final PlayabilityValidator validator = new PlayabilityValidator();
    private final Random rand = new Random();

    /**
     * Generates a complete procedural level using the specified archetype and density settings.
     *
     * @param level        The target level to populate.
     * @param w            Grid width in tiles.
     * @param h            Grid height in tiles.
     * @param archetype    The room carving archetype strategy.
     * @param enemyDensity Enemy and object density in the range {@code [0.0, 1.0]}.
     * @param decoDensity  Decoration density in the range {@code [0.0, 1.0]}.
     */
    public void generateProcedural(Level level, int w, int h, RoomArchetype archetype, double enemyDensity, double decoDensity) {
        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);

        List<Node> newNodes = new ArrayList<>();
        archetype.getStrategy().carve(level, solid, newNodes, w, h, rand);
        if (archetype != RoomArchetype.SMASH_GAUNTLET) {
            validator.ensurePlayable(solid, w, h);
        }

        newNodes.addAll(tileEngine.generateSolidTiles(level, solid, w, h));
        tileEngine.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(generateSpawnsAndTriggers(level, solid, w, h));
        if (archetype != RoomArchetype.SMASH_GAUNTLET) {
            newNodes.addAll(generateEntitiesAndObjects(level, solid, w, h, enemyDensity));
        }
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Generated " + archetype);
    }

    /**
     * Re-computes 8-neighbor auto-tile indices for all Layer 3 solid terrain currently in the level.
     *
     * @param level The target level to auto-tile.
     */
    public void autoTileCurrentLevel(Level level) {
        int w = level.getWidth();
        int h = level.getHeight();
        boolean[][] solid = extractSolidGrid(level, w, h);

        List<Node> newNodes = new ArrayList<>();
        for (Node n : level.getChildren()) {
            if (((Tile) n).getTileType() != TileType.SOLID) newNodes.add(n);
        }
        newNodes.addAll(tileEngine.generateSolidTiles(level, solid, w, h));

        executeBatchCommand(level, newNodes, w, h, "Auto-tiled solid terrain");
    }

    /**
     * Regenerates all subterranean Layer 5 wall pockets and Panel 2 decorations for the current level terrain.
     *
     * @param level       The target level to decorate.
     * @param decoDensity Decoration density in the range {@code [0.0, 1.0]}.
     */
    public void autoDecorateCurrentLevel(Level level, double decoDensity) {
        int w = level.getWidth(), h = level.getHeight();
        boolean[][] solid = extractSolidGrid(level, w, h);

        List<Node> newNodes = new ArrayList<>();
        for (Node n : level.getChildren()) {
            Tile t = (Tile) n;
            if (t.getTileType() != TileType.DECO && t.getTileType() != TileType.SOLID) {
                newNodes.add(n);
            }
        }
        newNodes.addAll(tileEngine.generateSolidTiles(level, solid, w, h));
        tileEngine.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Auto-decorated level");
    }

    /**
     * Fetches a level blueprint from the AI service or returns raw ASCII directly if pasted by the user.
     */
    public String fetchAiAscii(int w, int h, String promptOrAscii, String apiKey) throws Exception {
        if (promptOrAscii.contains("###") && promptOrAscii.contains("...")) {
            return promptOrAscii;
        }
        return aiService.requestAiBlueprint(w, h, promptOrAscii, apiKey);
    }

    /**
     * Parses a JSON blueprint or ASCII grid, validates jump reachability, and applies auto-tiling and decorations.
     */
    public void applyAsciiToLevel(Level level, int w, int h, String aiOutput, double decoDensity) {
        String cleaned = aiOutput.replace("```json", "").replace("```", "").trim();

        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);
        List<Node> extraNodes = new ArrayList<>();

        if (cleaned.startsWith("{")) {
            JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

            if (root.has("carve")) {
                for (JsonElement el : root.getAsJsonArray("carve")) {
                    JsonObject r = el.getAsJsonObject();
                    int rx = r.get("x").getAsInt(), ry = r.get("y").getAsInt();
                    int rw = r.get("w").getAsInt(), rh = r.get("h").getAsInt();
                    int startX = Math.max(3, rx);
                    int endX = Math.min(w - 3, rx + rw);
                    int startY = Math.max(3, ry);
                    int endY = Math.min(h - 3, ry + rh);
                    for (int x = startX; x < endX; x++) {
                        for (int y = startY; y < endY; y++) {
                            solid[x][y] = false;
                        }
                    }
                }
            }

            if (root.has("fill")) {
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

            carveBorderTunnels(solid, w, h);
            validator.ensurePlayable(solid, w, h);

            if (root.has("items")) {
                Set<String> occupied = new HashSet<>();
                for (JsonElement el : root.getAsJsonArray("items")) {
                    JsonObject item = el.getAsJsonObject();
                    String type = item.get("type").getAsString().toUpperCase();
                    if (type.startsWith("SPAWN") || type.startsWith("LOAD")) continue;

                    int x = Math.max(4, Math.min(w - 5, item.get("x").getAsInt()));
                    int rawY = Math.max(3, Math.min(h - 4, item.get("y").getAsInt()));
                    int y = snapToFloorY(solid, x, rawY, h);
                    if (y == -1 || !occupied.add(x + "," + y)) continue;

                    switch (type) {
                        case "SKELETON" -> extraNodes.add(createEnemy(level, x, y, LvlEnemyType.SKELETON.ordinal()));
                        case "GHOUL" -> extraNodes.add(createEnemy(level, x, y, LvlEnemyType.GHOUL.ordinal()));
                        case "KNIGHT" -> extraNodes.add(createEnemy(level, x, y, LvlEnemyType.KNIGHT.ordinal()));
                        case "WRAITH" -> extraNodes.add(createEnemy(level, x, y, LvlEnemyType.WRAITH.ordinal()));
                        case "SPIKES" -> extraNodes.add(createObj(level, x, y, LvlObjType.SPIKES_UP.ordinal()));
                        case "LAVA" -> extraNodes.add(createObj(level, x, y, LvlObjType.LAVA.ordinal()));
                        case "JUMP_PAD" -> extraNodes.add(createObj(level, x, y, LvlObjType.JUMP_PAD.ordinal()));
                        case "CRATE" -> extraNodes.add(createObj(level, x, y, LvlObjType.CRATE.ordinal()));
                        case "BARREL" -> extraNodes.add(createObj(level, x, y, LvlObjType.BARREL.ordinal()));
                        case "CANDLE" -> extraNodes.add(createObj(level, x, y - 1, LvlObjType.CANDLE.ordinal()));
                        case "TOTEM" -> extraNodes.add(createObj(level, x, y, LvlObjType.TOTEM.ordinal()));
                        case "SMASH_TRAP" -> {
                            int ceilY = snapToCeilingY(solid, x, y);
                            if (ceilY != -1) extraNodes.add(createObj(level, x, ceilY, LvlObjType.SMASH_TRAP.ordinal()));
                        }
                    }
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
            carveBorderTunnels(solid, w, h);
            validator.ensurePlayable(solid, w, h);
        }

        List<Node> newNodes = new ArrayList<>(tileEngine.generateSolidTiles(level, solid, w, h));
        tileEngine.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(generateSpawnsAndTriggers(level, solid, w, h));
        newNodes.addAll(extraNodes);
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Generated level from AI Blueprint");
    }

    private int snapToFloorY(boolean[][] solid, int x, int startY, int h) {
        for (int y = startY; y < h - 1; y++) {
            if (!solid[x][y] && solid[x][y + 1]) return y;
        }
        for (int y = startY; y >= 2; y--) {
            if (!solid[x][y] && solid[x][y + 1]) return y;
        }
        return -1;
    }

    private void carveBorderTunnels(boolean[][] solid, int w, int h) {
        int leftFloorY = h - 4;
        for (int y = 5; y < h - 3; y++) {
            if (!solid[4][y] && solid[4][y + 1]) {
                leftFloorY = y;
                break;
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int y = Math.max(3, leftFloorY - 3); y <= leftFloorY; y++) solid[x][y] = false;
            if (leftFloorY + 1 < h) solid[x][leftFloorY + 1] = true;
        }

        int rightFloorY = h - 4;
        for (int y = h - 4; y >= 5; y--) {
            if (!solid[w - 5][y] && solid[w - 5][y + 1]) {
                rightFloorY = y;
                break;
            }
        }
        for (int x = w - 5; x < w; x++) {
            for (int y = Math.max(3, rightFloorY - 3); y <= rightFloorY; y++) solid[x][y] = false;
            if (rightFloorY + 1 < h) solid[x][rightFloorY + 1] = true;
        }
    }

    private void executeBatchCommand(Level level, List<Node> newNodes, int w, int h, String logMsg) {
        Command cmd = new BatchLevelCommand(level, newNodes, w, h);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        Framework.getInstance().log(logMsg + " (Undoable with Ctrl+Z)", LogType.NOTIFICATION);
    }

    private boolean[][] extractSolidGrid(Level level, int w, int h) {
        boolean[][] solid = new boolean[w][h];
        for (Node n : level.getChildren()) {
            Tile t = (Tile) n;
            if (t.getTileType() == TileType.SOLID && t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
                solid[t.getX()][t.getY()] = true;
            }
        }
        return solid;
    }

    private List<Node> generateEntitiesAndObjects(Level parent, boolean[][] solid, int w, int h, double density) {
        List<Node> list = new ArrayList<>();
        int lastEnemyX = 5;
        int lastObjX = 5;
        boolean placedTotem = false;
        boolean placedCraftingTable = false;

        for (int x = 5; x < w - 5; x++) {
            for (int y = 4; y < h - 3; y++) {
                if (solid[x][y] || !solid[x][y + 1]) continue;

                if (solid[x + 1][y] && solid[x + 1][y - 1] && hasHorizontalLineOfSight(solid, x, y, -1, 7, w) && rand.nextDouble() < density * 0.15) {
                    list.add(createObj(parent, x, y, LvlObjType.ARROW_TRAP_LEFT.ordinal()));
                    lastObjX = x;
                    continue;
                }
                if (solid[x - 1][y] && solid[x - 1][y - 1] && hasHorizontalLineOfSight(solid, x, y, 1, 7, w) && rand.nextDouble() < density * 0.15) {
                    list.add(createObj(parent, x, y, LvlObjType.ARROW_TRAP_RIGHT.ordinal()));
                    lastObjX = x;
                    continue;
                }

                if (x + 1 < w && solid[x + 1][y + 1] && !solid[x + 1][y]) {
                    if (hasVerticalAirAbove(solid, x, y, 7) && solid[x][Math.min(h - 1, y + 2)] && rand.nextDouble() < 0.25 && x - lastObjX >= 5) {
                        list.add(createObj(parent, x, y, LvlObjType.JUMP_PAD.ordinal()));
                        lastObjX = x;
                        continue;
                    }

                    if (x - lastEnemyX >= 6 && rand.nextDouble() < density * 0.38) {
                        int[] enemies = {
                                LvlEnemyType.SKELETON.ordinal(),
                                LvlEnemyType.GHOUL.ordinal(),
                                LvlEnemyType.KNIGHT.ordinal(),
                                LvlEnemyType.WRAITH.ordinal()
                        };
                        list.add(createEnemy(parent, x, y, enemies[rand.nextInt(enemies.length)]));
                        lastEnemyX = x;
                    }
                    else if (x - lastObjX >= 4 && rand.nextDouble() < density * 0.30) {
                        double roll = rand.nextDouble();
                        if (!placedTotem && x > w / 2 && roll < 0.14) {
                            list.add(createObj(parent, x, y, LvlObjType.TOTEM.ordinal()));
                            placedTotem = true;
                        }
                        else if (!placedCraftingTable && roll < 0.20) {
                            list.add(createObj(parent, x, y, LvlObjType.TABLE.ordinal()));
                            placedCraftingTable = true;
                        }
                        else if (roll < 0.38) {
                            // Candle sprite is 2 tiles tall, so its top-left tile sits at y - 1
                            list.add(createObj(parent, x, y - 1, LvlObjType.CANDLE.ordinal()));
                        }
                        else if (roll < 0.55) {
                            int potion = rand.nextBoolean() ? LvlObjType.RED_POTION.ordinal() : LvlObjType.BLUE_POTION.ordinal();
                            list.add(createObj(parent, x, y, potion));
                        }
                        else {
                            int container = rand.nextBoolean() ? LvlObjType.CRATE.ordinal() : LvlObjType.BARREL.ordinal();
                            list.add(createObj(parent, x, y, container));
                        }
                        lastObjX = x;
                    }
                }
            }
        }
        return list;
    }

    public List<Node> snapshotNodes(Level level) {
        return new ArrayList<>(level.getChildren());
    }

    /**
     * Executes entirely on a background worker thread.
     * Requests a regional blueprint from AI, applies additive/subtractive terrain edits, preserves existing valid props, and places all requested enemies, objects, and decorations.
     */
    public List<Node> generateRegionalAiNodesOffThread(Level level, Rectangle region, boolean[][] solidSnapshot, List<Node> nodesSnapshot, String prompt, String apiKey) throws Exception {
        int lvlW = solidSnapshot.length, lvlH = solidSnapshot[0].length;
        String lowerPrompt = prompt.toLowerCase();

        String aiJson = aiService.requestRegionalBlueprint(region, lvlW, lvlH, nodesSnapshot, prompt, apiKey);
        String cleaned = aiJson.replace("```json", "").replace("```", "").trim();
        JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

        boolean clearExistingDecos = (root.has("clearExistingDecorations") && root.get("clearExistingDecorations").getAsBoolean()) || lowerPrompt.contains("remove") || lowerPrompt.contains("clear");

        // Apply Gemini's playable terrain carves
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
                    if (x >= 0 && x < lvlW && y >= 0 && y < lvlH) solidSnapshot[x][y] = false;
                }
            }
        }

        // Apply Gemini's playable terrain fills
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

        // Build subterranean structures
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
                    Tile t3 = new Tile("", level, TileType.SOLID, x, y, l3Id, 254, 254);
                    t3.setLayer(3);
                    regionNodes.add(t3);
                }
            }
        }

        // Preserve valid existing decorations & objects that weren't cleared or buried
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
            else if ((t.getTileType() == TileType.OBJECT || t.getTileType() == TileType.ENEMY
                    || t.getTileType() == TileType.TRIGGER || t.getTileType() == TileType.NPC)
                    && !clearExistingDecos && !solidSnapshot[tx][ty] && ty + 1 < lvlH && solidSnapshot[tx][ty + 1]) {
                regionNodes.add(t);
                occupiedPanel1Cells.add(tx + "," + ty);
            }
        }

        // Place all of Gemini's explicit decorations and items
        regionNodes.addAll(parseExplicitAiPlacements(level, region, solidSnapshot, placedL5, occupiedPanel1Cells, root));
        return regionNodes;
    }

    private void applyAiLayer5Structures(JsonObject root, Rectangle region, boolean[][] solidSnapshot, int[][] placedL5, int lvlW, int lvlH) {
        // Enclosed Caverns
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
                // Clamp 1 tile inside the region so the cavern's outer floor/ceiling/walls are always inside the region!
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
                    if (solidSnapshot[x][y] && l5Grid[x][y]) {
                        int id = tileEngine.pickExactTileId(l5Grid, x, y, lvlW, lvlH);
                        if (id != 8) placedL5[x][y] = id;
                    }
                }
            }
        }

        // Open-Bottom Hills & Stepped Terraces
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
                if (x2 <= x1 || !solidSnapshot[x1][topY]) continue;

                for (int x = x1; x <= x2; x++) {
                    if (!solidSnapshot[x][topY]) continue;
                    // If connecting to an adjacent higher hill step, use inner corners 11 or 12!
                    if (x == x1) {
                        boolean higherLeft = (topY > 1 && placedL5[x1][topY - 1] != -1);
                        placedL5[x][topY] = higherLeft ? 11 : 0;
                    }
                    else if (x == x2) {
                        boolean higherRight = (topY > 1 && placedL5[x2][topY - 1] != -1);
                        placedL5[x][topY] = higherRight ? 12 : 2;
                    }
                    else placedL5[x][topY] = 1;
                }
                for (int y = topY + 1; y <= botY; y++) {
                    if (solidSnapshot[x1][y] && placedL5[x1][y] == -1) placedL5[x1][y] = 7;
                    if (solidSnapshot[x2][y] && placedL5[x2][y] == -1) placedL5[x2][y] = 9;
                }
            }
        }

        // Floating Bridges & Stepping Nodules
        if (root.has("layer5Bridges") && root.get("layer5Bridges").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("layer5Bridges")) {
                JsonObject r = el.getAsJsonObject();
                int rx = resolveGlobalCoord(r.get("x").getAsInt(), region.x, region.width);
                int ry = resolveGlobalCoord(r.get("y").getAsInt(), region.y, region.height);
                int rw = r.has("w") ? Math.max(1, r.get("w").getAsInt()) : 1;

                int x1 = Math.max(region.x, rx);
                int x2 = Math.min(region.x + region.width - 1, rx + rw - 1);
                int by = Math.max(region.y, Math.min(region.y + region.height - 1, ry));
                if (!solidSnapshot[x1][by]) continue;

                if (x1 == x2) placedL5[x1][by] = 24;
                else {
                    for (int x = x1; x <= x2; x++) {
                        if (!solidSnapshot[x][by]) continue;
                        placedL5[x][by] = (x == x1) ? 21 : (x == x2) ? 23 : 22;
                    }
                }
            }
        }
    }

    private List<Node> parseExplicitAiPlacements(Level level, Rectangle region, boolean[][] solidSnapshot, int[][] placedL5, Set<String> occupiedPanel1Cells, JsonObject root) {
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

            // Check if it is a playable gameplay object or enemy
            int l3FloorAirY = snapToRegionFloorY(solidSnapshot, region, gx, gyInput);
            if (l3FloorAirY != -1 && occupiedPanel1Cells.contains(gx + "," + l3FloorAirY)) {
                int altX = findNearestFreeFloorX(solidSnapshot, occupiedPanel1Cells, region, gx, l3FloorAirY);
                if (altX != -1) {
                    gx = altX;
                    l3FloorAirY = snapToRegionFloorY(solidSnapshot, region, gx, l3FloorAirY);
                }
            }

            boolean matchedEntityOrObj = (l3FloorAirY != -1);
            if (matchedEntityOrObj) {
                switch (type) {
                    case "SKELETON", "E0" -> explicitAiPlacements.add(createEnemy(level, gx, l3FloorAirY, LvlEnemyType.SKELETON.ordinal()));
                    case "GHOUL", "E1" -> explicitAiPlacements.add(createEnemy(level, gx, l3FloorAirY, LvlEnemyType.GHOUL.ordinal()));
                    case "SPEAR_WOMAN", "LANCER", "E2" -> explicitAiPlacements.add(createEnemy(level, gx, l3FloorAirY, LvlEnemyType.SPEAR_WOMAN.ordinal()));
                    case "KNIGHT", "E3" -> explicitAiPlacements.add(createEnemy(level, gx, l3FloorAirY, LvlEnemyType.KNIGHT.ordinal()));
                    case "WRAITH", "E4" -> explicitAiPlacements.add(createEnemy(level, gx, l3FloorAirY, LvlEnemyType.WRAITH.ordinal()));
                    case "BLUE_POTION", "MANA_POTION", "O0" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.BLUE_POTION.ordinal()));
                    case "RED_POTION", "HEALTH_POTION", "POTION", "O1" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.RED_POTION.ordinal()));
                    case "CRATE", "BOX", "O2" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.CRATE.ordinal()));
                    case "BARREL", "O3" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.BARREL.ordinal()));
                    case "SPIKES", "SPIKE", "SPIKES_UP", "O4" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.SPIKES_UP.ordinal()));
                    case "ARROW_TRAP_LEFT", "ARROW_TRAP", "O5" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.ARROW_TRAP_LEFT.ordinal()));
                    case "ARROW_TRAP_RIGHT", "O6" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.ARROW_TRAP_RIGHT.ordinal()));
                    case "SHOP", "O8" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 3, LvlObjType.SHOP.ordinal()));
                    case "BLOCKER", "O9" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 2, LvlObjType.BLOCKER.ordinal()));
                    case "BLACKSMITH", "O10" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 1, LvlObjType.BLACKSMITH.ordinal()));
                    case "DOG", "O11" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.DOG.ordinal()));
                    case "TOTEM", "SAVE_TOTEM", "O12" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.TOTEM.ordinal()));
                    case "SMASH_TRAP", "O13" -> {
                        int ceilY = snapToCeilingY(solidSnapshot, gx, gyInput);
                        if (ceilY != -1) explicitAiPlacements.add(createObj(level, gx, ceilY, LvlObjType.SMASH_TRAP.ordinal()));
                    }
                    case "CANDLE", "LAMP", "LANTERN", "O14" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 1, LvlObjType.CANDLE.ordinal()));
                    case "LOOT", "CHEST", "O15" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.LOOT.ordinal()));
                    case "TABLE", "CRAFTING_TABLE", "O16" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.TABLE.ordinal()));
                    case "BOARD", "SIGN", "O17" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 1, LvlObjType.BOARD.ordinal()));
                    case "LAVA", "O19" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.LAVA.ordinal()));
                    case "BRICK", "O20" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.BRICK.ordinal()));
                    case "JUMP_PAD", "JUMPPAD", "O21" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY, LvlObjType.JUMP_PAD.ordinal()));
                    case "DOOR", "O27" -> explicitAiPlacements.add(createObj(level, gx, l3FloorAirY - 1, LvlObjType.DOOR.ordinal()));
                    default -> matchedEntityOrObj = false;
                }
                if (matchedEntityOrObj) {
                    occupiedPanel1Cells.add(gx + "," + l3FloorAirY);
                }
            }

            // If it's a decoration, snap to either a layer 5 subterranean surface or a layer 3 surface
            if (!matchedEntityOrObj && decoCatalog != null) {
                int decoIdx = findDecoIndexByName(decoCatalog, type);
                if (decoIdx != -1) {
                    DecoMetadata meta = decoCatalog.get(decoIdx);
                    int tilesTall = Math.max(1, meta.getHei() / 64);

                    int l5SurfaceY = findNearestLayer5SurfaceY(placedL5, region, gx, gyInput, type.startsWith("VINE") || type.startsWith("MOSS"));
                    if (l5SurfaceY != -1) {
                        int anchorY = (type.startsWith("VINE") || type.startsWith("MOSS")) ? (l5SurfaceY + 1) : Math.max(0, (l5SurfaceY - 1) - (tilesTall - 1));
                        Tile dt = new Tile("", level, TileType.DECO, gx, anchorY, 254, 4, decoIdx);
                        dt.setLayer(4);
                        explicitAiPlacements.add(dt);
                    }
                    else if (l3FloorAirY != -1) {
                        int anchorY = (type.startsWith("VINE") || type.startsWith("MOSS")) ? Math.max(0, snapToCeilingY(solidSnapshot, gx, gyInput)) : Math.max(0, l3FloorAirY - (tilesTall - 1));
                        int layer = (tilesTall == 1) ? 4 : 0;
                        Tile dt = new Tile("", level, TileType.DECO, gx, anchorY, 254, layer, decoIdx);
                        dt.setLayer(layer);
                        explicitAiPlacements.add(dt);
                    }
                    else {
                        int anchorY = Math.max(0, gyInput - (tilesTall - 1));
                        Tile dt = new Tile("", level, TileType.DECO, gx, anchorY, 254, 4, decoIdx);
                        dt.setLayer(4);
                        explicitAiPlacements.add(dt);
                    }
                }
            }
        }
        return explicitAiPlacements;
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

        // If it's a decoration inside a preserved subterranean structure, keep it
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
                    int fy = snapToRegionFloorY(solid, region, cx, startY);
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

    public boolean[][] snapshotSolidGrid(Level level) {
        return extractSolidGrid(level, level.getWidth(), level.getHeight());
    }

    public void commitRegionalPatchOnEdt(Level level, Rectangle region, List<Node> regionNodes) {
        Command cmd = new RegionPatchCommand(level, region, regionNodes);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        Framework.getInstance().log("Co-Op AI completed region (" + region.x + "," + region.y + " " + region.width + "x" + region.height + ")! (Undoable with Ctrl+Z)", LogType.NOTIFICATION);
    }

    private int findDecoIndexByName(List<DecoMetadata> catalog, String name) {
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).getId().equalsIgnoreCase(name)) return i;
        }
        return switch (name) {
            case "TREE", "TREES" -> 59 + rand.nextInt(3);
            case "BUSH", "BUSHES" -> (rand.nextBoolean()) ? 25 : (42 + rand.nextInt(3));
            case "ROCK", "STONE", "BOULDER", "BIG_STONE" -> rand.nextInt(3);
            case "SHROOM", "MUSHROOM", "MUSHROOMS", "SHROOMS" -> 55 + rand.nextInt(4);
            case "CRYSTAL", "CRYSTALS" -> 64 + rand.nextInt(2);
            case "FLOWER", "FLOWERS" -> 45 + rand.nextInt(2);
            case "LOG", "LOGS" -> 48 + rand.nextInt(3);
            case "PILLAR", "COLUMN" -> 53;
            case "TAVERN", "BUILDING", "HOUSE" -> 66;
            case "THORN", "THORNS" -> 3 + rand.nextInt(3);
            case "PLANT", "GRASS" -> 6 + rand.nextInt(11);
            case "VINE", "VINES" -> 17 + rand.nextInt(5);
            case "MOSS" -> 22 + rand.nextInt(2);
            default -> -1;
        };
    }

    private int snapToRegionFloorY(boolean[][] solid, Rectangle region, int x, int startY) {
        int maxY = Math.min(solid[0].length - 2, region.y + region.height + 1);
        int minY = Math.max(1, region.y);
        for (int y = minY; y <= maxY; y++) {
            if (!solid[x][y] && !solid[x][Math.max(0, y - 1)] && solid[x][y + 1]) {
                return y;
            }
        }
        return -1;
    }

    private boolean hasHorizontalLineOfSight(boolean[][] solid, int startX, int y, int dir, int dist, int w) {
        for (int i = 1; i <= dist; i++) {
            int cx = startX + i * dir;
            if (cx < 2 || cx >= w - 2 || solid[cx][y]) return false;
        }
        return true;
    }

    private boolean hasVerticalAirAbove(boolean[][] solid, int x, int startY, int dist) {
        if (startY - dist < 3) return false;
        for (int dy = 1; dy <= dist; dy++) {
            if (solid[x][startY - dy]) return false;
        }
        return true;
    }

    private List<Node> generateSpawnsAndTriggers(Level parent, boolean[][] solid, int w, int h) {
        List<Node> list = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) {
            if (!solid[0][y] && solid[0][y + 1]) {
                list.add(createTrigger(parent, 0, y, LvlTriggerType.LOAD_LEFT_LEVEL.ordinal()));
                list.add(createTrigger(parent, 2, y, LvlTriggerType.SPAWN_A.ordinal()));
                break;
            }
        }
        for (int y = 1; y < h - 1; y++) {
            if (!solid[w - 1][y] && solid[w - 1][y + 1]) {
                list.add(createTrigger(parent, w - 1, y, LvlTriggerType.LOAD_RIGHT_LEVEL.ordinal()));
                list.add(createTrigger(parent, w - 3, y, LvlTriggerType.SPAWN_B.ordinal()));
                break;
            }
        }
        return list;
    }

    private int snapToCeilingY(boolean[][] solid, int x, int startY) {
        for (int y = startY; y >= 2; y--) {
            if (!solid[x][y] && solid[x][y - 1]) return y;
        }
        return -1;
    }

    private Tile createObj(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.OBJECT, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }

    private Tile createEnemy(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.ENEMY, x, y, 254, id, 254);
        t.setLayer(5);
        return t;
    }

    private Tile createTrigger(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.TRIGGER, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }
}