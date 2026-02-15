package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.config.SpeedModConfig.RoadDetectionMode;
import com.example.qianmospeed.event.AdvancedRoadHandler;

public class RoadDetectionFactory {
    public interface IRoadDetector {
        boolean isOnRoad(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos);
    }

    /**
     * 创建道路检测器
     */
    public static IRoadDetector createDetector() {
        QianmoSpeedMod.LOGGER.info("================== 检测器创建 ==================");

        // 如果 RoadWeaver 可用，使用混合检测器（优先识别阡陌交通道路）
        if (AdvancedRoadHandler.isAvailable()) {
            QianmoSpeedMod.LOGGER.info(">>> 创建混合检测器 (RoadWeaver优先)");
            QianmoSpeedMod.LOGGER.info("说明: RoadWeaver道路不受方向检测约束");
            QianmoSpeedMod.LOGGER.info("最终检测器类型: HybridRoadDetector");
            QianmoSpeedMod.LOGGER.info("============================================");
            return new HybridRoadDetector();
        }

        // 根据配置的检测模式选择检测器
        RoadDetectionMode mode = SpeedModConfig.getRoadDetectionMode();
        boolean useAdvanced = SpeedModConfig.isAdvancedFeaturesEnabled() ||
                QianmoSpeedMod.hasDetectedProfessionalRoadMods();

        QianmoSpeedMod.LOGGER.info("配置的检测模式: {}", mode);
        QianmoSpeedMod.LOGGER.info("高级功能启用: {}", useAdvanced);

        IRoadDetector detector;

        switch (mode) {
            case BASIC:
                QianmoSpeedMod.LOGGER.info(">>> 创建基础道路检测器 (BASIC模式)");
                detector = new BasicRoadDetector();
                break;

            case ENHANCED:
                if (useAdvanced) {
                    QianmoSpeedMod.LOGGER.info(">>> 创建增强道路检测器 (ENHANCED模式)");
                    detector = new EnhancedRoadDetector();
                } else {
                    QianmoSpeedMod.LOGGER.info(">>> 创建基础道路检测器 (ENHANCED模式但未启用高级功能)");
                    detector = new BasicRoadDetector();
                }
                break;

            case SMART:
                QianmoSpeedMod.LOGGER.info(">>> 创建智能道路检测器 (SMART模式)");
                detector = new SmartRoadDetector();
                break;

            default:
                QianmoSpeedMod.LOGGER.info(">>> 创建基础道路检测器 (默认)");
                detector = new BasicRoadDetector();
                break;
        }

        QianmoSpeedMod.LOGGER.info("最终检测器类型: {}", detector.getClass().getSimpleName());
        QianmoSpeedMod.LOGGER.info("============================================");

        return detector;
    }
}