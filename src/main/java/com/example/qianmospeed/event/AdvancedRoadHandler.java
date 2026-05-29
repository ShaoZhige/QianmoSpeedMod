package com.example.qianmospeed.event;

import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.util.RoadWeaverH2Helper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 阡陌交通（RoadWeaver）高级道路处理器
 * <p>
 * 基于 RoadWeaverH2Helper 双数据源提供：
 * <ul>
 *   <li>规划区块查询</li>
 *   <li>连接数据查询（普通道路 + 高速公路）</li>
 *   <li>道路段精确检测</li>
 *   <li>道路类型判定（高速公路/已完成道路/规划中）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancedRoadHandler {

    private static boolean initialized = false;
    private static boolean roadWeaverAvailable = false;

    /**
     * 道路检测上下文结果
     */
    public enum RoadContext {
        /** 不在 RoadWeaver 道路网络上 */
        NONE(1.0),
        /** 规划中的区块 */
        PLANNED(1.0),
        /** 已完成的普通道路上 */
        COMPLETED_ROAD(1.0),
        /** 已完成的公路上 */
        HIGHWAY(1.0);

        private double defaultMultiplier = 1.0;

        RoadContext(double defaultMultiplier) {
            this.defaultMultiplier = defaultMultiplier;
        }

        public double getDefaultMultiplier() {
            return defaultMultiplier;
        }
    }

    // ==================== 初始化 ====================
    public static void init() {
        if (initialized) return;
        initialized = true;

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
            QianmoSpeedMod.LOGGER.info("✅ 阡陌交通数据读取器已启用");
        }
    }

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

    // ==================== 规划区块查询 ====================
    public static boolean isPlannedChunk(ServerLevel level, ChunkPos chunk) {
        if (!roadWeaverAvailable) return false;
        return RoadWeaverH2Helper.isPlannedChunk(level, chunk);
    }

    public static Set<Long> getPlannedChunks(ServerLevel level) {
        if (!roadWeaverAvailable) return Collections.emptySet();
        return RoadWeaverH2Helper.getPlannedChunks(level);
    }

    // ==================== 连接查询 ====================
    public static List<RoadWeaverH2Helper.StructureConnection> getConnections(ServerLevel level) {
        if (!roadWeaverAvailable) return Collections.emptyList();
        return RoadWeaverH2Helper.getConnections(level);
    }

    public static List<RoadWeaverH2Helper.StructureConnection> getHighwayConnections(ServerLevel level) {
        if (!roadWeaverAvailable) return Collections.emptyList();
        return RoadWeaverH2Helper.getHighwayConnections(level);
    }

    // ==================== 道路类型判定 ====================
    /**
     * 判断玩家所在位置属于哪种 RoadWeaver 道路类型
     */
    public static RoadContext getRoadContext(ServerLevel level, BlockPos pos) {
        if (!roadWeaverAvailable) return RoadContext.NONE;

        // 优先级：高速公路 > 已完成道路 > 道路段 > 规划中
        if (RoadWeaverH2Helper.isOnHighway(level, pos)) {
            return RoadContext.HIGHWAY;
        }

        if (RoadWeaverH2Helper.isOnCompletedRoad(level, pos)) {
            return RoadContext.COMPLETED_ROAD;
        }

        // 道路段精确检测（H2 数据）
        if (RoadWeaverH2Helper.isOnRoadSegment(level, pos)) {
            return RoadContext.COMPLETED_ROAD;
        }

        ChunkPos chunk = new ChunkPos(pos);
        if (RoadWeaverH2Helper.isPlannedChunk(level, chunk)) {
            return RoadContext.PLANNED;
        }

        return RoadContext.NONE;
    }

    /**
     * 判断是否在高速公路上
     */
    public static boolean isOnHighway(ServerLevel level, BlockPos pos) {
        if (!roadWeaverAvailable) return false;
        return RoadWeaverH2Helper.isOnHighway(level, pos);
    }

    /**
     * 判断是否在已完成的道路上
     */
    public static boolean isOnCompletedRoad(ServerLevel level, BlockPos pos) {
        if (!roadWeaverAvailable) return false;
        return RoadWeaverH2Helper.isOnCompletedRoad(level, pos);
    }

    /**
     * 判断是否在道路段上（精确）
     */
    public static boolean isOnRoadSegment(ServerLevel level, BlockPos pos) {
        if (!roadWeaverAvailable) return false;
        return RoadWeaverH2Helper.isOnRoadSegment(level, pos);
    }

    // ==================== 缓存管理 ====================
    public static void clearCache(ServerLevel level) {
        if (roadWeaverAvailable && level != null) {
            RoadWeaverH2Helper.clearCache(level.dimension().location().toString());
        }
    }
}