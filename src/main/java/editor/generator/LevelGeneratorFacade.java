package editor.generator;

import editor.command.Command;
import editor.command.commands.BatchLevelCommand;
import editor.command.commands.RegionPatchCommand;
import editor.core.Framework;
import editor.generator.strategy.ExitSide;
import editor.generator.strategy.RoomArchetype;
import editor.gui.view.tab.LevelSketchPanel;
import editor.logger.LogType;
import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Lightweight Facade coordinating room carving strategies, playability validation, auto-tiling, subterranean pocket generation, entity population, auto-decoration, and AI blueprint application.
 */
public class LevelGeneratorFacade {

    private final AutoTileRuleEngine tileEngine = new AutoTileRuleEngine();
    private final SubterraneanPocketGenerator pocketGenerator = new SubterraneanPocketGenerator(tileEngine);
    private final AutoDecoratorEngine decoEngine = new AutoDecoratorEngine();
    private final PlayabilityValidator validator = new PlayabilityValidator();
    private final EntityPopulator entityPopulator = new EntityPopulator();
    private final AiPromptService aiService = new AiPromptService();
    private final AiBlueprintApplier aiApplier = new AiBlueprintApplier(tileEngine, pocketGenerator, decoEngine, validator, entityPopulator);
    private final Random rand = new Random();

    /**
     * Generates a complete procedural level using the specified archetype, density settings, and exit sides.
     */
    public void generateProcedural(Level level, int w, int h, RoomArchetype archetype, double enemyDensity, double decoDensity, Set<ExitSide> exits) {
        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) Arrays.fill(solid[x], true);

        List<Node> newNodes = new ArrayList<>();
        archetype.getStrategy().carve(level, solid, newNodes, w, h, rand);
        if (archetype != RoomArchetype.SMASH_GAUNTLET) validator.ensurePlayable(solid, w, h, exits);
        else validator.enforceOuterShellAndSelectedExits(solid, w, h, exits);

