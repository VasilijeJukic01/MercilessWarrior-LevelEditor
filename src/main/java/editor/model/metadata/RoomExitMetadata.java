package editor.model.metadata;

/**
 * Metadata model representing a directional or coordinate-bounded exit transition in world_map.json.
 */
public class RoomExitMetadata {

    private String trigger;
    private String targetLevel;
    private String targetSpawn;
    private Integer minX;
    private Integer maxX;
    private Integer minY;
    private Integer maxY;

    public RoomExitMetadata() {}

    public RoomExitMetadata(String trigger, String targetLevel, String targetSpawn) {
        this.trigger = trigger;
        this.targetLevel = targetLevel;
        this.targetSpawn = targetSpawn;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(String targetLevel) {
        this.targetLevel = targetLevel;
    }

    public String getTargetSpawn() {
        return targetSpawn;
    }

    public void setTargetSpawn(String targetSpawn) {
        this.targetSpawn = targetSpawn;
    }

    public Integer getMinX() {
        return minX;
    }

    public void setMinX(Integer minX) {
        this.minX = minX;
    }

    public Integer getMaxX() {
        return maxX;
    }

    public void setMaxX(Integer maxX) {
        this.maxX = maxX;
    }

    public Integer getMinY() {
        return minY;
    }

    public void setMinY(Integer minY) {
        this.minY = minY;
    }

    public Integer getMaxY() {
        return maxY;
    }

    public void setMaxY(Integer maxY) {
        this.maxY = maxY;
    }
}