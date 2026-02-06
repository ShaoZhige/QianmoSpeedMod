package com.example.qianmospeed.event;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.road.RoadDetectionFactory;
import net.minecraft.core.BlockPos;
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
    // 用于跟踪上次检查的tick
    private static final Map<UUID, Integer> lastCheckTicks = new HashMap<>();
    // 用于存储玩家当前的速度效果级别
    private static final Map<UUID, Integer> playerSpeedLevels = new HashMap<>();
    //用于存储稳定的速度级别（防抖动）
    private static final Map<UUID, Integer> playerStableSpeedLevels = new HashMap<>();
    // 用于跟踪玩家腾空状态
    private static final Map<UUID, AirborneState> playerAirborneStates = new HashMap<>();
    // 属性修饰器的UUID
    private static final UUID TRAVEL_BLESSINGS_MODIFIER_UUID = UUID
            .nameUUIDFromBytes((QianmoSpeedMod.MODID + ":travel_blessings_speed_modifier").getBytes());

    /**
     *玩家腾空状态跟踪类（增强版 - 防止频繁切换）
     */
    private static class AirborneState {
        boolean wasOnRoad; // 腾空前是否在道路上
        int roadLevel; // 道路等级
        long airborneStartTime; // 腾空开始时间
        BlockPos takeoffPosition; // 起飞位置
        int consecutiveAirborneTicks; // 连续腾空的tick数
        int consecutiveGroundTicks; //连续在地面的tick数

        AirborneState(boolean wasOnRoad, int roadLevel, BlockPos takeoffPos) {
            this.wasOnRoad = wasOnRoad;
            this.roadLevel = roadLevel;
            this.airborneStartTime = System.currentTimeMillis();
            this.takeoffPosition = takeoffPos;
            this.consecutiveAirborneTicks = 0;
            this.consecutiveGroundTicks = 0; //初始化
        }

        void incrementAirborneTick() {
            consecutiveAirborneTicks++;
            consecutiveGroundTicks = 0; //腾空时重置地面计数
        }

        void incrementGroundTick() { //新增方法
            consecutiveGroundTicks++;
        }

        boolean isValid() {
            //腾空状态最多有效60 ticks（3秒）
            return consecutiveAirborneTicks <= 60;
        }

        boolean shouldEndAirborne() { //判断是否应该结束腾空状态
            // 在地面超过2 tick（约0.1秒）才确认落地
            return consecutiveGroundTicks >= 2;
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
                blockId.contains("layer");
    }

    /**
     * 多层检测
     */
    private static boolean checkRoadWithMultiLayer(Level level, Player player,
            RoadDetectionFactory.IRoadDetector detector) {
        // 优先级1：检查玩家脚下的方块
        BlockPos belowPlayer = player.blockPosition().below();
        if (detector.isOnRoad(level, belowPlayer)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                BlockState state = level.getBlockState(belowPlayer);
                QianmoSpeedMod.LOGGER.debug("玩家 {} 在脚下方块 {} 检测到道路",
                        player.getName().getString(),
                        ForgeRegistries.BLOCKS.getKey(state.getBlock()));
            }
            return true;
        }
        // 优先级2：检查玩家当前位置（对于站在台阶上的情况）
        BlockPos currentPos = player.blockPosition();
        if (detector.isOnRoad(level, currentPos)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                BlockState state = level.getBlockState(currentPos);
                QianmoSpeedMod.LOGGER.debug("玩家 {} 在当前位置 {} 检测到道路",
                        player.getName().getString(),
                        ForgeRegistries.BLOCKS.getKey(state.getBlock()));
            }
            return true;
        }
        // 优先级3：检查精确脚部位置
        BlockPos feetPos = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        if (detector.isOnRoad(level, feetPos)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                BlockState state = level.getBlockState(feetPos);
                QianmoSpeedMod.LOGGER.debug("玩家 {} 在精确脚部位置 {} 检测到道路",
                        player.getName().getString(),
                        ForgeRegistries.BLOCKS.getKey(state.getBlock()));
            }
            return true;
        }
        // 优先级4：检查脚部位置下方
        if (detector.isOnRoad(level, feetPos.below())) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                BlockState state = level.getBlockState(feetPos.below());
                QianmoSpeedMod.LOGGER.debug("玩家 {} 在脚部下方 {} 检测到道路",
                        player.getName().getString(),
                        ForgeRegistries.BLOCKS.getKey(state.getBlock()));
            }
            return true;
        }
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("玩家 {} 在所有位置都未检测到道路", player.getName().getString());
        }
        return false;
    }

    /**
     *更新玩家腾空状态（防止频繁切换）
     */
    private static void updateAirborneState(Player player, boolean isOnRoad, int roadLevel, BlockPos currentPos) {
        UUID playerId = player.getUUID();
        // 判断玩家是否在腾空状态（更精确的判断）
        boolean isAirborne = !player.onGround() &&
                !player.isInWater() &&
                !player.isInLava() &&
                !player.getAbilities().flying &&
                !player.isPassenger() &&
                !player.isSwimming();
        AirborneState state = playerAirborneStates.get(playerId);
        if (isAirborne) {
            // 玩家在腾空状态
            if (state == null) {
                // 新的腾空状态：记录腾空前的道路状态
                state = new AirborneState(isOnRoad, roadLevel, currentPos);
                playerAirborneStates.put(playerId, state);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("玩家 {} 开始腾空，起飞位置: {}, 道路状态: {}, 等级: {}",
                            player.getName().getString(), currentPos, isOnRoad, roadLevel);
                }
            } else {
                // 已经在腾空，更新状态
                state.incrementAirborneTick();
                // 如果当前检测到道路，更新腾空前的道路状态
                if (isOnRoad && roadLevel > 0) {
                    state.wasOnRoad = true;
                    state.roadLevel = roadLevel;
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("玩家 {} 腾空中检测到道路，更新状态: 等级={}",
                                player.getName().getString(), roadLevel);
                    }
                }
            }
        } else {
            // 玩家在地面
            if (state != null) {
                //增加地面计数
                state.incrementGroundTick();
                //修改：只有确认落地（连续2 tick在地面）才清理状态
                if (state.shouldEndAirborne()) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("玩家 {} 结束腾空，持续 {} ticks",
                                player.getName().getString(), state.consecutiveAirborneTicks);
                    }
                    playerAirborneStates.remove(playerId);
                }
            }
        }
    }

    /**
     *检查是否应该维持速度加成（腾空时）
     */
    private static boolean shouldMaintainSpeedBonus(Player player, RoadDetectionFactory.IRoadDetector detector) {
        UUID playerId = player.getUUID();
        AirborneState state = playerAirborneStates.get(playerId);
        // 检查腾空状态是否有效
        if (state == null) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  腾空维持检查: 没有腾空状态");
            }
            return false;
        }
        if (!state.isValid()) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  腾空维持检查: 腾空时间过长（{} ticks）", state.consecutiveAirborneTicks);
            }
            return false;
        }
        if (!state.wasOnRoad || state.roadLevel <= 0) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  腾空维持检查: 起飞前不在道路上");
            }
            return false;
        }
        //如果玩家已经落地在非道路上，立即停止加成
        if (player.onGround()) {
            BlockPos belowPlayer = player.blockPosition().below();
            boolean onRoadNow = detector.isOnRoad(player.level(), belowPlayer);
            if (!onRoadNow) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  腾空维持检查: 已落地在非道路上，停止加成");
                }
                playerAirborneStates.remove(playerId);
                return false;
            }
        }
        // 腾空时间在有效范围内，维持速度
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  腾空维持检查: 通过（等级={}, 已腾空={} ticks）",
                    state.roadLevel, state.consecutiveAirborneTicks);
        }
        return true;
    }

    /**
     * 确保完全清理玩家相关的所有数据
     */
    private static void cleanupPlayerData(Player player) {
        UUID playerId = player.getUUID();
        // 1. 移除属性修饰器
        removeSpeedAttribute(player);
        // 2. 清理内存中的数据
        playerSpeedLevels.remove(playerId);
        playerStableSpeedLevels.remove(playerId); //新增
        lastCheckTicks.remove(playerId);
        playerAirborneStates.remove(playerId);
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("清理玩家数据: {}", playerId);
        }
    }

    /**
     * 检查属性是否包含指定UUID的修饰器
     */
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

    /**
     * 玩家登录时发送欢迎消息
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 先清理旧数据
        cleanupPlayerData(event.getEntity());
        if (!SpeedModConfig.isLoginMessagesEnabled())
            return;
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            StringBuilder welcomeMessage = new StringBuilder();
            int checkInterval = SpeedModConfig.getCheckInterval();
            // 基础信息
            welcomeMessage.append("§a[阡陌疾旅] §f");
            // 检测模式信息
            boolean usingAdvanced = false;
            try {
                RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
                usingAdvanced = detector instanceof com.example.qianmospeed.road.EnhancedRoadDetector ||
                        detector instanceof com.example.qianmospeed.road.HybridRoadDetector;
            } catch (Exception e) {
                usingAdvanced = SpeedModConfig.isAdvancedFeaturesEnabled() ||
                        QianmoSpeedMod.hasDetectedProfessionalRoadMods();
            }
            if (usingAdvanced) {
                welcomeMessage.append("增强模式已启用");
                if (SpeedModConfig.isAdvancedFeaturesEnabled()) {
                    welcomeMessage.append(" (用户配置)");
                } else if (QianmoSpeedMod.hasDetectedProfessionalRoadMods()) {
                    var proMods = QianmoSpeedMod.getDetectedProfessionalModNames();
                    if (!proMods.isEmpty()) {
                        welcomeMessage.append(" (检测到").append(proMods.get(0)).append(")");
                    }
                }
            } else {
                welcomeMessage.append("基础模式");
            }
            welcomeMessage.append(" (检查间隔: ").append(checkInterval).append(" ticks)\n");
            // 检测到的模组信息
            if (QianmoSpeedMod.hasDetectedRoadMods()) {
                var modNames = QianmoSpeedMod.getDetectedRoadModNames();
                welcomeMessage.append("§7检测到道路模组: ");
                for (int i = 0; i < modNames.size(); i++) {
                    if (i > 0)
                        welcomeMessage.append(", ");
                    welcomeMessage.append(modNames.get(i));
                }
                if (QianmoSpeedMod.hasDetectedProfessionalRoadMods() && !usingAdvanced) {
                    welcomeMessage.append("\n§e检测到专业道路模组，已自动启用高级模式！");
                } else if (!usingAdvanced) {
                    welcomeMessage.append("\n§7建议在配置中启用高级模式以获得更好的道路识别");
                }
            } else {
                welcomeMessage.append("§7未检测到道路模组，使用基础道路检测");
            }
            serverPlayer.sendSystemMessage(Component.literal(welcomeMessage.toString()));
            QianmoSpeedMod.LOGGER.info("玩家 {} 登录，已发送欢迎消息", player.getName().getString());
        }
    }

    /**
     * 玩家登出时清理数据
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayerData(event.getEntity());
        // 清理 RoadWeaver 缓存
        if (QianmoSpeedMod.isRoadModLoaded("roadweaver")) {
            AdvancedRoadHandler.clearCache();
        }
    }

    /**
     *玩家每tick更新时检查 - 防止卡顿
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (event.player.level().isClientSide())
            return;
        Player player = event.player;
        // 检查间隔控制
        int currentTick = (int) player.level().getGameTime();
        Integer lastCheck = lastCheckTicks.get(player.getUUID());
        int checkInterval = SpeedModConfig.getCheckInterval();
        if (lastCheck != null && currentTick - lastCheck < checkInterval) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("跳过检查间隔: 玩家={}, 上次={}, 当前={}, 间隔={}",
                        player.getName().getString(), lastCheck, currentTick, checkInterval);
            }
            return;
        }
        // 更新最后检查时间
        lastCheckTicks.put(player.getUUID(), currentTick);
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("开始检查玩家 {} (tick={})", player.getName().getString(), currentTick);
        }
        // 检查玩家是否穿着有旅途祝福附魔的靴子
        ItemStack boots = player.getInventory().getArmor(0); // 0是靴子槽位
        if (!boots.isEmpty()) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(boots);
            // 使用安全的方式获取旅途祝福附魔
            Enchantment travelBlessingsEnchantment = null;
            try {
                travelBlessingsEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(
                        new net.minecraft.resources.ResourceLocation(QianmoSpeedMod.MODID, "travel_blessings"));
            } catch (Exception e) {
                QianmoSpeedMod.LOGGER.error("获取旅途祝福附魔失败: ", e);
            }
            if (travelBlessingsEnchantment != null && enchantments.containsKey(travelBlessingsEnchantment)) {
                int enchantLevel = enchantments.get(travelBlessingsEnchantment);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("玩家 {} 有旅途祝福附魔，等级: {}",
                            player.getName().getString(), enchantLevel);
                }
                // 创建检测器
                RoadDetectionFactory.IRoadDetector detector = RoadDetectionFactory.createDetector();
                // 🔍 添加检测器类型日志（每200 tick输出一次，避免刷屏）
                if (SpeedModConfig.isDebugMessagesEnabled() && player.level().getGameTime() % 200 == 0) {
                    QianmoSpeedMod.LOGGER.debug("================== 当前使用的检测器 ==================");
                    QianmoSpeedMod.LOGGER.debug("玩家: {}", player.getName().getString());
                    QianmoSpeedMod.LOGGER.debug("检测器类型: {}", detector.getClass().getSimpleName());
                    QianmoSpeedMod.LOGGER.debug("高级功能启用: {}", SpeedModConfig.isAdvancedFeaturesEnabled());
                    QianmoSpeedMod.LOGGER.debug("检测模式: {}", SpeedModConfig.getRoadDetectionMode());
                    QianmoSpeedMod.LOGGER.debug("===================================================");
                }
                // 使用多层检测方法
                boolean isOnRoad = checkRoadWithMultiLayer(player.level(), player, detector);
                // 腾空状态处理
                BlockPos belowPlayer = player.blockPosition().below();
                updateAirborneState(player, isOnRoad, enchantLevel, belowPlayer);
                //修改：检查是否应该维持速度加成（腾空时）
                boolean maintainBonus = false;
                int newLevel;
                if (isOnRoad) {
                    // 正常在道路上
                    newLevel = enchantLevel;
                } else {
                    // 不在道路上，检查是否腾空且应该维持
                    if (shouldMaintainSpeedBonus(player, detector)) {
                        maintainBonus = true;
                        //使用腾空状态记录的等级
                        AirborneState state = playerAirborneStates.get(player.getUUID());
                        newLevel = state != null ? state.roadLevel : enchantLevel;
                        if (SpeedModConfig.isDebugMessagesEnabled()) {
                            QianmoSpeedMod.LOGGER.debug("玩家 {} 腾空中，维持速度加成（等级={}）",
                                    player.getName().getString(), newLevel);
                        }
                    } else {
                        newLevel = 0;
                    }
                }
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    BlockState feetState = player.level().getBlockState(belowPlayer);
                    QianmoSpeedMod.LOGGER.debug("玩家 {} 最终检测结果: 道路={}, 腾空维持={}, 速度等级={}, 脚下方块={}",
                            player.getName().getString(), isOnRoad, maintainBonus, newLevel,
                            ForgeRegistries.BLOCKS.getKey(feetState.getBlock()));
                }
                // 获取之前的速度级别
                Integer previousLevel = playerSpeedLevels.get(player.getUUID());
                Integer stableLevel = playerStableSpeedLevels.get(player.getUUID());
                //防抖动逻辑 - 速度等级需要稳定2次检查才更新
                if (stableLevel == null || stableLevel != newLevel) {
                    // 第一次检测到变化，记录但不立即应用
                    playerStableSpeedLevels.put(player.getUUID(), newLevel);
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("玩家 {} 速度等级变化（待确认）: {} -> {}",
                                player.getName().getString(), stableLevel, newLevel);
                    }
                } else {
                    // 速度等级已经稳定，检查是否需要更新效果
                    if (previousLevel == null || previousLevel != newLevel) {
                        if (SpeedModConfig.isDebugMessagesEnabled()) {
                            QianmoSpeedMod.LOGGER.debug("玩家 {} 速度状态确认变化: 之前={}, 现在={}",
                                    player.getName().getString(), previousLevel, newLevel);
                        }
                        handleSpeedEffect(player, previousLevel, newLevel);
                    }
                    // 更新当前速度级别
                    playerSpeedLevels.put(player.getUUID(), newLevel);
                }
            } else {
                // 如果没有旅途祝福附魔，清理速度效果
                Integer previousLevel = playerSpeedLevels.get(player.getUUID());
                if (previousLevel != null && previousLevel > 0) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("玩家 {} 移除附魔，清理速度效果", player.getName().getString());
                    }
                    removeSpeedEffect(player, previousLevel);
                    playerSpeedLevels.put(player.getUUID(), 0);
                    playerStableSpeedLevels.put(player.getUUID(), 0); //同时清理稳定等级
                }
                // 清理腾空状态
                playerAirborneStates.remove(player.getUUID());
            }
        } else {
            // 如果没有穿靴子，清理速度效果
            Integer previousLevel = playerSpeedLevels.get(player.getUUID());
            if (previousLevel != null && previousLevel > 0) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("玩家 {} 没穿靴子，清理速度效果", player.getName().getString());
                }
                removeSpeedEffect(player, previousLevel);
                playerSpeedLevels.put(player.getUUID(), 0);
                playerStableSpeedLevels.put(player.getUUID(), 0); //同时清理稳定等级
            }
            // 清理腾空状态
            playerAirborneStates.remove(player.getUUID());
        }
        // 调试信息 - 定期记录速度属性状态
        if (SpeedModConfig.isDebugMessagesEnabled() && player.level().getGameTime() % 200 == 0) {
            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movementSpeed != null) {
                double baseSpeed = movementSpeed.getBaseValue();
                double currentSpeed = movementSpeed.getValue();
                boolean hasModifier = hasAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID);
                QianmoSpeedMod.LOGGER.debug("玩家 {} 速度状态: 基础={:.4f}, 当前={:.4f}, 有修饰器={}",
                        player.getName().getString(), baseSpeed, currentSpeed, hasModifier);
            }
        }
    }

    /**
     * 处理速度效果应用/移除
     */
    private static void handleSpeedEffect(Player player, Integer previousLevel, int newLevel) {
        // 移除旧的速度效果
        if (previousLevel != null && previousLevel > 0) {
            removeSpeedEffect(player, previousLevel);
            if (SpeedModConfig.isSpeedEffectMessagesEnabled() && player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                String removeMessage = "§7[阡陌疾旅] §f道路速度加成已移除";
                serverPlayer.sendSystemMessage(Component.literal(removeMessage));
                QianmoSpeedMod.LOGGER.debug("移除玩家 {} 的速度效果，之前等级: {}",
                        player.getName().getString(), previousLevel);
            }
        }
        // 应用新的速度效果
        if (newLevel > 0) {
            applySpeedEffect(player, newLevel);
            if (SpeedModConfig.isSpeedEffectMessagesEnabled() && player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                String romanNumeral;
                switch (newLevel) {
                    case 1 -> romanNumeral = "I";
                    case 2 -> romanNumeral = "II";
                    case 3 -> romanNumeral = "III";
                    default -> romanNumeral = String.valueOf(newLevel);
                }
                String appliedMessage = "§a[阡陌疾旅] §f道路速度加成已激活 (§e" + romanNumeral + "级§f)";
                serverPlayer.sendSystemMessage(Component.literal(appliedMessage));
                QianmoSpeedMod.LOGGER.debug("应用玩家 {} 的速度效果，新等级: {}",
                        player.getName().getString(), newLevel);
            }
        }
    }

    /**
     * 应用速度效果 - 使用属性修饰器
     */
    private static void applySpeedEffect(Player player, int level) {
        double multiplier = SpeedModConfig.getSpeedMultiplier(level);
        double speedBonus = multiplier - 1.0;
        // 获取移动速度属性
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            QianmoSpeedMod.LOGGER.error("玩家 {} 没有移动速度属性!", player.getName().getString());
            return;
        }
        // 创建属性修饰器
        AttributeModifier speedModifier = new AttributeModifier(
                TRAVEL_BLESSINGS_MODIFIER_UUID,
                "TravelBlessingsSpeedBonus",
                speedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        // 先移除可能存在的旧修饰器
        if (hasAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID);
        }
        // 添加新的修饰器
        movementSpeed.addTransientModifier(speedModifier);
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("应用速度修饰器: 玩家={}, 等级={}, 加成={:.2f}",
                    player.getName().getString(), level, speedBonus);
        }
    }

    /**
     * 移除属性修饰器
     */
    private static void removeAttributeModifier(AttributeInstance attribute, UUID modifierId) {
        if (attribute == null)
            return;
        Collection<AttributeModifier> modifiers = attribute.getModifiers();
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getId().equals(modifierId)) {
                toRemove = modifier;
                break;
            }
        }
        if (toRemove != null) {
            attribute.removeModifier(toRemove);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("移除属性修饰器: ID={}", modifierId);
            }
        }
    }

    /**
     * 移除速度效果 - 移除属性修饰器
     */
    private static void removeSpeedEffect(Player player, int level) {
        removeSpeedAttribute(player);
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("移除速度效果: 玩家={}, 等级={}",
                    player.getName().getString(), level);
        }
    }

    /**
     * 移除速度属性修饰器
     */
    private static void removeSpeedAttribute(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null)
            return;
        if (hasAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID)) {
            removeAttributeModifier(movementSpeed, TRAVEL_BLESSINGS_MODIFIER_UUID);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("移除速度修饰器: 玩家={}", player.getName().getString());
            }
        }
    }

    /**
     * 玩家重生时重置数据
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        cleanupPlayerData(event.getEntity());
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("玩家重生: {}", event.getEntity().getName().getString());
        }
    }

    /**
     * 玩家切换维度时重置数据
     */
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanupPlayerData(event.getEntity());
        // 清理 RoadWeaver 缓存
        if (QianmoSpeedMod.isRoadModLoaded("roadweaver")) {
            AdvancedRoadHandler.clearCache();
        }
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("玩家切换维度: {}", event.getEntity().getName().getString());
        }
    }

    /**
     * 玩家穿戴装备事件 - 确保装备变化时正确更新
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getSlot() == EquipmentSlot.FEET) {
                cleanupPlayerData(player);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("玩家装备变化: {} 更换了靴子", player.getName().getString());
                }
            }
        }
    }

    /**
     * 玩家死亡事件，确保数据清理
     */
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