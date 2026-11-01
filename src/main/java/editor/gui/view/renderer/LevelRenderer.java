package editor.gui.view.renderer;

import editor.core.Clipboard;
import editor.core.Framework;
import editor.gui.view.renderer.renderers.*;
import editor.gui.view.tab.TabView;
import editor.model.metadata.DecoMetadata;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;
import editor.settings.SettingsKey;
import editor.state.State;
import editor.state.states.AddState;
import editor.state.states.DeleteState;
import editor.state.states.PasteState;
import editor.state.states.SelectState;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static editor.constants.Constants.*;
import static editor.constants.ObjectConstants.*;

/**
 * Renders the level workspace including background, layered tiles, grid, selections, and semi-transparent ghost previews for active placement tools.
 */
public class LevelRenderer implements Renderer {

    private final Level level;
    private final TabView tabView;

    private RenderStrategy<Tile> terrainRenderer, objectRenderer, enemyRenderer, decoRenderer, triggerRenderer, npcRenderer;
    private String lastLoadedSet = "";

    public LevelRenderer(TabView tabView) {
        this.tabView = tabView;
        this.level = tabView.getLevel();
        reloadTileset();
    }

    private void reloadTileset() {
        String set = (String) tabView.getSettings().getParameter(SettingsKey.TILE_SET);
        if (set.equals(lastLoadedSet)) return;
        List<DecoMetadata> metadata = Framework.getInstance().getStorage().getDecoMetadataMap().get(set);
        this.terrainRenderer = new TerrainRenderer(Framework.getInstance().getStorage().getImageMap().get(set + "Tiles"));
        this.objectRenderer = new ObjectRenderer(Framework.getInstance().getStorage().getImageMap().get("Objects"));
        this.enemyRenderer = new EnemyRenderer(Framework.getInstance().getStorage().getImageMap().get("Enemies"));
        this.decoRenderer = new DecoRenderer(Framework.getInstance().getStorage().getImageMap().get(set + "Deco"), metadata);
        this.triggerRenderer = new TriggerRenderer();
        this.npcRenderer = new NpcRenderer();
        this.lastLoadedSet = set;
    }

    @Override
    public void render(Graphics g) {
        reloadTileset();
        if (level.getChildren() == null) return;
        String layersFlags = (String) tabView.getSettings().getParameter(SettingsKey.LAYERS_INFO);
        int fade = (int) tabView.getSettings().getParameter(SettingsKey.FADE);

        renderBackground(g);
        for (int layer = 0; layer < layersFlags.length() - 1; layer++) {
            if (layersFlags.charAt(layersFlags.length() - 1) == '1' || layersFlags.charAt(layer) == '1') {
                if (layer == 2 && fade == 1) renderFade(g);
                renderLayer(g, layer);
            }
        }
        renderSelection(g);
        renderGrid(g);
        renderGhostPreview(g);
        ((SelectState) Framework.getInstance().getGui().getProjectView().getStateManager().getSelectState()).renderSelection(g);
        Framework.getInstance().getGui().getProjectView().getStateManager().getRectFillState().renderPreview(g);
    }

