package com.example.qianmospeed.util;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.sql.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阡陌交通 H2 数据库工具类
 * 只读取规划数据，不修改任何数据
 * 可通过配置开关 enableRoadWeaverIntegration 禁用
 */
public class RoadWeaverH2Helper {

    // H2驱动路径（阡陌交通重定位后的路径）
    private static final String H2_DRIVER = "net.shiroha233.roadweaver.libs.h2.Driver";
    private static final String DB_FILE_NAME = "roadweaver";

    private static boolean h2Available = false;
    private static boolean initialized = false;

    // 缓存
    private static final Map<String, WorldDataCache> WORLD_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟

    private static class WorldDataCache {
        final Set<Long> plannedChunks = new HashSet<>();
        final Map<Long, Long> plannedCenters = new HashMap<>();
        final List<StructureConnection> connections = new ArrayList<>();
        long lastUpdateTime = 0;

        boolean isExpired() {
            return System.currentTimeMillis() - lastUpdateTime > CACHE_TTL;
        }
    }

    /**
     * 结构连接数据（简化版）
     */
    public static class StructureConnection {
        public final BlockPos from;
        public final BlockPos to;
        public final int roadType;

        public StructureConnection(BlockPos from, BlockPos to, int roadType) {
            this.from = from;
            this.to = to;
            this.roadType = roadType;
        }
    }

    /**
     * 初始化H2驱动
     * 如果配置中禁用了集成，则直接返回且不尝试加载驱动
     */
    public static void init() {
        if (initialized)
            return;
        initialized = true;

        // ========== 检查配置开关 ==========
        if (!SpeedModConfig.isRoadWeaverIntegrationEnabled()) {
            h2Available = false;
            QianmoSpeedMod.LOGGER.info("阡陌交通集成已通过配置禁用，将不使用规划数据");
            return;
        }

        try {
            Class.forName(H2_DRIVER);
            h2Available = true;
            QianmoSpeedMod.LOGGER.info("✅ 阡陌交通 H2 驱动加载成功，将读取规划数据优化检测");
        } catch (ClassNotFoundException e) {
            h2Available = false;
            QianmoSpeedMod.LOGGER.info("阡陌交通 H2 驱动不可用，将使用标准检测模式");
        }
    }

    /**
     * 关闭连接（释放资源）
     */
    public static void shutdown() {
        // H2 驱动不需要显式关闭全局连接，清理缓存即可
        WORLD_CACHE.clear();
        h2Available = false;
        initialized = false;
        QianmoSpeedMod.LOGGER.info("RoadWeaver H2 辅助器已关闭");
    }

