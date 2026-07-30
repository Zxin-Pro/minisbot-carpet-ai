package com.minisbot.command;

import com.minisbot.ai.BotAIManager;
import com.minisbot.ai.LLMClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MinisBotCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("player")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("ai")
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "name");
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("🟢 " + n + " AI已" + (BotAIManager.toggleAI(n) ? "开启" : "关闭")), true);
                                    return 1;
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "name");
                                    BotAIManager.enableAI(n);
                                    ctx.getSource().sendSuccess(() -> Component.literal("🟢 " + n + " AI已开启"), true);
                                    return 1;
                                }).then(Commands.literal("hard").executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "name");
                                    if (!LLMClient.isConfigured()) {
                                        ctx.getSource().sendFailure(Component.literal("❌ 未配置API"));
                                        return 0;
                                    }
                                    BotAIManager.enableAI(n);
                                    BotAIManager.enableLLM(n);
                                    ctx.getSource().sendSuccess(() -> Component.literal("🔴 " + n + " 困难模式已开启"), true);
                                    return 1;
                                })))
                                .then(Commands.literal("off").executes(ctx -> {
                                    BotAIManager.disableAI(StringArgumentType.getString(ctx, "name"));
                                    ctx.getSource().sendSuccess(() -> Component.literal("✅ AI已关闭"), true);
                                    return 1;
                                }))
                        )
                        .then(Commands.literal("auto").executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            BotAIManager.enableAI(n);
                            BotAIManager.startAutoUpgrade(n);
                            ctx.getSource().sendSuccess(() -> Component.literal("🟢 " + n + " 自动升级"), true);
                            return 1;
                        }).then(Commands.literal("hard").executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            if (!LLMClient.isConfigured()) { ctx.getSource().sendFailure(Component.literal("❌ 未配置API")); return 0; }
                            BotAIManager.enableAI(n);
                            BotAIManager.enableLLM(n);
                            BotAIManager.startAutoUpgrade(n);
                            ctx.getSource().sendSuccess(() -> Component.literal("🔴 " + n + " 困难自动升级"), true);
                            return 1;
                        })))
                        .then(Commands.literal("hunt").executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            BotAIManager.enableAI(n);
                            BotAIManager.startHunt(n, ctx.getSource().getPlayerOrException().getName().getString());
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("§a🟢 " + n + " 开始追杀"), false);
                            return 1;
                        }).then(Commands.argument("target", StringArgumentType.word()).executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            String t = StringArgumentType.getString(ctx, "target");
                            BotAIManager.enableAI(n);
                            BotAIManager.startHunt(n, t);
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("§a🟢 " + n + " 追杀 " + t), false);
                            return 1;
                        })).then(Commands.literal("hard").executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            if (!LLMClient.isConfigured()) { ctx.getSource().sendFailure(Component.literal("❌ 未配置API")); return 0; }
                            BotAIManager.enableAI(n);
                            BotAIManager.enableLLM(n);
                            BotAIManager.startHunt(n, ctx.getSource().getPlayerOrException().getName().getString());
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("§c🔴 " + n + " 困难追杀"), false);
                            return 1;
                        }).then(Commands.argument("target", StringArgumentType.word()).executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            String t = StringArgumentType.getString(ctx, "target");
                            if (!LLMClient.isConfigured()) { ctx.getSource().sendFailure(Component.literal("❌ 未配置API")); return 0; }
                            BotAIManager.enableAI(n);
                            BotAIManager.enableLLM(n);
                            BotAIManager.startHunt(n, t);
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                    Component.literal("§c🔴 " + n + " 困难追杀 " + t), false);
                            return 1;
                        }))))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal(BotAIManager.getStatus(StringArgumentType.getString(ctx, "name"))), false);
                            return 1;
                        }))
                )
        );
    }
}
