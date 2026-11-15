package editor.gui.view.renderer.renderers;

import editor.gui.view.renderer.RenderStrategy;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static editor.constants.Constants.TILE_SIZE;

public class DecoRenderer implements RenderStrategy<Tile> {

    private final BufferedImage[] decoTiles;
    private final List<DecoMetadata> decoMetadata;

    public DecoRenderer(BufferedImage[] decoTiles, List<DecoMetadata> decoMetadata) {
        this.decoTiles = decoTiles;
        this.decoMetadata = decoMetadata;
    }

    @Override
    public void render(Graphics g, Tile tile, int layer) {
        if (decoTiles == null || decoTiles.length == 0 || decoMetadata == null || tile.getTileType() != TileType.DECO) return;

        int value = tile.getBlue();
        int layerIndex = tile.getLayer();

        if (value != -1 && value < decoMetadata.size() && layerIndex == layer) {
            DecoMetadata deco = decoMetadata.get(value);
            BufferedImage model = decoTiles[value];
            if (model == null) return;

            double rotation = tile.getRotation();
            double scaleX = tile.getScaleX();
            double scaleY = tile.getScaleY();
            int baseX = tile.getX() * TILE_SIZE + deco.getxOffset() + tile.getOffsetX();
            int baseY = tile.getY() * TILE_SIZE + deco.getyOffset() + tile.getOffsetY();

            if (rotation == 0.0 && scaleX == 1.0 && scaleY == 1.0) {
                g.drawImage(model, baseX, baseY, deco.getWid(), deco.getHei(), null);
            } else {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double centerX = baseX + deco.getWid() / 2.0;
                double centerY = baseY + deco.getHei() / 2.0;

                g2d.translate(centerX, centerY);
                g2d.rotate(Math.toRadians(rotation));
                g2d.scale(scaleX, scaleY);

                g2d.drawImage(model, -deco.getWid() / 2, -deco.getHei() / 2, deco.getWid(), deco.getHei(), null);
                g2d.dispose();
            }
        }
    }
}