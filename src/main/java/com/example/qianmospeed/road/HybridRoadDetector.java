package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import com.example.qianmospeed.event.AdvancedRoadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 混合道路检测器 — 三级检测模式
 * <p>
 * 根据 RoadWeaver 道路上下文选择检测策略：
 * <ul>
 *   <li><b>高速公路/已完成道路</b> → 网络模式（最高灵敏度，确认是道路即认可）</li>
 *   <li><b>规划中区块</b> → 积极检测模式（较宽松的方向检测条件）</li>
 *   <li><b>未规划区块</b> → 标准检测模式</li>
 * </ul>
 * 所有具体检测逻辑委托给 BasicRoadDetector。
 */
public class HybridRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final Map<BlockPos, Boolean> cache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    /** 网络模式标尺：比积极模式更宽松 */
    private static final int NETWORK_MIN_LENGTH = 1;
    private static final int NETWORK_MAX_LENGTH = 999;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== HybridRoadDetector ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }

        // 客户端直接使用标准模式
        if (!(level instanceof ServerLevel serverLevel)) {
            boolean result = basicDetector.isOnRoad(level, pos, false);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("客户端模式，使用标准检测: {}", result);
            }
            return result;
        }

        // 检查缓存
        if (cache.containsKey(pos)) {
            boolean cached = cache.get(pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用缓存结果: {}", cached);
            }
            return cached;
        }

        // ========== 三级检测 ==========
        AdvancedRoadHandler.RoadContext context = AdvancedRoadHandler.getRoadContext(serverLevel, pos);

        boolean result;
        switch (context) {
            case HIGHWAY:
            case COMPLETED_ROAD:
                // 路网模式：最宽松 — 确认是道路就认
                result = basicDetector.isOnRoad(level, pos, NETWORK_MIN_LENGTH, NETWORK_MAX_LENGTH);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("路网模式 ({}): {}", context, result ? "是道路" : "非道路");
                }
                break;

            case PLANNED:
                // 积极模式 — 较宽松的方向检测
                result = basicDetector.isOnRoad(level, pos, true);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("积极模式 (规划中): {}", result ? "是道路" : "非道路");
                }
                break;

            default:
                // 标准模式
                result = basicDetector.isOnRoad(level, pos, false);
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("标准模式 (未规划): {}", result ? "是道路" : "非道路");
                }
                break;
        }

        // 更新缓存
        if (cache.size() >= CACHE_SIZE) {
            cache.clear();
        }
        cache.put(pos, result);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("======================================");
        }

        return result;
    }

    /**
     * 清除同一 chunk 内的缓存（性能优化）
     */
    public void clearChunkCache(int chunkX, int chunkZ) {
        cache.keySet().removeIf(pos ->
                pos.getX() >> 4 == chunkX && pos.getZ() >> 4 == chunkZ);
    }
}