package com.minisbot;

import com.minisbot.ai.BotAIManager;
import com.minisbot.ai.LLMClient;
import com.minisbot.command.MinisBotCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MinisBotCarpetAI.MOD_ID)
public class MinisBotCarpetAI {
    public static final String MOD_ID = "minisbot_carpet_ai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec CONFIG;

    public static final ModConfigSpec.ConfigValue<String> API_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> API_MODEL;

    static {
        BUILDER.push("llm_api");
        API_URL = BUILDER.define("url", "https://api.deepseek.com/v1/chat/completions");
        API_KEY = BUILDER.define("key", "");
        API_MODEL = BUILDER.define("model", "deepseek-chat");
        BUILDER.pop();
        CONFIG = BUILDER.build();
    }

    public MinisBotCarpetAI(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CONFIG);
        var gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(this::onRegisterCommands);
        gameBus.addListener(this::onServerTick);
        gameBus.addListener(this::onLoadConfig);
        gameBus.addListener(this::onPlayerChat);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MinisBotCommands.register(event.getDispatcher());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        BotAIManager.tickAll();
    }

    private void onLoadConfig(net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            LLMClient.configure(API_URL.get(), API_KEY.get(), API_MODEL.get());
            if (!API_KEY.get().isEmpty()) {
                LOGGER.info("[MinisBot] API已配置");
            }
        }
    }

    private void onPlayerChat(ServerChatEvent event) {
        BotAIManager.onPlayerChat(
            event.getPlayer().getName().getString(),
            event.getMessage().getString()
        );
    }
}
