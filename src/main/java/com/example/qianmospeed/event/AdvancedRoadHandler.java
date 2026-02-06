package com.example.qianmospeed.event;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/**
 * 高级道路处理器 - RoadWeaver 集成
 * 通过 RoadWeaver API 直接查询道路数据，提供精确的道路检测
 */
@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancedRoadHandler {

    private static boolean initialized = false;
    private static boolean roadWeaverAvailable = false;

    // 缓存：ChunkPos -> 该区块内的道路段
    private static final Map<ChunkCacheKey, List<RoadSegmentCache>> chunkCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 500;

    /**
     * 初始化 RoadWeaver 集成
     */
    public static void init() {
        if (initialized)
            return;
        initialized = true;

        // 检查 RoadWeaver 是否加载
        if (!ModList.get().isLoaded("roadweaver")) {
            QianmoSpeedMod.LOGGER.info("RoadWeaver 未加载，高级道路处理器不可用");
            return;
        }

        try {
            // 检查必需的类是否存在
            Class.forName("net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage");
            Class.forName("net.shiroha233.roadweaver.helpers.Records");
            Class.forName("net.shiroha233.roadweaver.persistence.WorldDataProvider");

            roadWeaverAvailable = true;
            QianmoSpeedMod.LOGGER.info("RoadWeaver 集成已启用 - 使用高级道路处理器");
        } catch (ClassNotFoundException e) {
            roadWeaverAvailable = false;
            QianmoSpeedMod.LOGGER.warn("RoadWeaver 类未找到，集成不可用: {}", e.getMessage());
        } catch (Exception e) {
            roadWeaverAvailable = false;
            QianmoSpeedMod.LOGGER.error("RoadWeaver 集成初始化失败", e);
        }
    }

    /**
     * 检查 RoadWeaver 集成是否可用
     */
    public static boolean isAvailable() {
        return roadWeaverAvailable;
    }

    /**
     * 检查指定位置是否是 RoadWeaver 道路
     * 
     * @param level ServerLevel
     * @param pos   位置
     * @return 道路类型
     */
    public static RoadType checkRoadType(ServerLevel level, BlockPos pos) {
        if (!roadWeaverAvailable)
            return RoadType.NONE;

        try {
            // 1. 检查区块缓存
            ChunkCacheKey cacheKey = new ChunkCacheKey(
                    level.dimension().location().toString(),
                    new ChunkPos(pos));
            List<RoadSegmentCache> segments = chunkCache.get(cacheKey);

            // 2. 如果缓存未命中，查询数据库
            if (segments == null) {
                segments = loadRoadSegmentsForChunk(level, new ChunkPos(pos));

                // 更新缓存
                if (chunkCache.size() >= MAX_CACHE_SIZE) {
                    chunkCache.clear(); // 简单清理策略
                }
                chunkCache.put(cacheKey, segments);
            }

            // 3. 检查位置是否在某个道路段内
            for (RoadSegmentCache segment : segments) {
                if (segment.containsPosition(pos)) {
                    return segment.roadType;
                }
            }

            return RoadType.NONE;
        } catch (Exception e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.error("RoadWeaver 道路检测失败", e);
            }
            return RoadType.NONE;
        }
    }

    /**
     * 从 RoadWeaver 数据库加载区块内的道路段
     */
    private static List<RoadSegmentCache> loadRoadSegmentsForChunk(ServerLevel level, ChunkPos chunk) {
        List<RoadSegmentCache> result = new ArrayList<>();

        //添加调试日志
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== RoadWeaver 数据查询 ==========");
            QianmoSpeedMod.LOGGER.debug("区块: {}", chunk);
            QianmoSpeedMod.LOGGER.debug("维度: {}", level.dimension().location());
        }

        try {
            // 使用反射调用 RoadWeaver API
            Class<?> storageClass = Class.forName("net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage");

            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            int maxX = chunk.getMaxBlockX();
            int maxZ = chunk.getMaxBlockZ();

            //添加调试日志
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("查询范围: X=[{}, {}], Z=[{}, {}]", minX, maxX, minZ, maxZ);
            }

            // 调用 RoadShardStorage.queryRect(level, minX, minZ, maxX, maxZ)
            var queryMethod = storageClass.getDeclaredMethod("queryRect",
                    ServerLevel.class, int.class, int.class, int.class, int.class);

            @SuppressWarnings("unchecked")
            List<Object> roadDataList = (List<Object>) queryMethod.invoke(null, level, minX, minZ, maxX, maxZ);

            //添加调试日志
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                if (roadDataList == null) {
                    QianmoSpeedMod.LOGGER.debug("查询结果: null");
                } else {
                    QianmoSpeedMod.LOGGER.debug("查询结果: 找到 {} 条道路数据", roadDataList.size());
                }
            }

            if (roadDataList == null || roadDataList.isEmpty()) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("该区块没有 RoadWeaver 道路数据");
                }
                return result;
            }

            // 获取 RoadData 的方法
            Class<?> roadDataClass = Class.forName("net.shiroha233.roadweaver.helpers.Records$RoadData");
            var roadTypeMethod = roadDataClass.getDeclaredMethod("roadType");
            var widthMethod = roadDataClass.getDeclaredMethod("width");
            var segmentsMethod = roadDataClass.getDeclaredMethod("roadSegmentList");

            // 获取 RoadSegmentPlacement 的方法
            Class<?> segmentClass = Class.forName("net.shiroha233.roadweaver.helpers.Records$RoadSegmentPlacement");
            var middlePosMethod = segmentClass.getDeclaredMethod("middlePos");
            var positionsMethod = segmentClass.getDeclaredMethod("positions");

            // 解析道路数据
            int totalSegments = 0;
            for (Object roadData : roadDataList) {
                int roadType = (int) roadTypeMethod.invoke(roadData);
                int width = (int) widthMethod.invoke(roadData);

                @SuppressWarnings("unchecked")
                List<Object> segments = (List<Object>) segmentsMethod.invoke(roadData);

                if (segments == null || segments.isEmpty())
                    continue;

                //添加调试日志
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  道路 - 类型: {}, 宽度: {}, 段数: {}",
                            roadType, width, segments.size());
                }

                // 遍历每个道路段
                for (Object segment : segments) {
                    BlockPos middle = (BlockPos) middlePosMethod.invoke(segment);

                    @SuppressWarnings("unchecked")
                    List<BlockPos> positions = (List<BlockPos>) positionsMethod.invoke(segment);

                    // 检查是否在当前区块内
                    ChunkPos segmentChunk = new ChunkPos(middle);
                    if (!segmentChunk.equals(chunk))
                        continue;

                    totalSegments++;

                    // 确定道路类型
                    RoadType type = switch (roadType) {
                        case 1 -> RoadType.HIGHWAY; // Highway
                        case 0 -> RoadType.ROAD; // 普通道路
                        default -> RoadType.NONE;
                    };

                    //添加调试日志
                    if (SpeedModConfig.isDebugMessagesEnabled()) {
                        QianmoSpeedMod.LOGGER.debug("    段中心: {}, 位置数: {}, 类型: {}",
                                middle, positions != null ? positions.size() : 0, type);
                    }

                    // 添加到结果
                    result.add(new RoadSegmentCache(middle, positions, width, type));
                }
            }

            //添加调试日志
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("区块 {} 解析完成: 总共 {} 个道路段", chunk, totalSegments);
                QianmoSpeedMod.LOGGER.debug("==========================================");
            }

        } catch (Exception e) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.error("加载 RoadWeaver 道路段失败", e);
            }
        }

        return result;
    }

    /**
     * 清理缓存（用于内存管理或维度切换）
     */
    public static void clearCache() {
        chunkCache.clear();
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("RoadWeaver 缓存已清理");
        }
    }

    // ========== 内部类 ==========

    /**
     * 区块缓存键
     */
    private record ChunkCacheKey(String dimensionId, ChunkPos chunk) {
    }

    /**
     * 道路段缓存
     */
    private static class RoadSegmentCache {
        @SuppressWarnings("unused")
        final BlockPos center;
        final List<BlockPos> positions;
        final int width;
        final RoadType roadType;
        final int minX, maxX, minZ, maxZ;

        RoadSegmentCache(BlockPos center, List<BlockPos> positions, int width, RoadType roadType) {
            this.center = center;
            this.positions = positions != null ? positions : Collections.singletonList(center);
            this.width = width;
            this.roadType = roadType;

            // 预计算边界框
            int tempMinX = center.getX(), tempMaxX = center.getX();
            int tempMinZ = center.getZ(), tempMaxZ = center.getZ();

            for (BlockPos pos : this.positions) {
                tempMinX = Math.min(tempMinX, pos.getX());
                tempMaxX = Math.max(tempMaxX, pos.getX());
                tempMinZ = Math.min(tempMinZ, pos.getZ());
                tempMaxZ = Math.max(tempMaxZ, pos.getZ());
            }

            // 扩展边界（考虑道路宽度）
            int expand = width / 2 + 2;
            this.minX = tempMinX - expand;
            this.maxX = tempMaxX + expand;
            this.minZ = tempMinZ - expand;
            this.maxZ = tempMaxZ + expand;
        }

        /**
         * 检查位置是否在道路段内
         */
        boolean containsPosition(BlockPos pos) {
            // 快速边界检查
            if (pos.getX() < minX || pos.getX() > maxX ||
                    pos.getZ() < minZ || pos.getZ() > maxZ) {
                return false;
            }

            // 精确检查：计算到道路中心线的距离
            double minDistSq = Double.MAX_VALUE;

            for (BlockPos roadPos : positions) {
                double distSq = pos.distSqr(roadPos);
                minDistSq = Math.min(minDistSq, distSq);
            }

            // 判断是否在道路宽度内
            double maxDist = (width / 2.0) + 2.0; // 稍微放宽一点
            return minDistSq <= maxDist * maxDist;
        }
    }

    /**
     * 道路类型枚举
     */
    public enum RoadType {
        NONE, // 不是道路
        ROAD, // 普通道路 (roadType == 0)
        HIGHWAY // 高速公路 (roadType == 1)
    }
}