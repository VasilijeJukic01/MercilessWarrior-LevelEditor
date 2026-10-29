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
 * Facade that coordinates room carving, auto-tiling, auto-decorating, AI prompts,
 * and dispatches undoable BatchLevelCommands to the editor's CommandManager.
 */
public class LevelGeneratorFacade {

    private final AutoTileRuleEngine tileEngine = new AutoTileRuleEngine();
    private final AutoDecoratorEngine decoEngine = new AutoDecoratorEngine();
    private final AiPromptService aiService = new AiPromptService();
    private final Random rand = new Random();

    public void generateProcedural(Level level, int w, int h, RoomArchetype archetype, double enemyDensity, double decoDensity) {
        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);

        List<Node> newNodes = new ArrayList<>();
        archetype.getStrategy().carve(level, solid, newNodes, w, h, rand);

        newNodes.addAll(tileEngine.generateSolidTiles(level, solid, w, h));
        newNodes.addAll(generateSpawnsAndTriggers(level, solid, w, h));
        if (archetype != RoomArchetype.SMASH_GAUNTLET) {
            newNodes.addAll(generateEntitiesAndObjects(level, solid, w, h, enemyDensity));
        }
        newNodes.addAll(decoEngine.generateDecorations(level, solid, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Generated " + archetype);
    }

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

    public void autoDecorateCurrentLevel(Level level, double decoDensity) {
        int w = level.getWidth();
        int h = level.getHeight();
        boolean[][] solid = extractSolidGrid(level, w, h);

        List<Node> newNodes = new ArrayList<>();
        for (Node n : level.getChildren()) {
            if (((Tile) n).getTileType() != TileType.DECO) newNodes.add(n);
        }
        newNodes.addAll(decoEngine.generateDecorations(level, solid, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Auto-decorated level");
    }

    public String fetchAiAscii(int w, int h, String promptOrAscii, String apiKey) throws Exception {
        if (promptOrAscii.contains("###") && promptOrAscii.contains("...")) {
            return promptOrAscii;
        }
        return aiService.requestAiBlueprint(w, h, promptOrAscii, apiKey);
    }

    public void applyAsciiToLevel(Level level, int w, int h, String aiOutput, double decoDensity) {
        String cleaned = aiOutput.replace("```json", "").replace("```", "").trim();

        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);
        List<Node> extraNodes = new ArrayList<>();

        // Case 1: JSON Blueprint from Gemini
        if (cleaned.startsWith("{")) {
           JsonObject root = JsonParser.parseString(cleaned).getAsJsonObject();

            if (root.has("carve")) {
                for (JsonElement el : root.getAsJsonArray("carve")) {
                    JsonObject r = el.getAsJsonObject();
                    int rx = r.get("x").getAsInt(), ry = r.get("y").getAsInt();
                    int rw = r.get("w").getAsInt(), rh = r.get("h").getAsInt();
                    for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
                        for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
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
                    for (int x = Math.max(0, rx); x < Math.min(w, rx + rw); x++) {
                        for (int y = Math.max(0, ry); y < Math.min(h, ry + rh); y++) {
                            solid[x][y] = true;
                        }
                    }
                }
            }

            if (root.has("items")) {
                for (JsonElement el : root.getAsJsonArray("items")) {
                    JsonObject item = el.getAsJsonObject();
                    String type = item.get("type").getAsString().toUpperCase();
                    int x = Math.max(0, Math.min(w - 1, item.get("x").getAsInt()));
                    int y = Math.max(0, Math.min(h - 1, item.get("y").getAsInt()));
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
                        case "CANDLE" -> extraNodes.add(createObj(level, x, y, LvlObjType.CANDLE.ordinal()));
                        case "TOTEM" -> extraNodes.add(createObj(level, x, y, LvlObjType.TOTEM.ordinal()));
                        case "SMASH_TRAP" -> extraNodes.add(createObj(level, x, y, LvlObjType.SMASH_TRAP.ordinal()));
                        case "SPAWN_A" -> extraNodes.add(createTrigger(level, x, y, LvlTriggerType.SPAWN_A.ordinal()));
                        case "SPAWN_B" -> extraNodes.add(createTrigger(level, x, y, LvlTriggerType.SPAWN_B.ordinal()));
                    }
                }
            }
        }

        // Case 2: Raw ASCII Grid pasted by user
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
        }

        List<Node> newNodes = new ArrayList<>(tileEngine.generateSolidTiles(level, solid, w, h));
        newNodes.addAll(generateSpawnsAndTriggers(level, solid, w, h));
        newNodes.addAll(extraNodes);
        newNodes.addAll(decoEngine.generateDecorations(level, solid, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Generated level from AI Blueprint");
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
        int lastEnemyX = 4;
        for (int x = 5; x < w - 5; x++) {
            for (int y = 3; y < h - 2; y++) {
                if (!solid[x][y] && solid[x][y + 1] && x + 1 < w && solid[x + 1][y + 1] && !solid[x + 1][y]) {
                    if (x - lastEnemyX >= 5 && rand.nextDouble() < density * 0.4) {
                        int[] enemies = {LvlEnemyType.SKELETON.ordinal(), LvlEnemyType.GHOUL.ordinal(), LvlEnemyType.KNIGHT.ordinal()};
                        list.add(createEnemy(parent, x, y, enemies[rand.nextInt(enemies.length)]));
                        lastEnemyX = x;
                    } else if (rand.nextDouble() < density * 0.22) {
                        int[] objs = {LvlObjType.CRATE.ordinal(), LvlObjType.BARREL.ordinal(), LvlObjType.CANDLE.ordinal(), LvlObjType.RED_POTION.ordinal()};
                        list.add(createObj(parent, x, y, objs[rand.nextInt(objs.length)]));
                    }
                }
            }
        }
        return list;
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