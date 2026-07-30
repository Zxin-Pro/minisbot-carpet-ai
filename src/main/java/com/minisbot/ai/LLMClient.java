package com.minisbot.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LLMClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson gson = new Gson();
    private static String apiUrl = "https://api.deepseek.com/v1/chat/completions";
    private static String apiKey = "";
    private static String model = "deepseek-chat";

    public static void configure(String url, String key, String modelName) {
        if (url != null && !url.isEmpty()) apiUrl = url;
        if (key != null && !key.isEmpty()) apiKey = key;
        if (modelName != null && !modelName.isEmpty()) model = modelName;
    }

    public static boolean isConfigured() { return !apiKey.isEmpty(); }

    public static String think(String systemPrompt, String context) {
        if (!isConfigured()) return "";
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 300);
            body.addProperty("temperature", 0.7);
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemPrompt);
            messages.add(sys);
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", context);
            messages.add(user);
            body.add("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
