package editor.gui.view.world;

import editor.gui.view.style.CustomButton;
import editor.model.metadata.RoomExitMetadata;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static editor.constants.Constants.*;

/**
 * Dialog view for the World Map & Routing Assembler, delegating business logic to {@link WorldMapController} and rendering to {@link WorldMapCanvas}.
 */
public class WorldMapDialog extends JDialog {

    private final WorldMapController controller;
    private final WorldMapCanvas canvas;

    private final JTextField tfStartLevel = new JTextField("begin", 10);
    private final JLabel lbSelectedRoomInfo = new JLabel("Select a room on the canvas");
    private final JSpinner spRoomX = new JSpinner(new SpinnerNumberModel(0, -500, 2000, 1));
    private final JSpinner spRoomY = new JSpinner(new SpinnerNumberModel(0, -500, 2000, 1));
    private final JLabel lbGapInfo = new JLabel("Shift+Drag on canvas to measure a gap");
    private final DefaultTableModel exitsTableModel;
    private final JTable exitsTable;
    private boolean suppressEvents = false;

    public WorldMapDialog(Frame owner, Project activeProject) {
        super(owner, "World Map & Routing Assembler", true);
        this.controller = new WorldMapController(activeProject);
        this.tfStartLevel.setText(controller.getStartLevelId());

        this.exitsTableModel = new DefaultTableModel(
                new Object[]{"Trigger", "Target Room", "Spawn", "minX", "maxX", "minY", "maxY"}, 0
        );
        this.exitsTable = new JTable(exitsTableModel);
        this.canvas = new WorldMapCanvas(controller, this::syncInspectorFromController, room -> {
            controller.openRoomInEditorTab(room);
            dispose();
        });

        initUI();
        syncInspectorFromController();
    }

