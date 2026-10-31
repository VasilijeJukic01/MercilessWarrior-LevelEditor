package editor.generator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.core.Framework;
import editor.generator.strategy.RoomArchetype;
import editor.logger.LogType;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.util.*;

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
            if (t.getTileType() == TileType.SOLID && t.getLayer() == 3 && t.getX() >= 0 && t.getX() < w && t.getY() >= 0 && t.getY() < h) {
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