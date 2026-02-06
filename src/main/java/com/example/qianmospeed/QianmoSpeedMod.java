package com.example.qianmospeed;

import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.registry.EnchantmentRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.example.qianmospeed.event.AdvancedRoadHandler; 

import java.util.HashSet;
import java.util.Set;

@Mod(QianmoSpeedMod.MODID)
public class QianmoSpeedMod {
    public static final String MODID = "qianmospeed";
    public static final Logger LOGGER = LogManager.getLogger();

    // 检测到的道路相关模组
    public static final Set<String> DETECTED_ROAD_MODS = new HashSet<>();

    // 已知的道路模组列表
    private static final Set<String> KNOWN_ROAD_MOD_IDS = Set.of(
            "roadweaver", // 阡陌交通
            "roadarchitect", // Road Architect
            "countereds_settlement_roads", // Countered's Settlement Roads
            "tongdaway", // 通达路
            "townandtower", // Town and Tower（如果有道路功能）
            "repurposed_structures", // 如果有道路结构
            "majruszsdifficulty" // 如果有道路相关内容
    );

    // 需要自动开启高级模式的"专业"道路模组
    private static final Set<String> PROFESSIONAL_ROAD_MODS = Set.of(
            "roadweaver", // 阡陌交通 - 专业道路建设
            "roadarchitect", // Road Architect - 专业道路设计
            "countereds_settlement_roads", // Countered's Settlement Roads - 专业道路
            "tongdaway" // 通达路 - 专业道路模组
    );

    // 模组友好名称映射
    private static final java.util.Map<String, String> MOD_FRIENDLY_NAMES = java.util.Map.of(
            "roadweaver", "RoadWeaver",
            "roadarchitect", "Road Architect",
            "countereds_settlement_roads", "Countered's Settlement Roads",
            "tongdaway", "TongDaWay",
            "townandtower", "Town and Tower",
            "repurposed_structures", "Repurposed Structures",
            "majruszsdifficulty", "Majrusz's Progressive Difficulty");

    // 用于标记是否已经提示过开启高级模式
    private static boolean hasShownAdvancedModeSuggestion = false;

    public QianmoSpeedMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpeedModConfig.SPEC);

        // 注册附魔
        EnchantmentRegistry.register(modEventBus);

        // 注册到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 检测道路相关模组
        detectRoadMods();

        // 初始化高级道路处理器
        if (DETECTED_ROAD_MODS.contains("roadweaver")) {
            AdvancedRoadHandler.init();
        }

        LOGGER.info("阡陌疾旅模组已加载");
    }

    /**
     * 检测已加载的道路相关模组
     */
    private void detectRoadMods() {
        // 获取所有已加载的模组
        var loadedMods = ModList.get().getMods();
        boolean foundProfessionalMod = false;
        String firstProfessionalMod = null;

        for (var modInfo : loadedMods) {
            String modId = modInfo.getModId();

            // 检查是否是我们知道的道路模组
            if (KNOWN_ROAD_MOD_IDS.contains(modId)) {
                DETECTED_ROAD_MODS.add(modId);
                String friendlyName = MOD_FRIENDLY_NAMES.getOrDefault(modId, modId);
                LOGGER.info("检测到道路相关模组: {} ({})", friendlyName, modId);

                // 检查是否是专业道路模组
                if (PROFESSIONAL_ROAD_MODS.contains(modId)) {
                    foundProfessionalMod = true;
                    if (firstProfessionalMod == null) {
                        firstProfessionalMod = friendlyName;
                    }
                }
            }
        }

        // 统计信息
        if (!DETECTED_ROAD_MODS.isEmpty()) {
            LOGGER.info("共检测到 {} 个道路相关模组", DETECTED_ROAD_MODS.size());

            // 如果检测到专业道路模组，检查是否需要开启高级模式
            if (foundProfessionalMod && !SpeedModConfig.isAdvancedFeaturesEnabled()) {
                // 这里只是记录日志，真正的开启逻辑在 RoadDetectionFactory 中
                LOGGER.info("检测到专业道路模组 {}，建议启用高级模式以获得最佳体验",
                        firstProfessionalMod);

                // 标记已检测到专业模组（用于后续逻辑）
                hasShownAdvancedModeSuggestion = true;
            }
        } else {
            LOGGER.info("未检测到已知的道路相关模组，使用基础道路检测");
        }
    }

    /**
     * 检查是否有检测到的道路模组
     */
    public static boolean hasDetectedRoadMods() {
        return !DETECTED_ROAD_MODS.isEmpty();
    }

    /**
     * 检查是否检测到专业道路模组
     */
    public static boolean hasDetectedProfessionalRoadMods() {
        for (String modId : DETECTED_ROAD_MODS) {
            if (PROFESSIONAL_ROAD_MODS.contains(modId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取检测到的专业道路模组名称
     */
    public static java.util.List<String> getDetectedProfessionalModNames() {
        return DETECTED_ROAD_MODS.stream()
                .filter(PROFESSIONAL_ROAD_MODS::contains)
                .map(modId -> MOD_FRIENDLY_NAMES.getOrDefault(modId, modId))
                .sorted()
                .toList();
    }

    /**
     * 获取检测到的道路模组友好名称列表
     */
    public static java.util.List<String> getDetectedRoadModNames() {
        return DETECTED_ROAD_MODS.stream()
                .map(modId -> MOD_FRIENDLY_NAMES.getOrDefault(modId, modId))
                .sorted()
                .toList();
    }

    /**
     * 检查特定模组是否已加载
     */
    public static boolean isRoadModLoaded(String modId) {
        return DETECTED_ROAD_MODS.contains(modId);
    }

    /**
     * 是否已经显示过高级模式建议
     */
    public static boolean hasShownAdvancedModeSuggestion() {
        return hasShownAdvancedModeSuggestion;
    }
}