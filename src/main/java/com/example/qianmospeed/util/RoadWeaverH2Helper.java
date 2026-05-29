package com.example.qianmospeed.util;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RoadWeaver 双数据源读取器（阡陌交通集成）
 * <p>
 * 数据来源：
 * <ul>
 *   <li><b>Forge SavedData</b> ("roadweaver_world_data") — 规划区块、规划中心、结构连接、公路连接</li>
 *   <li><b>H2 数据库</b> ("data/roadweaver/roads") — 道路段放置数据 (RoadData NBT BLOB)</li>
 * </ul>
 * <p>
 * 不依赖 RoadWeaver 编译时依赖，全部通过原生 NBT/H2 方式读取。
 * 可通过配置开关 {@code enableRoadWeaverIntegration} 禁用。
 */
public class RoadWeaverH2Helper {

    // ==================== 常量 ====================
    private static final String SAVED_DATA_KEY = "roadweaver_world_data";
    private static final String H2_DRIVER = "net.shiroha233.roadweaver.libs.h2.Driver";
    private static final String DB_DIR_NAME = "roadweaver";

    // SavedData NBT 键名（与 ForgeWorldDataProvider 对齐）
    private static final String KEY_PLANNED_TILES = "planned_tiles";
    private static final String KEY_PLANNED_TILE_CENTERS = "planned_tile_centers";
    private static final String KEY_CONNECTIONS = "connections";
    private static final String KEY_HIGHWAY_CONNECTIONS = "highway_connections";
    private static final String KEY_HIGHWAY_INTERSECTIONS = "highway_intersections";

    // ==================== 状态 ====================
    private static boolean h2Available = false;
    private static boolean savedDataAvailable = false;
    private static boolean initialized = false;

    // 缓存
    private static final Map<String, WorldDataCache> WORLD_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟

    // ==================== 内部缓存类 ====================
    private static class WorldDataCache {
        final Set<Long> plannedChunks = new HashSet<>();
        final Map<Long, Long> plannedCenters = new HashMap<>();
        final List<StructureConnection> connections = new ArrayList<>();
        final List<StructureConnection> highwayConnections = new ArrayList<>();
        final Map<Long, Long> highwayIntersections = new HashMap<>();
        // 道路段空间索引（按区块缓存 road segment positions）
        final Map<Long, Set<BlockPos>> roadSegmentPositions = new HashMap<>();
        long lastUpdateTime = 0;

        boolean isExpired() {
            return System.currentTimeMillis() - lastUpdateTime > CACHE_TTL;
        }
    }

    /**
     * 结构连接数据
     */
    public static class StructureConnection {
        public final BlockPos from;
        public final BlockPos to;
        public final String status; // PLANNED, GENERATING, COMPLETED, FAILED
        public final boolean isHighway;

        public StructureConnection(BlockPos from, BlockPos to, String status, boolean isHighway) {
            this.from = from;
            this.to = to;
            this.status = status;
            this.isHighway = isHighway;
        }

        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }

