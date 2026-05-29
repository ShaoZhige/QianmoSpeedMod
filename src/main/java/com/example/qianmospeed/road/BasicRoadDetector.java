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
     * 增强版：支持积极检测模式
     */
    public boolean isOnRoad(Level level, BlockPos pos, boolean aggressive) {
        return isOnRoad(level, pos,
                SpeedModConfig.getMinDirectionalLength(),
                SpeedModConfig.getMaxDirectionalLength(),
                aggressive);
    }

    /**
     * 完全自定义版：自定义最小/最大长度
     * 用于路网模式（最小长度1，无上限）
     */
    public boolean isOnRoad(Level level, BlockPos pos, int customMinLength, int customMaxLength) {
        return isOnRoad(level, pos, customMinLength, customMaxLength, false);
    }

    /**
     * 核心检测方法：自定义所有参数
     */
    public boolean isOnRoad(Level level, BlockPos pos, int minLength, int maxLength, boolean aggressive) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        boolean isBasic = SpeedModConfig.isBasicRoadBlock(block);
        String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("基础检测 - 方块: {}, 在基础列表: {}", blockId, isBasic);
        }

        if (!isBasic) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("基础检测: 方块不在基础列表中，不通过");
            }
            return false;
        }

        if (SpeedModConfig.isDirectionalDetectionEnabled()) {
            return isDirectionalRoad(level, pos, minLength, maxLength, aggressive);
        }

        return true;
    }

    /**
     * 方向检测逻辑
     */
    private boolean isDirectionalRoad(Level level, BlockPos pos, int minLength, int maxLength, boolean aggressive) {
        int xLength = calculateDirectionalLength(level, pos, true, maxLength);
        int zLength = calculateDirectionalLength(level, pos, false, maxLength);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方向检测 - 位置: {}, X长度: {}, Z长度: {} (要求: 最小{}x{}, 最大{}x{})",
                    pos, xLength, zLength, minLength, minLength, maxLength, maxLength);
        }

        if (aggressive) {
            int aggressiveMin = Math.max(1, minLength / 2);
            int aggressiveMax = maxLength * 2;

            boolean xMeetsMin = xLength >= aggressiveMin;
            boolean zMeetsMin = zLength >= aggressiveMin;
            boolean xWithinMax = xLength <= aggressiveMax;
            boolean zWithinMax = zLength <= aggressiveMax;

            if (xWithinMax && zWithinMax) {
                if (xMeetsMin || zMeetsMin) return true;
            } else if (xWithinMax || zWithinMax) {
                if ((xWithinMax && xMeetsMin) || (zWithinMax && zMeetsMin)) return true;
            }
            return false;
        }

        // 标准模式
        if (xLength > maxLength && zLength > maxLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 广场/地板 (两个方向都超过最大值)");
            }
            return false;
        }

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

    private int calculateDirectionalLength(Level level, BlockPos pos, boolean checkX, int maxLength) {
        int totalLength = 1;
        totalLength += checkDirection(level, pos, checkX, true, maxLength);
        totalLength += checkDirection(level, pos, checkX, false, maxLength);
        return totalLength;
    }

    private int checkDirection(Level level, BlockPos startPos, boolean checkX, boolean positive, int maxLength) {
        int length = 0;
        int direction = positive ? 1 : -1;
        int maxCheck = maxLength * 3;
        BlockPos currentPos = startPos;

        for (int i = 1; i <= maxCheck; i++) {
            currentPos = checkX ? currentPos.offset(direction, 0, 0) : currentPos.offset(0, 0, direction);

            if (!level.isLoaded(currentPos)) break;

            BlockState state = level.getBlockState(currentPos);
            if (!SpeedModConfig.isBasicRoadBlock(state.getBlock())) break;

            length++;
        }
        return length;
    }

    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        return SpeedModConfig.isBasicRoadBlock(level.getBlockState(pos).getBlock());
    }
}