    /**
     * Renders a translucent preview of the tile(s) about to be placed, pasted, or deleted
     * at the current mouse hover grid coordinate.
     */
    private void renderGhostPreview(Graphics g) {
        Point hover = tabView.getHoverTile();
        if (hover == null || hover.x < 0 || hover.x >= level.getWidth() || hover.y < 0 || hover.y >= level.getHeight()) {
            return;
        }

        State<TabView> currentState = Framework.getInstance().getGui().getProjectView().getStateManager().getCurrentState();
        Graphics2D g2d = (Graphics2D) g.create();

        if (currentState instanceof AddState) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.48f));
            Tile ghost = createGhostTileForCurrentSelection(hover.x, hover.y);
            if (ghost != null) {
                renderSingleTile(g2d, ghost, ghost.getLayer());
            }
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.drawRect(hover.x * TILE_SIZE, hover.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
        else if (currentState instanceof PasteState) {
            List<Tile> copied = Clipboard.getInstance().getCopiedTiles();
            if (!copied.isEmpty()) {
                int minX = copied.stream().mapToInt(Tile::getX).min().orElse(0);
                int minY = copied.stream().mapToInt(Tile::getY).min().orElse(0);
                int offsetX = hover.x - minX;
                int offsetY = hover.y - minY;

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.48f));
                for (Tile orig : copied) {
                    Tile ghost = new Tile("", level, orig.getTileType(), orig.getX() + offsetX, orig.getY() + offsetY, orig.getRed(), orig.getGreen(), orig.getBlue());
                    ghost.setLayer(orig.getLayer());
                    ghost.setRotation(orig.getRotation());
                    ghost.setScaleX(orig.getScaleX());
                    ghost.setScaleY(orig.getScaleY());
                    renderSingleTile(g2d, ghost, ghost.getLayer());
                }
            }
        }
        else if (currentState instanceof DeleteState) {
            g2d.setColor(new Color(231, 76, 60, 85));
            g2d.fillRect(hover.x * TILE_SIZE, hover.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            g2d.setColor(new Color(192, 57, 43, 210));
            g2d.drawRect(hover.x * TILE_SIZE, hover.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }

        g2d.dispose();
    }

    private Tile createGhostTileForCurrentSelection(int x, int y) {
        String set = (String) tabView.getSettings().getParameter(SettingsKey.SELECTED_SET);
        int index = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_TILE);
        int layer = (int) tabView.getSettings().getParameter(SettingsKey.SELECTED_LAYER);
        if (index < 0) return null;

        Tile ghost = switch (set) {
            case "Solid Tiles" -> new Tile("", level, TileType.SOLID, x, y, index, (layer == 5) ? 255 : 254, (layer == 5) ? 255 : 254);
            case "Decorations" -> new Tile("", level, TileType.DECO, x, y, 254, layer, index);
            case "Objects" -> new Tile("", level, TileType.OBJECT, x, y, 254, 254, index);
            case "Enemies" -> new Tile("", level, TileType.ENEMY, x, y, 254, index, 254);
            case "Player" -> new Tile("", level, TileType.PLAYER, x, y, 100, 100, 100);
            case "Triggers" -> new Tile("", level, TileType.TRIGGER, x, y, 254, 254, index);
            case "NPCs" -> new Tile("", level, TileType.NPC, x, y, 0, 0, index);
            default -> null;
        };
        if (ghost != null) ghost.setLayer(layer);
        return ghost;
    }

    private void renderBackground(Graphics g) {
        BufferedImage background = (BufferedImage) tabView.getSettings().getParameter(SettingsKey.BACKGROUND);
        if (background != null) g.drawImage(background, 0, 0, level.getWidth() * TILE_SIZE, level.getHeight() * TILE_SIZE, null);
    }

    private void renderLayer(Graphics g, int layer) {
        for (Node child : level.getChildren()) {
            renderSingleTile(g, (Tile) child, layer);
        }
    }

    private void renderSingleTile(Graphics g, Tile tile, int layer) {
        terrainRenderer.render(g, tile, layer);
        objectRenderer.render(g, tile, layer);
        decoRenderer.render(g, tile, layer);
        enemyRenderer.render(g, tile, layer);
        triggerRenderer.render(g, tile, layer);
        npcRenderer.render(g, tile, layer);
        if (tile.getTileType() == TileType.PLAYER && layer == 5) renderPlayer(g, tile);
    }

    private void renderPlayer(Graphics g, Tile t) {
        BufferedImage playerImg = Framework.getInstance().getStorage().getPlayerImg();
        int x = t.getX() * TILE_SIZE + PLAYER_OFFSET_X;
        int y = t.getY() * TILE_SIZE + PLAYER_OFFSET_Y;
        g.drawImage(playerImg, x, y, PLAYER_WID, PLAYER_HEI, null);
    }

    private void renderFade(Graphics g) {
        Color fadeColor = (Color) tabView.getSettings().getParameter(SettingsKey.FADE_COLOR);
        g.setColor(fadeColor);
        g.fillRect(0, 0, level.getWidth() * TILE_SIZE, level.getHeight() * TILE_SIZE);
    }

    private void renderSelection(Graphics g) {
        List<Tile> selection = (List<Tile>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        if (selection != null && !selection.isEmpty()) {
            g.setColor(SELECTION_COLOR);
            for (Tile selectedTile : selection) {
                g.fillRect(selectedTile.getX() * TILE_SIZE, selectedTile.getY() * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
    }

    private void renderGrid(Graphics g) {
        boolean isGrid = (boolean) tabView.getSettings().getParameter(SettingsKey.GRID);
        if (!isGrid) return;
        Color gridColor = (Color) tabView.getSettings().getParameter(SettingsKey.GRID_COLOR);
        g.setColor(gridColor);

        for (int row = 0; row < level.getHeight(); row++) {
            for (int col = 0; col < level.getWidth(); col++) {
                int x = col * TILE_SIZE;
                int y = row * TILE_SIZE;
                g.drawRect(x, y, TILE_SIZE, TILE_SIZE);
            }
        }
    }

}