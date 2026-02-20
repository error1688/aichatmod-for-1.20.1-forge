package com.example.dsassistant;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatListener {
    private static final Pattern PLAYER_CHAT_PATTERN = Pattern.compile("^<(.+?)> (.*)$");

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        if (AIChatMod.sendingAIResponse) return;
        if (!Config.CLIENT.enabled.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String rawMessage = event.getMessage().getString();
        Matcher matcher = PLAYER_CHAT_PATTERN.matcher(rawMessage);
        if (!matcher.matches()) return;

        String playerName = matcher.group(1);
        String content = matcher.group(2);

        if (playerName.equals(mc.player.getGameProfile().getName())) return;

        if (Config.CLIENT.mode.get() == Config.Mode.AUTO_REPLY) {
            handleAutoReply(playerName, content);
        } else if (Config.CLIENT.mode.get() == Config.Mode.CLICKABLE_REPLY) {
            modifyMessageForClickable(event, playerName, content);
        }
    }

    private void handleAutoReply(String playerName, String content) {
        if (!isPlayerAllowed(playerName)) return;
        if (!Config.CLIENT.autoReplyEnabled.get()) return;

        AIHandler.requestAIResponse(playerName, content, response -> {
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
        });
    }

    private void modifyMessageForClickable(ClientChatReceivedEvent event, String playerName, String content) {
        if (!isPlayerAllowed(playerName)) return;

        Component original = event.getMessage();
        Component clickable = Component.literal(" [AI回复]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/dsassistant reply " + playerName + " " + content))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("点击获取 AI 回复"))));
        Component newMessage = original.copy().append(clickable);
        event.setMessage(newMessage);
    }

    private boolean isPlayerAllowed(String playerName) {
        List<String> list = Config.CLIENT.playerList.get().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        boolean isWhitelist = Config.CLIENT.listIsWhitelist.get();
        if (list.isEmpty()) return true;
        boolean contains = list.contains(playerName);
        return isWhitelist ? contains : !contains;
    }
}