    /**
     * 获取数据库连接
     */
    private static Connection getConnection(ServerLevel level) {
        if (!h2Available)
            return null;

        try {
            Path dbPath = Paths.get(
                    level.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "data", DB_FILE_NAME);

            String url = "jdbc:h2:" + dbPath.toString() + ";IFEXISTS=TRUE";
            return DriverManager.getConnection(url, "sa", "");
        } catch (SQLException e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("连接阡陌交通数据库失败: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * 核心API 1: 检查区块是否在规划中
     */
    public static boolean isPlannedChunk(ServerLevel level, ChunkPos chunk) {
        if (!h2Available)
            return false;

        WorldDataCache cache = getWorldCache(level);
        if (cache == null)
            return false;

        return cache.plannedChunks.contains(ChunkPos.asLong(chunk.x, chunk.z));
    }

    /**
     * 核心API 2: 获取区块规划的道路中心点
     */
    public static BlockPos getPlannedCenter(ServerLevel level, ChunkPos chunk) {
        if (!h2Available)
            return null;

        WorldDataCache cache = getWorldCache(level);
        if (cache == null)
            return null;

        Long center = cache.plannedCenters.get(ChunkPos.asLong(chunk.x, chunk.z));
        return center != null ? BlockPos.of(center) : null;
    }

    /**
     * 核心API 3: 获取所有规划中的区块
     */
    public static Set<Long> getPlannedChunks(ServerLevel level) {
        if (!h2Available)
            return Collections.emptySet();

        WorldDataCache cache = getWorldCache(level);
        if (cache == null)
            return Collections.emptySet();

        return Collections.unmodifiableSet(cache.plannedChunks);
    }

    /**
     * 核心API 4: 获取结构连接（道路网络）
     */
    public static List<StructureConnection> getConnections(ServerLevel level) {
        if (!h2Available)
            return Collections.emptyList();

        WorldDataCache cache = getWorldCache(level);
        if (cache == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(cache.connections);
    }

    /**
     * 获取世界数据缓存，如果过期则重新加载
     */
    private static WorldDataCache getWorldCache(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        WorldDataCache cache = WORLD_CACHE.computeIfAbsent(dimension, k -> new WorldDataCache());

        if (cache.isExpired()) {
            synchronized (cache) {
                if (cache.isExpired()) {
                    loadWorldData(level, cache);
                }
            }
        }

        return cache;
    }

    /**
     * 从H2数据库加载世界数据
     */
    private static void loadWorldData(ServerLevel level, WorldDataCache cache) {
        cache.plannedChunks.clear();
        cache.plannedCenters.clear();
        cache.connections.clear();

        try (Connection conn = getConnection(level)) {
            if (conn == null)
                return;

            String dimension = level.dimension().location().toString();

            // ========== 1. 查询规划区块 ==========
            String sql = "SELECT PLANNED_TILE_KEYS, PLANNED_TILE_CENTERS FROM WORLD_DATA WHERE DIMENSION = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, dimension);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String plannedKeysStr = rs.getString("PLANNED_TILE_KEYS");
                        if (plannedKeysStr != null && !plannedKeysStr.isEmpty()) {
                            parsePlannedChunks(plannedKeysStr, cache);
                        }

                        String plannedCentersStr = rs.getString("PLANNED_TILE_CENTERS");
                        if (plannedCentersStr != null && !plannedCentersStr.isEmpty()) {
                            parsePlannedCenters(plannedCentersStr, cache);
                        }
                    }
                }
            }

            // ========== 2. 查询结构连接 ==========
            String connSql = "SELECT CONNECTIONS FROM WORLD_DATA WHERE DIMENSION = ?";
            try (PreparedStatement stmt = conn.prepareStatement(connSql)) {
                stmt.setString(1, dimension);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String connectionsStr = rs.getString("CONNECTIONS");
                        if (connectionsStr != null && !connectionsStr.isEmpty()) {
                            parseConnections(connectionsStr, cache);
                        }
                    }
                }
            }

            cache.lastUpdateTime = System.currentTimeMillis();

            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("加载阡陌交通规划数据: 维度={}, 规划区块={}, 连接={}",
                        dimension, cache.plannedChunks.size(), cache.connections.size());
            }

        } catch (SQLException e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.error("读取阡陌交通数据库失败", e);
            }
        }
    }

    /**
     * 解析规划区块数据
     */
    private static void parsePlannedChunks(String data, WorldDataCache cache) {
        if (data.startsWith("[")) {
            String[] parts = data.substring(1, data.length() - 1).split(",");
            for (String part : parts) {
                try {
                    cache.plannedChunks.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException e) {
                    // 忽略解析失败
                }
            }
        }
    }

    /**
     * 解析规划中心点数据
     */
    private static void parsePlannedCenters(String data, WorldDataCache cache) {
        // 简化的解析，根据实际数据格式调整
        // 假设格式为 {chunk1:center1, chunk2:center2}
        if (data.startsWith("{") && data.endsWith("}")) {
            String inner = data.substring(1, data.length() - 1);
            String[] pairs = inner.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try {
                        Long chunk = Long.parseLong(kv[0].trim());
                        Long center = Long.parseLong(kv[1].trim());
                        cache.plannedCenters.put(chunk, center);
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
        }
    }

    /**
     * 解析结构连接数据
     */
    private static void parseConnections(String data, WorldDataCache cache) {
        // 这里根据实际的 JSON 格式进行解析
        // 暂时留空，可以根据后续需要实现
    }

    /**
     * 清理缓存
     */
    public static void clearCache(String dimension) {
        WORLD_CACHE.remove(dimension);
    }

    /**
     * 清理所有缓存
     */
    public static void clearAllCache() {
        WORLD_CACHE.clear();
    }

    /**
     * 检查H2是否可用
     */
    public static boolean isAvailable() {
        return h2Available;
    }
}