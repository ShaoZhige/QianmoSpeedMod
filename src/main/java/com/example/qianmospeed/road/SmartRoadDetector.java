package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public class SmartRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final EnhancedRoadDetector enhancedDetector = new EnhancedRoadDetector();

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== SmartRoadDetector 智能检测 ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }

        BlockState state = level.getBlockState(pos);
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();

        // 判断是否应该使用高级检测
        boolean useEnhanced = shouldUseEnhancedDetection(level, pos, blockId);

        boolean result;
        if (useEnhanced) {
            // 使用高级检测（仍然必须通过基础检测器的验证）
            result = enhancedDetector.isOnRoad(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("智能模式选择: 高级检测, 结果: {}", result);
            }
        } else {
            // 使用基础检测
            result = basicDetector.isOnRoad(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("智能模式选择: 基础检测, 结果: {}", result);
            }
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("==============================================");
        }

        return result;
    }

    /**
     * 判断是否应该使用高级检测
     */
    private boolean shouldUseEnhancedDetection(Level level, BlockPos pos, String blockId) {
        // 1. 检测到专业道路模组 -> 使用高级检测
        if (QianmoSpeedMod.hasDetectedProfessionalRoadMods()) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("检测到专业道路模组，使用高级检测");
            }
            return true;
        }

        // 2. 方块在高级列表中但不在基础列表中 -> 使用高级检测
        BlockState state = level.getBlockState(pos);
        boolean inAdvanced = SpeedModConfig.isAdvancedRoadBlock(state.getBlock());
        boolean inBasic = SpeedModConfig.isBasicRoadBlock(state.getBlock());

        if (inAdvanced && !inBasic) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("方块仅在高级列表中，使用高级检测");
            }
            return true;
        }

        // 3. 用户配置了自动启用高级模式
        if (SpeedModConfig.shouldAutoEnableAdvanced() && QianmoSpeedMod.hasDetectedRoadMods()) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("用户配置自动启用高级模式，使用高级检测");
            }
            return true;
        }

        // 4. 自然方块类型使用高级检测（更宽松）
        if (isNaturalBlockType(blockId)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("检测到自然方块 {}，使用高级检测", blockId);
            }
            return true;
        }

        // 5. 默认使用基础检测
        return false;
    }

    /**
     * 检查是否是自然方块类型
     */
    private boolean isNaturalBlockType(String blockId) {
        return blockId.contains("dirt") ||
                blockId.contains("gravel") ||
                blockId.contains("sand") ||
                blockId.contains("mud") ||
                blockId.contains("clay") ||
                blockId.contains("snow") ||
                blockId.contains("grass") ||
                blockId.contains("podzol") ||
                blockId.contains("moss") ||
                blockId.contains("terracotta") ||
                blockId.contains("mycelium") ||
                blockId.contains("stone") ||
                blockId.contains("cobblestone") ||
                blockId.contains("andesite") ||
                blockId.contains("diorite") ||
                blockId.contains("granite");
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        enhancedDetector.clearCache();
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("SmartRoadDetector 缓存已清理");
        }
    }
}