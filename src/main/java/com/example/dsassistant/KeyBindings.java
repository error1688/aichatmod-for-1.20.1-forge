package com.example.dsassistant;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AIChatMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static final String KEY_CATEGORY = "key.category.dsassistant";
    public static final String KEY_TOGGLE_MOD = "key.dsassistant.toggle_mod";
    public static final String KEY_OPEN_CONFIG = "key.dsassistant.open_config";

    public static KeyMapping toggleModKey = new KeyMapping(
            KEY_TOGGLE_MOD,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    public static KeyMapping openConfigKey = new KeyMapping(
            KEY_OPEN_CONFIG,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KEY_CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(toggleModKey);
        event.register(openConfigKey);
    }

    @Mod.EventBusSubscriber(modid = AIChatMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.screen != null) return;

            while (toggleModKey.consumeClick()) {
                boolean newState = !Config.CLIENT.enabled.get();
                Config.CLIENT.enabled.set(newState);
                mc.player.displayClientMessage(
                        Component.literal("DS 助手 " + (newState ? "已启用" : "已禁用")), false);
            }

            while (openConfigKey.consumeClick()) {
                mc.setScreen(new ConfigScreen(mc.screen));
            }
        }
    }
}