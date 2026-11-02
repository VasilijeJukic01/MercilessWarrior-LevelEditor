package editor.gui.view;

import editor.core.Framework;
import editor.gui.controller.levelActions.ExportLevelAction;
import editor.gui.view.style.CustomButton;
import editor.logger.LogType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.repository.components.Tile;
import editor.model.tree.EditorTree;
import editor.model.tree.mvc.TreeItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

import static editor.constants.Constants.*;

/**
 * Dialog for configuring project & level properties, viewing project-wide statistics, duplicating levels, and batch-exporting all levels.
 */
public class ProjectOptionsDialog extends JDialog {

    private final Project project;
    private final Level selectedLevel;

    private final JTextField tfProjectName = new JTextField();
    private final JTextField tfAuthor = new JTextField();
    private final JTextField tfPath = new JTextField();
    private final JTextField tfLevelName = new JTextField();
    private JComboBox<String> cbLevelsToDuplicate;

    public ProjectOptionsDialog(Frame owner, Project project, Level selectedLevel) {
        super(owner, "Project & Level Options", true);
        this.project = project;
        this.selectedLevel = selectedLevel;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(EXPLORER_COLOR);
        main.setBorder(new EmptyBorder(14, 16, 14, 16));

        main.add(createProjectIdentitySection());
        main.add(Box.createVerticalStrut(12));

        if (selectedLevel != null) {
            main.add(createSelectedLevelSection());
            main.add(Box.createVerticalStrut(12));
        }

        main.add(createStatsAndToolsSection());

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.setBackground(EXPLORER_COLOR);
        northWrapper.add(main, BorderLayout.NORTH);

        add(northWrapper, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(490, 450));
        pack();
        setResizable(false);
        setLocationRelativeTo(getOwner());
    }

    private JPanel createProjectIdentitySection() {
        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        tfProjectName.setText(project.getName());
        tfAuthor.setText(project.getAuthor());
        tfPath.setText(project.getPath() != null ? project.getPath() : "");

        JPanel pathPanel = new JPanel(new BorderLayout(6, 0));
        pathPanel.setOpaque(false);
        pathPanel.add(tfPath, BorderLayout.CENTER);

        JButton btnBrowse = createFlatButton("Browse...");
        btnBrowse.setToolTipText("Choose project save file (.json)");
        btnBrowse.addActionListener(e -> browseProjectPath());
        pathPanel.add(btnBrowse, BorderLayout.EAST);

        addFormRow(body, gbc, 0, "Project Name:", tfProjectName);
        addFormRow(body, gbc, 1, "Author:", tfAuthor);
        addFormRow(body, gbc, 2, "Project File Path:", pathPanel);

        return wrapInCard("PROJECT METADATA", body);
    }

    private JPanel createSelectedLevelSection() {
        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        tfLevelName.setText(selectedLevel.getName());
        addFormRow(body, gbc, 0, "Level Name:", tfLevelName);

        return wrapInCard("SELECTED LEVEL (" + selectedLevel.getName().toUpperCase() + ")", body);
    }

    private JPanel createStatsAndToolsSection() {
        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createGbc();

        int levelCount = project.getChildren().size();
        int totalTiles = 0, totalEnemies = 0, totalObjects = 0, totalDecos = 0;

        String[] levelNames = new String[levelCount];
        for (int i = 0; i < levelCount; i++) {
            Level lvl = (Level) project.getChildren().get(i);
            levelNames[i] = lvl.getName() + " (" + lvl.getWidth() + "x" + lvl.getHeight() + ")";
            for (Node n : lvl.getChildren()) {
                if (n instanceof Tile t) {
                    totalTiles++;
                    switch (t.getTileType()) {
                        case ENEMY -> totalEnemies++;
                        case OBJECT -> totalObjects++;
                        case DECO -> totalDecos++;
                    }
                }
            }
        }

        JLabel lbStats = new JLabel(String.format(
                "<html><b>Levels:</b> %d &nbsp;|&nbsp; <b>Tiles:</b> %d &nbsp;|&nbsp; "
                        + "<b>Decos:</b> %d &nbsp;|&nbsp; <b>Objects:</b> %d &nbsp;|&nbsp; <b>Enemies:</b> %d</html>",
                levelCount, totalTiles, totalDecos, totalObjects, totalEnemies
        ));
        lbStats.setForeground(new Color(221, 230, 237));
        lbStats.setFont(new Font("SansSerif", Font.PLAIN, 12));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 4, 8, 4);
        body.add(lbStats, gbc);

        // Duplicate level
        cbLevelsToDuplicate = new JComboBox<>(levelNames);
        cbLevelsToDuplicate.setPreferredSize(new Dimension(200, 28));
        if (selectedLevel != null) {
            int idx = project.getChildren().indexOf(selectedLevel);
            if (idx >= 0) cbLevelsToDuplicate.setSelectedIndex(idx);
        }

        JButton btnDuplicate = createFlatButton("Duplicate Level");
        btnDuplicate.setEnabled(levelCount > 0);
        btnDuplicate.addActionListener(e -> duplicateSelectedLevel());

