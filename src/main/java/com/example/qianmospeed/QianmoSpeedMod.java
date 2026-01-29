package com.example.qianmospeed;

import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.registry.EnchantmentRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("qianmospeed")
public class QianmoSpeedMod {
    public static final String MODID = "qianmospeed";
    public static final Logger LOGGER = LogManager.getLogger();
    
    // 模组状态标志
    public static boolean ROADWEAVER_LOADED = false;
    public static final String ROADWEAVER_MODID = "roadweaver";
    
    public QianmoSpeedMod() {
        LOGGER.info("=== 阡陌疾旅模组初始化开始 ===");
        
        // 检测RoadWeaver是否加载
        detectRoadWeaver();
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        
        // 注册配置系统
        SpeedModConfig.register();
        
        // 注册附魔
        EnchantmentRegistry.register(modEventBus);
        
        // 注册事件处理器（条件注册）
        registerConditionalHandlers(forgeBus);
        
        LOGGER.info("阡陌疾旅模组初始化完成");
        LOGGER.info("模式: {}", ROADWEAVER_LOADED ? "完整联动" : "独立运行");
    }
    
    private void detectRoadWeaver() {
        ROADWEAVER_LOADED = ModList.get().isLoaded(ROADWEAVER_MODID);
        
        if (ROADWEAVER_LOADED) {
            LOGGER.info("✓ 检测到RoadWeaver模组，启用完整道路加速功能");
        } else {
            LOGGER.info("ⓘ 未检测到RoadWeaver模组，启用基础道路检测功能");
            LOGGER.info("  安装RoadWeaver后可获得更准确的道路识别");
        }
    }
    
    private void registerConditionalHandlers(IEventBus forgeBus) {
        // 总是注册的基础事件处理器
        forgeBus.register(com.example.qianmospeed.event.BasicEventHandler.class);
        
        // 仅在RoadWeaver加载时注册高级处理器
        if (ROADWEAVER_LOADED) {
            forgeBus.register(com.example.qianmospeed.event.AdvancedRoadHandler.class);
            LOGGER.debug("已注册RoadWeaver高级事件处理器");
        }
    }
}