package editor.serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import editor.core.Framework;
import editor.gui.view.EditorFrame;
import editor.logger.LogType;
import editor.model.repository.Node;
import editor.model.repository.components.Level;
import editor.model.repository.components.Project;
import editor.model.repository.components.Tile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class GsonSerializer implements Serializer<Project> {

    private final Gson gson;

    public GsonSerializer() {
        RuntimeTypeAdapterFactory<Node> runtimeTypeAdapterFactory = RuntimeTypeAdapterFactory.of(Node.class, "type", true)
                        .registerSubtype(Project.class, Project.class.getName())
                        .registerSubtype(Level.class, Level.class.getName())
                        .registerSubtype(Tile.class, Tile.class.getName())
                        .registerSubtype(Node.class, Node.class.getName());

        this.gson = new GsonBuilder()
                .registerTypeAdapterFactory(runtimeTypeAdapterFactory)
                .setPrettyPrinting()
                .create();
    }

    @Override
    public void loadProject(File file) {
        try (FileReader fileReader = new FileReader(file)) {
            Project project = gson.fromJson(fileReader, Project.class);
            project.setPath(file.getPath());
            EditorFrame.getInstance().getEditorTree().deserialize(project);
        } catch (Exception e) {
            Framework.getInstance().log("Failed to load project: " + e.getMessage(), LogType.ERROR);
        }
    }

    @Override
    public void saveProject(Project node) {
        if (node.getPath() == null || node.getPath().isEmpty()) return;
        try (FileWriter writer = new FileWriter(node.getPath())) {
            gson.toJson(node, writer);
        } catch (Exception e) {
            Framework.getInstance().log("Failed to save project: " + e.getMessage(), LogType.ERROR);
        }
    }

}
