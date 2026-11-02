package editor.gui.controller;

import editor.gui.view.EditorFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static editor.constants.Constants.*;

/**
 * Opens an in-app tabbed Help, Workflow, Keyboard Shortcuts, and RGB Encoding reference dialog.
 */
public class HelpAction extends AbstractEditorAction {

    public HelpAction() {
        super.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        super.putValue(NAME, "Help");
        super.putValue(SHORT_DESCRIPTION, "User Guide & Keyboard Shortcuts (F1)");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JDialog dialog = new JDialog(EditorFrame.getInstance(), "Merciless Warrior - Level Editor Guide", true);
        dialog.setSize(680, 540);
        dialog.setLocationRelativeTo(EditorFrame.getInstance());
        dialog.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        tabs.addTab("1. Quick Start & Workflow", createHtmlTab(getWorkflowHtml()));
        tabs.addTab("2. Shortcuts & Controls", createHtmlTab(getShortcutsHtml()));
        tabs.addTab("3. Layers & Smart Tools", createHtmlTab(getLayersHtml()));
        tabs.addTab("4. RGB Pixel Encoding", createHtmlTab(getEncodingHtml()));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        footer.setBackground(TOOLBAR_COLOR);
        JButton btnClose = new JButton("Got It");
        btnClose.setBackground(BUTTON_COLOR);
        btnClose.setForeground(Color.WHITE);
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(ev -> dialog.dispose());
        footer.add(btnClose);

        dialog.add(tabs, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JScrollPane createHtmlTab(String htmlBody) {
        JEditorPane pane = new JEditorPane("text/html", wrapHtml(htmlBody));
        pane.setEditable(false);
        pane.setBackground(TOOLBAR_COLOR);
        pane.setBorder(new EmptyBorder(12, 16, 12, 16));
        pane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(null);
        return scroll;
    }

    private String wrapHtml(String content) {
        return "<html><head><style>"
                + "body { font-family: SansSerif; font-size: 11px; color: #DDE6ED; background-color: #27374D; margin: 0; }"
                + "h2 { color: #749BC2; margin-top: 4px; margin-bottom: 6px; border-bottom: 1px solid #526D82; padding-bottom: 4px; }"
                + "h3 { color: #9DB2BF; margin-top: 10px; margin-bottom: 4px; }"
                + "p, li { line-height: 1.45; margin-top: 3px; margin-bottom: 3px; }"
                + "table { width: 100%; border-collapse: collapse; margin-top: 6px; margin-bottom: 10px; }"
                + "th { background-color: #1B2636; color: #749BC2; text-align: left; padding: 6px 8px; border: 1px solid #526D82; }"
                + "td { padding: 5px 8px; border: 1px solid #526D82; }"
                + "kbd { background-color: #1B2636; color: #F1C40F; padding: 2px 5px; font-weight: bold; }"
                + ".highlight { color: #2ECC71; font-weight: bold; }"
                + "</style></head><body>" + content + "</body></html>";
    }

    private String getWorkflowHtml() {
        return "<h2>Quick Start Workflow</h2>"
                + "<ol>"
                + "<li><b>Create a Project:</b> Click <kbd>Project Explorer</kbd> in the left tree, then click <b>New</b> (<kbd>Ctrl+N</kbd>) to create a Project.</li>"
                + "<li><b>Create Levels:</b> Select your Project in the tree and click <b>New</b> (<kbd>Ctrl+N</kbd>) to add one or more Levels.</li>"
                + "<li><b>Open Workspace Tabs:</b> <b>Double-click</b> the Project node in the left tree to open all its levels as tabs.</li>"
                + "<li><b>Place & Paint Tiles:</b> Select a <i>Tileset</i>, <i>Category</i>, and <i>Target Layer</i> in the bottom palette, click <b>Add</b>, and left-click or drag on the canvas.</li>"
                + "<li><b>Generate Rooms (Procedural or AI):</b> Click <b>Generator</b> in the top toolbar to instantly generate playable rooms, auto-tile terrain, or use <b>Co-Op Regional AI</b> on a selected box.</li>"
                + "<li><b>Save & Export:</b> Use <b>Save As</b> / <b>Save</b> (<kbd>Ctrl+S</kbd>) to save your project JSON, or click <b>Export PI</b> to export the 3-panel PNG + Metadata JSON for the game engine.</li>"
                + "</ol>"
                + "<h3>Co-Op Regional AI Inpainting</h3>"
                + "<p>Want the AI to redesign or decorate just one specific area while you keep working on the rest of the level?</p>"
                + "<ol>"
                + "<li>Click the <b>Select</b> tool and drag a rectangle over any region on the canvas.</li>"
                + "<li>Click <b>Generator</b>, type what you want in that box (e.g., <i>\"Add a subterranean cavern with glowing mushrooms\"</i> or <i>\"Add stepping platforms and 2 knights\"</i>), and click the orange <b>Co-Op AI</b> button.</li>"
                + "<li>The dialog closes immediately and locks only that region with an animated border while you continue editing the rest of the level!</li>"
                + "</ol>";
    }

    private String getShortcutsHtml() {
        return "<h2>Keyboard Shortcuts & Mouse Controls</h2>"
                + "<table>"
                + "<tr><th>Shortcut / Input</th><th>Action</th></tr>"
                + "<tr><td><kbd>Left-Click / Drag</kbd></td><td>Execute active tool (Add, Delete, Select, Move, Bucket, Rect Fill)</td></tr>"
                + "<tr><td><kbd>Right-Click + Drag</kbd></td><td>Pan / scroll around the level canvas</td></tr>"
                + "<tr><td><kbd>Alt + Left-Click</kbd> or <kbd>Middle-Click</kbd></td><td><b>Eyedropper Tool:</b> Pick tile, category, and layer directly from canvas</td></tr>"
                + "<tr><td><kbd>Ctrl + Mouse Wheel</kbd></td><td>Zoom In / Zoom Out smoothly</td></tr>"
                + "<tr><td><kbd>Mouse Wheel</kbd> / <kbd>Shift + Wheel</kbd></td><td>Scroll vertically / Scroll horizontally</td></tr>"
                + "<tr><td><kbd>Ctrl + Z</kbd> / <kbd>Ctrl + Y</kbd></td><td>Undo / Redo last action</td></tr>"
                + "<tr><td><kbd>Ctrl + C</kbd> / <kbd>Ctrl + V</kbd></td><td>Copy selected tiles / Enter Paste tool mode</td></tr>"
                + "<tr><td><kbd>Shift + Drag</kbd> (in <b>Select</b> tool)</td><td>Add to existing tile selection</td></tr>"
                + "<tr><td><kbd>Shift + Drag</kbd> (in <b>Rect Fill</b> tool)</td><td>Erase all tiles inside the dragged rectangle</td></tr>"
                + "<tr><td><kbd>Ctrl + G</kbd></td><td>Toggle Grid visibility</td></tr>"
                + "<tr><td><kbd>Ctrl + N</kbd> / <kbd>Ctrl + O</kbd> / <kbd>Ctrl + S</kbd></td><td>New Project or Level / Open Project / Save Project</td></tr>"
                + "<tr><td><kbd>Ctrl + R</kbd> / <kbd>Delete</kbd></td><td>Rename selected Project or Level / Close selected item</td></tr>"
                + "<tr><td><kbd>F1</kbd></td><td>Open this Help & Reference Guide</td></tr>"
                + "</table>";
    }

    private String getLayersHtml() {
        return "<h2>Z-Layers & Smart Editing Tools</h2>"
                + "<table>"
                + "<tr><th>Layer</th><th>Category</th><th>Purpose in Game Engine</th></tr>"
                + "<tr><td><b>Layer 0, 1, 2</b></td><td>Decorations</td><td>Background props rendered behind solid terrain (trees, pillars, background rocks, fade caps, and black cavern backing <kbd>34:2</kbd>).</td></tr>"
                + "<tr><td><b>Layer 3</b></td><td>Solid Tiles</td><td>Primary playable collision terrain (floors, walls, ceilings, platforms).</td></tr>"
                + "<tr><td><b>Layer 4</b></td><td>Decorations</td><td>Foreground foliage and decorations placed inside subterranean Layer 5 wall pockets.</td></tr>"
                + "<tr><td><b>Layer 5</b></td><td>Solid / Entities</td><td>Interactive Objects, Enemies, Player, Triggers, NPCs, and subterranean non-colliding wall structures.</td></tr>"
                + "</table>"
                + "<h3>Smart Tools Overview</h3>"
                + "<ul>"
                + "<li><b>Smart Auto-Tile (Side Panel):</b> When checked, drawing or erasing on Layer 3 automatically resolves all 49 8-neighbor grass/wall/ceiling/corner tile edges in real time.</li>"
                + "<li><b>Bucket Tool:</b> Flood-fills contiguous empty space or matching solid tiles and auto-tiles the perimeter.</li>"
                + "<li><b>Rect Fill Tool:</b> Drag a box to fill solid ground (automatically auto-tiled!), or hold <kbd>Shift</kbd> while dragging to bulk-erase a rectangular area.</li>"
                + "<li><b>Edit Tool:</b> Click any tile on the canvas to change its Layer, or interactively rotate and scale Decoration sprites. Clicking the same cell repeatedly cycles through stacked tiles.</li>"
                + "</ul>";
    }

    private String getEncodingHtml() {
        return "<h2>3-Panel Pixel Image Encoding (.png + .json)</h2>"
                + "<p>When you click <b>Export PI</b>, the editor generates a 3-panel horizontal PNG image (<kbd>width * 3</kbd> by <kbd>height</kbd>) plus an optional JSON metadata file for rotated/scaled decorations:</p>"
                + "<table>"
                + "<tr><th>Panel</th><th>Element Type</th><th>Red (R)</th><th>Green (G)</th><th>Blue (B)</th></tr>"
                + "<tr><td><b>Panel 1 (Left)</b><br>Physical World</td><td>Solid Terrain (Layer 3)<br>Subterranean Solid (Layer 5)<br>Enemy Spawn<br>Interactive Object<br>Player Spawn</td>"
                + "<td><kbd>tileId (0..48)</kbd><br><kbd>tileId (0..48)</kbd><br><kbd>254</kbd><br><kbd>254</kbd><br><kbd>100</kbd></td>"
                + "<td><kbd>254</kbd><br><kbd>255</kbd><br><kbd>enemyId</kbd><br><kbd>254</kbd><br><kbd>100</kbd></td>"
                + "<td><kbd>254</kbd><br><kbd>255</kbd><br><kbd>254</kbd><br><kbd>objectId</kbd><br><kbd>100</kbd></td></tr>"
                + "<tr><td><b>Panel 2 (Middle)</b><br>Decorations</td><td>Decoration Sprite</td><td><kbd>254</kbd></td><td><kbd>layer (0,1,2,4)</kbd></td><td><kbd>decoId (0..66)</kbd></td></tr>"
                + "<tr><td><b>Panel 3 (Right)</b><br>Logic & NPCs</td><td>Spatial Trigger<br>NPC Spawn</td><td><kbd>254</kbd><br><kbd>0</kbd></td><td><kbd>254</kbd><br><kbd>0</kbd></td><td><kbd>triggerId</kbd><br><kbd>npcId</kbd></td></tr>"
                + "</table>";
    }
}