package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public class BasicRoadDetector implements RoadDetectionFactory.IRoadDetector {

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        // 1. 基础检查：是不是道路方块
        if (!isRoadBlock(level, pos)) {
            return false;
        }

        // 2. 检查方块是否完整高度
        boolean isFullBlock = isFullHeightBlock(level, pos);
        
        // 3. 如果启用了方向检测，完整方块需要方向检查
        if (SpeedModConfig.isDirectionalDetectionEnabled() && isFullBlock) {
            return isDirectionalRoad(level, pos);
        }
        
        // 4. 其他情况（未启用方向检测或不完整方块）直接通过
        return true;
    }

    /**
     * 检查是否是完整高度的方块
     */
    private boolean isFullHeightBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        
        // 根据方块ID判断是否是不完整方块
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        
        // 常见的不完整方块类型
        boolean isIncomplete = blockId.contains("slab") || 
                               blockId.contains("stairs") || 
                               blockId.contains("carpet") ||
                               blockId.contains("snow") ||
                               blockId.contains("layer") ||
                               blockId.contains("farmland") ||
                               blockId.contains("path");
        
        return !isIncomplete;
    }

    /**
     * 检查是否是道路方块
     */
    private boolean isRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();

        boolean isRoad = SpeedModConfig.isBasicRoadBlock(block);

        if (SpeedModConfig.isDebugMessagesEnabled() && isRoad) {
            String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
            QianmoSpeedMod.LOGGER.debug("基础检测 - 位置: {}, 方块: {}, 是道路方块",
                    pos, blockId);
        }

        return isRoad;
    }

    /**
     * 方向检测：改进的逻辑
     * 规则：只有X和Z方向都超过最大值才算地板
     *       只要有一个方向在有效范围内就是道路
     */
    private boolean isDirectionalRoad(Level level, BlockPos pos) {
        // 检查X方向连续长度
        int xLength = calculateDirectionalLength(level, pos, true);
        
        // 检查Z方向连续长度
        int zLength = calculateDirectionalLength(level, pos, false);
        
        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方向检测 - 位置: {}, X长度: {}, Z长度: {} (范围: {}-{})",
                    pos, xLength, zLength, minLength, maxLength);
        }
        
        // 新逻辑：
        // 1. 如果两个方向都超过最大值 → 是地板/广场
        if (xLength > maxLength && zLength > maxLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 地板/广场 (两个方向都超过最大值)");
            }
            return false;
        }
        
        // 2. 如果两个方向都小于最小值 → 可能是装饰
        if (xLength < minLength && zLength < minLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 装饰方块 (两个方向都小于最小值)");
            }
            return false;
        }
        
        // 3. 其他情况 → 是道路
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            if (xLength >= minLength && xLength <= maxLength && 
                zLength >= minLength && zLength <= maxLength) {
                QianmoSpeedMod.LOGGER.debug("  判定: 标准道路 (两个方向都在范围内)");
            } else if (xLength >= minLength && xLength <= maxLength) {
                QianmoSpeedMod.LOGGER.debug("  判定: X方向道路 (X方向在范围内)");
            } else if (zLength >= minLength && zLength <= maxLength) {
                QianmoSpeedMod.LOGGER.debug("  判定: Z方向道路 (Z方向在范围内)");
            } else {
                QianmoSpeedMod.LOGGER.debug("  判定: 不完全符合但接受");
            }
        }
        return true;
    }

    /**
     * 计算一个方向上的连续道路长度
     */
    private int calculateDirectionalLength(Level level, BlockPos pos, boolean checkX) {
        int totalLength = 1; // 包括当前位置

        // 检查正方向
        int positiveLength = checkDirection(level, pos, checkX, true);
        totalLength += positiveLength;

        // 检查负方向
        int negativeLength = checkDirection(level, pos, checkX, false);
        totalLength += negativeLength;

        return totalLength;
    }

    /**
     * 检查单个方向上的连续道路
     */
    private int checkDirection(Level level, BlockPos startPos, boolean checkX, boolean positive) {
        int length = 0;
        int direction = positive ? 1 : -1;
        int maxCheck = SpeedModConfig.getMaxDirectionalLength() * 3; // 扩大搜索范围

        BlockPos currentPos = startPos;

        for (int i = 1; i <= maxCheck; i++) {
            // 移动到下一个位置
            if (checkX) {
                currentPos = currentPos.offset(direction, 0, 0);
            } else {
                currentPos = currentPos.offset(0, 0, direction);
            }

            // 只检查完整方块的道路
            if (!isRoadBlock(level, currentPos) || !isFullHeightBlock(level, currentPos)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 检查是否是基础模式道路方块
     */
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isBasicRoadBlock(block);
    }

    /**
     * 检查是否是高级模式道路方块
     */
    public boolean isAdvancedRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isAdvancedRoadBlock(block);
    }
}