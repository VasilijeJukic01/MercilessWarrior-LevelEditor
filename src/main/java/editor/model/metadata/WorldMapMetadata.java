package editor.model.metadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root metadata model for world_map.json.
 */
public class WorldMapMetadata {

    private String startLevel = "begin";
    private Map<String, RoomNodeMetadata> rooms = new LinkedHashMap<>();

    public String getStartLevel() {
        return startLevel;
    }

    public void setStartLevel(String startLevel) {
        this.startLevel = startLevel;
    }

    public Map<String, RoomNodeMetadata> getRooms() {
        return rooms;
    }

    public void setRooms(Map<String, RoomNodeMetadata> rooms) {
        this.rooms = rooms;
    }
}