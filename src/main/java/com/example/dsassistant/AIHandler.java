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
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AIHandler {
    private static final Logger LOGGER = LogManager.getLogger("AIHandler");
    private static final int MAX_HISTORY = 10;
    private static final int CONNECT_TIMEOUT = 5000;  // 5秒
    private static final int READ_TIMEOUT = 8000;     // 8秒
    private static final ExecutorService POOL = Executors.newCachedThreadPool(); // 独立线程池

    // 使用 CopyOnWriteArrayList 避免同步开销
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static volatile Config.Personality lastPersonality = null;

    public static void requestAIResponse(String playerName, String message, Consumer<String> callback) {
        // 人格变化检查（无需同步，volatile保证可见性）
        Config.Personality currentPersonality = Config.CLIENT.personality.get();
        if (lastPersonality != currentPersonality) {
            messageHistory.clear();
            lastPersonality = currentPersonality;
            if (Config.CLIENT.debug.get()) {
                LOGGER.info("人格切换为：{}", currentPersonality);
            }
        }

        POOL.submit(() -> {  // 使用独立线程池
            int retries = 1; // 只重试一次，避免占用资源
            String result = null;
            while (retries-- >= 0) {
                try {
                    String apiKey = ApiKeyStorage.loadApiKey();
                    if (apiKey.isEmpty()) {
                        result = "§c[DS助手] 请设置 API 密钥";
                        break;
                    }
                    String urlStr = UrlStorage.loadUrl();
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.setDoOutput(true);

                    String jsonInput = buildJsonRequest(playerName, message);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        String responseBody = readStream(conn.getInputStream());
                        result = parseResponse(responseBody);
                        if (result != null && !result.isEmpty()) {
                            messageHistory.add("assistant:" + result);
                            if (messageHistory.size() > MAX_HISTORY * 2) {
                                messageHistory.remove(0);
                            }
                            result = cleanResponse(result);
                        } else {
                            result = "§c[DS助手] 返回为空";
                        }
                        break;
                    } else {
                        LOGGER.warn("API 返回错误码: {}", code);
                        if (retries <= 0) result = "§c[DS助手] 请求失败，错误码 " + code;
                    }
                } catch (SocketTimeoutException e) {
                    LOGGER.warn("请求超时，剩余重试: {}", retries);
                    if (retries <= 0) result = "§c[DS助手] 请求超时";
                } catch (Exception e) {
                    LOGGER.error("API请求异常", e);
                    result = "§c[DS助手] 发生错误";
                    break;
                }
            }

            // 回调到主线程
            final String finalResult = result;
            Minecraft.getInstance().execute(() -> {
                try {
                    AIChatMod.sendingAIResponse = true;
                    var player = Minecraft.getInstance().player;
                    if (player != null && finalResult != null) {
                        if (finalResult.startsWith("§c[DS助手]")) {
                            player.displayClientMessage(Component.literal(finalResult), false);
                        } else {
                            player.connection.sendChat(finalResult);
                        }
                    }
                } finally {
                    AIChatMod.sendingAIResponse = false;
                }
            });
        });
    }

    private static String readStream(java.io.InputStream stream) throws java.io.IOException {
        if (stream == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String buildJsonRequest(String playerName, String message) {
        messageHistory.add("user:玩家 " + playerName + " 说：" + message);
        if (messageHistory.size() > MAX_HISTORY * 2) {
            messageHistory.remove(0);
        }

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
        Config.Personality p = Config.CLIENT.personality.get();
        switch (p) {
            case PROFESSIONAL_MC:
                return "你是一个专业的Minecraft玩家，精通游戏机制、红石、建筑等。回答简洁、专业，分点列出，每点不超过一行。";
            case SUNBA_HUANGPAI:
                return "你是一个孙吧黄牌用户，喜欢玩梗，说话幽默搞笑，有时带点讽刺。使用网络用语。";
            case CATGIRL:
                return "你是一只可爱的猫娘，喜欢撒娇，用可爱的语气说话，经常加上喵~等后缀。";
            case CUSTOM:
                return Config.CLIENT.customPrompt.get();
            default:
                return "你是一个友好的助手。";
        }
    }

    private static String parseResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("usage")) {
                JsonObject usage = root.getAsJsonObject("usage");
                int tokens = usage.get("total_tokens").getAsInt();
                UsageTracker.addTokens(tokens);
            }
            UsageTracker.incrementRequests();

            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");
                if (message != null && message.has("content")) {
                    return message.get("content").getAsString();
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("解析响应失败", e);
            return null;
        }
    }

    private static String cleanResponse(String text) {
        if (text == null) return "";
        // 移除控制字符
        String cleaned = text.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "");
        // 截断过长消息（256字符以内）
        if (cleaned.length() > 256) {
            int cut = Math.max(cleaned.lastIndexOf('.', 256), cleaned.lastIndexOf('\n', 256));
            if (cut > 200) {
                cleaned = cleaned.substring(0, cut + 1) + "……";
            } else {
                cleaned = cleaned.substring(0, 256) + "……";
            }
        }
        return cleaned;
    }
}