package editor.generator.strategy;

import editor.model.repository.Node;
import editor.model.repository.components.Level;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Carves an interconnected network of randomized caverns, stepped ledges, and L-shaped corridors separated by thick earth dividers.
 */
public class MultiPathHollowStrategy implements RoomCarverStrategy {

    @Override
    public void carve(Level parent, boolean[][] solid, List<Node> extraNodes, int w, int h, Random rand) {
        int numRooms = 3 + rand.nextInt(3);
        List<Rectangle> rooms = new ArrayList<>();

        int leftY = 5 + rand.nextInt(Math.max(2, h - 12));
        carveRect(solid, 0, leftY, 6, 4, w, h);
        rooms.add(new Rectangle(4, leftY - 1, 8, 5));

        for (int i = 0; i < numRooms; i++) {
            int rw = Math.max(8, w / 3 + rand.nextInt(Math.max(3, w / 4)));
            int rh = Math.max(5, 5 + rand.nextInt(4));
            int rx = 4 + rand.nextInt(Math.max(2, w - rw - 8));
            int ry = 4 + rand.nextInt(Math.max(2, h - rh - 8));
            Rectangle room = new Rectangle(rx, ry, rw, rh);
            rooms.add(room);
            carveRect(solid, rx, ry, rw, rh, w, h);

            if (rw >= 10 && rand.nextBoolean()) {
                int stepW = 3 + rand.nextInt(rw / 2 - 2);
                int stepX = rand.nextBoolean() ? rx : (rx + rw - stepW);
                fillRect(solid, stepX, ry + rh - 2, stepW, 2, w, h);
            }
        }

        int rightY = 5 + rand.nextInt(Math.max(2, h - 12));
        carveRect(solid, w - 6, rightY, 6, 4, w, h);
        rooms.add(new Rectangle(w - 10, rightY - 1, 6, 5));

        for (int i = 0; i < rooms.size() - 1; i++) {
            Rectangle a = rooms.get(i);
            Rectangle b = rooms.get(i + 1);
            int ax = (int) a.getCenterX(), ay = (int) a.getCenterY();
            int bx = (int) b.getCenterX(), by = (int) b.getCenterY();

            int hx = Math.min(ax, bx);
            int hw = Math.abs(bx - ax) + 4;
            carveRect(solid, hx, ay - 2, hw, 4, w, h);

            int vy = Math.min(ay, by);
            int vh = Math.abs(by - ay) + 4;
            carveRect(solid, bx - 2, vy, 5, vh, w, h);

            for (int py = vy + vh - 3; py > vy + 2; py -= 3) {
                int platW = 1 + rand.nextInt(3);
                int platX = bx - 1 + rand.nextInt(2);
                fillRect(solid, platX, py, platW, 1, w, h);
            }
        }
    }
}