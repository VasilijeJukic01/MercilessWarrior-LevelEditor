package editor.generator;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Validates and repairs generated terrain grids so that all chambers are connected
 * and a valid platforming jump path exists from the left entrance to the right exit.
 */
public class PlayabilityValidator {

    private static final int MAX_JUMP_UP = 3;
    private static final int MAX_JUMP_ACROSS = 4;
    private static final int MIN_HEADROOM = 3;

    /**
     * Enforces outer boundaries, connects isolated chambers, clears headroom, and constructs stepping platforms over unclimbable cliffs.
     *
     * @param solid The 2D solid occupancy grid to validate and modify in place.
     * @param w     Grid width in tiles.
     * @param h     Grid height in tiles.
     */
    public void ensurePlayable(boolean[][] solid, int w, int h) {
        enforceTopAndBottomShell(solid, w, h);
        connectAllAirChambers(solid, w, h);
        ensureHeadroom(solid, w, h);
        removeStackedPlatformBlockers(solid, w, h);
        guaranteeJumpPathFromStartToExit(solid, w, h);
    }

    private void enforceTopAndBottomShell(boolean[][] solid, int w, int h) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < 3; y++) solid[x][y] = true;
            for (int y = h - 3; y < h; y++) solid[x][y] = true;
        }
    }

    /**
     * Iteratively computes the set of standable tiles reachable via platformer jumps from the left entrance,
     * placing staggered bridge platforms toward unreachable targets until full traversal is possible.
     */
    private void guaranteeJumpPathFromStartToExit(boolean[][] solid, int w, int h) {
        Point start = findTunnelStandable(solid, 2, h);
        Point exit = findTunnelStandable(solid, w - 3, h);
        if (start == null || exit == null) return;

        for (int repairIter = 0; repairIter < 25; repairIter++) {
            List<Point> allStandables = findAllStandableTiles(solid, w, h);
            Set<Point> reachable = computeJumpReachableSet(solid, start, allStandables, w, h);

            Point target = null;
            if (!reachable.contains(exit)) {
                target = exit;
            } else {
                for (Point p : allStandables) {
                    if (!reachable.contains(p) && p.x > 4 && p.x < w - 5) {
                        target = p;
                        break;
                    }
                }
            }

            if (target == null) break;

            Point bestFrom = null;
            double bestScore = Double.MAX_VALUE;
            for (Point r : reachable) {
                double dist = Math.hypot((r.x - target.x) * 1.2, r.y - target.y);
                if (dist < bestScore) {
                    bestScore = dist;
                    bestFrom = r;
                }
            }

            if (bestFrom == null || !placeBridgePlatform(solid, bestFrom, target, w, h)) {
                break;
            }
        }
    }

    private boolean placeBridgePlatform(boolean[][] solid, Point from, Point target, int w, int h) {
        int dirX = Integer.compare(target.x, from.x);
        if (dirX == 0) dirX = (from.x < w / 2) ? 1 : -1;

        int[] candidateDy = (target.y < from.y) ? new int[]{-2, -3} : new int[]{-2, 0, 2};
        int[] candidateDx = {dirX * 3, dirX * 2, -dirX * 3, -dirX * 2, dirX * 4};

        for (int dy : candidateDy) {
            for (int dx : candidateDx) {
                int newAirX = Math.max(4, Math.min(w - 6, from.x + dx));
                int newAirY = Math.max(5, Math.min(h - 5, from.y + dy));
                int newFloorY = newAirY + 1;

                if (solid[newAirX][newAirY - 1] || solid[newAirX][newAirY - 2]) continue;

                for (int k = 0; k < 2; k++) {
                    solid[newAirX + k][newFloorY] = true;
                    for (int airDy = 0; airDy <= 3; airDy++) {
                        if (newAirY - airDy >= 3) {
                            solid[newAirX + k][newAirY - airDy] = false;
                        }
                    }
                }

                int minX = Math.min(from.x, newAirX), maxX = Math.max(from.x, newAirX + 1);
                int topArcY = Math.max(3, Math.min(from.y, newAirY) - 1);
                int botArcY = Math.max(from.y, newAirY);
                for (int cx = minX; cx <= maxX; cx++) {
                    for (int cy = topArcY; cy <= botArcY; cy++) {
                        if (cx >= 3 && cx < w - 3 && cy >= 3 && !(cy == newFloorY && (cx == newAirX || cx == newAirX + 1))) {
                            solid[cx][cy] = false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private Set<Point> computeJumpReachableSet(boolean[][] solid, Point start, List<Point> standables, int w, int h) {
        Set<Point> visited = new HashSet<>();
        Queue<Point> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Point curr = queue.poll();
            for (Point next : standables) {
                if (visited.contains(next)) continue;
                if (canPlayerJumpBetween(solid, curr.x, curr.y, next.x, next.y, w, h)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private boolean canPlayerJumpBetween(boolean[][] solid, int x1, int y1, int x2, int y2, int w, int h) {
        int dx = Math.abs(x2 - x1);
        int dy = y2 - y1;

        if (dx == 1 && dy >= -1 && dy <= 1) {
            return !solid[x2][Math.min(y1, y2)];
        }

        if (dy < 0 && dy >= -MAX_JUMP_UP) {
            int maxHorizontal = (dy == -MAX_JUMP_UP) ? 3 : MAX_JUMP_ACROSS;
            if (dx <= maxHorizontal) {
                return isJumpArcClear(solid, x1, y1, x2, y2, w, h);
            }
            return false;
        }

        if (dy >= 0 && dy <= 10) {
            int maxDropHorizontal = Math.min(6, MAX_JUMP_ACROSS + dy / 3);
            if (dx <= maxDropHorizontal) {
                return isJumpArcClear(solid, x1, y1, x2, y2, w, h);
            }
        }
        return false;
    }

    /**
     * Simulates a parabolic trajectory between two standable tiles and verifies that both the player's feet (cy) and head (cy - 1) remain unobstructed along the arc.
     */
    private boolean isJumpArcClear(boolean[][] solid, int x1, int y1, int x2, int y2, int w, int h) {
        if (x1 == x2 && y2 < y1) return false;

        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * 2;
        if (steps == 0) return true;
        int peakY = Math.max(3, Math.min(y1, y2) - 1);

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int cx = (int) Math.round(x1 + t * (x2 - x1));
            // Normalized parabola 4*t*(1-t) peaks at 1.0 when t = 0.5
            double parabola = 4 * t * (1 - t);
            double linearY = y1 + t * (y2 - y1);
            int cy = (int) Math.round(linearY * (1 - parabola * 0.5) + peakY * (parabola * 0.5));
            if (cx >= 0 && cx < w && cy >= 0 && cy < h) {
                if (solid[cx][cy] || (cy - 1 >= 0 && solid[cx][cy - 1])) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Point> findAllStandableTiles(boolean[][] solid, int w, int h) {
        List<Point> list = new ArrayList<>();
        for (int x = 1; x < w - 1; x++) {
            for (int y = 2; y < h - 1; y++) {
                if (!solid[x][y] && !solid[x][y - 1] && solid[x][y + 1]) {
                    list.add(new Point(x, y));
                }
            }
        }
        return list;
    }

    private Point findTunnelStandable(boolean[][] solid, int x, int h) {
        for (int y = 2; y < h - 1; y++) {
            if (!solid[x][y] && solid[x][y + 1]) return new Point(x, y);
        }
        return null;
    }

    private void connectAllAirChambers(boolean[][] solid, int w, int h) {
        int[][] comp = new int[w][h];
        List<List<Point>> regions = new ArrayList<>();
        int currentId = 0;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!solid[x][y] && comp[x][y] == 0) {
                    currentId++;
                    List<Point> cells = new ArrayList<>();
                    Queue<Point> q = new ArrayDeque<>();
                    q.add(new Point(x, y));
                    comp[x][y] = currentId;

                    while (!q.isEmpty()) {
                        Point p = q.poll();
                        cells.add(p);
                        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                        for (int[] d : dirs) {
                            int nx = p.x + d[0], ny = p.y + d[1];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && !solid[nx][ny] && comp[nx][ny] == 0) {
                                comp[nx][ny] = currentId;
                                q.add(new Point(nx, ny));
                            }
                        }
                    }
                    regions.add(cells);
                }
            }
        }

        if (regions.size() <= 1) return;
        regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
        List<Point> mainCavern = new ArrayList<>(regions.get(0));

        for (int i = 1; i < regions.size(); i++) {
            List<Point> isolated = regions.get(i);
            if (isolated.size() < 4) {
                for (Point p : isolated) solid[p.x][p.y] = true;
                continue;
            }

            Point bestMain = mainCavern.get(0);
            Point bestIso = isolated.get(0);
            double minDist = Double.MAX_VALUE;

            for (int m = 0; m < mainCavern.size(); m += Math.max(1, mainCavern.size() / 40)) {
                Point pm = mainCavern.get(m);
                for (int k = 0; k < isolated.size(); k += Math.max(1, isolated.size() / 20)) {
                    Point pi = isolated.get(k);
                    double dist = pm.distanceSq(pi);
                    if (dist < minDist) {
                        minDist = dist;
                        bestMain = pm;
                        bestIso = pi;
                    }
                }
            }

            carveConnectorTunnel(solid, bestMain.x, bestMain.y, bestIso.x, bestIso.y, w, h);
            mainCavern.addAll(isolated);
        }
    }

    private void carveConnectorTunnel(boolean[][] solid, int x1, int y1, int x2, int y2, int w, int h) {
        int minX = Math.max(3, Math.min(x1, x2));
        int maxX = Math.min(w - 4, Math.max(x1, x2));
        for (int x = minX; x <= maxX; x++) {
            for (int dy = -2; dy <= 1; dy++) {
                int cy = Math.max(4, Math.min(h - 4, y1 + dy));
                solid[x][cy] = false;
            }
        }
        int minY = Math.max(4, Math.min(y1, y2));
        int maxY = Math.min(h - 4, Math.max(y1, y2));
        for (int y = minY; y <= maxY; y++) {
            for (int dx = -1; dx <= 2; dx++) {
                int cx = Math.max(3, Math.min(w - 4, x2 + dx));
                solid[cx][y] = false;
            }
        }
    }

    /**
     * Trims thin floating platforms that sit directly above another platform's edge within 4 vertical tiles so they do not block upward jump arcs.
     */
    private void removeStackedPlatformBlockers(boolean[][] solid, int w, int h) {
        for (int y = h - 5; y >= 6; y--) {
            for (int x = 4; x < w - 4; x++) {
                if (solid[x][y] && !solid[x][y - 1]) {
                    for (int dy = 2; dy <= 4; dy++) {
                        int uy = y - dy;
                        if (uy >= 4 && solid[x][uy] && !solid[x][uy - 1] && !solid[x][uy + 1]) {
                            if (solid[x - 1][y] && solid[x + 1][y]) {
                                solid[x][uy] = false;
                            } else if (!solid[x + 1][y]) {
                                solid[x][uy] = false;
                                if (x + 1 < w - 3) solid[x + 1][uy] = false;
                            } else if (!solid[x - 1][y]) {
                                solid[x][uy] = false;
                                if (x - 1 >= 3) solid[x - 1][uy] = false;
                            }
                        }
                    }
                }
            }
        }
    }

    private void ensureHeadroom(boolean[][] solid, int w, int h) {
        for (int x = 3; x < w - 3; x++) {
            for (int y = h - 4; y >= 6; y--) {
                if (!solid[x][y] && solid[x][y + 1]) {
                    for (int dy = 1; dy < MIN_HEADROOM; dy++) {
                        if (y - dy >= 4) solid[x][y - dy] = false;
                    }
                }
            }
        }
    }
}