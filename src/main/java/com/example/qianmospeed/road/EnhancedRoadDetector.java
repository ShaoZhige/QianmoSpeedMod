package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.Map;

public class EnhancedRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final Map<BlockPos, Boolean> cache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== EnhancedRoadDetector ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }

        // 检查缓存
        if (cache.containsKey(pos)) {
            boolean cached = cache.get(pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用缓存结果: {}", cached);
            }
            return cached;
        }

        // 1. 首先检查方块是否在高级列表中
        if (!isAdvancedRoadBlock(level, pos)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                String blockId = getBlockId(level, pos);
                QianmoSpeedMod.LOGGER.debug("方块 {} 不在高级道路列表中", blockId);
            }

            // 更新缓存
            if (cache.size() >= CACHE_SIZE) {
                cache.clear();
            }
            cache.put(pos, false);
            return false;
        }

        // ========== ⭐⭐⭐ 核心修复：高级模式必须通过基础检测器的验证 ⭐⭐⭐ ==========
        // 不能只靠方块列表，必须满足基础检测器的所有条件！
        boolean result = basicDetector.isOnRoad(level, pos);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            String blockId = getBlockId(level, pos);
            QianmoSpeedMod.LOGGER.debug("方块ID: {}, 高级模式检测: {}", blockId, result);
            QianmoSpeedMod.LOGGER.debug("==========================================");
        }

        // 更新缓存
        if (cache.size() >= CACHE_SIZE) {
            cache.clear();
        }
        cache.put(pos, result);

        return result;
    }

    /**
     * 检查是否是高级模式道路方块
     */
    private boolean isAdvancedRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isAdvancedRoadBlock(block);
    }

    /**
     * 获取方块ID
     */
    private String getBlockId(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        cache.clear();
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("EnhancedRoadDetector 缓存已清理");
        }
    }

    /**
     * 统计相邻道路方块数量（东南西北4个方向）
     */
    private int countAdjacentRoadBlocks(Level level, BlockPos pos) {
        int count = 0;
        BlockPos[] adjacentPositions = {
                pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos adjPos : adjacentPositions) {
            if (isAdvancedRoadBlock(level, adjPos)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计周围道路方块数量（3x3区域，不包括中心）
     */
    private int countSurroundingRoadBlocks(Level level, BlockPos pos) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;
                if (isAdvancedRoadBlock(level, pos.offset(dx, 0, dz))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 检查是否是不完整方块
     */
    private boolean isIncompleteBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        return blockId.contains("slab") ||
                blockId.contains("stairs") ||
                blockId.contains("carpet") ||
                blockId.contains("snow") ||
                blockId.contains("layer") ||
                blockId.contains("farmland") ||
                blockId.contains("path");
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
                blockId.contains("mycelium");
    }

    /**
     * 检查是否形成线性道路
     */
    private boolean checkFormsLinearRoad(Level level, BlockPos pos) {
        int xLength = 1;
        xLength += checkDirectionSameType(level, pos, true, true, getBlockId(level, pos));
        xLength += checkDirectionSameType(level, pos, true, false, getBlockId(level, pos));

        int zLength = 1;
        zLength += checkDirectionSameType(level, pos, false, true, getBlockId(level, pos));
        zLength += checkDirectionSameType(level, pos, false, false, getBlockId(level, pos));

        int minLength = SpeedModConfig.getMinDirectionalLength();
        return xLength >= minLength || zLength >= minLength;
    }

    /**
     * 检查单个方向（相同类型方块）
     */
    private int checkDirectionSameType(Level level, BlockPos startPos, boolean checkX,
            boolean positive, String targetBlockId) {
        int length = 0;
        int direction = positive ? 1 : -1;
        int maxCheck = SpeedModConfig.getMaxDirectionalLength() * 2;
        BlockPos currentPos = startPos;

        for (int i = 1; i <= maxCheck; i++) {
            if (checkX) {
                currentPos = currentPos.offset(direction, 0, 0);
            } else {
                currentPos = currentPos.offset(0, 0, direction);
            }

            // 检查边界
            if (!level.isLoaded(currentPos)) {
                break;
            }

            BlockState state = level.getBlockState(currentPos);
            String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();

            if (!blockId.equals(targetBlockId)) {
                break;
            }
            length++;
        }
        return length;
    }

    /**
     * 路径方块严格检测
     */
    private boolean checkPathBlockStrict(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 路径方块严格检查: 位置 {}", pos);
        }

        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
        String blockId = getBlockId(level, pos);
        boolean isDirtPath = blockId.contains("dirt_path");

        if (isDirtPath) {
            if (adjacentRoads >= 2) {
                boolean formsLine = checkFormsLinearRoad(level, pos);
                if (formsLine) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("  → 土径形成线性道路，通过");
                    }
                    return true;
                }
            }
            int surroundingRoads = countSurroundingRoadBlocks(level, pos);
            if (surroundingRoads >= 6) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  → 土径被 {} 个道路方块包围，通过", surroundingRoads);
                }
                return true;
            }
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 土径不满足条件（相邻={}, 形成线性={}），不通过",
                        adjacentRoads, checkFormsLinearRoad(level, pos));
            }
            return false;
        } else {
            if (adjacentRoads >= 2) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  → 有 {} 个相邻道路方块（≥2），通过", adjacentRoads);
                }
                return true;
            }
            int surroundingRoads = countSurroundingRoadBlocks(level, pos);
            if (surroundingRoads >= 6) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  → 周围有 {} 个道路方块（≥6），通过", surroundingRoads);
                }
                return true;
            }
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 周围道路方块不足（相邻={}, 周围={}），不通过",
                        adjacentRoads, surroundingRoads);
            }
            return false;
        }
    }

    /**
     * 自然方块严格检查
     */
    private boolean checkNaturalBlockStrict(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 自然方块严格检查开始");
        }

        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
        if (adjacentRoads >= 4) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 有 {} 个相邻道路方块（≥4），通过", adjacentRoads);
            }
            return true;
        }

        int surroundingRoads = countSurroundingRoadBlocks(level, pos);
        if (surroundingRoads >= 8) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 周围有 {} 个道路方块（≥8），通过", surroundingRoads);
            }
            return true;
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 不满足道路特征（相邻={}, 周围={}），不通过",
                    adjacentRoads, surroundingRoads);
        }
        return false;
    }

    /**
     * 检查标准方块
     */
    private boolean checkStandardBlock(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("标准方块检查开始: 位置 {}", pos);
        }

        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
        if (adjacentRoads >= 2) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测: 有 {} 个相邻道路方块（≥2），通过", adjacentRoads);
            }
            return true;
        }
        int surroundingRoads = countSurroundingRoadBlocks(level, pos);
        if (surroundingRoads >= 6) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测: 周围有 {} 个道路方块（≥6），通过", surroundingRoads);
            }
            return true;
        }
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("标准方块检测: 周围道路方块不足（相邻={}, 周围={}），不通过",
                    adjacentRoads, surroundingRoads);
        }
        return false;
    }

    // 辅助方法
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        return basicDetector.isBasicRoadBlock(level, pos);
    }
}