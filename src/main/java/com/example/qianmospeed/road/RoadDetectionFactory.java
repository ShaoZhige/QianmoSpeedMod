package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class RoadDetectionFactory {
    
    /**
     * 检查 RoadWeaver 是否加载
     */
    private static boolean isRoadWeaverLoaded() {
        return ModList.get().isLoaded("roadweaver");
    }
    
    /**
     * 创建道路检测器 / Create road detector
     * @return 适当的检测器实例 / Appropriate detector instance
     */
    public static IRoadDetector createDetector() {
        if (isRoadWeaverLoaded() && SpeedModConfig.isAdvancedFeaturesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("创建RoadWeaver高级道路检测器 / Creating RoadWeaver advanced road detector");
            return new RoadWeaverDetector();
        } else if (SpeedModConfig.isBasicDetectionEnabled()) {
            QianmoSpeedMod.LOGGER.debug("创建智能基础道路检测器 / Creating intelligent basic road detector");
            return new SmartBasicRoadDetector();
        } else {
            QianmoSpeedMod.LOGGER.debug("创建空检测器（道路检测已禁用） / Creating empty detector (road detection disabled)");
            return new EmptyDetector();
        }
    }
    
    /**
     * 道路检测器接口 / Road Detector Interface
     */
    public interface IRoadDetector {
        /**
         * 检查方块是否是道路 / Check if a block is a road
         */
        boolean isRoadBlock(Block block);
        
        /**
         * 检查位置是否在道路上 / Check if a position is on a road
         */
        boolean isOnRoad(Level level, BlockPos pos);
        
        /**
         * 检查区域是否有道路 / Check if there is road in an area
         */
        boolean isRoadInArea(Level level, BlockPos center, int radius);
        
        /**
         * 获取检测器类型描述 / Get detector type description
         */
        String getDetectorType();
    }
    
    /**
     * 智能基础检测器 / Smart Basic Detector
     * 基础模式也使用智能检测，防止在单个孤立的道路方块上加速
     */
    private static class SmartBasicRoadDetector implements IRoadDetector {
        private final Set<String> roadBlocks = new HashSet<>();
        private int lastUpdateTick = 0;
        private static final int CHECK_INTERVAL = 20; // 每20tick检查一次周围方块
        
        public SmartBasicRoadDetector() {
            loadRoadBlocks();
        }
        
        private void loadRoadBlocks() {
            // 从配置加载基础模式道路方块 / Load basic mode road blocks from configuration
            roadBlocks.addAll(SpeedModConfig.getBasicRoadBlocks());
            
            // 记录日志 / Logging
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.info("智能基础检测器加载了 {} 个道路方块 / Smart basic detector loaded {} road blocks", 
                    roadBlocks.size(), roadBlocks.size());
                if (roadBlocks.size() <= 30) {
                    QianmoSpeedMod.LOGGER.info("基础道路方块列表: {} / Basic road block list: {}", 
                        String.join(", ", roadBlocks), 
                        String.join(", ", roadBlocks));
                }
            }
        }
        
        @Override
        public boolean isRoadBlock(Block block) {
            if (block == null) return false;
            String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
            return roadBlocks.contains(blockId);
        }
        
        @Override
        public boolean isOnRoad(Level level, BlockPos pos) {
            // 获取方块状态
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            
            // 检查是否是流体（水、岩浆等）或空气 - 不能在流体上加速
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
            
            // 检查是否是道路方块
            if (!isRoadBlock(state.getBlock())) {
                return false;
            }
            
            // 智能检测：检查周围是否有连续的道路方块
            return checkRoadContinuity(level, pos);
        }
        
        @Override
        public boolean isRoadInArea(Level level, BlockPos center, int radius) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos checkPos = center.offset(x, y, z);
                        
                        // 获取方块状态，检查是否是流体
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                            continue; // 跳过流体和空气
                        }
                        
                        if (isOnRoad(level, checkPos)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        
        /**
         * 检查道路连续性，防止在自然地形上加速
         * Check road continuity to prevent acceleration on natural terrain
         */
        private boolean checkRoadContinuity(Level level, BlockPos center) {
            int roadCount = 0;
            int totalChecked = 0;
            int artificialRoadCount = 0; // 人工道路计数
            
            // 检查3x3区域 / Check 3x3 area
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    // 跳过中心点 / Skip center point
                    if (x == 0 && z == 0) continue;
                    
                    BlockPos checkPos = center.offset(x, 0, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);
                    
                    // 跳过流体和空气
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        totalChecked++;
                        continue;
                    }
                    
                    // 检查是否是道路方块 / Check if it's a road block
                    if (isRoadBlock(state.getBlock())) {
                        roadCount++;
                        
                        // 检查是否是明确的人工道路（土径、混凝土等）
                        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
                        if (isClearlyArtificialRoad(blockId)) {
                            artificialRoadCount++;
                        }
                    }
                    
                    totalChecked++;
                }
            }
            
            // 基础模式的双重判断：
            // 1. 检查周围道路数量
            // 2. 必须包含至少1个明确的人工道路方块（如土径、混凝土等）
            boolean hasClearArtificialRoad = artificialRoadCount >= 1;
            boolean hasEnoughRoadBlocks = roadCount >= 3;
            boolean isContinuousRoad = hasEnoughRoadBlocks && hasClearArtificialRoad;
            
            // 如果没有明确的人工道路，但道路数量很多，可能是自然地形
            if (roadCount >= 4 && artificialRoadCount == 0) {
                // 这可能是一片自然石头地形，不应该是道路
                isContinuousRoad = false;
            }
            
            if (SpeedModConfig.isDebugMessagesEnabled() && level.getGameTime() - lastUpdateTick > CHECK_INTERVAL) {
                QianmoSpeedMod.LOGGER.debug("基础检测: 位置={}, 道路数={}, 人工路={}, 总数={}, 连续={}",
                    center, roadCount, artificialRoadCount, totalChecked, isContinuousRoad);
                lastUpdateTick = (int) level.getGameTime();
            }
            
            return isContinuousRoad;
        }
        
        /**
         * 检查是否是明确的人工道路方块
         * Check if it's a clearly artificial road block
         */
        private boolean isClearlyArtificialRoad(String blockId) {
            // 检查是否是明确的人工道路方块（这些通常不会在自然生成）
            // 你可以在这里添加更多的明确人工道路方块判断
            return blockId.equals("minecraft:dirt_path") ||
                   blockId.startsWith("minecraft:") && 
                   (blockId.contains("_concrete") ||
                    blockId.contains("polished_") ||
                    blockId.contains("_bricks") ||
                    blockId.contains("_planks") ||
                    blockId.contains("_slab"));
        }
        
        @Override
        public String getDetectorType() {
            return "智能基础检测 (" + roadBlocks.size() + "个方块) / Smart Basic Detection (" + roadBlocks.size() + " blocks)";
        }
    }
    
    /**
     * RoadWeaver高级检测器 / RoadWeaver Advanced Detector
     */
    private static class RoadWeaverDetector implements IRoadDetector {
        private final Set<String> roadBlocks = new HashSet<>();
        private int lastUpdateTick = 0;
        
        public RoadWeaverDetector() {
            loadRoadBlocks();
        }
        
        private void loadRoadBlocks() {
            // 从配置加载高级模式道路方块 / Load advanced mode road blocks from configuration
            roadBlocks.addAll(SpeedModConfig.getAdvancedRoadBlocks());
            
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.info("RoadWeaver检测器已加载 {} 种道路方块 / RoadWeaver detector loaded {} road blocks", 
                    roadBlocks.size(), roadBlocks.size());
            }
        }
        
        @Override
        public boolean isRoadBlock(Block block) {
            if (block == null) return false;
            String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
            return roadBlocks.contains(blockId);
        }
        
        @Override
        public boolean isOnRoad(Level level, BlockPos pos) {
            // 获取方块状态
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            
            // 检查是否是流体（水、岩浆等）或空气 - 不能在流体上加速
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
            
            if (!isRoadBlock(state.getBlock())) {
                return false;
            }
            
            // 高级检测：使用更智能的连续性检测
            // Advanced detection: Use smarter continuity detection
            return checkAdvancedRoadContinuity(level, pos);
        }
        
        @Override
        public boolean isRoadInArea(Level level, BlockPos center, int radius) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos checkPos = center.offset(x, y, z);
                        
                        // 获取方块状态，检查是否是流体
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);
                        if (state.isAir() || !state.getFluidState().isEmpty()) {
                            continue; // 跳过流体和空气
                        }
                        
                        if (isOnRoad(level, checkPos)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        
        private boolean checkAdvancedRoadContinuity(Level level, BlockPos center) {
            int roadCount = 0;
            int total = 0;
            int straightRoadCount = 0; // 直线方向的道路数量
            
            // 检查5x5区域（比基础模式范围更大） / Check 5x5 area (larger than basic mode)
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    // 跳过中心点 / Skip center point
                    if (x == 0 && z == 0) continue;
                    
                    BlockPos checkPos = center.offset(x, 0, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(checkPos);
                    
                    // 跳过流体和空气
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        total++;
                        continue;
                    }
                    
                    if (isRoadBlock(state.getBlock())) {
                        roadCount++;
                        
                        // 检查直线方向（东西南北） / Check straight directions (NSEW)
                        if (x == 0 || z == 0) {
                            straightRoadCount++;
                        }
                    }
                    total++;
                }
            }
            
            // 高级模式有更宽松的条件 / Advanced mode has looser conditions
            // 1. 检查周围道路密度 / Check road density around
            double roadDensity = (double) roadCount / total;
            
            // 2. 检查是否有直线道路 / Check if there are straight roads
            boolean hasStraightRoad = straightRoadCount >= 2;
            
            // 3. 检查道路连接性 / Check road connectivity
            boolean hasConnectedRoads = checkRoadConnectivity(level, center);
            
            // 高级模式使用更复杂的判断逻辑
            // Advanced mode uses more complex judgment logic
            boolean isOnValidRoad = (roadDensity >= 0.25 && hasStraightRoad) || hasConnectedRoads;
            
            if (SpeedModConfig.isDebugMessagesEnabled() && level.getGameTime() - lastUpdateTick > 40) {
                QianmoSpeedMod.LOGGER.debug("高级检测: 位置={}, 道路数={}, 直线={}, 密度={:.2f}, 连接={}, 有效={}",
                    center, roadCount, straightRoadCount, roadDensity, hasConnectedRoads, isOnValidRoad);
                lastUpdateTick = (int) level.getGameTime();
            }
            
            return isOnValidRoad;
        }
        
        /**
         * 检查道路连接性（深度优先搜索） / Check road connectivity (DFS)
         */
        private boolean checkRoadConnectivity(Level level, BlockPos start) {
            Set<BlockPos> visited = new HashSet<>();
            return dfsRoadCheck(level, start, start, visited, 0, 5); // 最多检查5个连接方块
        }
        
        private boolean dfsRoadCheck(Level level, BlockPos current, BlockPos start, 
                                   Set<BlockPos> visited, int depth, int maxDepth) {
            if (depth >= maxDepth) return true;
            if (visited.contains(current)) return false;
            
            visited.add(current);
            
            // 检查四个主要方向 / Check four main directions
            BlockPos[] directions = {
                current.north(), current.south(), current.east(), current.west()
            };
            
            int connectedRoads = 0;
            for (BlockPos nextPos : directions) {
                if (nextPos.equals(start) && depth > 0) {
                    connectedRoads++; // 回到起点也算连接
                    continue;
                }
                
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(nextPos);
                
                // 跳过流体和空气
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                
                if (isRoadBlock(state.getBlock())) {
                    connectedRoads++;
                    if (dfsRoadCheck(level, nextPos, start, visited, depth + 1, maxDepth)) {
                        return true;
                    }
                }
            }
            
            return connectedRoads >= 2; // 至少连接两个方向
        }
        
        @Override
        public String getDetectorType() {
            return "RoadWeaver高级检测 (" + roadBlocks.size() + "个方块) / RoadWeaver Advanced Detection (" + roadBlocks.size() + " blocks)";
        }
    }
    
    /**
     * 空检测器（当检测被禁用时） / Empty Detector (when detection is disabled)
     */
    private static class EmptyDetector implements IRoadDetector {
        @Override
        public boolean isRoadBlock(Block block) {
            return false;
        }
        
        @Override
        public boolean isOnRoad(Level level, BlockPos pos) {
            return false;
        }
        
        @Override
        public boolean isRoadInArea(Level level, BlockPos center, int radius) {
            return false;
        }
        
        @Override
        public String getDetectorType() {
            return "检测已禁用 / Detection Disabled";
        }
    }
}