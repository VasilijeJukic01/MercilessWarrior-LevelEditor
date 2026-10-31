package editor.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import editor.core.Framework;
import editor.logger.LogType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Service responsible for communicating with the Gemini REST API to generate JSON level blueprints from prompts.
 */
public class AiPromptService {

    private static final String[] MODELS_TO_TRY = {
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.8-flash"
    };

    private final Random rand = new Random();

    /**
     * Requests a JSON level blueprint from the Gemini API matching the given dimensions and prompt.
     * Iterates through available fast models with automatic fallback.
     *
     * @param width      The target level width in tiles.
     * @param height     The target level height in tiles.
     * @param userPrompt The user's natural-language level description.
     * @param apiKey     The Gemini API key.
     * @return The raw JSON blueprint string returned by the model.
     * @throws Exception If all model endpoints fail or time out.
     */
    public String requestAiBlueprint(int width, int height, String userPrompt, String apiKey) throws Exception {
        int seed = rand.nextInt(100000);
        int midY = height / 2;

        String systemPrompt =
                "You are an expert 2D Metroidvania level designer for 'Merciless Warrior' (Variation Seed #" + seed + ").\n" +
                        "Grid size: width=" + width + ", height=" + height + " (0,0 is top-left).\n" +
                        "CRITICAL DESIGN RULES:\n" +
                        "1. The grid starts 100% filled with solid earth. DO NOT carve one giant empty rectangle over the whole grid!\n" +
                        "2. Carve 4 to 7 smaller connected rooms, hallways (height 4..7), and vertical shafts (width 4..6) so that thick solid earth masses (4..7 tiles thick) remain between the upper, middle, and lower rooms.\n" +
                        "3. Keep at least a 3-tile solid border around the outer edges.\n" +
                        "4. Use 'fill' to add stepped floor terraces (h=2..4), 1-tile-thick floating bridges (h=1, w=2..5), and single 1x1 stepping stones (w=1, h=1) for platforming (max jump height=3 tiles, max jump length=5 tiles).\n" +
                        "5. Return ONLY valid JSON in this exact format:\n" +
                        "{\n" +
                        "  \"carve\": [\n" +
                        "    {\"x\":4, \"y\":5, \"w\":" + (width / 2 - 2) + ", \"h\":6},\n" +
                        "    {\"x\":" + (width / 2 - 2) + ", \"y\":5, \"w\":5, \"h\":" + (height - 10) + "},\n" +
                        "    {\"x\":4, \"y\":" + (midY + 3) + ", \"w\":" + (width - 8) + ", \"h\":7},\n" +
                        "    {\"x\":" + (width / 2 + 2) + ", \"y\":4, \"w\":" + (width / 2 - 6) + ", \"h\":6}\n" +
                        "  ],\n" +
                        "  \"fill\": [\n" +
                        "    {\"x\":8, \"y\":" + (midY + 7) + ", \"w\":4, \"h\":1},\n" +
                        "    {\"x\":" + (width / 2 - 1) + ", \"y\":" + (midY) + ", \"w\":3, \"h\":1},\n" +
                        "    {\"x\":" + (width - 14) + ", \"y\":" + (midY + 8) + ", \"w\":5, \"h\":2}\n" +
                        "  ],\n" +
                        "  \"items\": [\n" +
                        "    {\"type\":\"SKELETON\", \"x\":10, \"y\":9},\n" +
                        "    {\"type\":\"CANDLE\", \"x\":14, \"y\":9},\n" +
                        "    {\"type\":\"CRATE\", \"x\":7, \"y\":" + (midY + 9) + "},\n" +
                        "    {\"type\":\"KNIGHT\", \"x\":" + (width - 12) + ", \"y\":" + (midY + 7) + "}\n" +
                        "  ]\n" +
                        "}\n" +
                        "Allowed item types: SKELETON, GHOUL, KNIGHT, WRAITH, SPIKES, LAVA, JUMP_PAD, CRATE, BARREL, CANDLE, TOTEM, SMASH_TRAP.\n" +
                        "Be creative and build a unique, playable layout matching this user request: " + userPrompt;

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
        generationConfig.addProperty("temperature", 0.9);

        // Minimize reasoning token latency on Gemini 2.5 and 3.x models
        if (model.startsWith("gemini-3")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingLevel", "MINIMAL");
            generationConfig.add("thinkingConfig", thinkingConfig);
        }
        else if (model.startsWith("gemini-2.5")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingBudget", 0);
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