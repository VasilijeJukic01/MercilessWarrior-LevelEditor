package editor.generator.strategy;

public enum RoomArchetype {
    MULTI_PATH_HOLLOW("Multi-Path Forest Hollow (Level 1 & 4 Style)", new MultiPathHollowStrategy()),
    SMASH_GAUNTLET("Two-Tier Trap Gauntlet (Level 2 Style)", new SmashGauntletStrategy()),
    VERTICAL_SERPENT("Vertical S-Curve Shaft (Level 7 Style)", new VerticalSerpentStrategy()),
    CHAMBER_ARENA("Tiered Combat Chamber (Level 3 & 6 Style)", new ChamberArenaStrategy());

    private final String label;
    private final RoomCarverStrategy strategy;

    RoomArchetype(String label, RoomCarverStrategy strategy) {
        this.label = label;
        this.strategy = strategy;
    }

    public RoomCarverStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return label;
    }
}