package editor.generator;

import editor.generator.strategy.ExitSide;
import editor.model.loader.LvlEnemyType;
import editor.model.loader.LvlObjType;
import editor.model.loader.LvlTriggerType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Responsible for procedural placement of exit/spawn triggers, enemies, stacked crates, streetlamps, jump pads, and interactive objects on valid terrain surfaces.
 */
public class EntityPopulator {

    private final Random rand = new Random();

    /**
     * Places LOAD_*_LEVEL triggers across the entire tunnel opening and places SPAWN_A..D points on the nearest standable floor inside each selected exit tunnel.
     */
    public List<Node> generateSpawnsAndTriggers(Level parent, boolean[][] solid, int w, int h, Set<ExitSide> exits) {
        List<Node> list = new ArrayList<>();
        if (exits == null || exits.isEmpty()) exits = EnumSet.of(ExitSide.LEFT, ExitSide.RIGHT);

        if (exits.contains(ExitSide.LEFT)) {
            boolean placedSpawnA = false;
            for (int y = 1; y < h - 1; y++) {
                if (!solid[0][y]) {
                    list.add(createTrigger(parent, 0, y, LvlTriggerType.LOAD_LEFT_LEVEL.ordinal()));
                    if (!placedSpawnA && solid[0][y + 1]) {
                        list.add(createTrigger(parent, 2, y, LvlTriggerType.SPAWN_A.ordinal()));
                        placedSpawnA = true;
                    }
                }
            }
        }
        if (exits.contains(ExitSide.RIGHT)) {
            boolean placedSpawnB = false;
            for (int y = 1; y < h - 1; y++) {
                if (!solid[w - 1][y]) {
                    list.add(createTrigger(parent, w - 1, y, LvlTriggerType.LOAD_RIGHT_LEVEL.ordinal()));
                    if (!placedSpawnB && solid[w - 1][y + 1]) {
                        list.add(createTrigger(parent, w - 3, y, LvlTriggerType.SPAWN_B.ordinal()));
                        placedSpawnB = true;
                    }
                }
            }
        }
        if (exits.contains(ExitSide.TOP)) {
            for (int x = 3; x < w - 3; x++) {
                if (!solid[x][0]) {
                    list.add(createTrigger(parent, x, 0, LvlTriggerType.LOAD_UP_LEVEL.ordinal()));
                }
            }
            boolean placedSpawnC = false;
            for (int y = 2; y < Math.min(h - 2, 10) && !placedSpawnC; y++) {
                for (int x = 3; x < w - 3; x++) {
                    if (!solid[x][0] && !solid[x][y] && solid[x][y + 1]) {
                        list.add(createTrigger(parent, x, y, LvlTriggerType.SPAWN_C.ordinal()));
                        placedSpawnC = true;
                        break;
                    }
                }
            }
        }
        if (exits.contains(ExitSide.BOTTOM)) {
            int firstOpenX = -1;
            for (int x = 3; x < w - 3; x++) {
                if (!solid[x][h - 1]) {
                    if (firstOpenX == -1) firstOpenX = x;
                    list.add(createTrigger(parent, x, h - 1, LvlTriggerType.LOAD_DOWN_LEVEL.ordinal()));
                }
            }
            if (firstOpenX != -1) {
                int spawnX = Math.max(3, firstOpenX - 2);
                int spawnY = h - 4;
                list.add(createTrigger(parent, spawnX, spawnY, LvlTriggerType.SPAWN_D.ordinal()));
            }
        }
        return list;
    }

