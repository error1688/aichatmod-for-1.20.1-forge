package com.example.dsassistant;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ConfigScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger("ConfigScreen");
    private final Screen parent;
    private int scrollOffset = 0;
    private final int CONTENT_HEIGHT = 700; // 总内容高度，用于滚动条

    // UI 组件
    private CycleButton<Config.Mode> modeButton;          // 模式选择：自动回复/可点击回复
    private CycleButton<Config.Personality> personalityButton; // 人格预设
    private CycleButton<Boolean> listTypeButton;          // 名单类型：白名单/黑名单
    private EditBox customPromptBox;                      // 自定义提示词（仅当人格为自定义时显示）
    private EditBox playerListBox;                        // 玩家名单输入框
    private EditBox modelBox;                             // 模型名称输入框
    private Button enabledToggleButton;                   // 模组总开关
    private Button autoReplyToggleButton;                  // 自动回复开关（仅在自动回复模式下显示）
    private Button saveButton;                              // 保存按钮
    private Button cancelButton;                            // 取消按钮
    private Button resetUsageButton;                        // 重置用量按钮
    private Button openConfigFolderButton;                  // 打开配置文件夹按钮
    private Checkbox debugCheckbox;                         // 调试模式复选框
    private StringWidget usageTextWidget;                   // 用量统计文本
    private StringWidget modelLengthLabel;                  // 模型名称长度标签

    public ConfigScreen(Screen parent) {
        super(Component.literal("DS 助手配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 20;                      // 起始Y坐标
        int inputWidth = 400;                  // 输入框宽度
        int leftX = centerX - inputWidth / 2;  // 左对齐坐标
        int rowHeight = 25;                     // 行高

        // ----- 模组总开关 -----
        enabledToggleButton = Button.builder(
                        Component.literal("模组状态: " + (Config.CLIENT.enabled.get() ? "开启" : "关闭")),
                        btn -> {
                            boolean newState = !Config.CLIENT.enabled.get();
                            Config.CLIENT.enabled.set(newState);
                            btn.setMessage(Component.literal("模组状态: " + (newState ? "开启" : "关闭")));
                        })
                .pos(leftX, startY)
                .size(inputWidth, 20)
                .build();
        addRenderableWidget(enabledToggleButton);
        startY += rowHeight;

        // ----- 模式选择 -----
        modeButton = CycleButton.<Config.Mode>builder(mode ->
                        Component.literal(mode == Config.Mode.AUTO_REPLY ? "自动回复" : "可点击回复"))
                .withValues(Config.Mode.values())
                .withInitialValue(Config.CLIENT.mode.get())
                .displayOnlyValue()
                .create(leftX, startY, inputWidth, 20, Component.literal("模式: "),
                        (btn, mode) -> autoReplyToggleButton.visible = mode == Config.Mode.AUTO_REPLY);
        addRenderableWidget(modeButton);
        startY += rowHeight;

        // ----- API Key 只读显示 -----
        String apiKey = ApiKeyStorage.loadApiKey();
        String apiKeyDisplay = apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." + apiKey.substring(apiKey.length() - 4) : apiKey;
        StringWidget apiKeyWidget = new StringWidget(Component.literal("API Key: " + apiKeyDisplay + " (长度 " + apiKey.length() + ")"), font);
        apiKeyWidget.setPosition(leftX, startY);
        apiKeyWidget.setWidth(inputWidth);
        addRenderableWidget(apiKeyWidget);
        startY += 20;

        // ----- API URL 只读显示 -----
        String apiUrl = UrlStorage.loadUrl();
        String apiUrlDisplay = apiUrl.length() > 30 ? apiUrl.substring(0, 30) + "..." : apiUrl;
        StringWidget apiUrlWidget = new StringWidget(Component.literal("API URL: " + apiUrlDisplay + " (长度 " + apiUrl.length() + ")"), font);
        apiUrlWidget.setPosition(leftX, startY);
        apiUrlWidget.setWidth(inputWidth);
        addRenderableWidget(apiUrlWidget);
        startY += 20;

        // ----- 打开配置文件夹按钮 -----
        openConfigFolderButton = Button.builder(Component.literal("打开配置文件夹"), btn -> openConfigFolder())
                .pos(leftX, startY)
                .size(inputWidth, 20)
                .build();
        addRenderableWidget(openConfigFolderButton);
        startY += rowHeight;

        // ----- 提示信息 -----
        StringWidget hintWidget = new StringWidget(Component.literal("§7提示：API Key 和 URL 请直接编辑 config 文件夹下的文件"), font);
        hintWidget.setPosition(leftX, startY);
        hintWidget.setWidth(inputWidth);
        addRenderableWidget(hintWidget);
        startY += rowHeight;

        // ----- 模型名称输入框 -----
        modelBox = new EditBox(font, leftX, startY, inputWidth - 60, 20, Component.literal("模型名称"));
        modelBox.setValue(Config.CLIENT.model.get());
        modelBox.setMaxLength(Integer.MAX_VALUE);
        modelBox.setResponder(s -> updateModelLengthLabel());
        addRenderableWidget(modelBox);

        modelLengthLabel = new StringWidget(Component.literal("长度: " + Config.CLIENT.model.get().length()), font);
        modelLengthLabel.setPosition(leftX + inputWidth - 60, startY);
        modelLengthLabel.setWidth(60);
        addRenderableWidget(modelLengthLabel);
        startY += rowHeight;

        // ----- 人格预设 -----
        personalityButton = CycleButton.<Config.Personality>builder(personality -> {
                    switch (personality) {
                        case PROFESSIONAL_MC: return Component.literal("专业 MC 玩家");
                        case SUNBA_HUANGPAI: return Component.literal("孙吧黄牌");
                        case CATGIRL: return Component.literal("猫娘");
                        default: return Component.literal("自定义");
                    }
                })
                .withValues(Config.Personality.values())
                .withInitialValue(Config.CLIENT.personality.get())
                .displayOnlyValue()
                .create(leftX, startY, inputWidth, 20, Component.literal("人格: "),
                        (btn, pers) -> customPromptBox.setVisible(pers == Config.Personality.CUSTOM));
        addRenderableWidget(personalityButton);
        startY += rowHeight;

        // ----- 自定义提示词（仅当人格为 CUSTOM 时显示） -----
        customPromptBox = new EditBox(font, leftX, startY, inputWidth, 20, Component.literal("自定义提示词"));
        customPromptBox.setValue(Config.CLIENT.customPrompt.get());
        customPromptBox.setMaxLength(Integer.MAX_VALUE);
        customPromptBox.setVisible(Config.CLIENT.personality.get() == Config.Personality.CUSTOM);
        addRenderableWidget(customPromptBox);
        if (customPromptBox.visible) startY += rowHeight;

        // ----- 玩家名单输入框 -----
        playerListBox = new EditBox(font, leftX, startY, inputWidth, 20, Component.literal("玩家名单（用逗号分隔）"));
        playerListBox.setValue(String.join(",", Config.CLIENT.playerList.get()));
        playerListBox.setMaxLength(Integer.MAX_VALUE);
        addRenderableWidget(playerListBox);
        startY += rowHeight;

        // ----- 名单类型（白名单/黑名单） -----
        listTypeButton = CycleButton.<Boolean>builder(val -> val ? Component.literal("白名单") : Component.literal("黑名单"))
                .withValues(true, false)
                .withInitialValue(Config.CLIENT.listIsWhitelist.get())
                .displayOnlyValue()
                .create(leftX, startY, inputWidth, 20, Component.literal("名单类型: "), (btn, val) -> {});
        addRenderableWidget(listTypeButton);
        startY += rowHeight;

        // ----- 自动回复开关（仅当模式为 AUTO_REPLY 时可见） -----
        autoReplyToggleButton = Button.builder(
                        Component.literal("自动回复: " + (Config.CLIENT.autoReplyEnabled.get() ? "开启" : "关闭")),
                        btn -> {
                            boolean newState = !Config.CLIENT.autoReplyEnabled.get();
                            Config.CLIENT.autoReplyEnabled.set(newState);
                            btn.setMessage(Component.literal("自动回复: " + (newState ? "开启" : "关闭")));
                        })
                .pos(leftX, startY)
                .size(inputWidth, 20)
                .build();
        autoReplyToggleButton.visible = Config.CLIENT.mode.get() == Config.Mode.AUTO_REPLY;
        addRenderableWidget(autoReplyToggleButton);
        startY += rowHeight;

        // ----- 调试模式复选框 -----
        debugCheckbox = new Checkbox(leftX, startY, 20, 20, Component.literal("调试模式"), Config.CLIENT.debug.get()) {
            @Override
            public void onPress() {
                super.onPress();
                Config.CLIENT.debug.set(this.selected());
            }
        };
        addRenderableWidget(debugCheckbox);
        startY += rowHeight;

        // ----- 用量统计文本 -----
        String usageText = "请求次数: " + UsageTracker.getTotalRequests() + " | Token 消耗: " + UsageTracker.getTotalTokens();
        usageTextWidget = new StringWidget(Component.literal(usageText), font);
        usageTextWidget.setPosition(leftX, startY);
        usageTextWidget.setWidth(inputWidth);
        addRenderableWidget(usageTextWidget);
        startY += rowHeight;

        // ----- 重置用量按钮 -----
        resetUsageButton = Button.builder(Component.literal("重置用量"), btn -> UsageTracker.reset())
                .pos(leftX, startY)
                .size(inputWidth, 20)
                .build();
        addRenderableWidget(resetUsageButton);
        startY += rowHeight;

        // ----- 保存和取消按钮 -----
        saveButton = Button.builder(Component.literal("保存"), btn -> {
                    saveConfig();
                    Minecraft.getInstance().setScreen(parent);
                })
                .pos(leftX, startY)
                .size(inputWidth / 2 - 5, 20)
                .build();
        addRenderableWidget(saveButton);

        cancelButton = Button.builder(Component.literal("取消"), btn -> Minecraft.getInstance().setScreen(parent))
                .pos(leftX + inputWidth / 2 + 5, startY)
                .size(inputWidth / 2 - 5, 20)
                .build();
        addRenderableWidget(cancelButton);
    }

    // 更新模型名称长度标签
    private void updateModelLengthLabel() {
        String text = modelBox.getValue();
        modelLengthLabel.setMessage(Component.literal("长度: " + text.length()));
    }

    // 跨平台打开配置文件夹
    private void openConfigFolder() {
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec("explorer " + configDir.getAbsolutePath());
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec("open " + configDir.getAbsolutePath());
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                // Linux/Unix
                Runtime.getRuntime().exec("xdg-open " + configDir.getAbsolutePath());
            } else {
                // 回退：尝试使用 Desktop（可能失败）
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(configDir);
                } else {
                    LOGGER.error("无法打开文件夹：不支持的平台");
                }
            }
        } catch (IOException e) {
            LOGGER.error("打开配置文件夹失败", e);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, CONTENT_HEIGHT - (this.height - 40));
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * 10));
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -scrollOffset, 0);
        super.render(guiGraphics, mouseX, mouseY + scrollOffset, partialTick);
        guiGraphics.pose().popPose();
        guiGraphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        if (CONTENT_HEIGHT > height - 40) {
            int scrollBarHeight = (int) ((float) height / CONTENT_HEIGHT * (height - 40));
            int scrollBarY = (int) ((float) scrollOffset / (CONTENT_HEIGHT - height + 40) * (height - 40 - scrollBarHeight));
            guiGraphics.fill(width - 6, 20 + scrollBarY, width - 2, 20 + scrollBarY + scrollBarHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY + scrollOffset, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY + scrollOffset, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY + scrollOffset, button, dragX, dragY);
    }

    @Override
    public void tick() {
        super.tick();
        String usageText = "请求次数: " + UsageTracker.getTotalRequests() + " | Token 消耗: " + UsageTracker.getTotalTokens();
        usageTextWidget.setMessage(Component.literal(usageText));
    }

    // 保存配置
    private void saveConfig() {
        // API Key 和 URL 不通过界面保存
        Config.CLIENT.enabled.set(enabledToggleButton.getMessage().getString().contains("开启"));
        Config.CLIENT.mode.set(modeButton.getValue());

        String model = modelBox.getValue().trim();
        Config.CLIENT.model.set(model);
        LOGGER.info("保存模型名称: {}", model);

        Config.CLIENT.personality.set(personalityButton.getValue());
        LOGGER.info("保存人格: {}", personalityButton.getValue());

        Config.CLIENT.customPrompt.set(customPromptBox.getValue().trim());

        String listStr = playerListBox.getValue().trim();
        List<String> players = Arrays.stream(listStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        Config.CLIENT.playerList.set(players);
        Config.CLIENT.listIsWhitelist.set(listTypeButton.getValue());
        Config.CLIENT.autoReplyEnabled.set(autoReplyToggleButton.getMessage().getString().contains("开启"));

        // 保存调试模式状态
        Config.CLIENT.debug.set(debugCheckbox.selected());

        Config.saveClientConfig();
        LOGGER.info("其他配置已保存");
    }
}