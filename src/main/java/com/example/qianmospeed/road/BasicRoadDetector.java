package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public class BasicRoadDetector implements RoadDetectionFactory.IRoadDetector {

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        return isOnRoad(level, pos, false);
    }

    /**
     * 增强版：支持积极检测模式（仅用于规划中的区块）
     */
    public boolean isOnRoad(Level level, BlockPos pos, boolean aggressive) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // ⭐⭐⭐ 关键修复：强制使用基础列表 ⭐⭐⭐
        boolean isBasic = SpeedModConfig.isBasicRoadBlock(block);
        String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("基础检测 - 方块: {}, 在基础列表: {}", blockId, isBasic);
        }

        // 如果不在基础列表中，直接返回 false
        // 草方块、沙子等虽然在高级列表，但不应该通过基础检测
        if (!isBasic) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("基础检测: 方块不在基础列表中，不通过");
            }
            return false;
        }

        // 方向检测（如果启用）
        if (SpeedModConfig.isDirectionalDetectionEnabled()) {
            return isDirectionalRoad(level, pos, aggressive);
        }

        return true;
    }

    /**
     * 方向检测逻辑
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

        // 积极模式
        if (aggressive) {
            int aggressiveMin = Math.max(1, minLength / 2);
            int aggressiveMax = maxLength * 2;

            boolean xMeetsMin = xLength >= aggressiveMin;
            boolean zMeetsMin = zLength >= aggressiveMin;
            boolean xWithinMax = xLength <= aggressiveMax;
            boolean zWithinMax = zLength <= aggressiveMax;

            if (xWithinMax && zWithinMax) {
                if (xMeetsMin || zMeetsMin) {
                    return true;
                }
            } else if (xWithinMax || zWithinMax) {
                if ((xWithinMax && xMeetsMin) || (zWithinMax && zMeetsMin)) {
                    return true;
                }
            }
            return false;
        }

        // 标准模式
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

        if (xValid && zValid) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 有效道路 (X有效={}, Z有效={})", xValid, zValid);
            }
            return true;
        }

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

            if (!level.isLoaded(currentPos)) {
                break;
            }

            // ⭐⭐⭐ 修复：这里也要用基础列表 ⭐⭐⭐
            BlockState state = level.getBlockState(currentPos);
            Block block = state.getBlock();
            if (!SpeedModConfig.isBasicRoadBlock(block)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 公开方法：检查是否是基础模式道路方块
     */
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return SpeedModConfig.isBasicRoadBlock(block);
    }
}