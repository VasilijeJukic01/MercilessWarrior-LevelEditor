package editor.gui.view.tab;

import editor.generator.LevelGeneratorFacade;
import editor.generator.strategy.RoomArchetype;

import javax.swing.*;
import java.awt.*;

public class GenerateLevelDialog extends JDialog {

    private static final LevelGeneratorFacade generator = new LevelGeneratorFacade();

    private final TabView tabView;
    private final JSpinner spWidth = new JSpinner(new SpinnerNumberModel(45, 26, 99, 1));
    private final JSpinner spHeight = new JSpinner(new SpinnerNumberModel(26, 14, 99, 1));
    private final JComboBox<RoomArchetype> cbArchetype = new JComboBox<>(RoomArchetype.values());
    private final JSlider slEnemyDensity = new JSlider(0, 100, 45);
    private final JSlider slDecoDensity = new JSlider(0, 100, 65);

    private final JTextArea taAiPrompt = new JTextArea(3, 25);
    private final JPasswordField pfApiKey = new JPasswordField(20);

    public GenerateLevelDialog(Frame owner, TabView tabView) {
        super(owner, "Level Generator & Co-Pilot", true);
        this.tabView = tabView;
        spWidth.setValue(tabView.getLevel().getWidth());
        spHeight.setValue(tabView.getLevel().getHeight());
        initUI();
    }

    private void initUI() {
        setSize(520, 500);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, gbc, row++, "Width (Tiles):", spWidth);
        addRow(form, gbc, row++, "Height (Tiles):", spHeight);
        addRow(form, gbc, row++, "Room Archetype:", cbArchetype);
        addRow(form, gbc, row++, "Enemy Density:", slEnemyDensity);
        addRow(form, gbc, row++, "Decoration Density:", slDecoDensity);

        gbc.gridy = row++;
        form.add(new JSeparator(), gbc);

        gbc.gridy = row++;
        form.add(new JLabel("AI Prompt (Gemini) OR Paste Raw '#' and '.' ASCII Grid:"), gbc);

        taAiPrompt.setLineWrap(true);
        taAiPrompt.setWrapStyleWord(true);
        taAiPrompt.setText("A multi-tiered forest cavern with a central island, a jump pad on the left, and 3 knights guarding a save totem.");
        gbc.gridy = row++;
        form.add(new JScrollPane(taAiPrompt), gbc);

        gbc.gridwidth = 1;
        addRow(form, gbc, row++, "Gemini API Key:", pfApiKey);

        add(form, BorderLayout.CENTER);

        // Co-Pilot & Generator Action Buttons
        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton btnProcedural = new JButton("1. Generate Procedural Room");
        JButton btnAi = new JButton("2. Generate from AI / ASCII");
        JButton btnAutoTile = new JButton("3. Auto-Tile Current Terrain");
        JButton btnAutoDeco = new JButton("4. Auto-Decorate Current Level");

        btnProcedural.addActionListener(e -> {
            generator.generateProcedural(
                    tabView.getLevel(),
                    (Integer) spWidth.getValue(),
                    (Integer) spHeight.getValue(),
                    (RoomArchetype) cbArchetype.getSelectedItem(),
                    slEnemyDensity.getValue() / 100.0,
                    slDecoDensity.getValue() / 100.0
            );
            dispose();
        });

        btnAutoTile.addActionListener(e -> {
            generator.autoTileCurrentLevel(tabView.getLevel());
            dispose();
        });

        btnAutoDeco.addActionListener(e -> {
            generator.autoDecorateCurrentLevel(tabView.getLevel(), slDecoDensity.getValue() / 100.0);
            dispose();
        });

        btnAi.addActionListener(e -> runAiGeneration());

        buttons.add(btnProcedural);
        buttons.add(btnAi);
        buttons.add(btnAutoTile);
        buttons.add(btnAutoDeco);
        add(buttons, BorderLayout.SOUTH);
    }

    private void runAiGeneration() {
        String prompt = taAiPrompt.getText().trim();
        String apiKey = new String(pfApiKey.getPassword()).trim();
        int w = (Integer) spWidth.getValue();
        int h = (Integer) spHeight.getValue();
        double deco = slDecoDensity.getValue() / 100.0;

        boolean isRawAscii = prompt.contains("###") && prompt.contains("...");
        if (!isRawAscii && apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a Gemini API Key or paste an ASCII grid using '#' and '.'!", "Info", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new Thread(() -> {
            try {
                String ascii = generator.fetchAiAscii(w, h, prompt, apiKey);
                SwingUtilities.invokeLater(() -> {
                    generator.applyAsciiToLevel(tabView.getLevel(), w, h, ascii, deco);
                    dispose();
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    JOptionPane.showMessageDialog(this, "Generation failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        panel.add(comp, gbc);
    }
}