    /**
     * Populates a room with wall-corner crate stacks, patrolling enemies on wide grounded floors, spaced streetlamps, jump pads, potions, and save totems.
     */
    public List<Node> generateEntitiesAndObjects(Level parent, boolean[][] solid, int w, int h, double density) {
        List<Node> list = new ArrayList<>();
        int lastEnemyX = 6;
        int lastObjX = 5;
        int lastCandleX = 4;
        boolean placedTotem = false;

        for (int x = 6; x < w - 6; x++) {
            for (int y = 4; y < h - 3; y++) {
                if (solid[x][y] || !solid[x][y + 1]) continue;

                boolean isThinFloatingBridge = (y + 2 < h && !solid[x][y + 2]);
                if (isThinFloatingBridge) continue;

                boolean wallRight = solid[x + 1][y] && solid[x + 1][y - 1] && isFlatGroundedSpan(solid, x - 2, y + 1, 3, w, h);
                boolean wallLeft = solid[x - 1][y] && solid[x - 1][y - 1] && isFlatGroundedSpan(solid, x, y + 1, 3, w, h);
                if ((wallRight || wallLeft) && !solid[x][y - 1] && x - lastObjX >= 6 && rand.nextDouble() < density * 0.45) {
                    list.add(createObj(parent, x, y, LvlObjType.CRATE.ordinal()));
                    if (!solid[x][y - 2] && rand.nextBoolean()) {
                        list.add(createObj(parent, x, y - 1, rand.nextBoolean() ? LvlObjType.CRATE.ordinal() : LvlObjType.BARREL.ordinal()));
                        int adjX = wallRight ? (x - 1) : (x + 1);
                        if (!solid[adjX][y] && solid[adjX][y + 1]) {
                            list.add(createObj(parent, adjX, y, LvlObjType.CRATE.ordinal()));
                        }
                    }
                    lastObjX = x;
                    continue;
                }

                if (x - lastEnemyX >= 7 && isSafeEnemyPatrolFloor(solid, x, y, w, h) && rand.nextDouble() < density * 0.45) {
                    int[] enemies = {
                            LvlEnemyType.SKELETON.ordinal(),
                            LvlEnemyType.GHOUL.ordinal(),
                            LvlEnemyType.KNIGHT.ordinal(),
                            LvlEnemyType.WRAITH.ordinal()
                    };
                    list.add(createEnemy(parent, x, y, enemies[rand.nextInt(enemies.length)]));
                    lastEnemyX = x;
                    continue;
                }

                if (isFlatGroundedSpan(solid, x - 1, y + 1, 3, w, h)) {
                    if (hasVerticalAirAbove(solid, x, y, 7) && rand.nextDouble() < 0.20 && x - lastObjX >= 6) {
                        list.add(createObj(parent, x, y, LvlObjType.JUMP_PAD.ordinal()));
                        lastObjX = x;
                        continue;
                    }

                    if (x - lastCandleX >= 9 && !solid[x][y - 1] && !solid[x][y - 2] && rand.nextDouble() < 0.40) {
                        list.add(createObj(parent, x, y - 1, LvlObjType.CANDLE.ordinal()));
                        lastCandleX = x;
                        lastObjX = x;
                        continue;
                    }

                    if (x - lastObjX >= 5 && rand.nextDouble() < density * 0.28) {
                        double roll = rand.nextDouble();
                        if (!placedTotem && x > w / 2 && roll < 0.18) {
                            list.add(createObj(parent, x, y, LvlObjType.TOTEM.ordinal()));
                            placedTotem = true;
                        }
                        else if (roll < 0.45) {
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

    /**
     * Checks if (x, y) is the center of a 5-tile-wide flat, thick floor with 3 tiles of vertical headroom.
     */
    public boolean isSafeEnemyPatrolFloor(boolean[][] solid, int x, int y, int w, int h) {
        if (x - 2 < 4 || x + 2 >= w - 4 || y - 2 < 3 || y + 2 >= h) return false;
        if (!solid[x][0] || !solid[x][h - 1]) return false;

        for (int dx = -2; dx <= 2; dx++) {
            int cx = x + dx;
            if (solid[cx][y] || solid[cx][y - 1] || solid[cx][y - 2]) return false;
            if (!solid[cx][y + 1] || !solid[cx][y + 2]) return false;
        }
        return true;
    }

    /**
     * Searches outward from (targetX, targetY) within the given bounds to find the nearest valid 5-tile patrol floor for an enemy.
     */
    public Point findNearestSafeEnemyFloor(boolean[][] solid, int targetX, int targetY, int minX, int maxX, int minY, int maxY, int w, int h) {
        Point best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = Math.max(4, minX); x <= Math.min(w - 5, maxX); x++) {
            for (int y = Math.max(3, minY); y <= Math.min(h - 3, maxY); y++) {
                if (isSafeEnemyPatrolFloor(solid, x, y, w, h)) {
                    double d = Point.distance(targetX, targetY, x, y);
                    if (d < bestDist) {
                        bestDist = d;
                        best = new Point(x, y);
                    }
                }
            }
        }
        return best;
    }

    public boolean isFlatGroundedSpan(boolean[][] solid, int startX, int floorY, int span, int w, int h) {
        if (startX < 3 || startX + span >= w - 3 || floorY + 1 >= h || floorY - 2 < 0) return false;
        for (int i = 0; i < span; i++) {
            int cx = startX + i;
            if (solid[cx][floorY - 1] || solid[cx][floorY - 2]) return false;
            if (!solid[cx][floorY] || !solid[cx][floorY + 1]) return false;
        }
        return true;
    }

    public int snapToFloorY(boolean[][] solid, int x, int startY, int h) {
        for (int y = startY; y < h - 1; y++) {
            if (!solid[x][y] && solid[x][y + 1]) return y;
        }
        for (int y = startY; y >= 2; y--) {
            if (!solid[x][y] && solid[x][y + 1]) return y;
        }
        return -1;
    }

    public int snapToCeilingY(boolean[][] solid, int x, int startY) {
        for (int y = startY; y >= 2; y--) {
            if (!solid[x][y] && solid[x][y - 1]) return y;
        }
        return -1;
    }

    private boolean hasVerticalAirAbove(boolean[][] solid, int x, int startY, int dist) {
        if (startY - dist < 3) return false;
        for (int dy = 1; dy <= dist; dy++) {
            if (solid[x][startY - dy]) return false;
        }
        return true;
    }

    public Tile createObj(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.OBJECT, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }

    public Tile createEnemy(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.ENEMY, x, y, 254, id, 254);
        t.setLayer(5);
        return t;
    }

    public Tile createTrigger(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.TRIGGER, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }
}