        JPanel dupRow = new JPanel(new BorderLayout(8, 0));
        dupRow.setOpaque(false);
        dupRow.add(cbLevelsToDuplicate, BorderLayout.CENTER);
        dupRow.add(btnDuplicate, BorderLayout.EAST);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 4, 4, 4);
        body.add(dupRow, gbc);

        // Batch export
        JButton btnBatchExport = createFlatButton("Batch Export All Levels in Project (PNG + JSON)...");
        btnBatchExport.setEnabled(levelCount > 0);
        btnBatchExport.addActionListener(e -> batchExportAllLevels());

        gbc.gridy = 2;
        gbc.insets = new Insets(4, 4, 2, 4);
        body.add(btnBatchExport, gbc);

        return wrapInCard("PROJECT STATISTICS & BATCH ACTIONS", body);
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(TOOLBAR_COLOR);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(116, 155, 194, 80)));

        JButton btnCancel = new JButton("Cancel");
        btnCancel.setFocusPainted(false);
        btnCancel.setPreferredSize(new Dimension(90, 28));

        JButton btnSave = createFlatButton("Apply & Close");
        btnSave.setPreferredSize(new Dimension(120, 28));

        btnSave.addActionListener(e -> applyChangesAndClose());
        btnCancel.addActionListener(e -> dispose());

        footer.add(btnCancel);
        footer.add(btnSave);
        return footer;
    }

    private JButton createFlatButton(String text) {
        JButton btn = new JButton(text);
        btn.setUI(new CustomButton());
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void browseProjectPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Project Save File (.json)");
        if (!tfPath.getText().isEmpty() && !tfPath.getText().equals("/")) {
            chooser.setSelectedFile(new File(tfPath.getText()));
        }
        else chooser.setSelectedFile(new File(tfProjectName.getText().trim() + ".json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".json")) path += ".json";
            tfPath.setText(path);
        }
    }

    private void duplicateSelectedLevel() {
        int idx = cbLevelsToDuplicate.getSelectedIndex();
        if (idx < 0 || idx >= project.getChildren().size()) return;

        Level source = (Level) project.getChildren().get(idx);
        Level clone = new Level(source.getName() + " Copy", project, source.getWidth(), source.getHeight());

        for (Node n : source.getChildren()) {
            if (n instanceof Tile orig) {
                Tile copy = new Tile(orig.getName(), clone, orig.getTileType(), orig.getX(), orig.getY(), orig.getRed(), orig.getGreen(), orig.getBlue());
                copy.setLayer(orig.getLayer());
                copy.setRotation(orig.getRotation());
                copy.setScaleX(orig.getScaleX());
                copy.setScaleY(orig.getScaleY());
                clone.getChildren().add(copy);
            }
        }

        project.addChild(clone);
        EditorTree tree = (EditorTree) EditorFrame.getInstance().getEditorTree();
        addCloneToTreeUI(tree, clone);
        tree.notifyAdd(clone);
        Framework.getInstance().log("Duplicated level: " + source.getName() + " -> " + clone.getName(), LogType.NOTIFICATION);
        dispose();
    }

    private void addCloneToTreeUI(EditorTree editorTree, Level clone) {
        TreeItem selected = editorTree.getSelectedNode();
        TreeItem projectItem = null;
        if (selected != null) {
            if (selected.getNode() == project) projectItem = selected;
            else if (selected.getParent() instanceof TreeItem parentItem && parentItem.getNode() == project) {
                projectItem = parentItem;
            }
        }
        if (projectItem != null) {
            projectItem.add(new TreeItem(clone));
            editorTree.refreshView();
        }
    }

    private void batchExportAllLevels() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Output Directory for Batch Level Export");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            int count = 0;
            for (Node n : project.getChildren()) {
                if (n instanceof Level lvl) {
                    String safeName = lvl.getName().trim().replaceAll("\\s+", "_");
                    File pngFile = new File(dir, safeName + ".png");
                    File jsonFile = new File(dir, safeName + ".json");
                    ExportLevelAction.exportLevelToFiles(lvl, "Both (Full 3-Panel Engine Image)", true, pngFile, jsonFile);
                    count++;
                }
            }
            JOptionPane.showMessageDialog(this,
                    "Successfully exported " + count + " level(s) to:\n" + dir.getAbsolutePath(),
                    "Batch Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void applyChangesAndClose() {
        String newProjName = tfProjectName.getText().trim();
        String newAuthor = tfAuthor.getText().trim();
        String newPath = tfPath.getText().trim();

        if (!newProjName.isEmpty()) project.setName(newProjName);
        if (!newAuthor.isEmpty()) project.setAuthor(newAuthor);
        if (!newPath.isEmpty()) project.setPath(newPath);

        if (selectedLevel != null) {
            String newLvlName = tfLevelName.getText().trim();
            if (!newLvlName.isEmpty()) selectedLevel.setName(newLvlName);
        }

        ((EditorTree) EditorFrame.getInstance().getEditorTree()).refreshView();
        dispose();
    }

    private JPanel wrapInCard(String title, JPanel body) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        JLabel lbTitle = new JLabel(title);
        lbTitle.setForeground(new Color(221, 230, 237));
        lbTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        wrapper.add(lbTitle, BorderLayout.NORTH);

        body.setBackground(TOOLBAR_COLOR);
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(116, 155, 194, 90), 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        wrapper.add(body, BorderLayout.CENTER);
        return wrapper;
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        gbc.gridwidth = 1;
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        if (field instanceof JTextField) {
            field.setPreferredSize(new Dimension(200, 26));
        }
        panel.add(field, gbc);
    }
}