package com.example.dsassistant;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AIChatMod.MODID)
public class AIChatMod {
    public static final String MODID = "dsassistant";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static boolean sendingAIResponse = false;

    public AIChatMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.clientSpec);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
            MinecraftForge.EVENT_BUS.register(this);
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("DS 助手模组客户端初始化完成");
        MinecraftForge.EVENT_BUS.register(new ChatListener());
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("dsassistant")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reply")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.word())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("message", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String player = StringArgumentType.getString(context, "player");
                                                    String message = StringArgumentType.getString(context, "message");
                                                    AIHandler.requestAIResponse(player, message, response -> {
                                                        try {
                                                            sendingAIResponse = true;
                                                            var mc = net.minecraft.client.Minecraft.getInstance();
                                                            if (mc.player != null && response != null && !response.startsWith("API 请求失败") && !response.startsWith("发生错误")) {
                                                                mc.player.connection.sendChat(response);
                                                            } else if (response != null) {
                                                                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[DS助手] " + response), false);
                                                            }
                                                        } finally {
                                                            sendingAIResponse = false;
                                                        }
                                                    });
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
        if (Config.CLIENT.debug.get()) {
            LOGGER.info("DS 助手客户端命令已注册");
        }
    }
}