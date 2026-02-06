package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.Map;

/**
 * 增强道路检测器（无方向检测版本）
 * 专为混合检测器设计，避免方向检测导致的误判
 */
public class EnhancedRoadDetectorNoDirection implements RoadDetectionFactory.IRoadDetector {

    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    // 简单缓存，提高性能
    private final Map<BlockPos, Boolean> simpleCache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== EnhancedRoadDetectorNoDirection ==========");
            QianmoSpeedMod.LOGGER.debug("检查位置: {}", pos);
        }

        // 1. 首先检查是否是道路方块
        if (!isAdvancedRoadBlock(level, pos)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                String blockId = getBlockId(level, pos);
                QianmoSpeedMod.LOGGER.debug("方块 {} 不在高级道路列表中", blockId);
            }
            return false;
        }

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            String blockId = getBlockId(level, pos);
            QianmoSpeedMod.LOGGER.debug("方块 {} 在高级道路列表中 ✓", blockId);
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
            QianmoSpeedMod.LOGGER.debug("方块类型: 路径={}, 自然={}", isPathBlock, isNaturalBlock);
        }

        boolean result;
        if (isPathBlock) {
            // 🔧 修复：路径方块需要严格检测
            result = checkPathBlockStrict(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("路径方块检测结果: {}", result);
            }
        } else if (isNaturalBlock) {
            //修改：自然方块需要严格检查（无方向检测）
            result = checkNaturalBlockStrict(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("自然方块检测结果（严格）: {}", result);
            }
        } else {
            // 其他方块：标准检测
            result = checkStandardBlock(level, pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("标准方块检测结果: {}", result);
            }
        }

        // 4. 更新缓存
        if (simpleCache.size() >= CACHE_SIZE) {
            simpleCache.clear();
        }
        simpleCache.put(pos, result);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("最终结果: {}", result);
            QianmoSpeedMod.LOGGER.debug("==================================================");
        }

        return result;
    }

    /**
     * 🔧 修复：路径方块严格检测
     */
    private boolean checkPathBlockStrict(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 路径方块严格检查: 位置 {}", pos);
        }

        // 特别处理土径
        String blockId = getBlockId(level, pos);
        boolean isDirtPath = blockId.contains("dirt_path");
        
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);

        if (isDirtPath) {
            // 土径需要形成线性结构
            if (adjacentRoads >= 2) {
                // 检查是否形成线性道路
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
                QianmoSpeedMod.LOGGER.debug("  → 土径不满足条件（相邻={}），不通过", adjacentRoads);
            }
            return false;
        } else {
            // 其他路径方块
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
                QianmoSpeedMod.LOGGER.debug("  → 相邻道路方块不足（相邻={}, 周围={}），不通过",
                        adjacentRoads, surroundingRoads);
            }
            return false;
        }
    }
    
    /**
     * 检查是否形成线性道路
     */
    private boolean checkFormsLinearRoad(Level level, BlockPos pos) {
        String blockId = getBlockId(level, pos);
        
        // 检查X方向
        int xLength = 1;
        xLength += checkDirectionSameType(level, pos, true, true, blockId);
        xLength += checkDirectionSameType(level, pos, true, false, blockId);
        
        // 检查Z方向
        int zLength = 1;
        zLength += checkDirectionSameType(level, pos, false, true, blockId);
        zLength += checkDirectionSameType(level, pos, false, false, blockId);
        
        // 至少一个方向达到最小长度
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
     *修改：检查自然方块（严格版本，无方向检测）
     */
    private boolean checkNaturalBlockStrict(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 自然方块严格检查开始");
        }

        // 策略1: 至少需要2个相邻道路方块（形成线性道路）
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);

        if (adjacentRoads >= 2) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 有 {} 个相邻道路方块（≥2），通过", adjacentRoads);
            }
            return true;
        }

        // 策略2: 周围至少6个道路方块（被包围）
        int surroundingRoads = countSurroundingRoadBlocks(level, pos);

        if (surroundingRoads >= 6) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 周围有 {} 个道路方块（≥6），通过", surroundingRoads);
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
     * 🔧 修复：检查标准方块 - 需要相邻方块检查
     */
    private boolean checkStandardBlock(Level level, BlockPos pos) {
        // 检查相邻方块（四方向）
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);

        //修改：需要至少2个相邻道路方块
        if (adjacentRoads >= 2) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 有 {} 个相邻道路方块（≥2），通过", adjacentRoads);
            }
            return true;
        }

        // 检查3x3区域
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

    // 辅助方法
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        return basicDetector.isBasicRoadBlock(level, pos);
    }
}