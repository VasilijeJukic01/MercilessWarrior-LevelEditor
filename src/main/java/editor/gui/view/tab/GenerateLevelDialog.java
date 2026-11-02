package editor.gui.view.tab;

import editor.generator.LevelGeneratorFacade;
import editor.generator.strategy.RoomArchetype;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.settings.SettingsKey;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static editor.constants.Constants.TOOLBAR_COLOR;

/**
 * Dialog providing controls for procedural room generation, full-level AI generation, non-blocking Co-Op Regional AI inpainting, auto-tiling, and auto-decoration.
 */
public class GenerateLevelDialog extends JDialog {

    private static final LevelGeneratorFacade generator = new LevelGeneratorFacade();
    private static String cachedApiKey = "";
    private static String cachedPrompt = "A multi-tiered forest cavern with a central island, a jump pad on the left, and 3 knights.";

    private final TabView tabView;
    private final Rectangle selectedRegion;

    private final JSpinner spWidth = new JSpinner(new SpinnerNumberModel(45, 26, 99, 1));
    private final JSpinner spHeight = new JSpinner(new SpinnerNumberModel(26, 14, 99, 1));
    private final JComboBox<RoomArchetype> cbArchetype = new JComboBox<>(RoomArchetype.values());
    private final JSlider slEnemyDensity = new JSlider(0, 100, 45);
    private final JSlider slDecoDensity = new JSlider(0, 100, 65);

    private final JTextArea taAiPrompt = new JTextArea(3, 25);
    private final JPasswordField pfApiKey = new JPasswordField(20);

    /**
     * Constructs the level generator dialog bound to the specified tab view.
     *
     * @param owner   The parent editor frame.
     * @param tabView The active level tab view.
     */
    public GenerateLevelDialog(Frame owner, TabView tabView) {
        super(owner, "Level Generator & Co-Pilot", true);
        this.tabView = tabView;
        this.selectedRegion = (Rectangle) tabView.getSettings().getParameter(SettingsKey.SELECTED_REGION);
        spWidth.setValue(tabView.getLevel().getWidth());
        spHeight.setValue(tabView.getLevel().getHeight());
        pfApiKey.setText(cachedApiKey);
        taAiPrompt.setText(cachedPrompt);
        initUI();
    }

