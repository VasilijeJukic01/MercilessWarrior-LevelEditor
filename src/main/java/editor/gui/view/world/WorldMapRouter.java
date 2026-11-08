package editor.gui.view.world;

import editor.model.metadata.RoomExitMetadata;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Handles SAD image alignment against an existing minimap.png and automatic exit-to-spawn wiring between touching rooms.
 */
public class WorldMapRouter {

    /**
     * Attempts to automatically snap isolated rooms to their correct global X,Y coordinate by doing a pixel-perfect search against the legacy minimap.png image.
     *
     * @param minimapFile The global minimap.png file to align against.
     * @param rooms All imported rooms to test for alignment.
     * @param skipRoomIds Rooms that already have coordinates provided by world_map.json (so we don't overwrite them).
     */
    public void autoAlignRoomsToMinimap(File minimapFile, Collection<WorldMapRoomEntry> rooms, Set<String> skipRoomIds) throws Exception {
        if (minimapFile == null || !minimapFile.exists()) return;
        BufferedImage minimapImg = ImageIO.read(minimapFile);
        if (minimapImg == null) return;
        int[][] minimapGray = toGrayscale(minimapImg);

        for (WorldMapRoomEntry room : rooms) {
            if (skipRoomIds != null && skipRoomIds.contains(room.getId())) continue;
            Point match = findRoomOnMinimapSAD(minimapGray, room.getPanel1Image());
            if (match != null) {
                room.setMapX(match.x);
                room.setMapY(match.y);
            }
        }
    }

    /**
     * Implements a 2D template matching algorithm (Sum of Absolute Differences).
     * It slides a small room image over a massive global minimap image, computing the difference
     * in grayscale intensity at every possible offset. The offset with the lowest SAD is the match.
     */
    private Point findRoomOnMinimapSAD(int[][] minimapGray, BufferedImage roomPanel1) {
        int rw = roomPanel1.getWidth(), rh = roomPanel1.getHeight();
        int mh = minimapGray.length, mw = minimapGray[0].length;
        if (rw > mw || rh > mh) return null;

        int[][] roomGray = toGrayscale(roomPanel1);
        int bestSAD = Integer.MAX_VALUE;
        Point bestPt = null;

        for (int y = 0; y <= mh - rh; y++) {
            for (int x = 0; x <= mw - rw; x++) {
                int sad = 0;
                for (int j = 0; j < rh; j++) {
                    for (int i = 0; i < rw; i++) {
                        sad += Math.abs(minimapGray[y + j][x + i] - roomGray[j][i]);
                        if (sad >= bestSAD) break;
                    }
                    if (sad >= bestSAD) break;
                }
                if (sad < bestSAD) {
                    bestSAD = sad;
                    bestPt = new Point(x, y);
                }
            }
        }
        return bestPt;
    }

    private int[][] toGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y][x] = (r + g + b) / 3;
            }
        }
        return gray;
    }

    /**
     * Scans all rooms on the map canvas.
     * When two rooms are physically placed next to each other, this checks if their exit triggers align.
     * If they do, it automatically generates the RoomExitMetadata connecting them so the player can walk between rooms in-game.
     *
     * @param rooms All placed rooms on the canvas.
     * @return The number of successful exit connections wired.
     */
    public int autoWireTouchingExits(Collection<WorldMapRoomEntry> rooms) {
        int wiredCount = 0;
        for (WorldMapRoomEntry room : rooms) {
            Map<String, List<Point>> groupedTriggers = new LinkedHashMap<>();
            for (WorldMapRoomEntry.TriggerMarker tm : room.getTriggers()) {
                if (tm.type().name().startsWith("LOAD_")) {
                    groupedTriggers.computeIfAbsent(tm.type().name(), k -> new ArrayList<>()).add(new Point(tm.x(), tm.y()));
                }
            }

            List<RoomExitMetadata> newExits = new ArrayList<>();
            for (Map.Entry<String, List<Point>> entry : groupedTriggers.entrySet()) {
                String trigName = entry.getKey();
                List<Point> clusters = clusterTriggerPoints(entry.getValue());

                for (Point pt : clusters) {
                    int globalX = room.getMapX() + pt.x;
                    int globalY = room.getMapY() + pt.y;
                    int probeX = globalX + (trigName.equals("LOAD_RIGHT_LEVEL") ? 2 : trigName.equals("LOAD_LEFT_LEVEL") ? -2 : 0);
                    int probeY = globalY + (trigName.equals("LOAD_DOWN_LEVEL") ? 2 : trigName.equals("LOAD_UP_LEVEL") ? -2 : 0);

                    WorldMapRoomEntry neighbor = findRoomContainingGlobalPoint(rooms, probeX, probeY, room);
                    if (neighbor != null) {
                        String bestSpawn = findClosestSpawnInNeighbor(neighbor, globalX, globalY, trigName);
                        RoomExitMetadata exit = new RoomExitMetadata(trigName, neighbor.getId(), bestSpawn);
                        if (clusters.size() > 1) {
                            exit.setMinX(Math.max(0, pt.x - 4));
                            exit.setMaxX(pt.x + 4);
                            exit.setMinY(Math.max(0, pt.y - 4));
                            exit.setMaxY(pt.y + 4);
                        }
                        newExits.add(exit);
                        wiredCount++;
                    }
                }
            }
            if (!newExits.isEmpty()) room.setExits(newExits);
        }
        return wiredCount;
    }

    private List<Point> clusterTriggerPoints(List<Point> points) {
        List<Point> reps = new ArrayList<>();
        for (Point p : points) {
            boolean nearExisting = false;
            for (Point r : reps) {
                if (p.distance(r) <= 5.0) {
                    nearExisting = true;
                    break;
                }
            }
            if (!nearExisting) reps.add(p);
        }
        return reps;
    }

    private WorldMapRoomEntry findRoomContainingGlobalPoint(Collection<WorldMapRoomEntry> rooms, int gx, int gy, WorldMapRoomEntry exclude) {
        for (WorldMapRoomEntry candidate : rooms) {
            if (candidate == exclude) continue;
            if (candidate.getBounds().contains(gx, gy)) return candidate;
        }
        return null;
    }

    private String findClosestSpawnInNeighbor(WorldMapRoomEntry neighbor, int globalExitX, int globalExitY, String trigName) {
        String bestSpawn = switch (trigName) {
            case "LOAD_LEFT_LEVEL" -> "SPAWN_A";
            case "LOAD_RIGHT_LEVEL" -> "SPAWN_B";
            case "LOAD_UP_LEVEL" -> "SPAWN_C";
            default -> "SPAWN_D";
        };
        double bestDist = Double.MAX_VALUE;
        for (WorldMapRoomEntry.TriggerMarker tm : neighbor.getTriggers()) {
            if (tm.type().name().startsWith("SPAWN_")) {
                double d = Point.distance(globalExitX, globalExitY, neighbor.getMapX() + tm.x(), neighbor.getMapY() + tm.y());
                if (d < bestDist) {
                    bestDist = d;
                    bestSpawn = tm.type().name();
                }
            }
        }
        return bestSpawn;
    }
}