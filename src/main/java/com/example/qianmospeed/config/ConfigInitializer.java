package com.example.qianmospeed.config;

import com.example.qianmospeed.QianmoSpeedMod;

/**
 * 配置文件初始化器
 * 强制加载所有配置项，确保配置文件完整生成
 */
public class ConfigInitializer {
    
    public static void init() {
        QianmoSpeedMod.LOGGER.info("========== 初始化配置系统 ==========");
        
        try {
            // ========== 客户端配置 ==========
            SpeedModConfig.isDebugMessagesEnabled();
            SpeedModConfig.isLoginMessagesEnabled();
            SpeedModConfig.isSpeedEffectMessagesEnabled();
            QianmoSpeedMod.LOGGER.debug("✓ 客户端配置已加载");
            
            // ========== 游戏性配置 ==========
            SpeedModConfig.getCheckInterval();
            QianmoSpeedMod.LOGGER.debug("✓ 游戏性配置已加载");
            
            // ========== ⭐⭐⭐ 常驻道路加速配置（必须优先加载）⭐⭐⭐ ==========
            boolean permanentEnabled = SpeedModConfig.isPermanentSpeedEnabled();
            double permanentMultiplier = SpeedModConfig.getPermanentSpeedMultiplier();
            QianmoSpeedMod.LOGGER.info("✓ 常驻道路加速配置已加载: 启用={}, 倍率={}倍", 
                permanentEnabled, permanentMultiplier);
            
            // ========== 速度加成配置 ==========
            SpeedModConfig.getSpeedMultiplier(1);
            SpeedModConfig.getSpeedMultiplier(2);
            SpeedModConfig.getSpeedMultiplier(3);
            QianmoSpeedMod.LOGGER.debug("✓ 速度加成配置已加载");
            
            // ========== 方向检测配置 ==========
            SpeedModConfig.isDirectionalDetectionEnabled();
            SpeedModConfig.getMinDirectionalLength();
            SpeedModConfig.getMaxDirectionalLength();
            QianmoSpeedMod.LOGGER.debug("✓ 方向检测配置已加载");
            
            // ========== 高级功能配置 ==========
            SpeedModConfig.isAdvancedFeaturesEnabled();
            SpeedModConfig.shouldAutoEnableAdvanced();
            SpeedModConfig.getRoadDetectionMode();
            QianmoSpeedMod.LOGGER.debug("✓ 高级功能配置已加载");
            
            // ========== 道路方块列表 ==========
            int basicSize = SpeedModConfig.getBasicRoadBlockIds().size();
            int advancedSize = SpeedModConfig.getAdvancedRoadBlockIds().size();
            QianmoSpeedMod.LOGGER.debug("✓ 道路方块列表已加载: 基础={}个, 高级={}个", 
                basicSize, advancedSize);
            
            // ========== 验证配置 ==========
            boolean speedValid = SpeedModConfig.validateSpeedMultipliers();
            boolean directionalValid = SpeedModConfig.validateDirectionalDetection();
            boolean blocksValid = SpeedModConfig.validateRoadBlocks();
            
            QianmoSpeedMod.LOGGER.info("✓ 配置验证: 速度加成={}, 方向检测={}, 道路方块={}", 
                speedValid ? "有效" : "警告", 
                directionalValid ? "有效" : "警告", 
                blocksValid ? "有效" : "警告");
            
            // ========== 输出配置文件位置 ==========
            String configPath = System.getProperty("user.dir") + "/config/qianmospeed-common.toml";
            QianmoSpeedMod.LOGGER.info("📁 配置文件位置: {}", configPath);
            
        } catch (Exception e) {
            QianmoSpeedMod.LOGGER.error("❌ 配置系统初始化失败", e);
        }
        
        QianmoSpeedMod.LOGGER.info("========== 配置系统初始化完成 ==========");
    }
}