package editor.generator.strategy;

/**
 * Enumerates the available procedural room generation archetypes and their corresponding strategies.
 */
public enum RoomArchetype {
    NOISE("True Random Cave & Cliffs", new NoiseStrategy()),
    MULTI_PATH_HOLLOW("Multi-Path Forest Hollow", new MultiPathHollowStrategy()),
    SMASH_GAUNTLET("Two-Tier Trap Gauntlet", new SmashGauntletStrategy()),
    VERTICAL_SERPENT("Vertical S-Curve Shaft", new VerticalSerpentStrategy()),
    CHAMBER_ARENA("Tiered Combat Chamber", new ChamberArenaStrategy());

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