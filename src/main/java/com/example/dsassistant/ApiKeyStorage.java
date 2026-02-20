package com.example.dsassistant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApiKeyStorage {
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path KEY_FILE = CONFIG_DIR.resolve("dsassistant_apikey.txt");

    public static void saveApiKey(String key) {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            Files.writeString(KEY_FILE, key, StandardCharsets.UTF_8);
            if (Config.CLIENT.debug.get()) {
                AIChatMod.LOGGER.info("API Key 已保存: 长度={}, 最后四位={}",
                        key.length(),
                        key.length() > 4 ? key.substring(key.length() - 4) : key);
            }
        } catch (IOException e) {
            AIChatMod.LOGGER.error("保存 API Key 失败", e);
        }
    }

    public static String loadApiKey() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(KEY_FILE)) {
                // 文件不存在则创建空文件
                Files.createFile(KEY_FILE);
                if (Config.CLIENT.debug.get()) {
                    AIChatMod.LOGGER.info("API Key 文件不存在，已创建空文件");
                }
                return "";
            }
            String key = Files.readString(KEY_FILE, StandardCharsets.UTF_8).trim();
            if (Config.CLIENT.debug.get()) {
                AIChatMod.LOGGER.info("API Key 已加载: 长度={}, 最后四位={}",
                        key.length(),
                        key.length() > 4 ? key.substring(key.length() - 4) : key);
            }
            return key;
        } catch (IOException e) {
            AIChatMod.LOGGER.error("加载 API Key 失败", e);
        }
        return "";
    }
}