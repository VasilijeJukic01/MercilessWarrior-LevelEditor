package editor.model.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata model for a room's companion .json file (biome, background, tileset, particles, lights, NPCs, objects, and decoration transforms).
 */
public class LevelMetadata {

    private String backgroundId;
    private Boolean ambientParticlesEnabled;
    private String tileset;
    private String biome;
    private String clearEnemiesFlag;
    private List<Object> lights;
    private List<Object> npcs;
    private List<Object> objects;
    private List<ObjectMetadata> decorations = new ArrayList<>();

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