package editor.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import editor.core.Framework;
import editor.logger.LogType;
import editor.model.repository.Node;
import editor.model.repository.components.Tile;
import editor.model.repository.components.TileType;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Service responsible for loading external prompt templates, serializing 3-panel RGB level context, and communicating with the Gemini REST API.
 */
public class AiPromptService {

    private static final String FULL_LEVEL_PROMPT_PATH = "/prompts/full_level_prompt.txt";
    private static final String REGIONAL_COOP_PROMPT_PATH = "/prompts/regional_coop_prompt.txt";

    private static final String[] MODELS_TO_TRY = {
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.8-flash"
    };

    private final Random rand = new Random();

    /**
     * Requests a full-level JSON blueprint using the external {@code full_level_prompt.txt} template.
     */
    public String requestAiBlueprint(int width, int height, String userPrompt, String apiKey) throws Exception {
        int seed = rand.nextInt(100000);
        int midY = height / 2;

        String template = loadPromptTemplate(FULL_LEVEL_PROMPT_PATH);
        String systemPrompt = applyPlaceholders(template, Map.of(
                "SEED", String.valueOf(seed),
                "WIDTH", String.valueOf(width),
                "HEIGHT", String.valueOf(height),
                "HALF_W", String.valueOf(Math.max(4, width / 2 - 2)),
                "SHAFT_H", String.valueOf(Math.max(6, height - 10)),
                "LOWER_Y", String.valueOf(midY + 3),
                "INNER_W", String.valueOf(Math.max(8, width - 8)),
                "PLAT_Y", String.valueOf(midY + 7),
                "MID_Y", String.valueOf(midY),
                "USER_PROMPT", userPrompt
        ));

        return executeWithFallback(systemPrompt, apiKey);
    }

    /**
     * Requests a regional inpainting JSON blueprint using the external {@code regional_coop_prompt.txt} template and a complete RGB/ID dump of the level.
     */
    public String requestRegionalBlueprint(Rectangle region, int lvlW, int lvlH, List<Node> nodesSnapshot, String userPrompt, String apiKey) throws Exception {
        int rw = region.width, rh = region.height;
        int seed = rand.nextInt(100000);

        String threePanelDump = buildThreePanelContextDump(region, lvlW, lvlH, nodesSnapshot);
        int detectedFloorGlobalY = region.y + detectSurroundingFloorLocalY(region, nodesSnapshot);
        int sampleX1 = region.x + Math.max(1, rw / 4);
        int sampleX2 = region.x + Math.max(2, (rw * 2) / 3);
        int sampleY1 = region.y + Math.max(2, (rh * 2) / 3);
        int sampleY2 = region.y + Math.max(1, rh / 3);

        String template = loadPromptTemplate(REGIONAL_COOP_PROMPT_PATH);
        String systemPrompt = applyPlaceholders(template, Map.ofEntries(
                Map.entry("SEED", String.valueOf(seed)),
                Map.entry("LVL_W", String.valueOf(lvlW)),
                Map.entry("LVL_H", String.valueOf(lvlH)),
                Map.entry("REG_X", String.valueOf(region.x)),
                Map.entry("REG_Y", String.valueOf(region.y)),
                Map.entry("REG_MAX_X", String.valueOf(region.x + rw - 1)),
                Map.entry("REG_MAX_Y", String.valueOf(region.y + rh - 1)),
                Map.entry("WIDTH", String.valueOf(rw)),
                Map.entry("HEIGHT", String.valueOf(rh)),
                Map.entry("SAMPLE_PLAT_X1", String.valueOf(sampleX1)),
                Map.entry("SAMPLE_PLAT_X2", String.valueOf(sampleX2)),
                Map.entry("SAMPLE_PLAT_Y1", String.valueOf(sampleY1)),
                Map.entry("SAMPLE_PLAT_Y2", String.valueOf(sampleY2)),
                Map.entry("FLOOR_Y", String.valueOf(detectedFloorGlobalY)),
                Map.entry("CONTEXT_GRID", threePanelDump),
                Map.entry("USER_PROMPT", userPrompt)
        ));

        String response = executeWithFallback(systemPrompt, apiKey);
        Framework.getInstance().log("Co-Op AI JSON Response:\n" + response, LogType.INFORMATION);
        return response;
    }