        newNodes.addAll(tileEngine.generateSolidTiles(level, solid, w, h));
        pocketGenerator.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(entityPopulator.generateSpawnsAndTriggers(level, solid, w, h, exits));
        if (archetype != RoomArchetype.SMASH_GAUNTLET) {
            newNodes.addAll(entityPopulator.generateEntitiesAndObjects(level, solid, w, h, enemyDensity));
        }
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));

        executeBatchCommand(level, newNodes, w, h, "Generated " + archetype + " with exits " + exits);
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
        pocketGenerator.generateWallPockets(level, solid, w, h, newNodes);
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
    public void applyAsciiToLevel(Level level, int w, int h, String aiOutput, double decoDensity, Set<ExitSide> exits) {
        List<Node> newNodes = aiApplier.buildFullLevelNodesFromAi(level, w, h, aiOutput, decoDensity, exits);
        executeBatchCommand(level, newNodes, w, h, "Generated level from AI Blueprint");
    }

    public List<Node> generateRegionalAiNodesOffThread(Level level, Rectangle region, boolean[][] solidSnapshot, List<Node> nodesSnapshot, String prompt, String apiKey) throws Exception {
        int lvlW = solidSnapshot.length, lvlH = solidSnapshot[0].length;
        String aiJson = aiService.requestRegionalBlueprint(region, lvlW, lvlH, nodesSnapshot, prompt, apiKey);
        return aiApplier.buildRegionalAiNodes(level, region, solidSnapshot, nodesSnapshot, prompt, aiJson);
    }

    public void commitRegionalPatchOnEdt(Level level, Rectangle region, List<Node> regionNodes) {
        Command cmd = new RegionPatchCommand(level, region, regionNodes);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        Framework.getInstance().log("Co-Op AI completed region (" + region.x + "," + region.y + " " + region.width + "x" + region.height + ")! (Undoable with Ctrl+Z)", LogType.NOTIFICATION);
    }

    public List<Node> snapshotNodes(Level level) {
        return new ArrayList<>(level.getChildren());
    }

    public boolean[][] snapshotSolidGrid(Level level) {
        return extractSolidGrid(level, level.getWidth(), level.getHeight());
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

    private void executeBatchCommand(Level level, List<Node> newNodes, int w, int h, String logMsg) {
        Command cmd = new BatchLevelCommand(level, newNodes, w, h);
        Framework.getInstance().getGui().getCommandManager().addCommand(cmd);
        Framework.getInstance().log(logMsg + " (Undoable with Ctrl+Z)", LogType.NOTIFICATION);
    }

    /**
     * Builds a complete, playable, auto-tiled, and decorated level directly from the user's Mini-Paint sketch grid.
     */
    public void generateFromSketch(Level level, int[][] sketchGrid, boolean organicEdges, double enemyDensity, double decoDensity, Set<ExitSide> exits) {
        int w = sketchGrid.length;
        int h = sketchGrid[0].length;
        boolean[][] solid = new boolean[w][h];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                solid[x][y] = (sketchGrid[x][y] == LevelSketchPanel.CELL_SOLID);
            }
        }

        if (organicEdges) roughenSketchWalls(solid, w, h);
        validator.ensurePlayable(solid, w, h, exits);

        List<Node> newNodes = new ArrayList<>(tileEngine.generateSolidTiles(level, solid, w, h));
        pocketGenerator.generateWallPockets(level, solid, w, h, newNodes);
        newNodes.addAll(entityPopulator.generateSpawnsAndTriggers(level, solid, w, h, exits));

        Set<String> occupied = new HashSet<>();
        for (int x = 3; x < w - 3; x++) {
            for (int y = 2; y < h - 2; y++) {
                int marker = sketchGrid[x][y];
                if (marker == LevelSketchPanel.CELL_ENEMY) {
                    Point safePt = entityPopulator.findNearestSafeEnemyFloor(solid, x, y, 4, w - 5, 3, h - 3, w, h);
                    if (safePt != null && occupied.add(safePt.x + "," + safePt.y)) {
                        int[] enemies = {0, 1, 3, 4};
                        newNodes.add(entityPopulator.createEnemy(level, safePt.x, safePt.y, enemies[rand.nextInt(enemies.length)]));
                    }
                }
                else if (marker == LevelSketchPanel.CELL_HAZARD) {
                    int fy = entityPopulator.snapToFloorY(solid, x, y, h);
                    if (fy != -1 && occupied.add(x + "," + fy)) {
                        newNodes.add(entityPopulator.createObj(level, x, fy, LvlObjType.SPIKES_UP.ordinal()));
                    }
                }
                else if (marker == LevelSketchPanel.CELL_LOOT) {
                    int fy = entityPopulator.snapToFloorY(solid, x, y, h);
                    if (fy != -1 && occupied.add(x + "," + fy)) {
                        int objId = rand.nextBoolean() ? LvlObjType.LOOT.ordinal() : LvlObjType.TOTEM.ordinal();
                        newNodes.add(entityPopulator.createObj(level, x, fy, objId));
                    }
                }
            }
        }

        newNodes.addAll(entityPopulator.generateEntitiesAndObjects(level, solid, w, h, enemyDensity));
        newNodes.addAll(decoEngine.generateDecorations(level, solid, newNodes, w, h, decoDensity));
        executeBatchCommand(level, newNodes, w, h, "Generated level from Mini-Paint Sketch");
    }

    /**
     * Sends the user's Mini-Paint ASCII sketch + text prompt to Gemini so the AI refines the user's drawn shape.
     */
    public String fetchAiFromSketch(int w, int h, String asciiSketch, String userPrompt, String apiKey) throws Exception {
        String combinedPrompt = userPrompt
                + "\n\nIMPORTANT: The user drew this exact " + w + "x" + h
                + " layout sketch ('#'=Solid, '.'=Open Air, 'E'=Enemy, 'H'=Spikes/Hazard, 'L'=Loot/Totem). "
                + "Respect the main tunnels and chambers of this sketch while adding platforms and items:\n"
                + asciiSketch;
        return aiService.requestAiBlueprint(w, h, combinedPrompt, apiKey);
    }

    private void roughenSketchWalls(boolean[][] solid, int w, int h) {
        boolean[][] copy = new boolean[w][h];
        for (int x = 0; x < w; x++) System.arraycopy(solid[x], 0, copy[x], 0, h);

        for (int x = 4; x < w - 4; x++) {
            for (int y = 4; y < h - 4; y++) {
                if (!copy[x][y]) continue;
                boolean isThickCeiling = copy[x][y - 1] && copy[x][y - 2] && !copy[x][y + 1];
                boolean isThickWall = copy[x][y - 1] && copy[x][y + 1] && (!copy[x - 1][y] || !copy[x + 1][y]);
                if ((isThickCeiling || isThickWall) && rand.nextDouble() < 0.22) solid[x][y] = false;
            }
        }
    }
}