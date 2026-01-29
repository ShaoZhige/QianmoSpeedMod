package com.example.qianmospeed.event;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.registry.EnchantmentRegistry;
import com.example.qianmospeed.road.RoadDetectionFactory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BasicEventHandler {

    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static RoadDetectionFactory.IRoadDetector detector = null;

    // 检查 RoadWeaver 是否加载
    private static boolean isRoadWeaverLoaded() {
        return ModList.get().isLoaded("roadweaver");
    }

    // 跟踪玩家上次穿的靴子
    private static final Map<Player, ItemStack> lastBootsMap = new HashMap<>();

    // 记录玩家最后在道路上的时间
    private static final Map<Player, Long> lastOnRoadTimeMap = new HashMap<>();

    // 记录玩家最后的位置
    private static final Map<Player, net.minecraft.core.BlockPos> lastPositionMap = new HashMap<>();

    // 记录玩家最后在道路上的位置（用于区域检测）
    private static final Map<Player, net.minecraft.core.BlockPos> lastRoadPositionMap = new HashMap<>();

    // 跳跃时保持速度的时间（ticks，约1秒）
    private static final int JUMP_KEEP_TIME = 20;

    // 道路区域检测半径（方块数）
    private static final int ROAD_AREA_RADIUS = 2;

    private static RoadDetectionFactory.IRoadDetector getDetector() {
        if (detector == null) {
            detector = RoadDetectionFactory.createDetector();
        }
        return detector;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Player player = event.player;
        if (player.level().isClientSide)
            return;

        long currentTime = player.level().getGameTime();

        // 从配置读取检测间隔
        int checkInterval = SpeedModConfig.getCheckInterval();

        // 每帧检查跳跃保持逻辑
        checkMovementStatus(player, currentTime);

        // 使用配置的间隔进行检测（但稍微放宽条件）
        if (currentTime % checkInterval != 0 && checkInterval > 5 && currentTime % 5 != 0) {
            return;
        }

        // 获取检测器
        RoadDetectionFactory.IRoadDetector roadDetector = getDetector();

        // 检查靴子附魔
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        int enchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                EnchantmentRegistry.QIANMO_SWIFTJOURNEY.get(),
                boots);

        boolean hasEnchantment = enchantLevel > 0;
        boolean hasModifier = hasSpeedModifier(player);

        // 如果没有附魔，确保移除速度加成
        if (!hasEnchantment && hasModifier) {
            removeSpeedModifier(player);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("移除速度加成: 玩家={} (无附魔)", player.getName().getString());
            }
            return;
        }

        // 有附魔时，检查道路
        if (hasEnchantment) {
            // 检查玩家是否在道路上（使用改进的区域检测方法）
            boolean isOnRoad = checkIfPlayerInRoadArea(player, roadDetector, currentTime);

            // 获取最后在道路上的时间
            Long lastOnRoadTime = lastOnRoadTimeMap.get(player);
            boolean recentlyOnRoad = lastOnRoadTime != null &&
                    (currentTime - lastOnRoadTime) <= JUMP_KEEP_TIME;

            // 获取玩家是否在移动状态
            boolean isMoving = isPlayerMoving(player, currentTime);

            // 情况1：在道路上且没有加成 → 添加加成
            if (isOnRoad && !hasModifier) {
                applySpeedModifier(player, enchantLevel);
                lastOnRoadTimeMap.put(player, currentTime);

                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("+ 应用速度加成: 玩家={}, 等级={}, 加成={}%",
                            player.getName().getString(),
                            enchantLevel,
                            (int) ((SpeedModConfig.getSpeedMultiplier(enchantLevel) - 1.0) * 100));
                }

                // 给玩家显示本地化消息
                if (SpeedModConfig.isSpeedEffectMessagesEnabled()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("qianmospeed.message.speed.applied",
                                    enchantLevel),
                            false);
                }
            }
            // 情况2：不在道路上，但有加成 → 检查是否需要移除
            else if (!isOnRoad && hasModifier) {
                // 如果最近在道路上（跳跃保持）且玩家在移动，则保持加成
                if (recentlyOnRoad && isMoving) {
                    // 还在保持期内，且玩家在移动（上下坡、跳跃等），不移除
                    if (SpeedModConfig.isDebugMessagesEnabled() && currentTime % 20 == 0) {
                        QianmoSpeedMod.LOGGER.debug("保持速度加成: 玩家={} (移动保持, 剩余{}ticks)",
                                player.getName().getString(),
                                JUMP_KEEP_TIME - (currentTime - lastOnRoadTime));
                    }
                } else {
                    // 如果已经过了保持时间，或玩家停止移动，则移除加成
                    removeSpeedModifier(player);

                    // 给玩家显示本地化消息
                    if (SpeedModConfig.isSpeedEffectMessagesEnabled()) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("qianmospeed.message.speed.removed"),
                                false);
                    }

                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("- 移除速度加成: 玩家={} (离开道路)", player.getName().getString());
                    }
                }
            }
            // 情况3：在道路上且有加成 → 更新最后在道路上的时间
            else if (isOnRoad && hasModifier) {
                lastOnRoadTimeMap.put(player, currentTime);
            }
        }

        // 每200 ticks输出一次状态信息（调试用）
        if (SpeedModConfig.isDebugMessagesEnabled() && currentTime % 200 == 0 && hasEnchantment) {
            Long lastOnRoadTime = lastOnRoadTimeMap.get(player);
            long timeSinceLastRoad = lastOnRoadTime != null ? (currentTime - lastOnRoadTime) : -1;
            boolean recentlyOnRoad = lastOnRoadTime != null && timeSinceLastRoad <= JUMP_KEEP_TIME;

            QianmoSpeedMod.LOGGER.info("状态: 玩家={}, 等级={}, 道路={}, 加成={}, 最近在道路={}({}ticks前), 移动={}",
                    player.getName().getString(),
                    enchantLevel,
                    checkIfPlayerInRoadArea(player, roadDetector, currentTime),
                    hasSpeedModifier(player),
                    recentlyOnRoad,
                    timeSinceLastRoad,
                    isPlayerMoving(player, currentTime));
        }
    }

    /**
     * 改进的区域检测：检查玩家是否在道路区域内
     * 考虑到上下坡、台阶等导致的短暂滞空
     */
    private static boolean checkIfPlayerInRoadArea(Player player, RoadDetectionFactory.IRoadDetector detector,
            long currentTime) {
        // 获取玩家当前位置
        net.minecraft.core.BlockPos playerPos = player.blockPosition();

        // 方法1：直接检查玩家脚下的方块
        net.minecraft.core.BlockPos onPos = player.getOnPos();
        if (!isFluidOrDangerousBlock(player.level(), onPos) && detector.isOnRoad(player.level(), onPos)) {
            lastRoadPositionMap.put(player, onPos);
            return true;
        }

        // 方法2：检查玩家站立的位置（可能是台阶、楼梯的上半部分）
        net.minecraft.core.BlockPos standingPos = playerPos.below();
        if (!isFluidOrDangerousBlock(player.level(), standingPos) && detector.isOnRoad(player.level(), standingPos)) {
            lastRoadPositionMap.put(player, standingPos);
            return true;
        }

        // 方法3：向下搜索最多3格，寻找道路方块（处理下坡）
        for (int i = 1; i <= 3; i++) {
            net.minecraft.core.BlockPos belowPos = playerPos.below(i);
            net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(belowPos);

            // 跳过流体和危险方块
            if (isFluidOrDangerousBlock(player.level(), belowPos)) {
                break; // 遇到流体或危险方块就停止搜索
            }

            if (detector.isOnRoad(player.level(), belowPos)) {
                lastRoadPositionMap.put(player, belowPos);
                return true;
            }

            // 如果遇到完整方块，停止搜索
            if (state.isSolid() && state.isCollisionShapeFullBlock(player.level(), belowPos)) {
                break;
            }
        }

        // 方法4：检查周围区域（处理上坡、斜坡边缘等）
        net.minecraft.core.BlockPos lastRoadPos = lastRoadPositionMap.get(player);
        if (lastRoadPos != null) {
            // 检查是否在最后已知道路位置的附近
            double distance = Math.sqrt(
                    Math.pow(playerPos.getX() - lastRoadPos.getX(), 2) +
                            Math.pow(playerPos.getY() - lastRoadPos.getY(), 2) +
                            Math.pow(playerPos.getZ() - lastRoadPos.getZ(), 2));

            if (distance <= ROAD_AREA_RADIUS) {
                // 在道路区域内，检查区域内是否有道路
                if (checkRoadInArea(player.level(), playerPos, detector, ROAD_AREA_RADIUS)) {
                    return true;
                }
            }
        }

        // 方法5：检查玩家周围水平2格范围内是否有道路（处理在道路边缘移动）
        return checkRoadInArea(player.level(), playerPos, detector, 2);
    }

    /**
     * 检查指定区域内的道路方块
     */
    private static boolean checkRoadInArea(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos center,
            RoadDetectionFactory.IRoadDetector detector, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) { // 垂直方向只检查上下1格
                for (int z = -radius; z <= radius; z++) {
                    net.minecraft.core.BlockPos checkPos = center.offset(x, y, z);

                    // 跳过流体和危险方块
                    if (isFluidOrDangerousBlock(level, checkPos)) {
                        continue;
                    }

                    if (detector.isOnRoad(level, checkPos)) {
                        // 找到道路，记录位置
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 检查方块是否是流体或危险方块（岩浆等）
     */
    private static boolean isFluidOrDangerousBlock(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

        // 检查是否是空气
        if (state.isAir()) {
            return false; // 空气不是流体，但我们会在其他逻辑中处理
        }

        // 检查是否是流体（水、岩浆等）
        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        // 检查是否是危险方块（岩浆块、仙人掌等）
        // 岩浆块
        if (state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) ||
                state.is(net.minecraft.world.level.block.Blocks.LAVA) ||
                state.is(net.minecraft.world.level.block.Blocks.CACTUS) ||
                state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) ||
                state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)) {
            return true;
        }

        return false;
    }

    /**
     * 检查玩家是否在移动（包括上下坡）
     */
    private static boolean isPlayerMoving(Player player, long currentTime) {
        // 检查玩家是否在移动（水平或垂直）
        boolean isMovingHorizontally = player.getDeltaMovement().x != 0 || player.getDeltaMovement().z != 0;
        boolean isMovingVertically = player.getDeltaMovement().y != 0;

        // 检查玩家是否在地面上（或附近）
        boolean isNearGround = isPlayerNearGround(player);

        // 玩家在移动，或者虽然垂直移动但接近地面（上下坡情况）
        return isMovingHorizontally || (isMovingVertically && isNearGround);
    }

    /**
     * 检查玩家是否接近地面
     */
    private static boolean isPlayerNearGround(Player player) {
        // 向下检查最多2格是否有固体方块
        for (int i = 0; i <= 2; i++) {
            net.minecraft.core.BlockPos belowPos = player.blockPosition().below(i);
            net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(belowPos);

            // 跳过流体
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }

            // 如果有非空气非流体方块，认为接近地面
            return true;
        }
        return false;
    }

    /**
     * 检查移动状态，更新位置记录
     */
    private static void checkMovementStatus(Player player, long currentTime) {
        // 记录当前位置
        net.minecraft.core.BlockPos currentPos = player.blockPosition();
        net.minecraft.core.BlockPos lastPos = lastPositionMap.get(player);

        // 如果玩家移动了位置，更新记录
        if (lastPos == null || !lastPos.equals(currentPos)) {
            lastPositionMap.put(player, currentPos);
        }
    }

    /**
     * 监听玩家装备变更事件
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        // 确保是玩家 - 使用传统instanceof写法
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (player.level().isClientSide)
            return;

        // 确保是脚部装备变更
        if (event.getSlot() != EquipmentSlot.FEET)
            return;

        ItemStack fromItem = event.getFrom(); // 原来的靴子
        ItemStack toItem = event.getTo(); // 新的靴子

        // 检查原来的靴子是否有附魔
        boolean oldHadEnchantment = false;
        int oldEnchantLevel = 0;
        if (!fromItem.isEmpty()) {
            oldEnchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                    EnchantmentRegistry.QIANMO_SWIFTJOURNEY.get(),
                    fromItem);
            oldHadEnchantment = oldEnchantLevel > 0;
        }

        // 检查新的靴子是否有附魔
        boolean newHasEnchantment = false;
        int newEnchantLevel = 0;
        if (!toItem.isEmpty()) {
            newEnchantLevel = EnchantmentHelper.getTagEnchantmentLevel(
                    EnchantmentRegistry.QIANMO_SWIFTJOURNEY.get(),
                    toItem);
            newHasEnchantment = newEnchantLevel > 0;
        }

        // 获取玩家当前是否有速度加成
        boolean hasModifier = hasSpeedModifier(player);

        // 情况1: 脱下了有附魔的靴子（无论换上什么）
        if (oldHadEnchantment && hasModifier) {
            removeSpeedModifier(player);

            if (SpeedModConfig.isDebugMessagesEnabled()) {
                String action = toItem.isEmpty() ? "脱下靴子" : "更换靴子";
                QianmoSpeedMod.LOGGER.debug("- 装备变更移除速度加成: 玩家={} ({}, 旧等级={})",
                        player.getName().getString(), action, oldEnchantLevel);
            }
        }

        // 更新靴子记录
        lastBootsMap.put(player, toItem.copy());

        // 更换装备时清除所有状态
        lastOnRoadTimeMap.remove(player);
        lastRoadPositionMap.remove(player);
    }

    private static boolean hasSpeedModifier(Player player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return attribute != null && attribute.getModifier(SPEED_MODIFIER_ID) != null;
    }

    private static void applySpeedModifier(Player player, int enchantLevel) {
        removeSpeedModifier(player);

        // 从配置读取速度加成
        double speedMultiplier = SpeedModConfig.getSpeedMultiplier(enchantLevel);
        double speedBonus = speedMultiplier - 1.0;

        AttributeModifier speedModifier = new AttributeModifier(
                SPEED_MODIFIER_ID,
                "qianmospeed.road_speed_bonus",
                speedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.addPermanentModifier(speedModifier);
        }
    }

    private static void removeSpeedModifier(Player player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(SPEED_MODIFIER_ID);
        }
        // 移除所有状态记录
        lastOnRoadTimeMap.remove(player);
        lastRoadPositionMap.remove(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 传统写法
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (!player.level().isClientSide) {
            // 初始化各种记录
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            lastBootsMap.put(player, boots.copy());
            lastPositionMap.put(player, player.blockPosition());

            if (SpeedModConfig.isLoginMessagesEnabled()) {
                if (!isRoadWeaverLoaded()) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("qianmospeed.message.login.basic",
                                    SpeedModConfig.getCheckInterval()),
                            false);

                    // 可选：显示 RoadWeaver 未找到的消息
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component
                                    .translatable("qianmospeed.message.roadweaver.not_found"),
                            false);
                } else {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("qianmospeed.message.login.advanced"),
                            false);
                }
            }

            if (SpeedModConfig.isDebugMessagesEnabled()) {
                int basicCount = SpeedModConfig.getBasicRoadBlocks().size();
                int advancedCount = SpeedModConfig.getAdvancedRoadBlocks().size();

                QianmoSpeedMod.LOGGER.info("玩家登录: {}, RoadWeaver={}, 检测间隔={}ticks, 基础方块={}, 高级方块={}",
                        player.getName().getString(),
                        isRoadWeaverLoaded(),
                        SpeedModConfig.getCheckInterval(),
                        basicCount,
                        advancedCount);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 传统写法
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // 移除速度加成
        removeSpeedModifier(player);

        // 清理所有记录
        lastBootsMap.remove(player);
        lastOnRoadTimeMap.remove(player);
        lastPositionMap.remove(player);
        lastRoadPositionMap.remove(player);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.info("玩家退出: {} (清理状态)", player.getName().getString());
        }
    }

    /**
     * 监听玩家重生事件
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 传统写法
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (player.level().isClientSide)
            return;

        // 重生时移除速度加成
        removeSpeedModifier(player);

        // 重置所有记录
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        lastBootsMap.put(player, boots.copy());
        lastPositionMap.put(player, player.blockPosition());
        lastOnRoadTimeMap.remove(player);
        lastRoadPositionMap.remove(player);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.info("玩家重生: {} (重置状态)", player.getName().getString());
        }
    }

    /**
     * 监听玩家维度切换事件
     */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // 传统写法
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (player.level().isClientSide)
            return;

        // 切换维度时移除速度加成
        removeSpeedModifier(player);

        // 重置所有记录
        lastPositionMap.put(player, player.blockPosition());
        lastOnRoadTimeMap.remove(player);
        lastRoadPositionMap.remove(player);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.info("玩家切换维度: {} -> {} (重置速度加成)",
                    player.getName().getString(),
                    event.getTo().location().toString());
        }
    }
}