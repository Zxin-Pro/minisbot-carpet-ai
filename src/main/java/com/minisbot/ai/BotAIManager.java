package com.minisbot.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BotAIManager {
    private static final Map<String, AIInstance> activeAIs = new ConcurrentHashMap<>();

    public static void enableAI(String name) { activeAIs.putIfAbsent(name, new AIInstance(name)); activeAIs.get(name).active = true; }
    public static void disableAI(String name) { var a = activeAIs.get(name); if (a != null) a.active = false; }
    public static boolean toggleAI(String name) {
        var a = activeAIs.get(name);
        if (a == null) { activeAIs.put(name, new AIInstance(name)); activeAIs.get(name).active = true; return true; }
        a.active = !a.active; return a.active;
    }
    public static void enableLLM(String name) { enableAI(name); var a = activeAIs.get(name); if (a != null) { a.useLLM = true; a.llmTick = 999; } }
    public static void setDifficulty(String name, boolean hard) { var a = activeAIs.get(name); if (a != null) { a.isHard = hard; a.useLLM = hard; } }
    public static void startAutoUpgrade(String name) { enableAI(name); var a = activeAIs.get(name); if (a != null) a.startAutoUpgrade(); }
    public static void startHunt(String name, String target) { enableAI(name); var a = activeAIs.get(name); if (a != null) a.startHunt(target); }

    public static String getStatus(String name) {
        var a = activeAIs.get(name);
        if (a == null) return "§e" + name + " §7没有AI";
        return a.getStatus();
    }

    public static void onPlayerChat(String speaker, String msg) {
        for (var a : activeAIs.values()) { if (a.active && a.useLLM) a.onPlayerChat(speaker, msg); }
    }

    public static void tickAll() {
        var server = MinecraftServer.getServer();
        if (server == null) return;
        for (var a : activeAIs.values()) { if (a.active) a.tick(server); }
    }

    private static class AIInstance {
        final String name; boolean active = false; boolean useLLM = false, isHard = false;
        String description = "空闲"; int tick = 0;
        boolean isHunting = false; String huntTarget = ""; boolean targetKilled = false;
        int huntStage = 0; int pvpTick = 0; int strafeDir = 1; double lastTX, lastTZ;
        int llmTick = 0; int chatCooldown = 0; boolean chatLLM = false;

        // 升级阶段
        int stage = 0; // 0=idle,1=wood,2=stone,3=iron,4=diamond,5=done
        String taskType = ""; String taskTarget = ""; int taskProg = 0;

        AIInstance(String name) { this.name = name; }

        void startAutoUpgrade() { stage = 1; description = "🌲 开始升级！"; taskType = "chop"; taskProg = 0; isHunting = false; }
        void startHunt(String t) { isHunting = true; huntTarget = t; targetKilled = false;
            if (useLLM) { stage = 5; description = "🔪 追杀 " + t + "！"; taskType = "hunt"; }
            else { stage = 1; description = "🌲 先发育，再杀 " + t; taskType = "chop"; taskProg = 0; }
        }

        String getStatus() {
            if (!active) return "§e" + name + " §7AI关闭";
            if (useLLM) return "§e" + name + " §b🧠 " + description;
            if (isHunting) return "§e" + name + " §c🔪 " + description;
            return "§e" + name + " §7" + description;
        }

        void onPlayerChat(String speaker, String msg) {
            if (!useLLM || chatCooldown > 0) return;
            if (!msg.toLowerCase().contains(name.toLowerCase()) && !msg.contains("@")) return;
            chatCooldown = 100;
            String sys = "你是Minecraft玩家" + name + "，请像真人一样回复" + speaker + "说的一句话。保持简短自然。";
            String ctx = speaker + "说: " + msg;
            String reply = LLMClient.think(sys, ctx);
            if (reply != null && !reply.isEmpty()) {
                var server = MinecraftServer.getServer();
                if (server != null) server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§7[§b" + name + "§7] §f" + reply), false);
            }
        }

        void tick(MinecraftServer server) {
            tick++;
            var player = server.getPlayerList().getPlayerByName(name);
            if (player == null || !(player instanceof EntityPlayerMPFake)) return;
            var fake = (EntityPlayerMPFake) player;

            // 生存
            if (fake.getHealth() < 10) { fake.getFoodData().eat(6, 0.6f); return; }
            if (chatCooldown > 0) chatCooldown--;

            // LLM 模式
            if (useLLM && isHunting && stage >= 5) {
                llmTick++; if (llmTick < 2) return; llmTick = 0;
                doHuntLLM(fake, server);
                return;
            }
            if (useLLM) {
                int rate = isHunting ? 2 : (tick % 100 == 0 ? 100 : 10);
                if (tick % rate != 0) return;
                doLLM(fake, server);
                return;
            }

            // 规则模式
            if (isHunting) { doHuntRule(fake, server); return; }
            doTask(fake);
        }

        // ========== 规则任务系统 ==========
        void doTask(EntityPlayerMPFake fake) {
            if (taskType.isEmpty() && stage > 0 && stage < 5) advanceStage();
            if (taskType.isEmpty()) return;

            switch (taskType) {
                case "chop" -> doChop(fake);
                case "mine" -> doMine(fake);
                case "hunt" -> {}
            }
        }

        void advanceStage() {
            switch (stage) {
                case 1 -> { taskType = "chop"; description = "🌲 砍树"; }
                case 2 -> { taskType = "mine"; taskTarget = "stone"; description = "🪨 挖石头"; }
                case 3 -> { taskType = "mine"; taskTarget = "iron_ore"; description = "⛏️ 挖铁"; }
                case 4 -> { taskType = "mine"; taskTarget = "diamond"; description = "💎 挖钻石"; }
                case 5 -> { taskType = ""; description = "🏆 毕业！"; }
            }
            taskProg = 0;
        }

        void doChop(EntityPlayerMPFake fake) {
            var level = (ServerLevel) fake.level();
            var log = findBlock(level, fake.blockPosition(), 16, s -> s.is(net.minecraft.tags.BlockTags.LOGS));
            if (log == null) { taskProg += 10; if (taskProg >= 100) { stage = 2; advanceStage(); } return; }
            moveAndBreak(fake, log, true);
            taskProg += 10;
            if (taskProg >= 100) {
                giveItem(fake, Items.WOODEN_PICKAXE);
                giveItem(fake, Items.WOODEN_SWORD);
                stage = 2; advanceStage();
            }
        }

        void doMine(EntityPlayerMPFake fake) {
            var level = (ServerLevel) fake.level();
            var ore = findBlock(level, fake.blockPosition(), 20, s -> isOre(s, taskTarget));
            if (ore == null) {
                var below = fake.blockPosition().below();
                if (!level.getBlockState(below).isAir()) level.destroyBlock(below, true);
                taskProg += 2;
            } else { moveAndBreak(fake, ore, false); taskProg += 15; }
            if (taskProg >= 100) {
                if (taskTarget.contains("stone")) { giveItem(fake, Items.STONE_PICKAXE); giveItem(fake, Items.STONE_SWORD); stage = 3; }
                else if (taskTarget.contains("iron")) { giveItem(fake, Items.IRON_PICKAXE); giveItem(fake, Items.IRON_SWORD); stage = 4; }
                else if (taskTarget.contains("diamond")) { giveItem(fake, Items.DIAMOND_PICKAXE); giveItem(fake, Items.DIAMOND_SWORD); stage = 5; }
                taskType = ""; advanceStage();
            }
        }

        void giveItem(EntityPlayerMPFake fake, Items item) { fake.getInventory().add(new ItemStack(item)); }

        // ========== 规则追杀 ==========
        void doHuntRule(EntityPlayerMPFake fake, MinecraftServer server) {
            if (targetKilled) { isHunting = false; description = "☠️ 已击杀"; return; }
            if (stage < 5) { doTask(fake); return; }

            var target = server.getPlayerList().getPlayerByName(huntTarget);
            if (target == null || !target.isAlive()) {
                if (target == null) description = "🔍 等待 " + huntTarget + " 上线";
                else { targetKilled = true; server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c💀 " + name + " 击杀了 " + huntTarget), false); }
                return;
            }

            double dist = fake.distanceToSqr(target);
            if (fake.getHealth() < 8) { fake.getFoodData().eat(6, 0.6f); retreat(fake, target, 8); return; }
            faceTarget(fake, target);

            if (dist < 6) {
                if (fake.onGround()) fake.setJumping(true);
                fake.attack(target);
                knockback(fake, target, 0.7, 0.35);
                if (tick % 8 == 0) strafeDir = -strafeDir;
                fake.getNavigation().moveTo(fake.getX()+strafeDir*2, fake.getY(), fake.getZ()+1.5, 0.9);
                description = "⚔️ 跳劈 " + huntTarget;
                if (!target.isAlive()) { targetKilled = true;
                    server.getPlayerList().broadcastSystemMessage(
                            net.minecraft.network.chat.Component.literal("§c💀 " + name + " 击杀了 " + huntTarget), false); }
            } else {
                predChase(fake, target, dist);
                description = "🔪 追击 " + huntTarget;
            }
        }

        // ========== LLM 模式 ==========
        void doLLM(EntityPlayerMPFake fake, MinecraftServer server) {
            if (tick % 40 != 0) return;
            var level = (ServerLevel) fake.level();
            var pos = fake.blockPosition();
            StringBuilder ctx = new StringBuilder();
            ctx.append("你在MC生存模式。坐标[").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append("] ");
            ctx.append("血量").append((int)fake.getHealth()).append("/20 ");
            ctx.append("饱食度").append(fake.getFoodData().getFoodLevel()).append("/20\n");

            var players = level.getEntitiesOfClass(ServerPlayer.class, fake.getBoundingBox().inflate(20), p -> p != fake && p.isAlive());
            if (!players.isEmpty()) ctx.append("附近玩家: ").append(players.iterator().next().getName().getString()).append("\n");

            var sys = "你是一个MC生存模式玩家。根据情况用一句话说你要做什么（砍树/挖矿/战斗/吃东西/打招呼）。不要作弊。";
            String decision = LLMClient.think(sys, ctx.toString());
            if (decision == null || decision.isEmpty()) return;
            description = "🧠 " + decision.substring(0, Math.min(40, decision.length()));

            String d = decision.toLowerCase();
            if (d.contains("砍树") || d.contains("chop") || d.contains("wood")) doChop(fake);
            else if (d.contains("挖") || d.contains("矿") || d.contains("mine") || d.contains("ore")) { taskTarget = "diamond"; doMine(fake); }
            else if (d.contains("吃") || d.contains("eat") || d.contains("food")) fake.getFoodData().eat(6, 0.6f);
            else if (d.contains("打") || d.contains("fight") || d.contains("attack")) {
                var mobs = level.getEntitiesOfClass(Mob.class, fake.getBoundingBox().inflate(16), m -> m.isAlive());
                if (!mobs.isEmpty()) {
                    var m = mobs.iterator().next();
                    fake.getNavigation().moveTo(m.getX(), m.getY(), m.getZ(), 1.0);
                    if (fake.distanceToSqr(m) < 4) { fake.attack(m); }
                }
            }
        }

        void doHuntLLM(EntityPlayerMPFake fake, MinecraftServer server) {
            var target = server.getPlayerList().getPlayerByName(huntTarget);
            if (target == null || !target.isAlive()) {
                if (target == null) description = "🔍 等待上线";
                else { targetKilled = true; server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c💀 " + name + " 击杀了 " + huntTarget), false); }
                return;
            }
            double dist = fake.distanceToSqr(target);
            faceTarget(fake, target);

            if (fake.getHealth() < 8) { fake.getFoodData().eat(6, 0.6f); retreat(fake, target, 8); return; }

            String ctx = "追杀" + huntTarget + "！血量"+(int)fake.getHealth()+"/20 对方血量"+(int)target.getHealth()+"/20 距离"+(int)Math.sqrt(dist)+"米";
            if (target.isUsingItem()) ctx += " 对方正在使用物品";
            String sys = "你是Minecraft PVP高手。根据战况回复一个词: attack/axe/bow/retreat/chase/eat";
            String decision = LLMClient.think(sys, ctx);

            if (decision == null) decision = "chase";
            String d = decision.toLowerCase().trim();

            if (d.contains("attack")) {
                if (dist < 6) {
                    if (fake.onGround()) fake.setJumping(true);
                    fake.attack(target); knockback(fake, target, 0.7, 0.35);
                    fake.swing(InteractionHand.MAIN_HAND);
                } else moveTo(fake, target.getX(), target.getY(), target.getZ(), 1.3);
                description = "⚔️ 攻击!";
            } else if (d.contains("axe")) { moveTo(fake, target.getX(), target.getY(), target.getZ(), 1.3); fake.attack(target); description = "🪓 破盾!"; }
            else if (d.contains("bow")) { description = "🏹 射箭!"; }
            else if (d.contains("retreat")) { retreat(fake, target, 10); description = "🏃 撤退"; }
            else if (d.contains("eat")) { fake.getFoodData().eat(6, 0.6f); description = "🍎 回血"; }
            else { predChase(fake, target, dist); description = "🔪 追击"; }

            if (!target.isAlive()) { targetKilled = true;
                server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c💀 " + name + " 击杀了 " + huntTarget), false); }
        }

        // ========== 工具方法 ==========
        void moveTo(EntityPlayerMPFake f, double x, double y, double z, double s) { f.getNavigation().moveTo(x, y, z, s); }
        void faceTarget(EntityPlayerMPFake f, ServerPlayer t) {
            double dx = t.getX()-f.getX(), dz = t.getZ()-f.getZ();
            f.setYRot((float)Math.toDegrees(Math.atan2(dz, dx))-90);
        }
        void knockback(EntityPlayerMPFake f, ServerPlayer t, double h, double v) {
            double kx=t.getX()-f.getX(), kz=t.getZ()-f.getZ(), kl=Math.sqrt(kx*kx+kz*kz);
            if (kl>0) t.setDeltaMovement(t.getDeltaMovement().add(kx/kl*h, v, kz/kl*h));
        }
        void retreat(EntityPlayerMPFake f, ServerPlayer t, int d) {
            double dx=f.getX()-t.getX(), dz=f.getZ()-t.getZ(), l=Math.sqrt(dx*dx+dz*dz);
            if (l>0) f.getNavigation().moveTo(f.getX()+dx/l*d, f.getY(), f.getZ()+dz/l*d, 0.9);
        }
        void predChase(EntityPlayerMPFake f, ServerPlayer t, double dist) {
            double tdx=t.getX()-lastTX, tdz=t.getZ()-lastTZ;
            lastTX=t.getX(); lastTZ=t.getZ();
            f.getNavigation().moveTo(t.getX()+tdx*2, t.getY(), t.getZ()+tdz*2, dist>30?1.4:1.2);
        }

        void moveAndBreak(EntityPlayerMPFake f, BlockPos pos, boolean tree) {
            if (f.distanceToSqr(Vec3.atCenterOf(pos)) > 4) f.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 0.8);
            else {
                f.swing(InteractionHand.MAIN_HAND);
                ((ServerLevel)f.level()).destroyBlock(pos, true);
                if (tree) { BlockPos up = pos.above(); while(((ServerLevel)f.level()).getBlockState(up).is(net.minecraft.tags.BlockTags.LOGS)) { ((ServerLevel)f.level()).destroyBlock(up, true); up = up.above(); } }
            }
        }

        boolean isOre(BlockState s, String t) {
            if (s.isAir()) return false;
            String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().toLowerCase();
            if (t.contains("diamond")) return p.contains("diamond_ore");
            if (t.contains("iron")) return p.contains("iron_ore");
            if (t.contains("stone")) return p.contains("stone") || p.contains("cobble");
            return p.contains(t);
        }

        BlockPos findBlock(ServerLevel level, BlockPos center, int r, java.util.function.Predicate<BlockState> pred) {
            BlockPos best = null; double bestD = Double.MAX_VALUE;
            for (int dx=-r; dx<=r; dx++) for (int dy=-r; dy<=r; dy++) for (int dz=-r; dz<=r; dz++) {
                BlockPos p = center.offset(dx,dy,dz);
                if (pred.test(level.getBlockState(p))) { double d = center.distSqr(p); if (d < bestD) { bestD = d; best = p; } }
            }
            return best;
        }
    }
}
