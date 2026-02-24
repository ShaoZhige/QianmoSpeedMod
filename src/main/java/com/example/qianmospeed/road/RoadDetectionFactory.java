package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.config.SpeedModConfig.RoadDetectionMode;
import com.example.qianmospeed.event.AdvancedRoadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RoadDetectionFactory {

    public interface IRoadDetector {
        boolean isOnRoad(Level level, BlockPos pos);
    }

    // ========== 缓存相关 ==========
    private static IRoadDetector cachedDetector = null;
    private static RoadDetectionMode lastMode = null;
    private static boolean lastAdvancedState = false;
    private static boolean lastRoadWeaverAvailable = false;

    /**
     * ⭐⭐⭐ 核心方法：根据方块类型动态判断是否在道路上 ⭐⭐⭐
     * 此方法绕过缓存，直接根据方块类型选择检测器
     */
    public static boolean isOnRoad(Level level, BlockPos pos) {
        if (level == null || pos == null)
            return false;

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== 动态道路检测 ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方块ID: {}", blockId);
        }

        // ========== 优先级1：检查是否在高级列表中 ==========
        boolean inAdvanced = SpeedModConfig.isAdvancedRoadBlock(block);

        if (inAdvanced) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("✓ 方块在高级列表中，使用高级检测器");
            }

            // 创建高级检测器（无方向检测版本，避免误判）
            EnhancedRoadDetectorNoDirection enhancedDetector = new EnhancedRoadDetectorNoDirection();
            boolean result = enhancedDetector.isOnRoad(level, pos);

            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("高级检测器结果: {}", result ? "是道路" : "非道路");
            }

            return result;
        }

        // ========== 优先级2：检查是否在基础列表中 ==========
        boolean inBasic = SpeedModConfig.isBasicRoadBlock(block);

        if (inBasic) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("✓ 方块在基础列表中，使用基础检测器");
            }

            BasicRoadDetector basicDetector = new BasicRoadDetector();

            // 检查是否启用RoadWeaver集成，启用则使用混合模式
            if (AdvancedRoadHandler.isAvailable() && level instanceof net.minecraft.server.level.ServerLevel) {
                // 使用混合检测器，传递区块规划状态
                boolean isPlanned = AdvancedRoadHandler.isPlannedChunk(
                        (net.minecraft.server.level.ServerLevel) level,
                        new net.minecraft.world.level.ChunkPos(pos));

                boolean result = basicDetector.isOnRoad(level, pos, isPlanned);

                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("混合检测器结果 (规划={}): {}",
                            isPlanned ? "是" : "否", result ? "是道路" : "非道路");
                }

                return result;
            } else {
                boolean result = basicDetector.isOnRoad(level, pos);

                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("基础检测器结果: {}", result ? "是道路" : "非道路");
                }

                return result;
            }
        }

        // ========== 不在任何列表中 ==========
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("✗ 方块不在任何道路列表中");
            QianmoSpeedMod.LOGGER.debug("======================================");
        }

        return false;
    }

    /**
     * 原有的 createDetector 方法保留，用于其他需要检测器实例的场景
     */
    public static IRoadDetector createDetector() {
        // 获取当前配置状态
        RoadDetectionMode currentMode = SpeedModConfig.getRoadDetectionMode();
        boolean currentAdvanced = SpeedModConfig.isAdvancedFeaturesEnabled() ||
                QianmoSpeedMod.hasDetectedProfessionalRoadMods();
        boolean currentRoadWeaverAvailable = AdvancedRoadHandler.isAvailable();

        // 检查配置是否变化
        boolean configChanged = cachedDetector == null ||
                currentMode != lastMode ||
                currentAdvanced != lastAdvancedState ||
                currentRoadWeaverAvailable != lastRoadWeaverAvailable;

        // 如果配置没变且缓存有效，直接返回缓存的检测器
        if (!configChanged) {
            return cachedDetector;
        }

        // 配置变化或首次创建，重新创建检测器
        QianmoSpeedMod.LOGGER.debug("================== 检测器创建 ==================");

        // 如果 RoadWeaver 可用，使用混合检测器
        if (currentRoadWeaverAvailable) {
            QianmoSpeedMod.LOGGER.debug(">>> 创建混合检测器 (RoadWeaver优先)");
            cachedDetector = new HybridRoadDetector();
        } else {
            switch (currentMode) {
                case BASIC:
                    QianmoSpeedMod.LOGGER.debug(">>> 创建基础道路检测器 (BASIC模式)");
                    cachedDetector = new BasicRoadDetector();
                    break;
                case ENHANCED:
                    if (currentAdvanced) {
                        QianmoSpeedMod.LOGGER.debug(">>> 创建增强道路检测器 (ENHANCED模式)");
                        cachedDetector = new EnhancedRoadDetectorNoDirection();
                    } else {
                        QianmoSpeedMod.LOGGER.debug(">>> 创建基础道路检测器 (ENHANCED模式但未启用高级功能)");
                        cachedDetector = new BasicRoadDetector();
                    }
                    break;
                case SMART:
                    QianmoSpeedMod.LOGGER.debug(">>> 创建智能道路检测器 (SMART模式)");
                    cachedDetector = new SmartRoadDetector();
                    break;
                default:
                    QianmoSpeedMod.LOGGER.debug(">>> 创建基础道路检测器 (默认)");
                    cachedDetector = new BasicRoadDetector();
                    break;
            }
        }

        QianmoSpeedMod.LOGGER.debug("最终检测器类型: {}", cachedDetector.getClass().getSimpleName());
        QianmoSpeedMod.LOGGER.debug("============================================");

        // 更新缓存状态
        lastMode = currentMode;
        lastAdvancedState = currentAdvanced;
        lastRoadWeaverAvailable = currentRoadWeaverAvailable;

        return cachedDetector;
    }

    /**
     * 强制清除缓存
     */
    public static void invalidateCache() {
        cachedDetector = null;
        QianmoSpeedMod.LOGGER.debug("道路检测器缓存已清除");
    }

    /**
     * 获取当前缓存的检测器
     */
    public static IRoadDetector getCachedDetector() {
        return cachedDetector;
    }
}