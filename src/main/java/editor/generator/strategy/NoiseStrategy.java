package editor.generator.strategy;

import editor.model.loader.LvlObjType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural room carver combining 2D multi-octave Perlin/value noise, drunkard's walk tunnelers, cellular automata smoothing, and hidden destructible-brick loot alcoves.
 */
public class NoiseStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        long seed = rand.nextLong();

        for (int x = 3; x < w - 3; x++) {
            for (int y = 3; y < h - 3; y++) {
                double n = octaveNoise(x, y, seed, 3, 0.14, 0.5);
                double verticalBias = Math.abs((y - h / 2.0) / (h / 2.0)) * 0.18;
                solid[x][y] = (n + verticalBias) > 0.52;
            }
        }

        int leftY = 5 + rand.nextInt(Math.max(2, h - 10));
        int rightY = 5 + rand.nextInt(Math.max(2, h - 10));
        carveRect(solid, 0, leftY - 2, 5, 4, w, h);
        carveRect(solid, w - 5, rightY - 2, 5, 4, w, h);

        List<Point> mainPath = carveWormTunnel(solid, 4, leftY, w - 5, rightY, w, h, rand);

        int branches = 2 + rand.nextInt(2);
        for (int b = 0; b < branches && !mainPath.isEmpty(); b++) {
            Point start = mainPath.get(rand.nextInt(mainPath.size()));
            int targetX = 6 + rand.nextInt(Math.max(2, w - 12));
            int targetY = (start.y < h / 2)
                    ? Math.min(h - 5, start.y + 6 + rand.nextInt(Math.max(2, h / 3)))
                    : Math.max(5, start.y - 6 - rand.nextInt(Math.max(2, h / 3)));
            carveWormTunnel(solid, start.x, start.y, targetX, targetY, w, h, rand);
        }

        // Apply 4-5 cellular automata rule to smooth jagged noise into natural caverns
        for (int iter = 0; iter < 2; iter++) {
            boolean[][] next = new boolean[w][h];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (x < 3 || x >= w - 3 || y < 3 || y >= h - 3) {
                        next[x][y] = solid[x][y];
                        continue;
                    }
                    int neighbors = countSolidNeighbors(solid, x, y, w, h);
                    if (neighbors >= 5) next[x][y] = true;
                    else if (neighbors <= 3) next[x][y] = false;
                    else next[x][y] = solid[x][y];
                }
            }
            for (int x = 3; x < w - 3; x++) {
                System.arraycopy(next[x], 3, solid[x], 3, h - 6);
            }
        }

        quantizeForPlatformer(solid, w, h, rand);
        carveSecretBrickAlcove(parent, solid, extraNodes, w, h, rand);
    }

    private List<Point> carveWormTunnel(boolean[][] solid, int startX, int startY, int endX, int endY, int w, int h, Random rand) {
        List<Point> path = new ArrayList<>();
        double cx = startX, cy = startY;
        int steps = Math.max(w, h) * 3;

        for (int step = 0; step < steps; step++) {
            path.add(new Point((int) cx, (int) cy));
            int rx = 2 + rand.nextInt(2);
            int ry = 2 + rand.nextInt(2);
            carveRect(solid, (int) cx - rx, (int) cy - ry, rx * 2 + 1, ry * 2, w, h);

            if (Math.hypot(endX - cx, endY - cy) < 3.0) break;

            double dx = endX - cx;
            double dy = endY - cy;
            double dist = Math.max(1.0, Math.hypot(dx, dy));
            cx += (dx / dist) * 0.85 + (rand.nextDouble() - 0.5) * 1.4;
            cy += (dy / dist) * 0.75 + (rand.nextDouble() - 0.5) * 1.4;

            cx = Math.max(4, Math.min(w - 5, cx));
            cy = Math.max(5, Math.min(h - 5, cy));
        }
        return path;
    }

    private void quantizeForPlatformer(boolean[][] solid, int w, int h, Random rand) {
        for (int x = 4; x < w - 4; x++) {
            for (int y = 4; y < h - 4; y++) {
                if (solid[x][y] && !solid[x][y - 1] && !solid[x][y + 1]) {
                    if ((solid[x + 1][y] && solid[x + 1][y + 1]) || (solid[x - 1][y] && solid[x - 1][y + 1])) {
                        solid[x][y + 1] = true;
                    }
                }
            }
        }

        int rowIdx = 0;
        for (int y = 6; y < h - 6; y += 5) {
            int staggerX = (rowIdx % 2 == 0) ? 0 : 4;
            rowIdx++;
            for (int x = 6 + staggerX; x < w - 8; x += 8) {
                if (isOpenAirBox(solid, x - 1, y - 1, 6, 6, w, h) && rand.nextDouble() < 0.65) {
                    int platLen = 1 + rand.nextInt(3);
                    for (int k = 0; k < platLen; k++) {
                        solid[x + 1 + k][y + 2] = true;
                    }
                }
            }
        }
    }

    private void carveSecretBrickAlcove(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = 6 + rand.nextInt(Math.max(2, w - 14));
            int y = 6 + rand.nextInt(Math.max(2, h - 10));

            if (!solid[x][y] && !solid[x][y - 1] && solid[x][y + 1] && isSolidBox(solid, x + 1, y - 2, 5, 4, w, h)) {
                carveRect(solid, x + 1, y - 1, 4, 2, w, h);
                extraNodes.add(createObj(parent, x + 1, y, LvlObjType.BRICK.ordinal()));
                extraNodes.add(createObj(parent, x + 3, y, LvlObjType.LOOT.ordinal()));
                extraNodes.add(createObj(parent, x + 4, y, LvlObjType.RED_POTION.ordinal()));
                return;
            }
        }
    }

    private boolean isOpenAirBox(boolean[][] solid, int sx, int sy, int bw, int bh, int w, int h) {
        for (int x = sx; x < Math.min(w, sx + bw); x++) {
            for (int y = sy; y < Math.min(h, sy + bh); y++) {
                if (solid[x][y]) return false;
            }
        }
        return true;
    }

    private boolean isSolidBox(boolean[][] solid, int sx, int sy, int bw, int bh, int w, int h) {
        if (sx + bw >= w - 2 || sy < 3 || sy + bh >= h - 2) return false;
        for (int x = sx; x < sx + bw; x++) {
            for (int y = sy; y < sy + bh; y++) {
                if (!solid[x][y]) return false;
            }
        }
        return true;
    }

    private int countSolidNeighbors(boolean[][] solid, int cx, int cy, int w, int h) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = cx + dx, ny = cy + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h || solid[nx][ny]) count++;
            }
        }
        return count;
    }

    private double octaveNoise(double x, double y, long seed, int octaves, double freq, double persistence) {
        double total = 0;
        double amplitude = 1.0;
        double maxAmplitude = 0;
        double currentFreq = freq;

        for (int i = 0; i < octaves; i++) {
            total += interpolatedNoise(x * currentFreq, y * currentFreq, seed + i * 9973L) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            currentFreq *= 2.0;
        }
        return total / maxAmplitude;
    }

    private double interpolatedNoise(double x, double y, long seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;

        double v1 = hashNoise(ix, iy, seed);
        double v2 = hashNoise(ix + 1, iy, seed);
        double v3 = hashNoise(ix, iy + 1, seed);
        double v4 = hashNoise(ix + 1, iy + 1, seed);

        double i1 = cosineInterp(v1, v2, fx);
        double i2 = cosineInterp(v3, v4, fx);
        return cosineInterp(i1, i2, fy);
    }

    private double cosineInterp(double a, double b, double t) {
        double ft = t * Math.PI;
        double f = (1.0 - Math.cos(ft)) * 0.5;
        return a * (1.0 - f) + b * f;
    }

    private double hashNoise(int x, int y, long seed) {
        long n = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return ((n ^ (n >> 16)) & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    private Tile createObj(Level parent, int x, int y, int id) {
        Tile t = new Tile("", parent, TileType.OBJECT, x, y, 254, 254, id);
        t.setLayer(5);
        return t;
    }
}