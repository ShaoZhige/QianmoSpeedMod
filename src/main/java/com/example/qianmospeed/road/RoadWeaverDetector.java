package com.example.qianmospeed.road;
import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.event.AdvancedRoadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
/**
 * RoadWeaver 专用道路检测器
 * 直接查询 RoadWeaver 的 SQLite 数据，精确识别道路
 */
public class RoadWeaverDetector implements RoadDetectionFactory.IRoadDetector {
    
    private final EnhancedRoadDetector fallbackDetector = new EnhancedRoadDetector();
    
    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        // 只在服务器端使用 RoadWeaver API
        if (level instanceof ServerLevel serverLevel) {
            try {
                //添加详细调试日志
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("RoadWeaverDetector: 开始查询位置 {}", pos);
                }
                
                AdvancedRoadHandler.RoadType roadType = 
                    AdvancedRoadHandler.checkRoadType(serverLevel, pos);
                
                boolean isRoad = roadType != AdvancedRoadHandler.RoadType.NONE;
                
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("RoadWeaverDetector: 查询结果 - 类型={}, 是道路={}", 
                        roadType, isRoad);
                }
                
                //关键修改：如果 RoadWeaver 查询失败，使用降级检测器
                if (!isRoad) {
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("RoadWeaver未检测到道路，尝试降级检测器");
                    }
                    
                    // 使用增强检测器作为降级方案
                    boolean fallbackResult = fallbackDetector.isOnRoad(level, pos);
                    
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("降级检测器结果: {}", fallbackResult);
                    }
                    
                    return fallbackResult;
                }
                
                return true;
            } catch (Exception e) {
                // 如果 RoadWeaver API 调用失败，降级到增强检测器
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("RoadWeaver检测异常，使用降级检测器", e);
                }
                return fallbackDetector.isOnRoad(level, pos);
            }
        }
        
        // 客户端降级到增强检测器
        return fallbackDetector.isOnRoad(level, pos);
    }
    
    /**
     * 获取道路类型（用于扩展功能）
     */
    public AdvancedRoadHandler.RoadType getRoadType(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return AdvancedRoadHandler.checkRoadType(serverLevel, pos);
        }
        return AdvancedRoadHandler.RoadType.NONE;
    }
}