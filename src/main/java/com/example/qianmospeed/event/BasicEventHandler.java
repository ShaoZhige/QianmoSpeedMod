package com.example.qianmospeed.event;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.road.RoadDetectionFactory;
import com.example.qianmospeed.util.LocalizationHelper;
import com.example.qianmospeed.util.RoadWeaverH2Helper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BasicEventHandler {
    // ========== 独立的间隔追踪 ==========
    private static final Map<UUID, Integer> lastEnchantmentCheckTicks = new HashMap<>();
    private static final Map<UUID, Integer> lastPermanentCheckTicks = new HashMap<>();

    // 玩家状态追踪
    private static final Map<UUID, Integer> playerSpeedLevels = new HashMap<>();
    private static final Map<UUID, Integer> playerStableSpeedLevels = new HashMap<>();
    private static final Map<UUID, AirborneState> playerAirborneStates = new HashMap<>();
    private static final Map<UUID, Boolean> playerPermanentSpeedActive = new HashMap<>();

    // UUID
    private static final UUID TRAVEL_BLESSINGS_MODIFIER_UUID = UUID
            .nameUUIDFromBytes((QianmoSpeedMod.MODID + ":travel_blessings_speed_modifier").getBytes());
    private static final UUID PERMANENT_SPEED_MODIFIER_UUID = UUID
            .nameUUIDFromBytes((QianmoSpeedMod.MODID + ":permanent_road_speed_modifier").getBytes());

    /**
     * 玩家腾空状态跟踪类
     */
    private static class AirborneState {
        boolean wasOnRoad;
        int roadLevel;
        long airborneStartTime;
        BlockPos takeoffPosition;
        int consecutiveAirborneTicks;
        int consecutiveGroundTicks;

        AirborneState(boolean wasOnRoad, int roadLevel, BlockPos takeoffPos) {
            this.wasOnRoad = wasOnRoad;
            this.roadLevel = roadLevel;
            this.airborneStartTime = System.currentTimeMillis();
            this.takeoffPosition = takeoffPos;
            this.consecutiveAirborneTicks = 0;
            this.consecutiveGroundTicks = 0;
        }

        void incrementAirborneTick() {
            consecutiveAirborneTicks++;
            consecutiveGroundTicks = 0;
        }

        void incrementGroundTick() {
            consecutiveGroundTicks++;
        }

        boolean isValid() {
            return consecutiveAirborneTicks <= 60; // 3秒
        }

        boolean shouldEndAirborne() {
            return consecutiveGroundTicks >= 2; // 0.1秒
        }
    }

    /**
     * 检查是否是不完整方块
     */
    private static boolean isIncompleteBlock(BlockState state) {
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        return blockId.contains("slab") ||
                blockId.contains("stairs") ||
                blockId.contains("carpet") ||
                blockId.contains("snow") ||
                blockId.contains("layer") ||
                blockId.contains("farmland") ||
                blockId.contains("path");
    }

    // ========== 核心：统一道路检测方法（附魔和常驻共用）==========
    private static boolean checkRoadWithMultiLayer(Level level, Player player,
            RoadDetectionFactory.IRoadDetector detector) {
        BlockPos belowPlayer = player.blockPosition().below();
        if (detector.isOnRoad(level, belowPlayer)) {
            return true;
        }
        BlockPos currentPos = player.blockPosition();
        if (detector.isOnRoad(level, currentPos)) {
            return true;
        }
        BlockPos feetPos = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        if (detector.isOnRoad(level, feetPos)) {
            return true;
        }
        if (detector.isOnRoad(level, feetPos.below())) {
            return true;
        }
        return false;
    }

    // ========== 统一腾空维持检查（附魔和常驻共用）==========
    private static boolean shouldMaintainSpeedBonus(Player player, RoadDetectionFactory.IRoadDetector detector) {
        UUID playerId = player.getUUID();
        AirborneState state = playerAirborneStates.get(playerId);

        // 如果没有腾空状态，不维持
        if (state == null) {
            return false;
        }

        // 如果起跳时不在道路上，不维持
        if (!state.wasOnRoad) {
            return false;
        }

        // 腾空时间太长，不维持
        if (!state.isValid()) {
            return false;
        }

        // 如果在地面，检查是否还在道路上
        if (player.onGround()) {
            BlockPos belowPlayer = player.blockPosition().below();
            boolean onRoadNow = detector.isOnRoad(player.level(), belowPlayer);

            // 需要连续2 tick不在道路才移除
            if (!onRoadNow) {
                if (state.consecutiveGroundTicks >= 2) {
                    playerAirborneStates.remove(playerId);
                    return false;
                }
                // 刚落地，还在第一 tick，继续维持
                return true;
            }
            return true;
        }

        // 在空中，维持加速
        return true;
    }

    /**
     * 更新腾空状态 - 附魔和常驻共用
     */
    private static void updateAirborneState(Player player, boolean isOnRoad, int roadLevel, BlockPos currentPos) {
        UUID playerId = player.getUUID();
        boolean isAirborne = !player.onGround() &&
                !player.isInWater() &&
                !player.isInLava() &&
                !player.getAbilities().flying &&
                !player.isPassenger() &&
                !player.isSwimming();

        AirborneState state = playerAirborneStates.get(playerId);

        if (isAirborne) {
            if (state == null) {
                // 重要：起跳时记录是否在道路上
                state = new AirborneState(isOnRoad, roadLevel, currentPos);
                playerAirborneStates.put(playerId, state);

                if (SpeedModConfig.isDebugMessagesEnabled() && isOnRoad) {
                    QianmoSpeedMod.LOGGER.debug("玩家 {} 从道路起跳，将维持加速", player.getName().getString());
                }
            } else {
                state.incrementAirborneTick();
                // 腾空中如果检测到道路，更新状态（比如从空中落到道路上）
                if (isOnRoad && roadLevel > 0) {
                    state.wasOnRoad = true;
                    state.roadLevel = Math.max(state.roadLevel, roadLevel);
                }
            }
        } else {
            if (state != null) {
                state.incrementGroundTick();
                // 需要连续2 tick在地面才结束腾空状态
                if (state.shouldEndAirborne()) {
                    playerAirborneStates.remove(playerId);
                }
            }
        }
    }

    // ========== 清理玩家数据 ==========
    private static void cleanupPlayerData(Player player) {
        UUID playerId = player.getUUID();

        QianmoSpeedMod.LOGGER.debug("清理玩家数据: {}", player.getName().getString());

        // 移除实际效果
        removeSpeedAttribute(player);
        removePermanentSpeedEffect(player);

        // 清理内存状态
        playerSpeedLevels.remove(playerId);
        playerStableSpeedLevels.remove(playerId);
        playerPermanentSpeedActive.remove(playerId);
        lastEnchantmentCheckTicks.remove(playerId);
        lastPermanentCheckTicks.remove(playerId);
        playerAirborneStates.remove(playerId);
    }

    // ========== 属性修饰器工具方法 ==========
    private static boolean hasAttributeModifier(AttributeInstance attribute, UUID modifierId) {
        if (attribute == null)
            return false;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(modifierId)) {
                return true;
            }
        }
        return false;
    }

    private static void removeAttributeModifier(AttributeInstance attribute, UUID modifierId) {
        if (attribute == null)
            return;
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getId().equals(modifierId)) {
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            attribute.removeModifier(toRemove);
        }
    }

    // ========== 常驻加速核心方法 ==========
    private static void applyPermanentSpeedEffect(Player player) {
        double multiplier = SpeedModConfig.getPermanentSpeedMultiplier();
        double speedBonus = multiplier - 1.0;

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null)
            return;

        QianmoSpeedMod.LOGGER.info("★★★★★ 【常驻加速】应用！玩家={}, 倍率={}, 加成={}%",
                player.getName().getString(), multiplier, (int) (speedBonus * 100));

        AttributeModifier speedModifier = new AttributeModifier(
                PERMANENT_SPEED_MODIFIER_UUID,
                "PermanentRoadSpeedBonus",
                speedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        if (hasAttributeModifier(movementSpeed, PERMANENT_SPEED_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, PERMANENT_SPEED_MODIFIER_UUID);
        }

        movementSpeed.addTransientModifier(speedModifier);
        playerPermanentSpeedActive.put(player.getUUID(), true);
    }

    private static void removePermanentSpeedEffect(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null)
            return;

        if (hasAttributeModifier(movementSpeed, PERMANENT_SPEED_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, PERMANENT_SPEED_MODIFIER_UUID);
            QianmoSpeedMod.LOGGER.info("【移除常驻】玩家={} 的常驻道路加速已移除", player.getName().getString());
        }
    }

    // ========== 常驻加速主逻辑（使用统一腾空判断）==========
    private static void handlePermanentRoadSpeed(Player player, int currentTick) {
        UUID playerId = player.getUUID();

        // ========== 第一道防线：附魔检测 ==========
        boolean hasActiveEnchantment = playerSpeedLevels.getOrDefault(playerId, 0) > 0;
        if (hasActiveEnchantment) {
            if (playerPermanentSpeedActive.getOrDefault(playerId, false)) {
                removePermanentSpeedEffect(player);
                playerPermanentSpeedActive.put(playerId, false);
                QianmoSpeedMod.LOGGER.debug("【常驻】附魔激活，强制移除常驻加速: 玩家={}", player.getName().getString());
            }
            return;
        }

        // 1. 配置检查
        if (!SpeedModConfig.isPermanentSpeedEnabled()) {
            if (playerPermanentSpeedActive.getOrDefault(playerId, false)) {
                removePermanentSpeedEffect(player);
                playerPermanentSpeedActive.put(playerId, false);
            }
            return;
        }

        // 2. 间隔控制
        Integer lastCheck = lastPermanentCheckTicks.get(playerId);
        int checkInterval = SpeedModConfig.getCheckInterval();
        if (lastCheck != null && currentTick - lastCheck < checkInterval) {
            return;
        }
        lastPermanentCheckTicks.put(playerId, currentTick);

        // 3. 统一道路检测
        RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
        boolean isOnRoad = checkRoadWithMultiLayer(player.level(), player, detector);

        // 4. 更新腾空状态
        BlockPos belowPlayer = player.blockPosition().below();
        updateAirborneState(player, isOnRoad, 1, belowPlayer);

        // 5. 使用统一腾空维持检查
        boolean shouldMaintain = false;
        if (!isOnRoad) {
            shouldMaintain = shouldMaintainSpeedBonus(player, detector);
            if (shouldMaintain && SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("玩家 {} 腾空中，维持常驻加速", player.getName().getString());
            }
        }

        // 6. 应用/移除效果
        boolean currentlyActive = playerPermanentSpeedActive.getOrDefault(playerId, false);
        double multiplier = SpeedModConfig.getPermanentSpeedMultiplier();

        if (isOnRoad || shouldMaintain) {
            if (!currentlyActive) {
                applyPermanentSpeedEffect(player);
                playerPermanentSpeedActive.put(playerId, true);

                if (SpeedModConfig.isSpeedEffectMessagesEnabled()) {
                    int percent = (int) Math.round((multiplier - 1.0) * 100);
                    player.sendSystemMessage(Component.literal(
                            "§a[阡陌疾旅] §f常驻道路加速已激活 (§e" + percent + "%§f)"));
                }
            }
        } else {
            if (currentlyActive) {
                removePermanentSpeedEffect(player);
                playerPermanentSpeedActive.put(playerId, false);

                if (SpeedModConfig.isSpeedEffectMessagesEnabled()) {
                    player.sendSystemMessage(Component.literal(
                            "§7[阡陌疾旅] §f常驻道路加速已移除"));
                }
            }
        }
    }

    // ========== 附魔加速核心方法 ==========
    private static void applySpeedEffect(Player player, int level) {
        double multiplier = SpeedModConfig.getSpeedMultiplier(level);
        double speedBonus = multiplier - 1.0;

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null)
            return;

        // 附魔生效时，强制移除常驻加速
        UUID playerId = player.getUUID();
        if (playerPermanentSpeedActive.getOrDefault(playerId, false)) {
            removePermanentSpeedEffect(player);
            playerPermanentSpeedActive.put(playerId, false);
            QianmoSpeedMod.LOGGER.debug("附魔生效，移除常驻加速: 玩家={}", player.getName().getString());
        }

        AttributeModifier speedModifier = new AttributeModifier(
                TRAVEL_BLESSINGS_MODIFIER_UUID,
                "TravelBlessingsSpeedBonus",
                speedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        if (hasAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID);
        }

        movementSpeed.addTransientModifier(speedModifier);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("应用附魔速度: 玩家={}, 等级={}, 加成={}%",
                    player.getName().getString(), level, (int) (speedBonus * 100));
        }
    }

    private static void removeSpeedAttribute(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null)
            return;
        if (hasAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID);
            QianmoSpeedMod.LOGGER.info("【移除附魔】玩家={} 的旅途祝福效果已移除", player.getName().getString());
        }
    }

    private static void removeSpeedEffect(Player player, int level) {
        removeSpeedAttribute(player);
    }

    private static void handleSpeedEffect(Player player, Integer previousLevel, int newLevel) {
        if (previousLevel != null && previousLevel > 0) {
            removeSpeedEffect(player, previousLevel);
            if (SpeedModConfig.isSpeedEffectMessagesEnabled() && player instanceof ServerPlayer) {
                player.sendSystemMessage(Component.literal("§7[阡陌疾旅] §f道路速度加成已移除"));
            }
        }

        if (newLevel > 0) {
            applySpeedEffect(player, newLevel);
            if (SpeedModConfig.isSpeedEffectMessagesEnabled() && player instanceof ServerPlayer) {
                String romanNumeral = switch (newLevel) {
                    case 1 -> "I";
                    case 2 -> "II";
                    case 3 -> "III";
                    default -> String.valueOf(newLevel);
                };
                player.sendSystemMessage(Component.literal(
                        "§a[阡陌疾旅] §f道路速度加成已激活 (§e" + romanNumeral + "级§f)"));
            }
        }
    }

    // ========== 附魔加速主逻辑（使用统一腾空判断）==========
    private static boolean checkAndHandleEnchantmentSpeed(Player player, int currentTick) {
        UUID playerId = player.getUUID();

        Integer lastCheck = lastEnchantmentCheckTicks.get(playerId);
        int checkInterval = SpeedModConfig.getCheckInterval();
        if (lastCheck != null && currentTick - lastCheck < checkInterval) {
            return playerSpeedLevels.getOrDefault(playerId, 0) > 0;
        }
        lastEnchantmentCheckTicks.put(playerId, currentTick);

        ItemStack boots = player.getInventory().getArmor(0);
        if (!boots.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(boots);
            Enchantment travelBlessingsEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(
                    new net.minecraft.resources.ResourceLocation(QianmoSpeedMod.MODID, "travel_blessings"));

            if (travelBlessingsEnchantment != null && enchantments.containsKey(travelBlessingsEnchantment)) {
                int enchantLevel = enchantments.get(travelBlessingsEnchantment);

                RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
                boolean isOnRoad = checkRoadWithMultiLayer(player.level(), player, detector);

                BlockPos belowPlayer = player.blockPosition().below();
                updateAirborneState(player, isOnRoad, enchantLevel, belowPlayer);

                int newLevel;
                if (isOnRoad) {
                    newLevel = enchantLevel;
                } else {
                    // 使用统一腾空维持检查
                    if (shouldMaintainSpeedBonus(player, detector)) {
                        AirborneState state = playerAirborneStates.get(playerId);
                        newLevel = state != null ? state.roadLevel : enchantLevel;
                    } else {
                        newLevel = 0;
                    }
                }

                Integer previousLevel = playerSpeedLevels.get(playerId);
                Integer stableLevel = playerStableSpeedLevels.get(playerId);

                if (stableLevel == null || stableLevel != newLevel) {
                    playerStableSpeedLevels.put(playerId, newLevel);
                } else {
                    if (previousLevel == null || previousLevel != newLevel) {
                        handleSpeedEffect(player, previousLevel, newLevel);
                    }
                    playerSpeedLevels.put(playerId, newLevel);
                }

                return newLevel > 0;
            }
        }

        // 没有附魔，清理效果
        Integer previousLevel = playerSpeedLevels.get(playerId);
        if (previousLevel != null && previousLevel > 0) {
            removeSpeedEffect(player, previousLevel);
            playerSpeedLevels.put(playerId, 0);
            playerStableSpeedLevels.put(playerId, 0);
        }
        playerAirborneStates.remove(playerId);

        return false;
    }

    // ========== 立即激活附魔的方法 ==========
    private static void handleEnchantmentSpeedImmediate(Player player, int currentTick, int enchantLevel,
            boolean isOnRoad) {
        UUID playerId = player.getUUID();

        // 更新腾空状态
        BlockPos belowPlayer = player.blockPosition().below();
        updateAirborneState(player, isOnRoad, enchantLevel, belowPlayer);

        // 已知在道路上，直接激活附魔
        int newLevel = enchantLevel;

        Integer previousLevel = playerSpeedLevels.get(playerId);
        Integer stableLevel = playerStableSpeedLevels.get(playerId);

        if (stableLevel == null || stableLevel != newLevel) {
            playerStableSpeedLevels.put(playerId, newLevel);
        } else {
            if (previousLevel == null || previousLevel != newLevel) {
                handleSpeedEffect(player, previousLevel, newLevel);
            }
            playerSpeedLevels.put(playerId, newLevel);
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("【附魔立即激活】玩家={}, 等级={}",
                    player.getName().getString(), enchantLevel);
        }
    }

    // ========== 事件监听 ==========

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (event.player.level().isClientSide())
            return;

        Player player = event.player;
        int currentTick = (int) player.level().getGameTime();
        UUID playerId = player.getUUID();

        // ========== 先检测道路，再决定哪个加速生效 ==========

        // 1. 先检测是否在道路上
        RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
        boolean isOnRoad = checkRoadWithMultiLayer(player.level(), player, detector);

        // 2. 检查玩家是否穿着附魔靴子
        boolean hasEnchantmentBoots = false;
        int enchantmentLevel = 0;
        ItemStack boots = player.getInventory().getArmor(0);
        if (!boots.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(boots);
            Enchantment travelBlessingsEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(
                    new net.minecraft.resources.ResourceLocation(QianmoSpeedMod.MODID, "travel_blessings"));
            if (travelBlessingsEnchantment != null && enchantments.containsKey(travelBlessingsEnchantment)) {
                hasEnchantmentBoots = true;
                enchantmentLevel = enchantments.get(travelBlessingsEnchantment);
            }
        }

        // 3. 处理附魔加速（如果穿着附魔靴子且在道路上）
        if (hasEnchantmentBoots && isOnRoad) {
            // 附魔应该立即激活，强制移除常驻加速
            if (playerPermanentSpeedActive.getOrDefault(playerId, false)) {
                removePermanentSpeedEffect(player);
                playerPermanentSpeedActive.put(playerId, false);
                QianmoSpeedMod.LOGGER.debug("【附魔优先】道路上检测到附魔靴子，移除常驻加速");
            }

            // 调用新方法，直接应用附魔加速
            handleEnchantmentSpeedImmediate(player, currentTick, enchantmentLevel, isOnRoad);
        } else {
            // 4. 没有附魔或不在道路上，正常处理附魔（可能维持或移除）
            boolean hasEnchantment = checkAndHandleEnchantmentSpeed(player, currentTick);

            // 5. 只有完全没有附魔时，才处理常驻加速
            if (!hasEnchantment) {
                handlePermanentRoadSpeed(player, currentTick);
            } else {
                // 附魔激活时，强制移除常驻加速
                if (playerPermanentSpeedActive.getOrDefault(playerId, false)) {
                    removePermanentSpeedEffect(player);
                    playerPermanentSpeedActive.put(playerId, false);
                    QianmoSpeedMod.LOGGER.debug("【附魔优先】强制移除常驻加速: 玩家={}", player.getName().getString());
                }
            }
        }

        // 调试日志（每200 tick）
        if (SpeedModConfig.isDebugMessagesEnabled() && currentTick % 200 == 0) {
            QianmoSpeedMod.LOGGER.info("【状态】玩家={}, 附魔激活={}, 常驻激活={}",
                    player.getName().getString(),
                    playerSpeedLevels.getOrDefault(playerId, 0) > 0,
                    playerPermanentSpeedActive.getOrDefault(playerId, false));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        cleanupPlayerData(event.getEntity());

        Player player = event.getEntity();
        if (player instanceof ServerPlayer && SpeedModConfig.isLoginMessagesEnabled()) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            StringBuilder welcomeMessage = new StringBuilder();

            // 模组前缀
            String prefix = LocalizationHelper.getLocalized("qianmospeed.message.login.prefix");
            welcomeMessage.append(prefix);

            boolean usingAdvanced = false;
            try {
                RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
                usingAdvanced = detector instanceof com.example.qianmospeed.road.HybridRoadDetector;
            } catch (Exception e) {
                usingAdvanced = SpeedModConfig.isAdvancedFeaturesEnabled() ||
                        QianmoSpeedMod.hasDetectedProfessionalRoadMods();
            }

            // 模式消息
            if (usingAdvanced) {
                String advancedMsg = LocalizationHelper.getLocalized(
                        "qianmospeed.message.login.advanced");
                welcomeMessage.append(advancedMsg);

                if (AdvancedRoadHandler.isAvailable()) {
                    welcomeMessage.append(" (规划数据优化)");
                }
            } else {
                String basicMsg = LocalizationHelper.getLocalized(
                        "qianmospeed.message.login.basic",
                        SpeedModConfig.getCheckInterval());
                welcomeMessage.append(basicMsg);
            }

            // 常驻加速提示
            if (SpeedModConfig.isPermanentSpeedEnabled()) {
                int percent = (int) Math.round((SpeedModConfig.getPermanentSpeedMultiplier() - 1.0) * 100);
                String permanentSpeedMsg = LocalizationHelper.getLocalized(
                        "qianmospeed.message.login.permanent_speed",
                        percent);
                String priorityMsg = LocalizationHelper.getLocalized(
                        "qianmospeed.message.login.permanent_speed_priority");
                welcomeMessage.append("\n").append(permanentSpeedMsg).append(priorityMsg);
            }

            // 检测到的模组
            if (QianmoSpeedMod.hasDetectedRoadMods()) {
                var modNames = QianmoSpeedMod.getDetectedRoadModNames();
                String detectedModsMsg = LocalizationHelper.getLocalized("qianmospeed.message.login.detected_mods");
                welcomeMessage.append("\n").append(detectedModsMsg);

                for (int i = 0; i < modNames.size(); i++) {
                    if (i > 0)
                        welcomeMessage.append(", ");
                    welcomeMessage.append(modNames.get(i));
                }
            }

            serverPlayer.sendSystemMessage(Component.literal(welcomeMessage.toString()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayerData(event.getEntity());
        if (SpeedModConfig.isRoadWeaverIntegrationEnabled() && QianmoSpeedMod.isRoadModLoaded("roadweaver")) {
            AdvancedRoadHandler.clearCache(null);
            RoadWeaverH2Helper.clearAllCache();
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        cleanupPlayerData(event.getEntity());
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("玩家重生: {}", event.getEntity().getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanupPlayerData(event.getEntity());
        if (SpeedModConfig.isRoadWeaverIntegrationEnabled() && QianmoSpeedMod.isRoadModLoaded("roadweaver")) {
            if (event.getEntity().level() instanceof ServerLevel serverLevel) {
                AdvancedRoadHandler.clearCache(serverLevel);
                RoadWeaverH2Helper.clearCache(serverLevel.dimension().location().toString());
            }
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getSlot() == EquipmentSlot.FEET) {
            QianmoSpeedMod.LOGGER.info("【装备变化】{} 更换了靴子，强制移除所有速度效果", player.getName().getString());

            UUID playerId = player.getUUID();

            // 1. 必须先移除实际的速度效果！
            removeSpeedAttribute(player);
            removePermanentSpeedEffect(player);

            // 2. 再清理内存状态
            playerSpeedLevels.remove(playerId);
            playerStableSpeedLevels.remove(playerId);
            playerPermanentSpeedActive.remove(playerId);
            lastEnchantmentCheckTicks.remove(playerId);
            lastPermanentCheckTicks.remove(playerId);
            playerAirborneStates.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            cleanupPlayerData(event.getOriginal());
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("玩家死亡: {}", event.getOriginal().getName().getString());
            }
        }
    }
}