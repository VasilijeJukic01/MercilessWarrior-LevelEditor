package editor.model.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata model for a room's companion .json file (biome, background, tileset, particles, lights, NPCs, objects, and decoration transforms).
 */
public class LevelMetadata {

    public static final String DEFAULT_BACKGROUND_ID = "FOREST1";
    public static final boolean DEFAULT_AMBIENT_PARTICLES = true;
    public static final String DEFAULT_TILESET = "Forest";
    public static final String DEFAULT_BIOME = "FOREST";

    private String backgroundId;
    private Boolean ambientParticlesEnabled;
    private String tileset;
    private String biome;
    private String clearEnemiesFlag;
    private List<Object> lights;
    private List<Object> npcs;
    private List<Object> objects;
    private List<ObjectMetadata> decorations = new ArrayList<>();

    public void ensureDefaults(String fallbackTileset) {
        if (backgroundId == null || backgroundId.isBlank()) {
            backgroundId = DEFAULT_BACKGROUND_ID;
        }
        if (ambientParticlesEnabled == null) {
            ambientParticlesEnabled = DEFAULT_AMBIENT_PARTICLES;
        }
        if (tileset == null || tileset.isBlank()) {
            tileset = (fallbackTileset != null && !fallbackTileset.isBlank()) ? fallbackTileset : DEFAULT_TILESET;
        }
        if (biome == null || biome.isBlank()) {
            biome = DEFAULT_BIOME;
        }
        if (decorations == null) {
            decorations = new ArrayList<>();
        }
    }

    public LevelMetadata copy() {
        LevelMetadata clone = new LevelMetadata();
        clone.setBackgroundId(this.backgroundId);
        clone.setAmbientParticlesEnabled(this.ambientParticlesEnabled);
        clone.setTileset(this.tileset);
        clone.setBiome(this.biome);
        clone.setClearEnemiesFlag(this.clearEnemiesFlag);
        if (this.lights != null) clone.setLights(new ArrayList<>(this.lights));
        if (this.npcs != null) clone.setNpcs(new ArrayList<>(this.npcs));
        if (this.objects != null) clone.setObjects(new ArrayList<>(this.objects));
        if (this.decorations != null) {
            List<ObjectMetadata> decoCopies = new ArrayList<>(this.decorations.size());
            for (ObjectMetadata om : this.decorations) {
                ObjectMetadata omCopy = new ObjectMetadata();
                omCopy.setX(om.getX());
                omCopy.setY(om.getY());
                omCopy.setLayer(om.getLayer());
                omCopy.setRotation(om.getRotation());
                omCopy.setScaleX(om.getScaleX());
                omCopy.setScaleY(om.getScaleY());
                omCopy.setOffsetX(om.getOffsetX());
                omCopy.setOffsetY(om.getOffsetY());
                decoCopies.add(omCopy);
            }
            clone.setDecorations(decoCopies);
        }
        return clone;
    }

    public String getBackgroundId() {
        return backgroundId;
    }

    public void setBackgroundId(String backgroundId) {
        this.backgroundId = backgroundId;
    }

    public Boolean getAmbientParticlesEnabled() {
        return ambientParticlesEnabled;
    }

    public void setAmbientParticlesEnabled(Boolean ambientParticlesEnabled) {
        this.ambientParticlesEnabled = ambientParticlesEnabled;
    }

    public String getTileset() {
        return tileset;
    }

    public void setTileset(String tileset) {
        this.tileset = tileset;
    }

    public String getBiome() {
        return biome;
    }

    public void setBiome(String biome) {
        this.biome = biome;
    }

    public String getClearEnemiesFlag() {
        return clearEnemiesFlag;
    }

    public void setClearEnemiesFlag(String clearEnemiesFlag) {
        this.clearEnemiesFlag = clearEnemiesFlag;
    }

    public List<Object> getLights() {
        return lights;
    }

    public void setLights(List<Object> lights) {
        this.lights = lights;
    }

    public List<Object> getNpcs() {
        return npcs;
    }

    public void setNpcs(List<Object> npcs) {
        this.npcs = npcs;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public void setObjects(List<Object> objects) {
        this.objects = objects;
    }

    public List<ObjectMetadata> getDecorations() {
        return decorations;
    }

    public void setDecorations(List<ObjectMetadata> decorations) {
        this.decorations = decorations;
    }
}