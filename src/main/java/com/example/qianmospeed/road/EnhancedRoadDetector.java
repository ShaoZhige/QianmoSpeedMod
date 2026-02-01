package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public class EnhancedRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    // 简单缓存，提高性能
    private final Map<BlockPos, Boolean> simpleCache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        // 🔍 添加入口日志
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== EnhancedRoadDetector.isOnRoad ==========");
            QianmoSpeedMod.LOGGER.debug("检查位置: {}", pos);
        }
        // 1. 首先检查是否是道路方块
        if (!isAdvancedRoadBlock(level, pos)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                String blockId = getBlockId(level, pos);
                QianmoSpeedMod.LOGGER.debug("位置 {} 的方块 {} 不在高级道路列表中", pos, blockId);
            }
            return false;
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            String blockId = getBlockId(level, pos);
            QianmoSpeedMod.LOGGER.debug("位置 {} 的方块 {} 在高级道路列表中 ✓", pos, blockId);
        }

        // 2. 检查缓存
        if (simpleCache.containsKey(pos)) {
            boolean cached = simpleCache.get(pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用缓存结果: {}", cached);
            }
            return cached;
        }

        // 3. 根据方块类型选择检测方法
        String blockId = getBlockId(level, pos);
        boolean isPathBlock = blockId.contains("path");
        boolean isNaturalBlock = isNaturalBlockType(blockId);
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方块类型判断: 路径方块={}, 自然方块={}", isPathBlock, isNaturalBlock);
        }
        boolean result;
        if (isPathBlock) {
            // 土径等路径方块：简化检测
            result = checkPathBlock(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("路径方块检测结果: {}", result);
            }
        } else if (isNaturalBlock) {
            // 自然方块（土、砂、砾石等）：宽松检测
            result = checkNaturalBlockSimple(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("自然方块检测结果: {}", result);
            }
        } else {
            // 其他方块：标准检测
            result = checkStandardBlock(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测结果: {}", result);
            }
        }

        // 4. 对于完整方块，应用方向检测（超大连接判断）
        if (result && isFullHeightBlock(level, pos)) {
            boolean beforeDirectional = result;
            result = applyDirectionalDetection(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("方向检测: 之前={}, 之后={}", beforeDirectional, result);
            }
        }

        // 5. 更新缓存
        if (simpleCache.size() >= CACHE_SIZE) {
            simpleCache.clear();
        }
        simpleCache.put(pos, result);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("高级检测最终结果: 位置={}, 方块={}, 结果={} (类型: {})",
                    pos, blockId, result,
                    isPathBlock ? "路径" : (isNaturalBlock ? "自然" : "标准"));
            QianmoSpeedMod.LOGGER.debug("==================================================");
        }

        return result;
    }

    /**
     * 应用方向检测（超大连接判断）
     * 使用配置中的最小和最大道路宽度
     */
    private boolean applyDirectionalDetection(Level level, BlockPos pos) {
        // 只有在配置中启用了方向检测才应用
        if (!SpeedModConfig.isDirectionalDetectionEnabled()) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("方向检测未启用，跳过");
            }
            return true;
        }

        // 检查X方向连续长度
        int xLength = calculateDirectionalLength(level, pos, true);
        // 检查Z方向连续长度
        int zLength = calculateDirectionalLength(level, pos, false);
        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("高级方向检测 - 位置: {}, X长度: {}, Z长度: {} (范围: {}-{})",
                    pos, xLength, zLength, minLength, maxLength);
        }
        // 使用与基础模式相同的逻辑：
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
            if (!isAdvancedRoadBlock(level, currentPos) || !isFullHeightBlock(level, currentPos)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 检查是否是完整高度的方块
     */
    private boolean isFullHeightBlock(Level level, BlockPos pos) {
        String blockId = getBlockId(level, pos);
        // 不完整方块的特征
        boolean isIncomplete = blockId.contains("slab") ||
                blockId.contains("stairs") ||
                blockId.contains("carpet") ||
                blockId.contains("snow") ||
                blockId.contains("layer") ||
                blockId.contains("farmland") ||
                blockId.contains("path"); // 土径也是不完整方块
        return !isIncomplete;
    }

    /**
     * 获取方块ID
     */
    private String getBlockId(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
    }

    /**
     * 检查是否是自然方块类型
     */
    private boolean isNaturalBlockType(String blockId) {
        // 常见自然方块类型
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
                blockId.contains("mycelium");
    }

    /**
     * 检查路径方块（土径等）- 最宽松
     */
    private boolean checkPathBlock(Level level, BlockPos pos) {
        // 路径方块总是被认为是道路
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("路径方块检查: 位置 {} 是路径方块，直接返回 true", pos);
        }
        return true;
    }

    /**
     * 检查自然方块 - 极简版本
     */
    private boolean checkNaturalBlockSimple(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== 自然方块检测开始 ==========");
            QianmoSpeedMod.LOGGER.debug("检查位置: {}", pos);
            String blockId = getBlockId(level, pos);
            QianmoSpeedMod.LOGGER.debug("方块ID: {}", blockId);
        }
        // 自然方块直接返回 true
        // 理由：
        // 1. 已经通过 isAdvancedRoadBlock() 检查，确认是配置中的道路方块
        // 2. 高级模式的目的是更宽松的检测，不应该再添加额外限制
        // 3. 方向检测会在后续的 applyDirectionalDetection() 中进行
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("自然方块检测: 直接返回 true（已通过高级方块列表检查）");
            QianmoSpeedMod.LOGGER.debug("==========================================");
        }
        return true;
    }

    /**
     * 检查标准方块 - 严格检测，只判定真正在道路上的方块
     */
    private boolean checkStandardBlock(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== 标准方块检测开始 ==========");
            QianmoSpeedMod.LOGGER.debug("检查位置: {}", pos);
        }
        // 检查相邻方块（四方向）
        int adjacentRoads = 0;
        BlockPos[] adjacentPositions = {
                pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos adjPos : adjacentPositions) {
            if (isAdvancedRoadBlock(level, adjPos)) {
                adjacentRoads++;
            }
        }
        // 需要至少1个相邻道路方块
        if (adjacentRoads >= 1) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测: 位置 {} 有 {} 个相邻道路方块（≥1）",
                        pos, adjacentRoads);
            }
            return true;
        }
        // 条件2：检查3x3区域
        int roadCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (isAdvancedRoadBlock(level, pos.offset(dx, 0, dz))) {
                    roadCount++;
                }
            }
        }
        if (roadCount >= 6) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测: 位置 {} 周围有 {} 个道路方块（≥6）",
                        pos, roadCount);
            }
            return true;
        }
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("标准方块检测: 位置 {} 未通过检测（相邻={}, 周围={}）",
                    pos, adjacentRoads, roadCount);
        }
        return false;
    }

    /**
     * 检查是否是高级模式道路方块
     */
    private boolean isAdvancedRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        boolean isRoad = SpeedModConfig.isAdvancedRoadBlock(block);
        // 🔍 添加详细调试日志（仅在非常详细的调试模式下）
        // 注释掉以减少日志量，需要时可以取消注释
        /*
         * if (SpeedModConfig.isDebugMessagesEnabled()) {
         * String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
         * QianmoSpeedMod.LOGGER.debug("高级方块检查: 位置={}, 方块={}, 是道路={}",
         * pos, blockId, isRoad);
         * }
         */
        return isRoad;
    }

    // 辅助方法
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        return basicDetector.isBasicRoadBlock(level, pos);
    }
}