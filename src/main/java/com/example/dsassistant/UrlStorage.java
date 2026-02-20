package com.example.dsassistant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UrlStorage {
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path URL_FILE = CONFIG_DIR.resolve("dsassistant_url.txt");
    private static final String DEFAULT_URL = "https://api.deepseek.com/v1/chat/completions";

    public static void saveUrl(String url) {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            Files.writeString(URL_FILE, url, StandardCharsets.UTF_8);
            if (Config.CLIENT.debug.get()) {
                AIChatMod.LOGGER.info("API URL 已保存: 长度={}, 内容={}", url.length(), url);
            }
        } catch (IOException e) {
            AIChatMod.LOGGER.error("保存 API URL 失败", e);
        }
    }

    public static String loadUrl() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(URL_FILE)) {
                // 文件不存在，创建并写入默认 URL
                Files.writeString(URL_FILE, DEFAULT_URL, StandardCharsets.UTF_8);
                if (Config.CLIENT.debug.get()) {
                    AIChatMod.LOGGER.info("API URL 文件不存在，已创建并写入默认值");
                }
                return DEFAULT_URL;
            }
            String url = Files.readString(URL_FILE, StandardCharsets.UTF_8).trim();
            if (Config.CLIENT.debug.get()) {
                AIChatMod.LOGGER.info("API URL 已加载: 长度={}, 内容={}", url.length(), url);
            }
            return url;
        } catch (IOException e) {
            AIChatMod.LOGGER.error("加载 API URL 失败", e);
        }
        return DEFAULT_URL;
    }
}