    private void initUI() {
        setSize(1320, 780);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(createTopToolbar(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, createInspectorPanel());
        splitPane.setResizeWeight(0.68);
        splitPane.setDividerLocation(860);
        add(splitPane, BorderLayout.CENTER);

        add(createFooter(), BorderLayout.SOUTH);
    }

    private JPanel createTopToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(TOOLBAR_COLOR);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(116, 155, 194, 80)));

        JButton btnImportAll = createFlatButton("1. Import Game World (levels + minimap + json)...");
        btnImportAll.setBackground(new Color(41, 128, 185));
        btnImportAll.setToolTipText("Select your game's 'resources' or 'images/levels' folder to load all levels, minimap.png, and world_map.json at once!");
        btnImportAll.addActionListener(e -> onImportEntireWorld());

        JButton btnAutoWire = createFlatButton("2. Auto-Detect Exits from Touching Edges");
        btnAutoWire.addActionListener(e -> {
            int count = controller.autoWireTouchingExits();
            syncInspectorFromController();
            canvas.repaint();
            JOptionPane.showMessageDialog(this, "Auto-detected and wired " + count + " exit routes!", "Auto-Wire Complete", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton btnPreviewFullWorld = createFlatButton("3. Open Entire World as 1 Mega-Level!");
        btnPreviewFullWorld.setBackground(new Color(142, 68, 173));
        btnPreviewFullWorld.setToolTipText("Stitches all loaded rooms into a single giant Level tab so you can inspect the entire world in full detail");
        btnPreviewFullWorld.addActionListener(e -> {
            Level preview = controller.buildAndOpenMegaWorldPreviewTab();
            if (preview != null) dispose();
        });

        JLabel lbStart = new JLabel("Start Level:");
        lbStart.setForeground(Color.WHITE);
        lbStart.setFont(new Font("SansSerif", Font.BOLD, 12));

        bar.add(btnImportAll);
        bar.add(btnAutoWire);
        bar.add(btnPreviewFullWorld);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(lbStart);
        bar.add(tfStartLevel);
        return bar;
    }


    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBackground(EXPLORER_COLOR);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topCards = new JPanel();
        topCards.setLayout(new BoxLayout(topCards, BoxLayout.Y_AXIS));
        topCards.setOpaque(false);

        JPanel roomCard = new JPanel(new GridBagLayout());
        roomCard.setBackground(TOOLBAR_COLOR);
        roomCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 155, 194, 90)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        lbSelectedRoomInfo.setForeground(new Color(241, 196, 15));
        lbSelectedRoomInfo.setFont(new Font("SansSerif", Font.BOLD, 13));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        roomCard.add(lbSelectedRoomInfo, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1;
        gbc.gridx = 0;
        JLabel lx = new JLabel("mapX:"); lx.setForeground(Color.WHITE);
        roomCard.add(lx, gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        roomCard.add(spRoomX, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JLabel ly = new JLabel("mapY:"); ly.setForeground(Color.WHITE);
        roomCard.add(ly, gbc);
        gbc.gridx = 3; gbc.weightx = 0.5;
        roomCard.add(spRoomY, gbc);

        spRoomX.addChangeListener(e -> {
            if (!suppressEvents && controller.getSelectedRoom() != null) {
                controller.getSelectedRoom().setMapX((Integer) spRoomX.getValue());
                canvas.repaint();
            }
        });
        spRoomY.addChangeListener(e -> {
            if (!suppressEvents && controller.getSelectedRoom() != null) {
                controller.getSelectedRoom().setMapY((Integer) spRoomY.getValue());
                canvas.repaint();
            }
        });

        topCards.add(roomCard);
        topCards.add(Box.createVerticalStrut(10));

        JPanel gapCard = new JPanel(new BorderLayout(6, 6));
        gapCard.setBackground(TOOLBAR_COLOR);
        gapCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(46, 204, 113, 120)),
                new EmptyBorder(8, 10, 8, 10)
        ));
        lbGapInfo.setForeground(new Color(46, 204, 113));
        lbGapInfo.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton btnCreateRoomInGap = createFlatButton("+ Create New Level in Measured Box");
        btnCreateRoomInGap.addActionListener(e -> onCreateLevelInGap());
        gapCard.add(lbGapInfo, BorderLayout.NORTH);
        gapCard.add(btnCreateRoomInGap, BorderLayout.SOUTH);

        topCards.add(gapCard);
        panel.add(topCards, BorderLayout.NORTH);

        JPanel exitsPanel = new JPanel(new BorderLayout(0, 6));
        exitsPanel.setOpaque(false);
        JLabel lbExits = new JLabel("ROOM EXIT ROUTES (Supports Multiple Routes & Optional Tile Bounds)");
        lbExits.setForeground(Color.WHITE);
        lbExits.setFont(new Font("SansSerif", Font.BOLD, 11));
        exitsPanel.add(lbExits, BorderLayout.NORTH);

        exitsTable.setRowHeight(24);
        exitsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        exitsTableModel.addTableModelListener(e -> saveTableEditsToController());

        exitsPanel.add(new JScrollPane(exitsTable), BorderLayout.CENTER);

        JPanel exitButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        exitButtons.setOpaque(false);
        JButton btnAddExit = createFlatButton("+ Add Exit Route");
        JButton btnRemoveExit = createFlatButton("- Remove Selected Exit");

        btnAddExit.addActionListener(e -> {
            if (controller.getSelectedRoom() != null) {
                exitsTableModel.addRow(new Object[]{"LOAD_RIGHT_LEVEL", "", "SPAWN_B", "", "", "", ""});
            }
        });
        btnRemoveExit.addActionListener(e -> {
            int row = exitsTable.getSelectedRow();
            if (row >= 0) exitsTableModel.removeRow(row);
        });

        exitButtons.add(btnAddExit);
        exitButtons.add(btnRemoveExit);
        exitsPanel.add(exitButtons, BorderLayout.SOUTH);

        panel.add(exitsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(TOOLBAR_COLOR);
        footer.setBorder(new EmptyBorder(8, 14, 8, 14));

        JLabel helpLabel = new JLabel("Left-Drag = Move Room  |  Double-Click Room = Edit Room Tab  |  Shift+Drag = Measure Gap  |  Right-Drag = Pan");
        helpLabel.setForeground(new Color(180, 200, 220));
        helpLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.add(helpLabel, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBtns.setOpaque(false);

        JButton btnExport = createFlatButton("Export minimap.png & world_map.json...");
        btnExport.setBackground(new Color(39, 174, 96));
        btnExport.addActionListener(e -> onExport());

        JButton btnClose = new JButton("Close");
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(e -> dispose());

        rightBtns.add(btnExport);
        rightBtns.add(btnClose);
        footer.add(rightBtns, BorderLayout.EAST);
        return footer;
    }

    private void onLoadFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Directory Containing Level PNGs");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            controller.loadLevelsFromDirectory(chooser.getSelectedFile());
            syncInspectorFromController();
            canvas.repaint();
        }
    }

    private void onLoadJson() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select world_map.json");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                controller.loadWorldMapJson(chooser.getSelectedFile());
                tfStartLevel.setText(controller.getStartLevelId());
                syncInspectorFromController();
                canvas.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to read world_map.json:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onAutoAlign() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Existing minimap.png");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                controller.autoAlignFromMinimapPng(chooser.getSelectedFile());
                syncInspectorFromController();
                canvas.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to align from minimap.png:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onCreateLevelInGap() {
        Rectangle gap = controller.getMeasuredGap();
        if (gap == null || gap.width < 10 || gap.height < 10) {
            JOptionPane.showMessageDialog(this, "Hold Shift + Left-Drag on the canvas to measure a gap (at least 10x10 tiles) first!", "Measure a Gap First", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                String.format("Enter new Level ID for %dx%d room at (%d, %d):", gap.width, gap.height, gap.x, gap.y),
                "forest_new");
        if (name != null && !name.trim().isEmpty()) {
            controller.createNewLevelInGap(name.trim());
            syncInspectorFromController();
            canvas.repaint();
        }
    }

    private void onImportEntireWorld() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Game 'resources' or 'images/levels' Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (controller.getLastResourcesRoot() != null) {
            chooser.setCurrentDirectory(controller.getLastResourcesRoot());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                controller.importEntireGameWorld(chooser.getSelectedFile());
                tfStartLevel.setText(controller.getStartLevelId());
                syncInspectorFromController();
                canvas.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to import game world:\n" + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private void onExport() {
        if (controller.getRooms().isEmpty()) return;

        File minimapFile = controller.getLoadedMinimapFile();
        File jsonFile = controller.getLoadedWorldMapJsonFile();

        if (minimapFile == null || jsonFile == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Stitched minimap.png");
            chooser.setSelectedFile(new File("minimap.png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            minimapFile = chooser.getSelectedFile();
            if (!minimapFile.getName().toLowerCase().endsWith(".png")) {
                minimapFile = new File(minimapFile.getAbsolutePath() + ".png");
            }

            JFileChooser jsonChooser = new JFileChooser(minimapFile.getParentFile());
            jsonChooser.setDialogTitle("Save world_map.json");
            jsonChooser.setSelectedFile(new File(minimapFile.getParentFile(), "world_map.json"));
            if (jsonChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            jsonFile = jsonChooser.getSelectedFile();
            if (!jsonFile.getName().toLowerCase().endsWith(".json")) {
                jsonFile = new File(jsonFile.getAbsolutePath() + ".json");
            }
        }

        try {
            controller.exportWorldMapAndMinimap(minimapFile, jsonFile, tfStartLevel.getText().trim());
            syncInspectorFromController();
            canvas.repaint();
            JOptionPane.showMessageDialog(this,
                    "Successfully saved directly to:\n1. " + minimapFile.getAbsolutePath() + "\n2. " + jsonFile.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void syncInspectorFromController() {
        suppressEvents = true;
        WorldMapRoomEntry selected = controller.getSelectedRoom();
        exitsTableModel.setRowCount(0);
        if (selected != null) {
            lbSelectedRoomInfo.setText(String.format("Room: %s  (%dx%d tiles)", selected.getId(), selected.getWidth(), selected.getHeight()));
            spRoomX.setValue(selected.getMapX());
            spRoomY.setValue(selected.getMapY());
            for (RoomExitMetadata ex : selected.getExits()) {
                exitsTableModel.addRow(new Object[]{
                        ex.getTrigger(),
                        ex.getTargetLevel(),
                        ex.getTargetSpawn(),
                        ex.getMinX() != null ? ex.getMinX() : "",
                        ex.getMaxX() != null ? ex.getMaxX() : "",
                        ex.getMinY() != null ? ex.getMinY() : "",
                        ex.getMaxY() != null ? ex.getMaxY() : ""
                });
            }
        } else {
            lbSelectedRoomInfo.setText("No room selected");
        }

        Rectangle gap = controller.getMeasuredGap();
        if (gap != null) {
            lbGapInfo.setText(String.format("Measured Box: %dx%d tiles at (%d, %d)", gap.width, gap.height, gap.x, gap.y));
        }
        suppressEvents = false;
    }

    private void saveTableEditsToController() {
        if (suppressEvents || controller.getSelectedRoom() == null) return;
        List<RoomExitMetadata> updated = new ArrayList<>();
        for (int i = 0; i < exitsTableModel.getRowCount(); i++) {
            String trig = Objects.toString(exitsTableModel.getValueAt(i, 0), "").trim();
            String target = Objects.toString(exitsTableModel.getValueAt(i, 1), "").trim();
            String spawn = Objects.toString(exitsTableModel.getValueAt(i, 2), "").trim();
            RoomExitMetadata ex = new RoomExitMetadata(trig, target, spawn);
            ex.setMinX(parseIntOrNull(exitsTableModel.getValueAt(i, 3)));
            ex.setMaxX(parseIntOrNull(exitsTableModel.getValueAt(i, 4)));
            ex.setMinY(parseIntOrNull(exitsTableModel.getValueAt(i, 5)));
            ex.setMaxY(parseIntOrNull(exitsTableModel.getValueAt(i, 6)));
            updated.add(ex);
        }
        controller.getSelectedRoom().setExits(updated);
        canvas.repaint();
    }

    private Integer parseIntOrNull(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JButton createFlatButton(String text) {
        JButton b = new JButton(text);
        b.setUI(new CustomButton());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}