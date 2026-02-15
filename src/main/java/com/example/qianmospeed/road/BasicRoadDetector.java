package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BasicRoadDetector implements RoadDetectionFactory.IRoadDetector {

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        return isOnRoad(level, pos, false);
    }

    /**
     * 增强版：支持积极检测模式（仅用于规划中的区块）
     * 
     * @param aggressive true: 放宽检测条件（用于规划中的区块）
     */
    public boolean isOnRoad(Level level, BlockPos pos, boolean aggressive) {
        // 1. 基础检查：是不是道路方块
        if (!isRoadBlock(level, pos)) {
            return false;
        }

        // 2. 方向检测（如果启用）
        if (SpeedModConfig.isDirectionalDetectionEnabled()) {
            return isDirectionalRoad(level, pos, aggressive);
        }

        return true;
    }

    /**
     * ========== 新方向检测逻辑 ==========
     * 规则：
     * 1. 如果两个方向都超过最大值 → false（广场/地板）
     * 2. 否则，只要有一个方向在最小~最大范围内 → true（道路）
     * 3. 否则 false
     */
    private boolean isDirectionalRoad(Level level, BlockPos pos, boolean aggressive) {
        int xLength = calculateDirectionalLength(level, pos, true);
        int zLength = calculateDirectionalLength(level, pos, false);

        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方向检测 - 位置: {}, X长度: {}, Z长度: {} (要求: 最小{}x{}, 最大{}x{})",
                    pos, xLength, zLength, minLength, minLength, maxLength, maxLength);
        }

        // ========== 积极模式：放宽检测条件（用于规划中的区块）==========
        if (aggressive) {
            int aggressiveMin = Math.max(1, minLength / 2);
            int aggressiveMax = maxLength * 2;

            boolean xMeetsMin = xLength >= aggressiveMin;
            boolean zMeetsMin = zLength >= aggressiveMin;
            boolean xWithinMax = xLength <= aggressiveMax;
            boolean zWithinMax = zLength <= aggressiveMax;

            // 积极模式下仍然沿用新逻辑：至少一个方向在放宽后的范围内
            if (xWithinMax && zWithinMax) {
                // 如果两个方向都未超过放宽的最大值
                // 只要有一个方向满足最小长度就通过
                if (xMeetsMin || zMeetsMin) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("  积极模式判定: 道路 (X={}, Z={}, 要求: 最小{})",
                                xLength, zLength, aggressiveMin);
                    }
                    return true;
                }
            } else if (xWithinMax || zWithinMax) {
                // 只有一个方向在放宽的最大值内，且该方向满足最小长度
                if ((xWithinMax && xMeetsMin) || (zWithinMax && zMeetsMin)) {
                    return true;
                }
            }
            return false;
        }

        // ========== 标准模式：新逻辑 ==========

        // 规则1：如果两个方向都超过最大值 → 广场/地板
        if (xLength > maxLength && zLength > maxLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 广场/地板 (两个方向都超过最大值)");
            }
            return false;
        }

        // 规则2：至少有一个方向在最小~最大范围内 → 道路
        boolean xValid = xLength >= minLength && xLength <= maxLength;
        boolean zValid = zLength >= minLength && zLength <= maxLength;

        if (xValid || zValid) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 有效道路 (X有效={}, Z有效={})", xValid, zValid);
            }
            return true;
        }

        // 规则3：其他情况（例如两个方向都小于最小值，或一个太小一个太大但都不在范围内）
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  判定: 非道路 (X={}, Z={})", xLength, zLength);
        }
        return false;
    }

    /**
     * 计算一个方向上的连续道路长度
     */
    private int calculateDirectionalLength(Level level, BlockPos pos, boolean checkX) {
        int totalLength = 1;

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
        int maxCheck = SpeedModConfig.getMaxDirectionalLength() * 3;

        BlockPos currentPos = startPos;

        for (int i = 1; i <= maxCheck; i++) {
            if (checkX) {
                currentPos = currentPos.offset(direction, 0, 0);
            } else {
                currentPos = currentPos.offset(0, 0, direction);
            }

            // 检查边界，防止越界
            if (!level.isLoaded(currentPos)) {
                break;
            }

            if (!isRoadBlock(level, currentPos)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 检查是否是道路方块
     */
    private boolean isRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isBasicRoadBlock(block);
    }

    /**
     * 公开方法：检查是否是基础模式道路方块
     */
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isBasicRoadBlock(block);
    }
}