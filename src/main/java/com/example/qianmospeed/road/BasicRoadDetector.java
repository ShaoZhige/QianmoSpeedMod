package com.example.qianmospeed.road;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.config.SpeedModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public class BasicRoadDetector implements RoadDetectionFactory.IRoadDetector {

    @Override
    public boolean isOnRoad(Level level, BlockPos pos) {
        // 1. 基础检查：是不是道路方块
        if (!isRoadBlock(level, pos)) {
            return false;
        }

        // 2. 如果启用了方向检测，进行方向检查
        //修改：移除对不完整方块的跳过逻辑，所有方块都进行方向检测
        if (SpeedModConfig.isDirectionalDetectionEnabled()) {
            return isDirectionalRoad(level, pos);
        }
        
        // 3. 未启用方向检测时直接通过
        return true;
    }

    /**
     * 方向检测：严格的双方向检测
     * 新规则：X和Z方向都必须满足最小长度要求
     *        即：minLength x minLength 的最小网格才被视为道路
     */
    private boolean isDirectionalRoad(Level level, BlockPos pos) {
        // 检查X方向连续长度
        int xLength = calculateDirectionalLength(level, pos, true);
        
        // 检查Z方向连续长度
        int zLength = calculateDirectionalLength(level, pos, false);
        
        int minLength = SpeedModConfig.getMinDirectionalLength();
        int maxLength = SpeedModConfig.getMaxDirectionalLength();
        
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("方向检测 - 位置: {}, X长度: {}, Z长度: {} (要求: 最小{}x{}, 最大{}x{})",
                pos, xLength, zLength, minLength, minLength, maxLength, maxLength);
        }
        
        //新逻辑：两个方向都必须满足最小长度要求
        boolean xMeetsMin = xLength >= minLength;
        boolean zMeetsMin = zLength >= minLength;
        
        //两个方向都不能超过最大长度（避免地板/广场）
        boolean xWithinMax = xLength <= maxLength;
        boolean zWithinMax = zLength <= maxLength;
        
        // 情况1：完美道路（两个方向都在有效范围内）
        if (xMeetsMin && zMeetsMin && xWithinMax && zWithinMax) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 完美道路 ({}x{} 在有效范围内)", xLength, zLength);
            }
            return true;
        }
        
        // 情况2：一个方向太长（可能是地板/广场）
        if ((!xWithinMax && zMeetsMin) || (!zWithinMax && xMeetsMin)) {
            // 一个方向超出最大值，但另一个方向满足最小要求
            // 可能是窄长的走廊或边界，给予通过
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 边界/走廊道路 (一个方向超出但另一个有效)");
            }
            return true;
        }
        
        // 情况3：两个方向都超出最大值（绝对是地板/广场）
        if (!xWithinMax && !zWithinMax) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 地板/广场 (两个方向都超出最大值)");
            }
            return false;
        }
        
        // 情况4：两个方向都不满足最小要求（太小了）
        if (!xMeetsMin && !zMeetsMin) {
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 装饰/小平台 (两个方向都小于最小值)");
            }
            return false;
        }
        
        // 情况5：只有一个方向满足最小要求（如2x1、3x1等）
        if ((xMeetsMin && !zMeetsMin) || (!xMeetsMin && zMeetsMin)) {
            // 对于这种线性结构，需要更严格的相邻检查
            if (checkLinearStructure(level, pos, xLength, zLength)) {
                if (SpeedModConfig.isDebugMessagesEnabled()) {
                    QianmoSpeedMod.LOGGER.debug("  判定: 线性结构通过额外检查");
                }
                return true;
            }
            if (SpeedModConfig.isDebugMessagesEnabled()) {
                QianmoSpeedMod.LOGGER.debug("  判定: 线性结构未通过检查 (如2x1、3x1等)");
            }
            return false;
        }
        
        // 默认情况：不通过
        if (SpeedModConfig.isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.debug("  判定: 不符合任何道路特征");
        }
        return false;
    }

    /**
     * 检查线性结构（如2x1、3x1）是否可以作为道路
     */
    private boolean checkLinearStructure(Level level, BlockPos pos, int xLength, int zLength) {
        // 线性结构需要至少2个相邻道路方块
        int adjacentRoads = countAdjacentRoadBlocks(level, pos);
        
        // 或者形成连续的线性道路
        boolean isContinuousLine = checkContinuousLine(level, pos, xLength > zLength);
        
        return adjacentRoads >= 2 || isContinuousLine;
    }

    /**
     * 检查是否形成连续线性的道路
     */
    private boolean checkContinuousLine(Level level, BlockPos pos, boolean isXDirection) {
        int continuousCount = 1;
        int maxCheck = SpeedModConfig.getMaxDirectionalLength();
        
        // 检查正方向
        BlockPos current = pos;
        for (int i = 1; i <= maxCheck; i++) {
            if (isXDirection) {
                current = current.offset(1, 0, 0);
            } else {
                current = current.offset(0, 0, 1);
            }
            
            if (!isRoadBlock(level, current)) {
                break;
            }
            continuousCount++;
        }
        
        // 检查负方向
        current = pos;
        for (int i = 1; i <= maxCheck; i++) {
            if (isXDirection) {
                current = current.offset(-1, 0, 0);
            } else {
                current = current.offset(0, 0, -1);
            }
            
            if (!isRoadBlock(level, current)) {
                break;
            }
            continuousCount++;
        }
        
        // 线性道路需要至少达到最小长度
        return continuousCount >= SpeedModConfig.getMinDirectionalLength();
    }

    /**
     * 统计相邻道路方块数量（8方向）
     */
    private int countAdjacentRoadBlocks(Level level, BlockPos pos) {
        int count = 0;
        
        // 四方向相邻
        BlockPos[] fourDirections = {
            pos.north(), pos.south(), pos.east(), pos.west()
        };
        
        for (BlockPos adjPos : fourDirections) {
            if (isRoadBlock(level, adjPos)) {
                count++;
            }
        }
        
        // 如果四方向不够，检查对角线
        if (count < 2) {
            BlockPos[] diagonalDirections = {
                pos.north().east(), pos.north().west(),
                pos.south().east(), pos.south().west()
            };
            
            for (BlockPos adjPos : diagonalDirections) {
                if (isRoadBlock(level, adjPos)) {
                    count++;
                }
            }
        }
        
        return count;
    }

    /**
     * 计算一个方向上的连续道路长度
     */
    private int calculateDirectionalLength(Level level, BlockPos pos, boolean checkX) {
        int totalLength = 1; // 包括当前位置

        // 检查正方向
        int positiveLength = checkDirection(level, pos, checkX, true);
        totalLength += positiveLength;

        // 检查负方向
        int negativeLength = checkDirection(level, pos, checkX, false);
        totalLength += negativeLength;

        return totalLength;
    }

    /**
     * 检查单个方向上的连续道路
     */
    private int checkDirection(Level level, BlockPos startPos, boolean checkX, boolean positive) {
        int length = 0;
        int direction = positive ? 1 : -1;
        int maxCheck = SpeedModConfig.getMaxDirectionalLength() * 3; // 扩大搜索范围

        BlockPos currentPos = startPos;

        for (int i = 1; i <= maxCheck; i++) {
            // 移动到下一个位置
            if (checkX) {
                currentPos = currentPos.offset(direction, 0, 0);
            } else {
                currentPos = currentPos.offset(0, 0, direction);
            }

            //修改：只检查是否是道路方块，不检查是否完整
            if (!isRoadBlock(level, currentPos)) {
                break;
            }

            length++;
        }

        return length;
    }

    /**
     * 检查是否是道路方块
     */
    private boolean isRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();

        boolean isRoad = SpeedModConfig.isBasicRoadBlock(block);

        if (SpeedModConfig.isDebugMessagesEnabled() && isRoad) {
            String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
            QianmoSpeedMod.LOGGER.debug("基础检测 - 位置: {}, 方块: {}, 是道路方块",
                    pos, blockId);
        }

        return isRoad;
    }

    /**
     * 检查是否是基础模式道路方块
     */
    public boolean isBasicRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isBasicRoadBlock(block);
    }

    /**
     * 检查是否是高级模式道路方块
     */
    public boolean isAdvancedRoadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        return SpeedModConfig.isAdvancedRoadBlock(block);
    }
}