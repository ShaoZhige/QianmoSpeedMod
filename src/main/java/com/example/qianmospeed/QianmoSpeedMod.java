package com.example.qianmospeed;

import com.example.qianmospeed.config.ConfigInitializer;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.event.AdvancedRoadHandler;
import com.example.qianmospeed.registry.EnchantmentRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

@Mod(QianmoSpeedMod.MODID)
public class QianmoSpeedMod {
    public static final String MODID = "qianmospeed";
    public static final Logger LOGGER = LogManager.getLogger();

    // 检测到的道路相关模组
    public static final Set<String> DETECTED_ROAD_MODS = new HashSet<>();

    private static final Set<String> KNOWN_ROAD_MOD_IDS = Set.of(
            "roadweaver", "roadarchitect", "countereds_settlement_roads",
            "tongdaway", "townandtower", "repurposed_structures", "majruszsdifficulty");

    private static final Set<String> PROFESSIONAL_ROAD_MODS = Set.of(
            "roadweaver", "roadarchitect", "countereds_settlement_roads", "tongdaway");

    private static final java.util.Map<String, String> MOD_FRIENDLY_NAMES = java.util.Map.of(
            "roadweaver", "RoadWeaver",
            "roadarchitect", "Road Architect",
            "countereds_settlement_roads", "Countered's Settlement Roads",
            "tongdaway", "TongDaWay",
            "townandtower", "Town and Tower",
            "repurposed_structures", "Repurposed Structures",
            "majruszsdifficulty", "Majrusz's Progressive Difficulty");

    private static boolean hasShownAdvancedModeSuggestion = false;

    public QianmoSpeedMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpeedModConfig.SPEC);
        LOGGER.info("✅ 配置文件已注册");

        ConfigInitializer.init();
        LOGGER.info("✅ 配置系统初始化完成");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EnchantmentRegistry.register(modEventBus);
        LOGGER.info("✅ 附魔注册完成");

        // 在CommonSetup中初始化，避免过早加载
        modEventBus.addListener(this::commonSetup);

        // 注册Forge事件总线（用于服务器关闭事件）
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("✅ 事件总线注册完成");

        detectRoadMods();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (DETECTED_ROAD_MODS.contains("roadweaver")) {
                AdvancedRoadHandler.init();
            }
        });
    }

    private void detectRoadMods() {
        var loadedMods = ModList.get().getMods();
        boolean foundProfessionalMod = false;
        String firstProfessionalMod = null;

        for (var modInfo : loadedMods) {
            String modId = modInfo.getModId();

            if (KNOWN_ROAD_MOD_IDS.contains(modId)) {
                DETECTED_ROAD_MODS.add(modId);
                String friendlyName = MOD_FRIENDLY_NAMES.getOrDefault(modId, modId);
                LOGGER.info("检测到道路相关模组: {} ({})", friendlyName, modId);

                if (PROFESSIONAL_ROAD_MODS.contains(modId)) {
                    foundProfessionalMod = true;
                    if (firstProfessionalMod == null) {
                        firstProfessionalMod = friendlyName;
                    }
                }
            }
        }

        if (!DETECTED_ROAD_MODS.isEmpty()) {
            LOGGER.info("共检测到 {} 个道路相关模组", DETECTED_ROAD_MODS.size());

            if (foundProfessionalMod && !SpeedModConfig.isAdvancedFeaturesEnabled()) {
                LOGGER.info("检测到专业道路模组 {}，建议启用高级模式", firstProfessionalMod);
                hasShownAdvancedModeSuggestion = true;
            }
        } else {
            LOGGER.info("未检测到已知的道路相关模组");
        }
    }

    // ========== ⭐⭐⭐ 服务器关闭事件 ⭐⭐⭐ ==========
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 服务器停止时清理资源（游戏关闭时触发）
        if (DETECTED_ROAD_MODS.contains("roadweaver")) {
            AdvancedRoadHandler.shutdown();
            LOGGER.info("✅ RoadWeaver 资源已清理");
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // 服务器完全停止后的额外清理
        LOGGER.info("✅ 服务器已停止，资源清理完成");
    }

    // ========== 公共静态工具方法 ==========

    public static boolean hasDetectedRoadMods() {
        return !DETECTED_ROAD_MODS.isEmpty();
    }

    public static boolean hasDetectedProfessionalRoadMods() {
        for (String modId : DETECTED_ROAD_MODS) {
            if (PROFESSIONAL_ROAD_MODS.contains(modId)) {
                return true;
            }
        }
        return false;
    }

    public static java.util.List<String> getDetectedProfessionalModNames() {
        return DETECTED_ROAD_MODS.stream()
                .filter(PROFESSIONAL_ROAD_MODS::contains)
                .map(modId -> MOD_FRIENDLY_NAMES.getOrDefault(modId, modId))
                .sorted()
                .toList();
    }

    public static java.util.List<String> getDetectedRoadModNames() {
        return DETECTED_ROAD_MODS.stream()
                .map(modId -> MOD_FRIENDLY_NAMES.getOrDefault(modId, modId))
                .sorted()
                .toList();
    }

    public static boolean isRoadModLoaded(String modId) {
        return DETECTED_ROAD_MODS.contains(modId);
    }

    public static boolean hasShownAdvancedModeSuggestion() {
        return hasShownAdvancedModeSuggestion;
    }
}