    /**
     * Serializes level panels in the exact ID:Layer format so the LLM has complete visibility into all panels.
     */
    private String buildThreePanelContextDump(Rectangle region, int lvlW, int lvlH, List<Node> nodes) {
        String[][] p1 = new String[lvlW][lvlH];
        String[][] p2 = new String[lvlW][lvlH];
        String[][] p3 = new String[lvlW][lvlH];

        for (int x = 0; x < lvlW; x++) {
            for (int y = 0; y < lvlH; y++) {
                p1[x][y] = "  .  ";
                p2[x][y] = "  .  ";
                p3[x][y] = "  .  ";
            }
        }

        for (Node n : nodes) {
            if (!(n instanceof Tile t)) continue;
            int x = t.getX(), y = t.getY();
            if (x < 0 || x >= lvlW || y < 0 || y >= lvlH) continue;

            switch (t.getTileType()) {
                case SOLID -> p1[x][y] = String.format("%2d:%d ", t.getRed(), t.getLayer());
                case OBJECT -> p1[x][y] = String.format(" O%-2d ", t.getBlue());
                case ENEMY -> p1[x][y] = String.format(" E%-2d ", t.getGreen());
                case PLAYER -> p1[x][y] = " PLR ";
                case DECO -> p2[x][y] = String.format("%2d:%d ", t.getBlue(), t.getLayer());
                case TRIGGER -> p3[x][y] = String.format(" T%-2d ", t.getBlue());
                case NPC -> p3[x][y] = String.format(" N%-2d ", t.getBlue());
            }
        }

        int pad = 4;
        int minX = Math.max(0, region.x - pad);
        int maxX = Math.min(lvlW - 1, region.x + region.width - 1 + pad);
        int minY = Math.max(0, region.y - pad);
        int maxY = Math.min(lvlH - 1, region.y + region.height - 1 + pad);

        StringBuilder sb = new StringBuilder();
        sb.append("[PANEL 1 - TERRAIN (TileID:Layer) / OBJECTS (O_id) / ENEMIES (E_id)]\n");
        appendGridSection(sb, p1, minX, maxX, minY, maxY);
        sb.append("\n[PANEL 2 - DECORATIONS (DecoID:Layer)]\n");
        appendGridSection(sb, p2, minX, maxX, minY, maxY);
        sb.append("\n[PANEL 3 - TRIGGERS (T_id) & NPCS (N_id)]\n");
        appendGridSection(sb, p3, minX, maxX, minY, maxY);

        return sb.toString();
    }

    private void appendGridSection(StringBuilder sb, String[][] grid, int minX, int maxX, int minY, int maxY) {
        sb.append("Y\\X ");
        for (int x = minX; x <= maxX; x++) sb.append(String.format(" %2d  ", x));
        sb.append('\n');

        for (int y = minY; y <= maxY; y++) {
            sb.append(String.format("%2d: ", y));
            for (int x = minX; x <= maxX; x++) {
                sb.append(grid[x][y]);
            }
            sb.append('\n');
        }
    }

    private int detectSurroundingFloorLocalY(Rectangle region, List<Node> nodes) {
        for (Node n : nodes) {
            if (n instanceof Tile t && t.getTileType() == TileType.SOLID && t.getLayer() == 3 && t.getRed() == 1) {
                if (t.getX() >= region.x - 2 && t.getX() <= region.x + region.width + 1 && t.getY() >= region.y && t.getY() <= region.y + region.height) {
                    return Math.max(0, Math.min(region.height - 1, t.getY() - region.y));
                }
            }
        }
        return Math.max(0, region.height - 2);
    }

    private String loadPromptTemplate(String resourcePath) throws IOException {
        try (InputStream is = Objects.requireNonNull(getClass().getResourceAsStream(resourcePath), "Prompt file not found: " + resourcePath)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String applyPlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String executeWithFallback(String systemPrompt, String apiKey) throws Exception {
        Exception lastError = null;
        for (String model : MODELS_TO_TRY) {
            try {
                return callModel(model, systemPrompt, apiKey.trim());
            } catch (Exception ex) {
                lastError = ex;
                Framework.getInstance().log("Model " + model + " failed (" + ex.getMessage() + "), trying next...", LogType.WARNING);
            }
        }
        throw lastError;
    }

    /**
     * Executes an POST request to a specific Gemini model endpoint with minimal thinking overhead.
     */
    private String callModel(String model, String systemPrompt, String apiKey) throws Exception {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", systemPrompt);
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        parts.add(part);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.addProperty("temperature", 0.8);

        // Minimize reasoning token latency on Gemini 2.5 and 3.x models
        if (model.startsWith("gemini-3")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingLevel", "LOW");
            generationConfig.add("thinkingConfig", thinkingConfig);
        }
        else if (model.startsWith("gemini-2.5")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingBudget", 1024);
            generationConfig.add("thinkingConfig", thinkingConfig);
        }
        requestBody.add("generationConfig", generationConfig);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        Framework.getInstance().log("Calling " + model + "...", LogType.INFORMATION);
        long t0 = System.currentTimeMillis();

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(300000);
        conn.setDoOutput(true);

        byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }

        if (code != 200) throw new RuntimeException("HTTP " + code + ": " + sb);

        long elapsed = System.currentTimeMillis() - t0;
        Framework.getInstance().log("Received response from " + model + " in " + elapsed + " ms!", LogType.NOTIFICATION);

        JsonObject jsonResponse = JsonParser.parseString(sb.toString()).getAsJsonObject();
        JsonArray responseParts = jsonResponse.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts");

        // Skip intermediate thought parts if present in the candidate response
        String aiText = null;
        for (JsonElement elem : responseParts) {
            JsonObject partObj = elem.getAsJsonObject();
            if (partObj.has("text") && (!partObj.has("thought") || !partObj.get("thought").getAsBoolean())) {
                aiText = partObj.get("text").getAsString();
            }
        }
        if (aiText == null && !responseParts.isEmpty()) {
            aiText = responseParts.get(responseParts.size() - 1).getAsJsonObject().get("text").getAsString();
        }
        return aiText;
    }
}