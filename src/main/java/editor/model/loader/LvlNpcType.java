package editor.model.loader;

import java.awt.Color;

public enum LvlNpcType {
    ANITA("Anita", new Color(212, 145, 237, 180)),                  // 0
    NIKOLAS("Nikolas", new Color(224, 107, 81, 180)),               // 1
    SIR_DEJANOVIC("Sir Dejanovic", new Color(103, 171, 235, 180)),  // 2
    KRYSANTHE("Krysanthe", new Color(255, 112, 238, 180)),          // 3
    RORIC("Roric", new Color(156, 221, 74, 180)),                   // 4
    MITCH_OF_ASSIS("Mitch", new Color(255, 228, 83, 180)),          // 5
    ANDELLIA("Andellia", new Color(0, 34, 223, 180)),               // 6
    EERYN("Eeryn", new Color(124, 0, 188, 180)),                    // 7
    LANCER("Lancer", new Color(78, 232, 255, 180)),                 // 8
    BLACKSMITH("Blacksmith", new Color(232, 152, 70, 180)),         // 9
    DOG("Dog", new Color(212, 176, 123, 180)),                      // 10
    MAX("", Color.BLACK);                                           // 11

    private final String label;
    private final Color color;

    LvlNpcType(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public Color getColor() {
        return color;
    }
}