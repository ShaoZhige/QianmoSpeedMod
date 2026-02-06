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
        // 🔍 添加关键调试日志
        QianmoSpeedMod.LOGGER.info("================== 检测器创建 ==================");

        //最高优先级：如果 RoadWeaver 可用，使用混合检测器
        if (QianmoSpeedMod.isRoadModLoaded("roadweaver") &&
                AdvancedRoadHandler.isAvailable()) {
            QianmoSpeedMod.LOGGER.info(">>> 创建混合检测器 (RoadWeaver + 智能方块检测)");
            QianmoSpeedMod.LOGGER.info("说明: RoadWeaver数据库查询 + 基础方块方向检测 + 高级方块宽松检测");
            QianmoSpeedMod.LOGGER.info("最终检测器类型: HybridRoadDetector");
            QianmoSpeedMod.LOGGER.info("============================================");
            return new HybridRoadDetector();
        }

        // 原有逻辑：根据配置和检测到的模组选择检测器
        boolean useAdvanced = shouldUseAdvancedDetection();

        QianmoSpeedMod.LOGGER.info("是否使用高级检测: {}", useAdvanced);
        QianmoSpeedMod.LOGGER.info("配置的检测模式: {}", SpeedModConfig.getRoadDetectionMode());
        QianmoSpeedMod.LOGGER.info("高级功能配置: {}", SpeedModConfig.isAdvancedFeaturesEnabled());
        QianmoSpeedMod.LOGGER.info("检测到专业道路模组: {}", QianmoSpeedMod.hasDetectedProfessionalRoadMods());

        // 根据检测模式选择具体的检测器
        RoadDetectionMode mode = SpeedModConfig.getRoadDetectionMode();

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

    /**
     * 判断是否应该使用高级检测
     * 优先级：
     * 1. 用户明确开启高级模式
     * 2. 检测到专业道路模组（自动开启）
     * 3. 用户配置的自动开启
     * 4. 默认基础模式
     */
    private static boolean shouldUseAdvancedDetection() {
        // 1. 最高优先级：用户明确开启高级模式
        if (SpeedModConfig.isAdvancedFeaturesEnabled()) {
            QianmoSpeedMod.LOGGER.info("高级模式：用户手动开启");
            return true;
        }

        // 2. 检测到专业道路模组，自动开启高级模式
        if (QianmoSpeedMod.hasDetectedProfessionalRoadMods()) {
            QianmoSpeedMod.LOGGER.info("检测到专业道路模组，自动启用高级模式");
            return true;
        }

        // 3. 用户配置的自动开启
        if (SpeedModConfig.shouldAutoEnableAdvanced() && QianmoSpeedMod.hasDetectedRoadMods()) {
            QianmoSpeedMod.LOGGER.info("自动启用高级模式（用户配置+检测到道路模组）");
            return true;
        }

        // 4. 默认：基础模式
        QianmoSpeedMod.LOGGER.info("使用基础检测模式");
        return false;
    }
}