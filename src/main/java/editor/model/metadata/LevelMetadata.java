package editor.model.metadata;

import java.util.ArrayList;
import java.util.List;

public class LevelMetadata {

    private String backgroundId;
    private Boolean ambientParticlesEnabled;
    private String tileset;
    private List<Object> lights = new ArrayList<>();
    private List<ObjectMetadata> decorations = new ArrayList<>();
    private String biome;

    public List<ObjectMetadata> getDecorations() {
        return decorations;
    }

    public String getBiome() {
        return biome;
    }

    public void setDecorations(List<ObjectMetadata> decorations) {
        this.decorations = decorations;
    }

    public void setBiome(String biome) {
        this.biome = biome;
    }
}
