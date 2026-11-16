package editor.model.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata model for a single room node in world_map.json.
 */
public class RoomNodeMetadata {

    private Integer mapX;
    private Integer mapY;
    private Integer totemId;
    private String totemName;
    private List<RoomExitMetadata> exits = new ArrayList<>();

    public Integer getMapX() {
        return mapX;
    }

    public void setMapX(Integer mapX) {
        this.mapX = mapX;
    }

    public Integer getMapY() {
        return mapY;
    }

    public void setMapY(Integer mapY) {
        this.mapY = mapY;
    }

    public Integer getTotemId() {
        return totemId;
    }

    public void setTotemId(Integer totemId) {
        this.totemId = totemId;
    }

    public String getTotemName() {
        return totemName;
    }

    public void setTotemName(String totemName) {
        this.totemName = totemName;
    }

    public List<RoomExitMetadata> getExits() {
        return exits;
    }

    public void setExits(List<RoomExitMetadata> exits) {
        this.exits = exits;
    }
}