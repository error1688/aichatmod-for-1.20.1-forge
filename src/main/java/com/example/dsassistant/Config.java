package com.example.dsassistant;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = AIChatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static class Client {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.EnumValue<Mode> mode;
        public final ForgeConfigSpec.ConfigValue<String> model;
        public final ForgeConfigSpec.EnumValue<Personality> personality;
        public final ForgeConfigSpec.ConfigValue<String> customPrompt;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerList;
        public final ForgeConfigSpec.BooleanValue listIsWhitelist;
        public final ForgeConfigSpec.BooleanValue autoReplyEnabled;
        public final ForgeConfigSpec.IntValue maxTokens;
        public final ForgeConfigSpec.DoubleValue temperature;
        public final ForgeConfigSpec.BooleanValue debug; // 新增调试模式

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("通用设置").push("general");

            enabled = builder
                    .comment("启用模组")
                    .define("enabled", true);

            mode = builder
                    .comment("模式：AUTO_REPLY 自动回复，CLICKABLE_REPLY 可点击回复")
                    .defineEnum("mode", Mode.AUTO_REPLY);

            model = builder
                    .comment("模型名称，例如 deepseek-chat")
                    .define("model", "deepseek-chat");

            personality = builder
                    .comment("人格预设：PROFESSIONAL_MC（专业 MC 玩家），SUNBA_HUANGPAI（孙吧黄牌），CATGIRL（猫娘），CUSTOM（自定义）")
                    .defineEnum("personality", Personality.PROFESSIONAL_MC);

            customPrompt = builder
                    .comment("当人格为 CUSTOM 时的自定义提示词")
                    .define("customPrompt", "你是一个友好的助手。");

            playerList = builder
                    .comment("玩家名单（用于黑/白名单）")
                    .defineList("playerList", ArrayList::new, it -> it instanceof String);

            listIsWhitelist = builder
                    .comment("true = 白名单（仅这些玩家），false = 黑名单（忽略这些玩家）")
                    .define("listIsWhitelist", false);

            autoReplyEnabled = builder
                    .comment("启用自动回复（仅在自动回复模式下有效）")
                    .define("autoReplyEnabled", true);

            maxTokens = builder
                    .comment("最大回复 token 数")
                    .defineInRange("maxTokens", 200, 1, 2000); // 默认改为 200

            temperature = builder
                    .comment("温度（0.0 到 2.0）")
                    .defineInRange("temperature", 0.7, 0.0, 2.0);

            debug = builder
                    .comment("调试模式：输出详细日志（API 请求、Key 加载等）")
                    .define("debug", false);

            builder.pop();
        }
    }

    public enum Mode {
        AUTO_REPLY,
        CLICKABLE_REPLY
    }

    public enum Personality {
        PROFESSIONAL_MC,
        SUNBA_HUANGPAI,
        CATGIRL,
        CUSTOM
    }

    static final ForgeConfigSpec clientSpec;
    public static final Client CLIENT;
    private static ModConfig clientModConfig;

    static {
        Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);
        clientSpec = pair.getRight();
        CLIENT = pair.getLeft();
    }

    public static void saveClientConfig() {
        if (clientModConfig != null) {
            clientModConfig.save();
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading configEvent) {
        if (configEvent.getConfig().getType() == ModConfig.Type.CLIENT) {
            clientModConfig = configEvent.getConfig();
        }
        AIChatMod.LOGGER.debug("配置文件已加载");
    }

    @SubscribeEvent
    public static void onFileChange(final ModConfigEvent.Reloading configEvent) {
        if (configEvent.getConfig().getType() == ModConfig.Type.CLIENT) {
            clientModConfig = configEvent.getConfig();
        }
        AIChatMod.LOGGER.debug("配置文件已更改");
    }
}