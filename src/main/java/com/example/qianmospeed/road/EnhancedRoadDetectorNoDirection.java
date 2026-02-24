package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EnhancedRoadDetectorNoDirection implements RoadDetectionFactory.IRoadDetector {

    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final Map<BlockPos, Boolean> simpleCache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== EnhancedRoadDetectorNoDirection ==========");
            QianmoSpeedMod.LOGGER.debug("检查位置: {}", pos);
        }

        if (simpleCache.containsKey(pos)) {
            return simpleCache.get(pos);
        }

        // ========== ⭐⭐⭐ 第一步：基础检测 ⭐⭐⭐ ==========
        boolean basicResult = basicDetector.isOnRoad(level, pos, false);

        if (basicResult) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("基础检测通过，直接判定为道路");
            }
            simpleCache.put(pos, true);
            return true;
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("基础检测未通过，进行密度检查");
        }

        // ========== ⭐⭐⭐ 第二步：密度检查 ⭐⭐⭐ ==========
        DensityCheckResult densityResult = checkDensity(level, pos);

        if (!densityResult.passed) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("密度检查不通过: 高级方块数={}, 不同方块种类={}, 要求至少6个且至少2种",
                        densityResult.count, densityResult.variety);
            }
            simpleCache.put(pos, false);
            return false;
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("密度检查通过: 高级方块数={}, 不同方块种类={}",
                    densityResult.count, densityResult.variety);
        }

        // ========== 第三步：高级检测 ==========
        int xLength = calculateContinuousRoadLength(level, pos, true);
        int zLength = calculateContinuousRoadLength(level, pos, false);
        int surroundingCount = countSurroundingRoadBlocks(level, pos);

        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        int minLength = SpeedModConfig.getMinDirectionalLength();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("高级检测数据 - X方向长度: {}, Z方向长度: {}, 周围道路方块: {}",
                    xLength, zLength, surroundingCount);
        }

        // 规则1：最大长度约束
        if (xLength > maxLength && zLength > maxLength) {
            if (surroundingCount >= 6) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("规则1豁免：密集道路区域");
                }
                simpleCache.put(pos, true);
                return true;
            }
            simpleCache.put(pos, false);
            return false;
        }

        // 规则2：线性道路
        boolean isLinearRoad = (xLength >= minLength) || (zLength >= minLength);

        // 规则3：被包围道路
        boolean isSurroundedRoad = surroundingCount >= 3;

        boolean result = isLinearRoad || isSurroundedRoad;

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            if (result) {
                QianmoSpeedMod.LOGGER.debug("✓ 高级检测通过 (规则2={}, 规则3={})", isLinearRoad, isSurroundedRoad);
            } else {
                QianmoSpeedMod.LOGGER.debug("✗ 高级检测未通过");
            }
        }

        if (simpleCache.size() >= CACHE_SIZE) {
            simpleCache.clear();
        }
        simpleCache.put(pos, result);

        return result;
    }

    /**
     * ⭐⭐⭐ 密度检查：3x3区域内高级方块数量 ≥ 6 且至少2种不同方块 ⭐⭐⭐
     */
    private DensityCheckResult checkDensity(Level level, BlockPos center) {
        int count = 0;
        Set<String> blockTypes = new HashSet<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos checkPos = center.offset(dx, 0, dz);

                // 只检查同一Y高度
                if (checkPos.getY() != center.getY())
                    continue;

                if (isAdvancedRoadBlock(level, checkPos)) {
                    count++;

                    // 记录方块类型
                    BlockState state = level.getBlockState(checkPos);
                    String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                    blockTypes.add(blockId);
                }
            }
        }

        boolean passed = count >= 6 && blockTypes.size() >= 2;
        return new DensityCheckResult(passed, count, blockTypes.size());
    }

    /**
     * 密度检查结果类
     */
    private static class DensityCheckResult {
        final boolean passed;
        final int count;
        final int variety;

        DensityCheckResult(boolean passed, int count, int variety) {
            this.passed = passed;
            this.count = count;
            this.variety = variety;
        }
    }

    /**
     * 计算连续道路长度（所有高级列表方块一视同仁）
     */
    private int calculateContinuousRoadLength(Level level, BlockPos pos, boolean checkX) {
        int totalLength = 1;

        totalLength += checkDirection(level, pos, checkX, true);
        totalLength += checkDirection(level, pos, checkX, false);

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
        int startY = startPos.getY();

        for (int i = 1; i <= maxCheck; i++) {
            if (checkX) {
                currentPos = currentPos.offset(direction, 0, 0);
            } else {
                currentPos = currentPos.offset(0, 0, direction);
            }

            if (!level.isLoaded(currentPos)) {
                break;
            }

            // 只检查同一Y高度
            if (Math.abs(currentPos.getY() - startY) > 1) {
                break;
            }

            if (!isAdvancedRoadBlock(level, currentPos)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 统计周围8格内的道路方块数量
     */
    private int countSurroundingRoadBlocks(Level level, BlockPos pos) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;

                BlockPos checkPos = pos.offset(dx, 0, dz);
                if (checkPos.getY() == pos.getY() && isAdvancedRoadBlock(level, checkPos)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 检查是否是高级模式道路方块
     */
    private boolean isAdvancedRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return SpeedModConfig.isAdvancedRoadBlock(block);
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        simpleCache.clear();
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("EnhancedRoadDetectorNoDirection 缓存已清理");
        }
    }
}