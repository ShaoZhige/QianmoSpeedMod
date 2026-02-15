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
 * 混合道路检测器 - 简化版
 * 只负责根据区块规划状态选择检测模式（标准或积极）
 * 所有具体检测逻辑委托给 BasicRoadDetector
 */
public class HybridRoadDetector implements RoadDetectionFactory.IRoadDetector {
    private final BasicRoadDetector basicDetector = new BasicRoadDetector();
    private final Map<BlockPos, Boolean> cache = new HashMap<>();
    private static final int CACHE_SIZE = 500;

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("========== HybridRoadDetector ==========");
            QianmoSpeedMod.LOGGER.debug("位置: {}", pos);
        }

        // 客户端直接使用基础检测器（标准模式）
        if (!(level instanceof ServerLevel serverLevel)) {
            boolean result = basicDetector.isOnRoad(level, pos, false);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("客户端模式，使用标准检测: {}", result);
                QianmoSpeedMod.LOGGER.debug("======================================");
            }
            return result;
        }

        // 检查缓存
        if (cache.containsKey(pos)) {
            boolean cached = cache.get(pos);
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("使用缓存结果: {}", cached);
                QianmoSpeedMod.LOGGER.debug("======================================");
            }
            return cached;
        }

        boolean result;
        ChunkPos chunk = new ChunkPos(pos);

        // 根据规划状态选择检测模式
        boolean isPlanned = AdvancedRoadHandler.isAvailable() &&
                AdvancedRoadHandler.isPlannedChunk(serverLevel, chunk);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("区块 {} 规划状态: {}", chunk, isPlanned ? "规划中" : "未规划");
        }

        // ⭐⭐⭐ 统一使用 BasicRoadDetector，只传递不同的 aggressive 参数
        result = basicDetector.isOnRoad(level, pos, isPlanned);

        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("使用{}检测模式: {}",
                    isPlanned ? "积极" : "标准", result ? "是道路" : "非道路");
            QianmoSpeedMod.LOGGER.debug("======================================");
        }

        // 更新缓存
        if (cache.size() >= CACHE_SIZE) {
            cache.clear();
        }
        cache.put(pos, result);

        return result;
    }
}