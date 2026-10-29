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

public class AiPromptService {

    private static final String[] MODELS_TO_TRY = {
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.8-flash"
    };

    public String requestAiBlueprint(int width, int height, String userPrompt, String apiKey) throws Exception {
        String systemPrompt =
                "You are a 2D platformer level designer for 'Merciless Warrior'. " +
                        "The level is a grid of width=" + width + ", height=" + height + " (0,0 is top-left). " +
                        "The level starts 100% filled with solid earth. You design the level by carving air rectangles ('carve'), " +
                        "adding solid islands/platforms ('fill'), and placing entities ('items').\n" +
                        "Return ONLY valid JSON in this exact format:\n" +
                        "{\n" +
                        "  \"carve\": [ {\"x\":0,\"y\":6,\"w\":6,\"h\":4}, {\"x\":4,\"y\":4,\"w\":" + (width - 8) + ",\"h\":" + (height - 8) + "}, {\"x\":" + (width - 6) + ",\"y\":" + (height - 8) + ",\"w\":6,\"h\":4} ],\n" +
                        "  \"fill\": [ {\"x\":10,\"y\":10,\"w\":6,\"h\":1}, {\"x\":18,\"y\":14,\"w\":8,\"h\":4} ],\n" +
                        "  \"items\": [ {\"type\":\"SPAWN_A\",\"x\":2,\"y\":9}, {\"type\":\"SPAWN_B\",\"x\":" + (width - 3) + ",\"y\":" + (height - 5) + "}, {\"type\":\"SKELETON\",\"x\":12,\"y\":9}, {\"type\":\"CRATE\",\"x\":15,\"y\":13} ]\n" +
                        "}\n" +
                        "Allowed item types: SKELETON, GHOUL, KNIGHT, WRAITH, SPIKES, LAVA, JUMP_PAD, CRATE, BARREL, CANDLE, TOTEM, SMASH_TRAP, SPAWN_A, SPAWN_B.\n" +
                        "Note: For items on a floor at row Y_floor, their y coordinate must be Y_floor - 1.\n" +
                        "User request: " + userPrompt;

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
        generationConfig.addProperty("temperature", 0.7);

        if (model.startsWith("gemini-3")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingLevel", "MINIMAL");
            generationConfig.add("thinkingConfig", thinkingConfig);
        } else if (model.startsWith("gemini-2.5")) {
            JsonObject thinkingConfig = new JsonObject();
            thinkingConfig.addProperty("thinkingBudget", 0);
            generationConfig.add("thinkingConfig", thinkingConfig);
        }
        requestBody.add("generationConfig", generationConfig);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        Framework.getInstance().log("Calling " + model + " (fast mode)...", LogType.INFORMATION);
        long t0 = System.currentTimeMillis();

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(300000); // 5 minutes max fallback
        conn.setDoOutput(true);

        byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
        }

        if (code != 200) {
            throw new RuntimeException("HTTP " + code + ": " + sb);
        }

        long elapsed = System.currentTimeMillis() - t0;
        Framework.getInstance().log("Received response from " + model + " in " + elapsed + " ms!", LogType.NOTIFICATION);

        JsonObject jsonResponse = JsonParser.parseString(sb.toString()).getAsJsonObject();
        JsonArray responseParts = jsonResponse.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts");

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