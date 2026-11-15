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
     * Scans all rooms on the map canvas, clusters exit trigger tiles by tunnel, finds the touching neighbor room across each seam, and links to the nearest SPAWN_A..D inside
     * that specific entry tunnel (automatically creating a new SPAWN marker if the tunnel lacks one).
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
                List<Rectangle> tunnelBounds = clusterTriggerBounds(entry.getValue());

                for (Rectangle bounds : tunnelBounds) {
                    int centerX = bounds.x + bounds.width / 2;
                    int centerY = bounds.y + bounds.height / 2;
                    int globalX = room.getMapX() + centerX;
                    int globalY = room.getMapY() + centerY;

                    WorldMapRoomEntry neighbor = findNeighborAcrossSeam(rooms, room, trigName, globalX, globalY);
                    if (neighbor != null) {
                        RoomExitMetadata exit = buildExitToNeighborTunnel(neighbor, globalX, globalY, trigName);
                        if (tunnelBounds.size() > 1) {
                            exit.setMinX(Math.max(0, bounds.x - 2));
                            exit.setMaxX(bounds.x + bounds.width + 2);
                            exit.setMinY(Math.max(0, bounds.y - 2));
                            exit.setMaxY(bounds.y + bounds.height + 2);
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

    private RoomExitMetadata buildExitToNeighborTunnel(WorldMapRoomEntry neighbor, int globalExitX, int globalExitY, String trigName) {
        String defaultSpawn = switch (trigName) {
            case "LOAD_LEFT_LEVEL" -> "SPAWN_A";
            case "LOAD_RIGHT_LEVEL" -> "SPAWN_B";
            case "LOAD_UP_LEVEL" -> "SPAWN_C";
            default -> "SPAWN_D";
        };

        WorldMapRoomEntry.TriggerMarker closestMarker = null;
        double bestDist = Double.MAX_VALUE;

        for (WorldMapRoomEntry.TriggerMarker tm : neighbor.getTriggers()) {
            if (tm.type().name().startsWith("SPAWN_")) {
                double d = Point.distance(globalExitX, globalExitY, neighbor.getMapX() + tm.x(), neighbor.getMapY() + tm.y());
                if (d < bestDist) {
                    bestDist = d;
                    closestMarker = tm;
                }
            }
        }

        if (closestMarker != null && bestDist <= 10.0) {
            RoomExitMetadata exit = new RoomExitMetadata(trigName, neighbor.getId(), closestMarker.type().name());
            exit.setSpawnX(closestMarker.x());
            exit.setSpawnY(closestMarker.y());
            return exit;
        }

        int localEntryX = Math.max(2, Math.min(neighbor.getWidth() - 3, globalExitX - neighbor.getMapX()));
        int localEntryY = Math.max(2, Math.min(neighbor.getHeight() - 3, globalExitY - neighbor.getMapY()));
        if (trigName.equals("LOAD_LEFT_LEVEL")) localEntryX = Math.max(2, neighbor.getWidth() - 3);
        else if (trigName.equals("LOAD_RIGHT_LEVEL")) localEntryX = 2;
        else if (trigName.equals("LOAD_UP_LEVEL")) localEntryY = Math.max(2, neighbor.getHeight() - 3);
        else if (trigName.equals("LOAD_DOWN_LEVEL")) localEntryY = 2;

        BufferedImage p1 = neighbor.getPanel1Image();
        for (int dy = 0; dy < 8 && localEntryY + dy < neighbor.getHeight() - 1; dy++) {
            int y = localEntryY + dy;
            int rHere = (p1.getRGB(localEntryX, y) >> 16) & 0xFF;
            int rBelow = (p1.getRGB(localEntryX, y + 1) >> 16) & 0xFF;
            if (rHere >= 49 && rBelow < 49) {
                localEntryY = y;
                break;
            }
        }

        RoomExitMetadata exit = new RoomExitMetadata(trigName, neighbor.getId(), defaultSpawn);
        exit.setSpawnX(localEntryX);
        exit.setSpawnY(localEntryY);
        return exit;
    }

    /**
     * Groups contiguous or nearby trigger tiles (within 3 tiles) into bounding rectangles so each distinct exit tunnel gets its own accurate bounding box and center point.
     */
    private List<Rectangle> clusterTriggerBounds(List<Point> points) {
        List<Rectangle> clusters = new ArrayList<>();
        boolean[] visited = new boolean[points.size()];

        for (int i = 0; i < points.size(); i++) {
            if (visited[i]) continue;
            visited[i] = true;

            Queue<Point> q = new ArrayDeque<>();
            q.add(points.get(i));
            int minX = points.get(i).x, maxX = points.get(i).x;
            int minY = points.get(i).y, maxY = points.get(i).y;

            while (!q.isEmpty()) {
                Point curr = q.poll();
                minX = Math.min(minX, curr.x);
                maxX = Math.max(maxX, curr.x);
                minY = Math.min(minY, curr.y);
                maxY = Math.max(maxY, curr.y);
                for (int j = 0; j < points.size(); j++) {
                    if (!visited[j] && curr.distance(points.get(j)) <= 3.5) {
                        visited[j] = true;
                        q.add(points.get(j));
                    }
                }
            }
            clusters.add(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
        }
        return clusters;
    }

    private WorldMapRoomEntry findNeighborAcrossSeam(Collection<WorldMapRoomEntry> rooms, WorldMapRoomEntry source, String trigName, int globalX, int globalY) {
        int dx = trigName.equals("LOAD_RIGHT_LEVEL") ? 1 : trigName.equals("LOAD_LEFT_LEVEL") ? -1 : 0;
        int dy = trigName.equals("LOAD_DOWN_LEVEL") ? 1 : trigName.equals("LOAD_UP_LEVEL") ? -1 : 0;
        for (int step = 1; step <= 4; step++) {
            int probeX = globalX + dx * step;
            int probeY = globalY + dy * step;
            for (WorldMapRoomEntry candidate : rooms) {
                if (candidate == source) continue;
                if (candidate.getBounds().contains(probeX, probeY)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}