package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.event.AdvancedRoadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.Map;

/**
 * 混合道路检测器（智能版本 - 防止自然地形误判）
 * 
 * 检测策略（按优先级）：
 * 1. RoadWeaver API 查询 → 精确识别 RoadWeaver 生成的道路
 * 2. 基础道路方块 → 使用方向检测（严格，避免误判地板）
 * 3. 加工自然方块 → 中等严格检测
 * 4. 纯自然方块 → 超严格检测（防止自然地形误判）
 */
public class HybridRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final Map<BlockPos, Boolean> cache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== HybridRoadDetector 混合检测 ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }
        
        // 客户端直接使用基础检测器
        if (!(level instanceof ServerLevel serverLevel)) {
            return basicDetector.isOnRoad(level, pos);
        }
        
        // 检查缓存
        if (cache.containsKey(pos)) {
            boolean cached = cache.get(pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用缓存结果: {}", cached);
            }
            return cached;
        }
        
        boolean result = false;
        String detectionMethod = "未检测到";
        
        // ========== 策略 1: RoadWeaver API（最高优先级，精确） ==========
        if (AdvancedRoadHandler.isAvailable()) {
            try {
                AdvancedRoadHandler.RoadType roadType = AdvancedRoadHandler.checkRoadType(serverLevel, pos);
                if (roadType != AdvancedRoadHandler.RoadType.NONE) {
                    result = true;
                    detectionMethod = "RoadWeaver数据库";
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("✅ RoadWeaver检测通过: 类型={}", roadType);
                    }
                    // RoadWeaver 检测成功，直接返回
                    cache.put(pos, result);
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("混合检测最终结果: {}, 方式: {}", result, detectionMethod);
                        QianmoSpeedMod.LOGGER.debug("==============================================");
                    }
                    return result;
                }
            } catch (Exception e) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("RoadWeaver查询异常: {}", e.getMessage());
                }
            }
        }
        
        // ========== 策略 2-4: 方块检测（RoadWeaver 没有数据时） ==========
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
        
        boolean isBasicBlock = SpeedModConfig.isBasicRoadBlock(block);
        boolean isAdvancedBlock = SpeedModConfig.isAdvancedRoadBlock(block);
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方块ID: {}, 基础列表={}, 高级列表={}", 
                blockId, isBasicBlock, isAdvancedBlock);
        }
        
        if (isBasicBlock) {
            // 策略 2: 基础列表方块 → 使用基础检测器（含方向检测）
            result = basicDetector.isOnRoad(level, pos);
            detectionMethod = "基础检测器（含方向检测）";
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用基础检测器: 结果={}", result);
            }
        } else if (isAdvancedBlock) {
            // 策略 3-4: 高级列表方块（但不在基础列表） → 根据类型选择检测方法
            result = checkAdvancedBlockStrict(level, pos, blockId);
            detectionMethod = "高级方块严格检测";
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用严格检测: 结果={}", result);
            }
        } else {
            // 不在任何列表中
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("方块不在任何道路列表中");
            }
        }
        
        // 更新缓存
        if (cache.size() >= CACHE_SIZE) {
            cache.clear();
        }
        cache.put(pos, result);
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("混合检测最终结果: {}, 方式: {}", result, detectionMethod);
            QianmoSpeedMod.LOGGER.debug("==============================================");
        }
        
        return result;
    }

    /**
     * 检查高级方块（分类严格检测 - 防止自然地形误判）
     */
    private boolean checkAdvancedBlockStrict(Level level, BlockPos pos, String blockId) {
        // 🔧 修复：路径方块严格检测，避免单个土径被误判
        if (blockId.contains("path")) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 路径方块，严格检查");
            }

            // 特别处理土径：需要形成线性结构
            boolean isDirtPath = blockId.contains("dirt_path");
            
            // 条件1：需要至少2个相邻道路方块
            int adjacentRoads = countAdjacentRoadBlocks(level, pos);

            if (isDirtPath) {
                // 对于土径，需要形成线性道路
                if (adjacentRoads >= 2) {
                    // 检查是否形成线性道路
                    boolean formsLine = checkFormsLinearRoad(level, pos, blockId);
                    if (formsLine) {
                        if (SpeedModConfig.isDebugMessagesEnabled()) {
                            QianmoSpeedMod.LOGGER.debug("  → 土径形成线性道路，通过");
                        }
                        return true;
                    }
                }
                
                // 备用检查：被其他道路方块包围
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
        
        // ⭐⭐⭐ 纯自然方块（dirt, grass_block, sand 等）：需要超严格检查
        if (isPureNaturalBlock(blockId)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 纯自然方块 {}，超严格检查", blockId);
            }
            return checkPureNaturalBlock(level, pos, blockId);
        }
        
        // 加工自然方块（packed_mud, coarse_dirt 等）：中等严格
        if (isProcessedNaturalBlock(blockId)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 加工自然方块 {}，中等严格检查", blockId);
            }
            return checkProcessedNaturalBlock(level, pos);
        }
        
        // 其他方块：标准检测
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 其他高级方块，标准检测");
        }
        return checkStandardBlock(level, pos, blockId);
    }
    
    /**
     * 检查是否形成线性道路
     */
    private boolean checkFormsLinearRoad(Level level, BlockPos pos, String blockId) {
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
     * 判断是否是纯自然方块（未加工）
     */
    private boolean isPureNaturalBlock(String blockId) {
        return blockId.equals("minecraft:dirt") ||
                blockId.equals("minecraft:grass_block") ||
                blockId.equals("minecraft:sand") ||
                blockId.equals("minecraft:red_sand") ||
                blockId.equals("minecraft:gravel") ||
                blockId.equals("minecraft:moss_block") ||
                blockId.equals("minecraft:podzol") ||
                blockId.equals("minecraft:mycelium") ||
                blockId.equals("minecraft:stone") ||
                blockId.equals("minecraft:andesite") ||
                blockId.equals("minecraft:diorite") ||
                blockId.equals("minecraft:granite");
    }

    /**
     * 判断是否是加工过的自然方块
     */
    private boolean isProcessedNaturalBlock(String blockId) {
        return blockId.equals("minecraft:packed_mud") ||
                blockId.equals("minecraft:coarse_dirt") ||
                blockId.equals("minecraft:rooted_dirt") ||
                blockId.equals("minecraft:packed_ice") ||
                blockId.equals("minecraft:mud");
    }

    /**
     * 检查纯自然方块（超严格 - 必须有明确的道路意图）
     */
    private boolean checkPureNaturalBlock(Level level, BlockPos pos, String blockId) {
        //策略1: 必须与"明确的道路方块"相邻
        // 检查周围是否有基础列表中的道路方块（stone_bricks, mud_bricks 等）
        int adjacentClearRoads = countAdjacentClearRoadBlocks(level, pos);
        if (adjacentClearRoads >= 2) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 有 {} 个明确道路方块相邻（≥2），通过", adjacentClearRoads);
            }
            return true;
        }
        
        //策略2: 检查是否在 RoadWeaver 道路附近（3格内）
        if (isNearRoadWeaverRoad(level, pos, 3)) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 在 RoadWeaver 道路3格范围内，通过");
            }
            return true;
        }
        
        //策略3: 检查是否形成明确的道路形状（方向检测）
        int xLength = calculateDirectionalLength(level, pos, true, blockId);
        int zLength = calculateDirectionalLength(level, pos, false, blockId);
        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 方向长度: X={}, Z={} (有效范围: {}-{})", 
                xLength, zLength, minLength, maxLength);
        }
        
        //必须有一个方向在有效范围内，且另一个方向不能太长（避免大片地形）
        boolean isValidRoad = (xLength >= minLength && xLength <= maxLength && zLength <= maxLength) ||
                (zLength >= minLength && zLength <= maxLength && xLength <= maxLength);
        
        if (isValidRoad) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 形成有效道路形状，通过");
            }
            return true;
        }
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 不满足任何道路特征（明确相邻={}, X={}, Z={}），不通过",
                adjacentClearRoads, xLength, zLength);
        }
        return false;
    }

    /**
     * 检查加工过的自然方块（中等严格）
     */
    private boolean checkProcessedNaturalBlock(Level level, BlockPos pos) {
        // 加工方块比纯自然方块宽松，但仍需检查
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
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

    /**
     * 🔧 修复：检查标准方块 - 需要方向检测
     * 用于处理木板、混凝土等完整方块
     */
    private boolean checkStandardBlock(Level level, BlockPos pos, String blockId) {
        // 检查是否是完整方块
        boolean isFullBlock = isFullHeightBlock(blockId);
        
        // 对于完整方块，应用方向检测
        if (isFullBlock && SpeedModConfig.isDirectionalDetectionEnabled()) {
            boolean directionalResult = applyDirectionalDetectionForStandardBlock(level, pos, blockId);
            if (directionalResult) {
                // 方向检测通过，还需要检查相邻道路
                int adjacentRoads = countAdjacentRoadBlocks(level, pos);
                if (adjacentRoads >= 2) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("  → 方向检测通过 + 有 {} 个相邻道路方块，通过", adjacentRoads);
                    }
                    return true;
                }
            } else {
                // 方向检测失败
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  → 方向检测失败，不通过");
                }
                return false;
            }
        }
        
        // 非完整方块或未启用方向检测：使用原有逻辑
        // 检查相邻方块（四方向）
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
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
     * 应用方向检测（标准方块专用）
     */
    private boolean applyDirectionalDetectionForStandardBlock(Level level, BlockPos pos, String blockId) {
        // 检查X方向连续长度
        int xLength = calculateDirectionalLength(level, pos, true, blockId);
        // 检查Z方向连续长度
        int zLength = calculateDirectionalLength(level, pos, false, blockId);
        
        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 方向检测: X={}, Z={} (范围: {}-{})", 
                xLength, zLength, minLength, maxLength);
        }
        
        // 1. 如果两个方向都超过最大值 → 是地板/广场
        if (xLength > maxLength && zLength > maxLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 判定: 地板/广场 (两个方向都超过最大值)");
            }
            return false;
        }
        
        // 2. 如果两个方向都小于最小值 → 可能是装饰
        if (xLength < minLength && zLength < minLength) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  → 判定: 装饰方块 (两个方向都小于最小值)");
            }
            return false;
        }
        
        // 3. 其他情况 → 通过
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  → 判定: 通过方向检测");
        }
        return true;
    }
    
    /**
     * 检查是否是完整高度的方块
     */
    private boolean isFullHeightBlock(String blockId) {
        // 不完整方块的特征
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
     * 统计相邻的"明确道路方块"数量
     * 只计算基础列表中的方块（排除 dirt, sand 等自然方块）
     */
    private int countAdjacentClearRoadBlocks(Level level, BlockPos pos) {
        int count = 0;
        BlockPos[] adjacentPositions = {
            pos.north(), pos.south(), pos.east(), pos.west()
        };
        for (BlockPos adjPos : adjacentPositions) {
            BlockState adjState = level.getBlockState(adjPos);
            //只计算基础列表中的方块（明确的道路方块）
            if (SpeedModConfig.isBasicRoadBlock(adjState.getBlock())) {
                count++;
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    String adjBlockId = ForgeRegistries.BLOCKS.getKey(adjState.getBlock()).toString();
                    QianmoSpeedMod.LOGGER.debug("    明确道路 - 位置: {}, 方块: {}, 是道路方块", adjPos, adjBlockId);
                }
            }
        }
        return count;
    }

    /**
     * 检查是否在 RoadWeaver 道路附近
     */
    private boolean isNearRoadWeaverRoad(Level level, BlockPos pos, int range) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!AdvancedRoadHandler.isAvailable()) {
            return false;
        }
        
        // 检查周围指定范围
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos nearPos = pos.offset(dx, 0, dz);
                try {
                    AdvancedRoadHandler.RoadType roadType = AdvancedRoadHandler.checkRoadType(serverLevel, nearPos);
                    if (roadType != AdvancedRoadHandler.RoadType.NONE) {
                        if (SpeedModConfig.isDebugMessagesEnabled()) {
                            QianmoSpeedMod.LOGGER.debug("    发现 RoadWeaver 道路: 位置={}, 类型={}", 
                                nearPos, roadType);
                        }
                        return true; // 附近有 RoadWeaver 道路
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            }
        }
        return false;
    }

    /**
     * 计算方向长度（只计算相同类型的方块）
     */
    private int calculateDirectionalLength(Level level, BlockPos pos, boolean checkX, String targetBlockId) {
        int totalLength = 1;
        totalLength += checkDirectionSameType(level, pos, checkX, true, targetBlockId);
        totalLength += checkDirectionSameType(level, pos, checkX, false, targetBlockId);
        return totalLength;
    }

    /**
     * 检查单个方向（只计算相同类型的方块）
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
            
            //只计算相同类型的方块
            if (!blockId.equals(targetBlockId)) {
                break;
            }
            length++;
        }
        return length;
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
            BlockState adjState = level.getBlockState(adjPos);
            if (SpeedModConfig.isAdvancedRoadBlock(adjState.getBlock())) {
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
                if (dx == 0 && dz == 0) continue;
                BlockState nearbyState = level.getBlockState(pos.offset(dx, 0, dz));
                if (SpeedModConfig.isAdvancedRoadBlock(nearbyState.getBlock())) {
                    count++;
                }
            }
        }
        return count;
    }
}