        @Override
        public String toString() {
            return String.format("Connection[%s → %s, %s]", from, to, status);
        }
    }

    // ==================== 初始化 ====================
    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!SpeedModConfig.isRoadWeaverIntegrationEnabled()) {
            h2Available = false;
            savedDataAvailable = false;
            QianmoSpeedMod.LOGGER.info("阡陌交通集成已通过配置禁用，将不使用规划数据");
            return;
        }

        // 尝试加载 H2 驱动
        try {
            Class.forName(H2_DRIVER);
            h2Available = true;
            QianmoSpeedMod.LOGGER.info("✅ 阡陌交通 H2 驱动加载成功");
        } catch (ClassNotFoundException e) {
            h2Available = false;
            QianmoSpeedMod.LOGGER.info("阡陌交通 H2 驱动不可用，将使用基础检测模式");
        }

        // SavedData 始终可用（只要有 ServerLevel 就能读取）
        savedDataAvailable = true;
        QianmoSpeedMod.LOGGER.info("✅ 阡陌交通 SavedData 读取器已就绪");
    }

    public static void shutdown() {
        WORLD_CACHE.clear();
        h2Available = false;
        savedDataAvailable = false;
        initialized = false;
        QianmoSpeedMod.LOGGER.info("RoadWeaver 数据读取器已关闭");
    }

    public static boolean isAvailable() {
        return h2Available || savedDataAvailable;
    }

    // ==================== SavedData 读取 ====================

    /**
     * 获取 RoadWeaver 的 Forge SavedData NBT。
     * 直接读取 NBT 文件，不依赖 RoadWeaver 编译时类。
     * 
     * 文件路径：&lt;world&gt;/dimensions/&lt;ns&gt;/&lt;path&gt;/data/roadweaver_world_data.dat
     */
    private static CompoundTag getSavedData(ServerLevel level) {
        try {
            // 构建维度数据目录路径
            Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
            String ns = level.dimension().location().getNamespace();
            String path = level.dimension().location().getPath();
            Path dataFile = worldRoot.resolve("dimensions").resolve(ns).resolve(path)
                    .resolve("data").resolve(SAVED_DATA_KEY + ".dat");

            if (!Files.exists(dataFile)) {
                return null;
            }
            return NbtIo.readCompressed(dataFile.toFile());
        } catch (IOException e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("读取 RoadWeaver SavedData 文件失败: {}", e.getMessage());
            }
            return null;
        }
    }

    // ==================== 核心API 1: 规划区块 ====================

    public static boolean isPlannedChunk(ServerLevel level, ChunkPos chunk) {
        if (!savedDataAvailable) return false;
        WorldDataCache cache = getWorldCache(level);
        return cache != null && cache.plannedChunks.contains(ChunkPos.asLong(chunk.x, chunk.z));
    }

    public static BlockPos getPlannedCenter(ServerLevel level, ChunkPos chunk) {
        if (!savedDataAvailable) return null;
        WorldDataCache cache = getWorldCache(level);
        if (cache == null) return null;
        Long center = cache.plannedCenters.get(ChunkPos.asLong(chunk.x, chunk.z));
        return center != null ? BlockPos.of(center) : null;
    }

    public static Set<Long> getPlannedChunks(ServerLevel level) {
        if (!savedDataAvailable) return Collections.emptySet();
        WorldDataCache cache = getWorldCache(level);
        return cache != null ? Collections.unmodifiableSet(cache.plannedChunks) : Collections.emptySet();
    }

    // ==================== 核心API 2: 结构连接 ====================

    /**
     * 获取所有普通道路连接
     */
    public static List<StructureConnection> getConnections(ServerLevel level) {
        if (!savedDataAvailable) return Collections.emptyList();
        WorldDataCache cache = getWorldCache(level);
        return cache != null ? Collections.unmodifiableList(cache.connections) : Collections.emptyList();
    }

    /**
     * 获取所有公路连接
     */
    public static List<StructureConnection> getHighwayConnections(ServerLevel level) {
        if (!savedDataAvailable) return Collections.emptyList();
        WorldDataCache cache = getWorldCache(level);
        return cache != null ? Collections.unmodifiableList(cache.highwayConnections) : Collections.emptyList();
    }

    /**
     * 判断指定位置是否在已完成的连接路段上（粗略判断，基于连接端点）
     */
    public static boolean isOnCompletedRoad(ServerLevel level, BlockPos pos) {
        if (!savedDataAvailable) return false;
        WorldDataCache cache = getWorldCache(level);
        if (cache == null) return false;

        // 检查普通道路连接
        for (StructureConnection conn : cache.connections) {
            if (!conn.isCompleted()) continue;
            if (isPointNearLine(pos, conn.from, conn.to, 300)) return true;
        }
        // 检查公路连接
        for (StructureConnection conn : cache.highwayConnections) {
            if (!conn.isCompleted()) continue;
            if (isPointNearLine(pos, conn.from, conn.to, 300)) return true;
        }
        return false;
    }

    /**
     * 判断指定位置是否在高速公路上
     */
    public static boolean isOnHighway(ServerLevel level, BlockPos pos) {
        if (!savedDataAvailable) return false;
        WorldDataCache cache = getWorldCache(level);
        if (cache == null) return false;

        for (StructureConnection conn : cache.highwayConnections) {
            if (!conn.isCompleted()) continue;
            if (isPointNearLine(pos, conn.from, conn.to, 300)) return true;
        }
        return false;
    }

    /**
     * 判断点是否在两点连线的指定距离范围内
     */
    private static boolean isPointNearLine(BlockPos point, BlockPos lineStart, BlockPos lineEnd, double maxDistance) {
        // 只考虑 XZ 平面
        double px = point.getX(), pz = point.getZ();
        double ax = lineStart.getX(), az = lineStart.getZ();
        double bx = lineEnd.getX(), bz = lineEnd.getZ();

        double dx = bx - ax, dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        if (lenSq < 1.0) {
            // 连接端点重合
            double dist = Math.sqrt((px - ax) * (px - ax) + (pz - az) * (pz - az));
            return dist <= maxDistance;
        }

        // 投影参数 t（限制在 [0, 1] 内）
        double t = ((px - ax) * dx + (pz - az) * dz) / lenSq;
        t = Math.max(0, Math.min(1, t));

        double projX = ax + t * dx, projZ = az + t * dz;
        double dist = Math.sqrt((px - projX) * (px - projX) + (pz - projZ) * (pz - projZ));
        return dist <= maxDistance;
    }

    // ==================== 核心API 3: 道路段精确数据 (H2) ====================

    /**
     * 判断指定位置是否在 RoadWeaver 生成的路面上（精确，通过 H2 道路段数据）
     */
    public static boolean isOnRoadSegment(ServerLevel level, BlockPos pos) {
        if (!h2Available) return false;
        WorldDataCache cache = getWorldCache(level);
        if (cache == null) return false;

        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> positions = cache.roadSegmentPositions.get(chunkKey);
        if (positions == null) return false;
        return positions.contains(pos);
    }

    // ==================== 缓存加载 ====================
    private static WorldDataCache getWorldCache(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        WorldDataCache cache = WORLD_CACHE.computeIfAbsent(dimension, k -> new WorldDataCache());

        if (cache.isExpired()) {
            synchronized (cache) {
                if (cache.isExpired()) {
                    loadSavedData(level, cache);
                    if (h2Available) {
                        loadRoadSegments(level, cache);
                    }
                }
            }
        }
        return cache;
    }

    /**
     * 从 Forge SavedData 加载规划数据和连接数据
     */
    private static void loadSavedData(ServerLevel level, WorldDataCache cache) {
        cache.plannedChunks.clear();
        cache.plannedCenters.clear();
        cache.connections.clear();
        cache.highwayConnections.clear();
        cache.highwayIntersections.clear();

        CompoundTag tag = getSavedData(level);
        if (tag == null) {
            cache.lastUpdateTime = System.currentTimeMillis();
            return;
        }

        // 1. 解析 planned_tiles (LongArray)
        if (tag.contains(KEY_PLANNED_TILES, Tag.TAG_LONG_ARRAY)) {
            long[] tiles = tag.getLongArray(KEY_PLANNED_TILES);
            for (long tile : tiles) {
                cache.plannedChunks.add(tile);
            }
        }

        // 2. 解析 planned_tile_centers (Compound: String→Long)
        if (tag.contains(KEY_PLANNED_TILE_CENTERS, Tag.TAG_COMPOUND)) {
            CompoundTag centers = tag.getCompound(KEY_PLANNED_TILE_CENTERS);
            for (String key : centers.getAllKeys()) {
                try {
                    long chunkKey = Long.parseLong(key);
                    long centerKey = centers.getLong(key);
                    cache.plannedCenters.put(chunkKey, centerKey);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. 解析 connections (ListTag)
        if (tag.contains(KEY_CONNECTIONS, Tag.TAG_LIST)) {
            parseConnectionList(tag.getList(KEY_CONNECTIONS, Tag.TAG_COMPOUND), cache.connections, false);
        }

        // 4. 解析 highway_connections (ListTag)
        if (tag.contains(KEY_HIGHWAY_CONNECTIONS, Tag.TAG_LIST)) {
            parseConnectionList(tag.getList(KEY_HIGHWAY_CONNECTIONS, Tag.TAG_COMPOUND), cache.highwayConnections, true);
        }

        cache.lastUpdateTime = System.currentTimeMillis();

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("加载阡陌交通数据: 维度={}, 规划区块={}, 连接={}, 公路={}",
                    level.dimension().location(), cache.plannedChunks.size(),
                    cache.connections.size(), cache.highwayConnections.size());
        }
    }

    /**
     * 解析连接列表 NBT
     * 格式：[{from:[x,y,z], to:[x,y,z], status:"COMPLETED"}, ...]
     */
    private static void parseConnectionList(ListTag list, List<StructureConnection> target, boolean isHighway) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag connTag = list.getCompound(i);

            BlockPos from = readBlockPos(connTag, "from");
            BlockPos to = readBlockPos(connTag, "to");
            String status = connTag.contains("status", Tag.TAG_STRING)
                    ? connTag.getString("status") : "UNKNOWN";

            if (from != null && to != null) {
                target.add(new StructureConnection(from, to, status, isHighway));
            }
        }
    }

    /**
     * 从 NBT 中读取 BlockPos（格式：[x, y, z] 的 IntArray）
     */
    private static BlockPos readBlockPos(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_INT_ARRAY)) return null;
        int[] arr = parent.getIntArray(key);
        if (arr.length >= 3) {
            return new BlockPos(arr[0], arr[1], arr[2]);
        }
        return null;
    }

    // ==================== H2 道路段数据加载 ====================

    /**
     * 从 H2 数据库加载道路段位置数据
     */
    private static void loadRoadSegments(ServerLevel level, WorldDataCache cache) {
        cache.roadSegmentPositions.clear();

        try (Connection conn = getH2Connection(level)) {
            if (conn == null) return;

            String dimensionKey = level.dimension().location().toString();
            // 尝试找到对应的数据库文件
            String sql = "SELECT data FROM roads";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    byte[] blob = rs.getBytes("data");
                    if (blob == null) continue;
                    parseRoadDataBlob(blob, cache);
                }
            }

            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("加载阡陌交通道路段数据: {} 个区块有道路段",
                        cache.roadSegmentPositions.size());
            }

        } catch (SQLException e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("读取阡陌交通 roads 表失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 解析 RoadData NBT BLOB，提取道路段位置
     * RoadData 序列化格式：
     * {road: {placements: [{middle_pos: [x,y,z], positions: [[x,y,z],...]}, ...]}}
     */
    private static void parseRoadDataBlob(byte[] blob, WorldDataCache cache) {
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(blob));
            if (root == null) return;

            CompoundTag roadTag = root.getCompound("road");
            if (roadTag.isEmpty()) return;

            // 解析 placements 列表
            if (roadTag.contains("placements", Tag.TAG_LIST)) {
                ListTag placements = roadTag.getList("placements", Tag.TAG_COMPOUND);
                for (int i = 0; i < placements.size(); i++) {
                    CompoundTag placement = placements.getCompound(i);

                    if (placement.contains("positions", Tag.TAG_LIST)) {
                        ListTag posList = placement.getList("positions", Tag.TAG_INT_ARRAY);
                        for (int j = 0; j < posList.size(); j++) {
                            int[] arr = posList.getIntArray(j);
                            if (arr.length >= 3) {
                                BlockPos pos = new BlockPos(arr[0], arr[1], arr[2]);
                                long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                                cache.roadSegmentPositions
                                        .computeIfAbsent(chunkKey, k -> new HashSet<>())
                                        .add(pos);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("解析 RoadData BLOB 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取 H2 数据库连接
     */
    private static Connection getH2Connection(ServerLevel level) {
        if (!h2Available) return null;
        try {
            // H2 数据库路径：<world>/data/roadweaver/<dim>/roads
            Path dbPath = Paths.get(
                    level.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "data", DB_DIR_NAME);

            // 查找维度对应的数据库文件
            // RoadWeaver 的存储结构：data/roadweaver/<dimension_namespace>_<dimension_path>/roads
            String dimKey = level.dimension().location().toString().replace(':', '_');
            Path dimDir = dbPath.resolve(dimKey);
            Path dbFile = dimDir.resolve("roads");

            if (!dbFile.toFile().exists()) {
                return null;
            }

            String url = "jdbc:h2:" + dbFile.toString() + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
            return DriverManager.getConnection(url, "sa", "");
        } catch (SQLException e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("连接阡陌交通 H2 数据库失败: {}", e.getMessage());
            }
            return null;
        }
    }

    // ==================== 缓存管理 ====================
    public static void clearCache(String dimension) {
        WORLD_CACHE.remove(dimension);
    }

    public static void clearAllCache() {
        WORLD_CACHE.clear();
    }
}