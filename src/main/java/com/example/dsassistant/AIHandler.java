package com.example.dsassistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AIHandler {
    private static final Logger LOGGER = LogManager.getLogger("AIHandler");
    private static final int MAX_HISTORY = 10;
    private static final List<String> messageHistory = new ArrayList<>();
    private static Config.Personality lastPersonality = null;

    public static void requestAIResponse(String playerName, String message, Consumer<String> callback) {
        Config.Personality currentPersonality = Config.CLIENT.personality.get();
        if (lastPersonality != currentPersonality) {
            if (Config.CLIENT.debug.get()) {
                LOGGER.info("人格从 {} 切换为 {}，清空历史记录", lastPersonality, currentPersonality);
            }
            synchronized (messageHistory) {
                messageHistory.clear();
            }
            lastPersonality = currentPersonality;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = ApiKeyStorage.loadApiKey();
                if (apiKey.isEmpty()) {
                    return "请先在配置中设置 API 密钥";
                }
                String urlStr = UrlStorage.loadUrl();
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);

                String jsonInput = buildJsonRequest(playerName, message);
                if (Config.CLIENT.debug.get()) {
                    LOGGER.info("Request JSON: {}", jsonInput);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    String errorMessage = "无详细错误信息";
                    if (conn.getErrorStream() != null) {
                        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                            StringBuilder errorResponse = new StringBuilder();
                            String line;
                            while ((line = errorReader.readLine()) != null) {
                                errorResponse.append(line);
                            }
                            errorMessage = errorResponse.toString();
                        }
                    }
                    LOGGER.error("API 请求失败，错误码: {}, 错误信息: {}", responseCode, errorMessage);
                    return "API 请求失败，错误码：" + responseCode + "，详情：" + errorMessage;
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String aiResponse = parseResponse(response.toString());
                    addToHistory("assistant", aiResponse);
                    return cleanResponse(aiResponse);
                }

            } catch (Exception e) {
                LOGGER.error("API 请求出错", e);
                return "发生错误: " + e.getMessage();
            }
        }).thenAcceptAsync(response -> {
            try {
                AIChatMod.sendingAIResponse = true;
                var player = Minecraft.getInstance().player;
                if (player != null && response != null && !response.startsWith("API 请求失败") && !response.startsWith("发生错误")) {
                    player.connection.sendChat(response);
                } else if (response != null) {
                    player.displayClientMessage(Component.literal("§c[DS助手] " + response), false);
                }
            } finally {
                AIChatMod.sendingAIResponse = false;
            }
        }, Minecraft.getInstance()::submit);
    }

    private static synchronized void addToHistory(String role, String content) {
        messageHistory.add(role + ":" + content);
        if (messageHistory.size() > MAX_HISTORY * 2) {
            messageHistory.remove(0);
        }
    }

    private static synchronized String buildJsonRequest(String playerName, String message) {
        addToHistory("user", "玩家 " + playerName + " 说：" + message);

        JsonObject root = new JsonObject();
        root.addProperty("model", Config.CLIENT.model.get());

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", getSystemPrompt());
        messages.add(system);

        for (String hist : messageHistory) {
            String[] parts = hist.split(":", 2);
            if (parts.length == 2) {
                JsonObject histMsg = new JsonObject();
                histMsg.addProperty("role", parts[0]);
                histMsg.addProperty("content", parts[1]);
                messages.add(histMsg);
            }
        }

        root.add("messages", messages);
        root.addProperty("max_tokens", Config.CLIENT.maxTokens.get());
        root.addProperty("temperature", Config.CLIENT.temperature.get());

        return root.toString();
    }

    private static String getSystemPrompt() {
        Config.Personality currentPersonality = Config.CLIENT.personality.get();
        if (Config.CLIENT.debug.get()) {
            AIChatMod.LOGGER.info("当前人格: {}", currentPersonality);
        }
        String prompt;
        switch (currentPersonality) {
            case PROFESSIONAL_MC:
                prompt = "你是一个专业的 Minecraft 玩家，精通游戏机制、红石、建筑等。回答尽量简洁、专业，分点列出，每点不超过一行。";
                break;
            case SUNBA_HUANGPAI:
                prompt = "你是一个孙吧黄牌用户，喜欢玩梗，说话幽默搞笑，有时带点讽刺。使用网络用语。";
                break;
            case CATGIRL:
                prompt = "你是一只可爱的猫娘，喜欢撒娇，用可爱的语气说话，经常加上喵~等后缀。";
                break;
            case CUSTOM:
                prompt = Config.CLIENT.customPrompt.get();
                break;
            default:
                prompt = "你是一个友好的助手。";
        }
        if (Config.CLIENT.debug.get()) {
            AIChatMod.LOGGER.info("系统提示词: {}", prompt);
        }
        return prompt;
    }

    private static String parseResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("usage")) {
                JsonObject usage = root.getAsJsonObject("usage");
                int totalTokens = usage.get("total_tokens").getAsInt();
                UsageTracker.addTokens(totalTokens);
            }
            UsageTracker.incrementRequests();

            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null) {
                    return message.get("content").getAsString();
                }
            }
            return "无法解析回复";
        } catch (Exception e) {
            LOGGER.error("解析响应失败", e);
            return "解析错误";
        }
    }

    private static String cleanResponse(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "");
        // 智能截断：在句子边界截断
        if (cleaned.length() > 256) {
            int lastPeriod = cleaned.lastIndexOf('.', 256);
            int lastNewline = cleaned.lastIndexOf('\n', 256);
            int cut = Math.max(lastPeriod, lastNewline);
            if (cut > 200) {
                cleaned = cleaned.substring(0, cut + 1) + "……";
            } else {
                cleaned = cleaned.substring(0, 256) + "……";
            }
        }
        return cleaned;
    }
}