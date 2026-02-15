package com.example.qianmospeed.event;

import com.example.qianmospeed.config.SpeedModConfig; 
import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.util.RoadWeaverH2Helper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancedRoadHandler {

    private static boolean initialized = false;
    private static boolean roadWeaverAvailable = false;

    public static void init() {
        if (initialized)
            return;
        initialized = true;

        // ========== 新增：检查配置开关 ==========
        if (!SpeedModConfig.isRoadWeaverIntegrationEnabled()) {
            roadWeaverAvailable = false;
            QianmoSpeedMod.LOGGER.info("阡陌交通集成已通过配置禁用");
            return;
        }

        if (!ModList.get().isLoaded("roadweaver")) {
            QianmoSpeedMod.LOGGER.info("RoadWeaver 未加载，规划数据读取器不可用");
            return;
        }

        RoadWeaverH2Helper.init();
        roadWeaverAvailable = RoadWeaverH2Helper.isAvailable();

        if (roadWeaverAvailable) {
            QianmoSpeedMod.LOGGER.info("✅ 阡陌交通规划数据读取器已启用");
        }
    }

    // ========== ⭐⭐⭐ 关闭方法 ⭐⭐⭐ ==========
    public static void shutdown() {
        if (roadWeaverAvailable) {
            RoadWeaverH2Helper.shutdown();
        }
        initialized = false;
        roadWeaverAvailable = false;
    }

    public static boolean isAvailable() {
        return roadWeaverAvailable;
    }

    public static boolean isPlannedChunk(ServerLevel level, ChunkPos chunk) {
        if (!roadWeaverAvailable)
            return false;
        return RoadWeaverH2Helper.isPlannedChunk(level, chunk);
    }

    public static java.util.Set<Long> getPlannedChunks(ServerLevel level) {
        if (!roadWeaverAvailable)
            return java.util.Collections.emptySet();
        return RoadWeaverH2Helper.getPlannedChunks(level);
    }

    public static void clearCache(ServerLevel level) {
        if (roadWeaverAvailable && level != null) {
            RoadWeaverH2Helper.clearCache(level.dimension().location().toString());
        }
    }
    /**
     * 已弃用
     * public enum RoadType {
     * NONE,
     * ROAD,
     * HIGHWAY
     * }
     */
}