    private void initUI() {
        setSize(540, 510);
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

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        form.add(new JSeparator(), gbc);

        String promptLabel = (selectedRegion != null)
                ? String.format("AI Prompt (Active Selection: X=%d, Y=%d, Size=%dx%d):", selectedRegion.x, selectedRegion.y, selectedRegion.width, selectedRegion.height)
                : "AI Prompt (Gemini) OR Paste Raw '#' and '.' ASCII Grid:";
        gbc.gridy = row++;
        form.add(new JLabel(promptLabel), gbc);

        taAiPrompt.setLineWrap(true);
        taAiPrompt.setWrapStyleWord(true);
        gbc.gridy = row++;
        form.add(new JScrollPane(taAiPrompt), gbc);

        gbc.gridwidth = 1;
        addRow(form, gbc, row++, "Gemini API Key:", pfApiKey);

        add(form, BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout(8, 8));
        southContainer.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton btnCoOpRegion = new JButton();
        if (selectedRegion != null) {
            btnCoOpRegion.setText(String.format("Co-Op AI: Remake Selected %dx%d Region in Background", selectedRegion.width, selectedRegion.height));
            btnCoOpRegion.setBackground(new Color(211, 84, 0));
            btnCoOpRegion.setForeground(Color.WHITE);
            btnCoOpRegion.setFont(btnCoOpRegion.getFont().deriveFont(Font.BOLD, 13f));
            btnCoOpRegion.addActionListener(e -> runCoOpRegionalAi());
        }
        else {
            btnCoOpRegion.setText("Co-Op AI: Select a Region with 'Select' Tool First to Remake Part of Level");
            btnCoOpRegion.setEnabled(false);
        }
        southContainer.add(btnCoOpRegion, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton btnProcedural = new JButton("1. Generate Procedural Room");
        JButton btnAi = new JButton("2. Generate Full Level (AI)");
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

        btnAi.addActionListener(e -> runFullLevelAiGeneration());

        buttons.add(btnProcedural);
        buttons.add(btnAi);
        buttons.add(btnAutoTile);
        buttons.add(btnAutoDeco);
        southContainer.add(buttons, BorderLayout.CENTER);

        add(southContainer, BorderLayout.SOUTH);
    }

    /**
     * Launches a non-blocking background AI worker for the selected region and immediately closes the dialog so the user can continue editing other parts of the level in real time.
     */
    private void runCoOpRegionalAi() {
        if (selectedRegion == null) return;
        String prompt = taAiPrompt.getText().trim();
        String apiKey = new String(pfApiKey.getPassword()).trim();
        cachedPrompt = prompt;
        if (!apiKey.isEmpty()) cachedApiKey = apiKey;

        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your Gemini API Key first!", "Missing API Key", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Level level = tabView.getLevel();
        Rectangle targetRegion = new Rectangle(selectedRegion);
        double decoDensity = slDecoDensity.getValue() / 100.0;

        // Snapshot the terrain on the EDT and register the locked construction zone on the canvas
        boolean[][] solidSnapshot = generator.snapshotSolidGrid(level);
        ActiveAiZone zone = new ActiveAiZone(targetRegion, prompt, System.currentTimeMillis());
        tabView.addActiveAiZone(zone);
        tabView.getSettings().updateParameter(SettingsKey.SELECTED_REGION, null);
        List<?> editSelection = (List<?>) tabView.getSettings().getParameter(SettingsKey.EDIT_SELECTION);
        if (editSelection != null) editSelection.clear();

        dispose();

        // Compute regional blueprint off-thread, then patch on the EDT when ready
        new Thread(() -> {
            try {
                List<Node> nodesSnapshot = generator.snapshotNodes(level);
                List<Node> regionNodes = generator.generateRegionalAiNodesOffThread(level, targetRegion, solidSnapshot, nodesSnapshot, prompt, apiKey);
                SwingUtilities.invokeLater(() -> {
                    tabView.removeActiveAiZone(zone);
                    generator.commitRegionalPatchOnEdt(level, targetRegion, regionNodes);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> {
                    tabView.removeActiveAiZone(zone);
                    JOptionPane.showMessageDialog(tabView, "Co-Op AI Region failed:\n" + ex.getMessage(), "AI Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "CoOp-AI-Region-Worker").start();
    }

    private void runFullLevelAiGeneration() {
        String prompt = taAiPrompt.getText().trim();
        String apiKey = new String(pfApiKey.getPassword()).trim();
        cachedPrompt = prompt;
        if (!apiKey.isEmpty()) cachedApiKey = apiKey;

        int w = (Integer) spWidth.getValue();
        int h = (Integer) spHeight.getValue();
        double deco = slDecoDensity.getValue() / 100.0;

        boolean isRawAscii = prompt.contains("###") && prompt.contains("...");
        if (!isRawAscii && apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a Gemini API Key or paste an ASCII grid using '#' and '.'!", "Info", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AiLoadingDialog loadingDialog = new AiLoadingDialog(this, cancelled);

        Thread worker = new Thread(() -> {
            try {
                String blueprint = generator.fetchAiAscii(w, h, prompt, apiKey);
                if (cancelled.get()) return;

                SwingUtilities.invokeLater(() -> {
                    loadingDialog.stopAndClose();
                    generator.applyAsciiToLevel(tabView.getLevel(), w, h, blueprint, deco);
                    dispose();
                });
            } catch (Throwable ex) {
                if (cancelled.get()) return;
                SwingUtilities.invokeLater(() -> {
                    loadingDialog.stopAndClose();
                    JOptionPane.showMessageDialog(this, "Generation failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });

        loadingDialog.setWorkerThread(worker);
        worker.start();
        loadingDialog.setVisible(true);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.65;
        panel.add(comp, gbc);
    }

    /**
     * Undecorated modal progress dialog displayed while waiting for the AI generation response.
     */
    private static class AiLoadingDialog extends JDialog {

        private final JLabel lbStatus = new JLabel("Connecting to Gemini AI...", SwingConstants.CENTER);
        private final JLabel lbTimer = new JLabel("Elapsed: 0.0s", SwingConstants.CENTER);
        private final Timer uiTimer;
        private final long startTime;
        private Thread workerThread;

        private static final String[] STATUS_MESSAGES = {
                "Designing cavern geometry & platforms...",
                "Calculating jump arcs & enemy placements...",
                "Preparing auto-tiling & foliage layers...",
                "Finalizing level blueprint..."
        };

        public AiLoadingDialog(Dialog owner, AtomicBoolean cancelled) {
            super(owner, "Generating Level...", true);
            setUndecorated(true);
            setSize(340, 150);
            setLocationRelativeTo(owner);

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBackground(TOOLBAR_COLOR);
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(116, 155, 194), 2),
                    BorderFactory.createEmptyBorder(15, 20, 15, 20)
            ));

            lbStatus.setForeground(Color.WHITE);
            lbStatus.setFont(lbStatus.getFont().deriveFont(Font.BOLD, 13f));

            lbTimer.setForeground(new Color(200, 220, 240));
            lbTimer.setFont(lbTimer.getFont().deriveFont(Font.PLAIN, 12f));

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);

            JPanel centerPanel = new JPanel(new GridLayout(2, 1, 5, 5));
            centerPanel.setOpaque(false);
            centerPanel.add(progressBar);
            centerPanel.add(lbTimer);

            JButton btnCancel = new JButton("Cancel");
            btnCancel.setFocusPainted(false);
            btnCancel.addActionListener(e -> {
                cancelled.set(true);
                if (workerThread != null) workerThread.interrupt();
                stopAndClose();
            });

            JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            southPanel.setOpaque(false);
            southPanel.add(btnCancel);

            content.add(lbStatus, BorderLayout.NORTH);
            content.add(centerPanel, BorderLayout.CENTER);
            content.add(southPanel, BorderLayout.SOUTH);
            setContentPane(content);

            this.startTime = System.currentTimeMillis();
            this.uiTimer = new Timer(100, e -> updateProgressText());
            this.uiTimer.start();
        }

        private void updateProgressText() {
            double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
            lbTimer.setText(String.format("Elapsed: %.1fs", elapsedSec));
            int msgIndex = Math.min(STATUS_MESSAGES.length - 1, (int) (elapsedSec / 1.5));
            lbStatus.setText(STATUS_MESSAGES[msgIndex]);
        }

        public void setWorkerThread(Thread workerThread) {
            this.workerThread = workerThread;
        }

        public void stopAndClose() {
            uiTimer.stop();
            dispose();
        }
    }
}