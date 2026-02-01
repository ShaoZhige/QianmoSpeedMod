package com.example.qianmospeed.road;
import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
public class SmartRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final EnhancedRoadDetector enhancedDetector = new EnhancedRoadDetector();
    
    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        // 智能检测策略：
        // 1. 先用基础检测器快速检查
        // 2. 如果基础检测通过 → 直接返回 true（高效）
        // 3. 如果基础检测失败 → 判断是否需要增强检测
        //    - 自然方块 → 用增强检测器（宽松）
        //    - 检测到道路模组 → 用增强检测器（兼容性）
        //    - 其他情况 → 返回 false（严格）
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== SmartRoadDetector 智能检测 ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }
        
        // 第一步：基础检测（快速）
        boolean basicResult = basicDetector.isOnRoad(level, pos);
        
        if (basicResult) {
            // 基础检测通过 → 直接返回 true
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("SmartRoadDetector: ✓ 基础检测通过");
                QianmoSpeedMod.LOGGER.debug("==============================================");
            }
            return true;
        }
        
        // 第二步：判断是否需要增强检测
        boolean needsEnhanced = shouldUseEnhancedDetection(level, pos);
        
        if (needsEnhanced) {
            // 使用增强检测器复查
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("SmartRoadDetector: 基础检测失败，切换到增强检测");
            }
            
            boolean enhancedResult = enhancedDetector.isOnRoad(level, pos);
            
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("SmartRoadDetector: {} 增强检测{}",
                        enhancedResult ? "✓" : "✗",
                        enhancedResult ? "通过" : "失败");
                QianmoSpeedMod.LOGGER.debug("==============================================");
            }
            
            return enhancedResult;
        }
        
        // 第三步：不需要增强检测，直接返回 false
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("SmartRoadDetector: ✗ 基础检测失败，不使用增强检测");
            QianmoSpeedMod.LOGGER.debug("==============================================");
        }
        
        return false;
    }
    
    /**
     * 判断是否应该使用增强检测
     */
    private boolean shouldUseEnhancedDetection(Level level, BlockPos pos) {
        // 条件1：检测到道路模组 → 使用增强检测（兼容性）
        if (QianmoSpeedMod.hasDetectedRoadMods()) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("检测到道路模组，使用增强检测");
            }
            return true;
        }
        
        // 条件2：是自然方块 → 使用增强检测（宽松）
        BlockState state = level.getBlockState(pos);
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        
        boolean isNaturalBlock = isNaturalBlockType(blockId);
        
        if (isNaturalBlock) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("检测到自然方块 {}，使用增强检测", blockId);
            }
            return true;
        }
        
        // 条件3：方块在高级列表中但不在基础列表中 → 使用增强检测
        boolean inAdvanced = SpeedModConfig.isAdvancedRoadBlock(state.getBlock());
        boolean inBasic = SpeedModConfig.isBasicRoadBlock(state.getBlock());
        
        if (inAdvanced && !inBasic) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("方块 {} 仅在高级列表中，使用增强检测", blockId);
            }
            return true;
        }
        
        // 其他情况：不使用增强检测
        return false;
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
}