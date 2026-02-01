package com.example.qianmospeed.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class SpeedModConfig {
    // 配置规范
    public static final ForgeConfigSpec SPEC;

    // 客户端配置
    public static final ForgeConfigSpec.BooleanValue DEBUG_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue LOGIN_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue SPEED_EFFECT_MESSAGES;

    // 游戏性配置
    public static final ForgeConfigSpec.IntValue CHECK_INTERVAL;

    // 速度加成配置
    public static final ForgeConfigSpec.DoubleValue SPEED_MULTIPLIER_1;
    public static final ForgeConfigSpec.DoubleValue SPEED_MULTIPLIER_2;
    public static final ForgeConfigSpec.DoubleValue SPEED_MULTIPLIER_3;

    // 高级功能配置
    public static final ForgeConfigSpec.BooleanValue ADVANCED_FEATURES;
    public static final ForgeConfigSpec.BooleanValue AUTO_ENABLE_ADVANCED;

    // 道路检测模式枚举
    public enum RoadDetectionMode {
        BASIC, // 基础模式：只检测配置的单个方块
        ENHANCED, // 增强模式：检测道路网络和连续性
        SMART // 智能模式：动态调整检测策略
    }

    public static final ForgeConfigSpec.EnumValue<RoadDetectionMode> ROAD_DETECTION_MODE;

    // ========== 方向检测配置 ==========

    // 方向检测开关（仅基础模式使用）
    public static final ForgeConfigSpec.BooleanValue DIRECTIONAL_DETECTION;

    // 方向检测的最小长度
    public static final ForgeConfigSpec.IntValue MIN_DIRECTIONAL_LENGTH;

    // 方向检测的最大长度
    public static final ForgeConfigSpec.IntValue MAX_DIRECTIONAL_LENGTH;

    // ========== 分开的道路方块配置 ==========

    // 基础模式道路方块列表
    private static List<String> basicRoadBlockIds = new java.util.ArrayList<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BASIC_ROAD_BLOCKS;

    // 高级模式道路方块列表（通常包含更多方块）
    private static List<String> advancedRoadBlockIds = new java.util.ArrayList<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADVANCED_ROAD_BLOCKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ========== 客户端配置 ==========
        builder.push("client");

        DEBUG_MESSAGES = builder
                .comment(
                        "启用调试消息（在控制台输出详细日志）",
                        "Enable debug messages (output detailed logs to console)",
                        "默认/Default: false")
                .define("debugMessages", false);

        LOGIN_MESSAGES = builder
                .comment(
                        "启用登录欢迎消息",
                        "Enable login welcome messages",
                        "默认/Default: true")
                .define("loginMessages", true);

        SPEED_EFFECT_MESSAGES = builder
                .comment(
                        "启用速度效果应用/移除的消息提示",
                        "Enable speed effect application/removal message prompts",
                        "默认/Default: false")
                .define("speedEffectMessages", false);

        builder.pop();

        // ========== 游戏性配置 ==========
        builder.push("gameplay");

        CHECK_INTERVAL = builder
                .comment(
                        "检查玩家是否在道路上的间隔（tick）",
                        "Interval for checking if player is on road (in ticks)",
                        "20 tick = 1秒/20 ticks = 1 second",
                        "最小值/Minimum: 1, 最大值/Maximum: 200",
                        "默认/Default: 40 (2秒/2 seconds)")
                .defineInRange("checkInterval", 40, 1, 200);

        builder.pop();

        // ========== 速度加成配置 ==========
        builder.push("speed_multipliers");

        SPEED_MULTIPLIER_1 = builder
                .comment(
                        "旅行的祝福 I 级速度加成倍数",
                        "Travel Blessings Level I speed multiplier",
                        "范围/Range: 1.0 - 5.0",
                        "1.0 = 无加成/No bonus, 1.4 = 40% 加速/40% speed increase",
                        "默认/Default: 1.4")
                .defineInRange("speedMultiplier1", 1.4, 1.0, 5.0);

        SPEED_MULTIPLIER_2 = builder
                .comment(
                        "旅行的祝福 II 级速度加成倍数",
                        "Travel Blessings Level II speed multiplier",
                        "范围/Range: 1.0 - 5.0",
                        "应大于等于 I 级/Must be >= Level I",
                        "默认/Default: 1.8")
                .defineInRange("speedMultiplier2", 1.8, 1.0, 5.0);

        SPEED_MULTIPLIER_3 = builder
                .comment(
                        "旅行的祝福 III 级速度加成倍数",
                        "Travel Blessings Level III speed multiplier",
                        "范围/Range: 1.0 - 5.0",
                        "应大于等于 II 级/Must be >= Level II",
                        "默认/Default: 2.2")
                .defineInRange("speedMultiplier3", 2.2, 1.0, 5.0);

        builder.pop();

        // ========== 方向检测配置 ==========
        builder.push("directional_detection");

        DIRECTIONAL_DETECTION = builder
                .comment(
                        "启用方向检测功能（仅基础模式有效）",
                        "Enable directional detection (only effective in basic mode)",
                        "功能：检查道路在X或Z方向上的连续长度",
                        "Function: Check continuous length of road in X or Z direction",
                        "用于区分道路和地板/广场",
                        "Used to distinguish roads from floors/plazas",
                        "默认/Default: true")
                .define("directionalDetection", true);

        MIN_DIRECTIONAL_LENGTH = builder
                .comment(
                        "道路最小连续长度（格数）",
                        "Minimum continuous length for roads (in blocks)",
                        "小于此值被视为地板/广场/装饰",
                        "Values less than this are considered floors/plazas/decorations",
                        "范围/Range: 1-20",
                        "默认/Default: 2")
                .defineInRange("minDirectionalLength", 2, 1, 20);

        MAX_DIRECTIONAL_LENGTH = builder
                .comment(
                        "道路最大连续长度（格数）",
                        "Maximum continuous length for roads (in blocks)",
                        "大于此值被视为地板/广场（无限延伸）",
                        "Values greater than this are considered floors/plazas (infinite extension)",
                        "范围/Range: 1-100",
                        "注意：此设置仅影响基础模式",
                        "Note: This setting only affects basic mode",
                        "默认/Default: 5")
                .defineInRange("maxDirectionalLength", 5, 1, 100);

        builder.pop();

        // ========== 高级功能配置 ==========
        builder.push("advanced_features");

        ADVANCED_FEATURES = builder
                .comment(
                        "启用高级道路检测功能",
                        "Enable advanced road detection features",
                        "功能包括：道路网络检测、连续性检查等",
                        "Features include: road network detection, continuity checking, etc.",
                        "检测到专业道路模组（如阡陌交通、Road Architect等）会自动启用",
                        "Professional road mods (like RoadWeaver, Road Architect) will auto-enable",
                        "注意：高级模式不使用方向检测",
                        "Note: Advanced mode does not use directional detection",
                        "默认/Default: false")
                .define("advancedFeaturesEnabled", false);

        AUTO_ENABLE_ADVANCED = builder
                .comment(
                        "检测到非专业道路模组时自动启用高级模式",
                        "Automatically enable advanced mode when non-professional road mods are detected",
                        "专业道路模组会强制启用，不受此设置影响",
                        "Professional road mods force enable, unaffected by this setting",
                        "默认/Default: false")
                .define("autoEnableAdvanced", false);

        ROAD_DETECTION_MODE = builder
                .comment(
                        "道路检测模式",
                        "Road detection mode",
                        "BASIC: 基础模式 - 只检测单个方块",
                        "ENHANCED: 增强模式 - 检测道路网络和连续性",
                        "SMART: 智能模式 - 根据情况动态调整",
                        "默认/Default: SMART")
                .defineEnum("roadDetectionMode", RoadDetectionMode.SMART);

        builder.pop();

        // ========== 基础模式道路方块配置 ==========
        builder.push("basic_road_blocks");

        BASIC_ROAD_BLOCKS = builder
                .comment(
                        "基础模式：被认为是道路的方块ID列表",
                        "Basic mode: List of block IDs considered as roads",
                        "只在基础模式下使用/Only used in basic mode",
                        "受方向检测影响/Affected by directional detection",
                        "格式/Format: modid:blockid")
                .defineList("basicRoadBlocks",
                        Arrays.asList(
                                "minecraft:dirt_path",
                                "minecraft:stone_bricks",
                                "minecraft:sandstone",
                                "minecraft:mud_bricks",
                                "minecraft:packed_mud",
                                "minecraft:mossy_stone_bricks",
                                "minecraft:cracked_stone_bricks",
                                "minecraft:polished_blackstone_bricks",
                                "minecraft:nether_bricks",
                                "minecraft:end_stone_bricks",
                                "minecraft:stone_brick_slab",
                                "minecraft:sandstone_slab",
                                "minecraft:polished_andesite_slab",
                                "minecraft:mud_brick_slab",
                                "minecraft:mossy_stone_brick_slab",
                                "minecraft:blackstone_slab",
                                "minecraft:nether_brick_slab",
                                "minecraft:end_stone_brick_slab",
                                "minecraft:oak_slab",
                                "minecraft:birch_slab",
                                "minecraft:spruce_slab",
                                "minecraft:cherry_slab",
                                "minecraft:acacia_slab",
                                "minecraft:dark_oak_slab",
                                "minecraft:jungle_slab",
                                "minecraft:mangrove_slab",
                                "minecraft:crimson_slab",
                                "minecraft:warped_slab",
                                "minecraft:polished_andesite",
                                "minecraft:polished_diorite",
                                "minecraft:polished_granite",
                                "minecraft:white_concrete",
                                "minecraft:orange_concrete",
                                "minecraft:magenta_concrete",
                                "minecraft:light_blue_concrete",
                                "minecraft:yellow_concrete",
                                "minecraft:lime_concrete",
                                "minecraft:pink_concrete",
                                "minecraft:gray_concrete",
                                "minecraft:light_gray_concrete",
                                "minecraft:cyan_concrete",
                                "minecraft:purple_concrete",
                                "minecraft:blue_concrete",
                                "minecraft:brown_concrete",
                                "minecraft:green_concrete",
                                "minecraft:red_concrete",
                                "minecraft:black_concrete"),
                        o -> o instanceof String);

        builder.pop();

        // ========== 高级模式道路方块配置 ==========
        builder.push("advanced_road_blocks");

        ADVANCED_ROAD_BLOCKS = builder
                .comment(
                        "高级模式：被认为是道路的方块ID列表",
                        "Advanced mode: List of block IDs considered as roads",
                        "只在高级模式下使用/Only used in advanced mode",
                        "不受方向检测影响/Not affected by directional detection",
                        "可以包含更多种类的方块，因为高级模式有连续性检测",
                        "Can include more types of blocks because advanced mode has continuity checking",
                        "包含基础模式的方块，并添加更多选择",
                        "Includes basic mode blocks and adds more options",
                        "为专业道路模组优化",
                        "Optimized for professional road mods")
                .defineList("advancedRoadBlocks",
                        Arrays.asList(
                                "minecraft:dirt_path",
                                "minecraft:dirt",
                                "minecraft:stone_bricks",
                                "minecraft:sandstone",
                                "minecraft:mud_bricks",
                                "minecraft:packed_mud",
                                "minecraft:mossy_stone_bricks",
                                "minecraft:cracked_stone_bricks",
                                "minecraft:polished_blackstone_bricks",
                                "minecraft:nether_bricks",
                                "minecraft:end_stone_bricks",
                                "minecraft:stone_brick_slab",
                                "minecraft:sandstone_slab",
                                "minecraft:polished_andesite_slab",
                                "minecraft:mud_brick_slab",
                                "minecraft:mossy_stone_brick_slab",
                                "minecraft:blackstone_slab",
                                "minecraft:nether_brick_slab",
                                "minecraft:end_stone_brick_slab",
                                "minecraft:oak_slab",
                                "minecraft:birch_slab",
                                "minecraft:spruce_slab",
                                "minecraft:cherry_slab",
                                "minecraft:acacia_slab",
                                "minecraft:dark_oak_slab",
                                "minecraft:jungle_slab",
                                "minecraft:mangrove_slab",
                                "minecraft:crimson_slab",
                                "minecraft:warped_slab",
                                "minecraft:polished_andesite",
                                "minecraft:polished_diorite",
                                "minecraft:polished_granite",
                                "minecraft:white_concrete",
                                "minecraft:orange_concrete",
                                "minecraft:magenta_concrete",
                                "minecraft:light_blue_concrete",
                                "minecraft:yellow_concrete",
                                "minecraft:lime_concrete",
                                "minecraft:pink_concrete",
                                "minecraft:gray_concrete",
                                "minecraft:light_gray_concrete",
                                "minecraft:cyan_concrete",
                                "minecraft:purple_concrete",
                                "minecraft:blue_concrete",
                                "minecraft:brown_concrete",
                                "minecraft:green_concrete",
                                "minecraft:red_concrete",
                                "minecraft:black_concrete",
                                "minecraft:stone",
                                "minecraft:andesite",
                                "minecraft:diorite",
                                "minecraft:granite",
                                "minecraft:gravel",
                                "minecraft:sand",
                                "minecraft:coarse_dirt",
                                "minecraft:moss_block",
                                "minecraft:rooted_dirt",
                                "minecraft:podzol",
                                "minecraft:snow_block",
                                "minecraft:packed_ice",
                                "minecraft:red_sand",
                                "minecraft:terracotta",
                                "minecraft:mud",
                                "minecraft:muddy_mangrove_roots"),
                        o -> o instanceof String);

        builder.pop();

        SPEC = builder.build();
    }

    // ========== 配置值获取方法 ==========

    // 客户端配置
    public static boolean isDebugMessagesEnabled() {
        return DEBUG_MESSAGES.get();
    }

    public static boolean isLoginMessagesEnabled() {
        return LOGIN_MESSAGES.get();
    }

    public static boolean isSpeedEffectMessagesEnabled() {
        return SPEED_EFFECT_MESSAGES.get();
    }

    // 游戏性配置
    public static int getCheckInterval() {
        return CHECK_INTERVAL.get();
    }

    // 速度加成配置
    public static double getSpeedMultiplier(int level) {
        return switch (level) {
            case 1 -> SPEED_MULTIPLIER_1.get();
            case 2 -> SPEED_MULTIPLIER_2.get();
            case 3 -> SPEED_MULTIPLIER_3.get();
            default -> 1.0;
        };
    }

    // 高级功能配置
    public static boolean isAdvancedFeaturesEnabled() {
        return ADVANCED_FEATURES.get();
    }

    public static boolean shouldAutoEnableAdvanced() {
        return AUTO_ENABLE_ADVANCED.get();
    }

    public static RoadDetectionMode getRoadDetectionMode() {
        return ROAD_DETECTION_MODE.get();
    }

    // ========== 方向检测相关方法 ==========

    /**
     * 是否启用方向检测（仅基础模式）
     * Whether directional detection is enabled (basic mode only)
     */
    public static boolean isDirectionalDetectionEnabled() {
        return DIRECTIONAL_DETECTION.get();
    }

    /**
     * 获取最小方向长度
     * Get minimum directional length
     */
    public static int getMinDirectionalLength() {
        return MIN_DIRECTIONAL_LENGTH.get();
    }

    /**
     * 获取最大方向长度
     * Get maximum directional length
     */
    public static int getMaxDirectionalLength() {
        return MAX_DIRECTIONAL_LENGTH.get();
    }

    /**
     * 检查长度是否在有效道路范围内
     * Check if length is within valid road range
     */
    public static boolean isValidRoadLength(int length) {
        int min = getMinDirectionalLength();
        int max = getMaxDirectionalLength();
        return length >= min && length <= max;
    }

    /**
     * 检查是否是地板/广场（长度超出范围）
     * Check if it's floor/plaza (length out of range)
     */
    public static boolean isFloorOrPlaza(int length) {
        return !isValidRoadLength(length);
    }

    // ========== 分开的道路方块相关方法 ==========

    /**
     * 获取当前模式使用的道路方块列表
     * Get road block list for current mode
     */
    public static List<String> getCurrentRoadBlockIds() {
        return isAdvancedFeaturesEnabled() ? getAdvancedRoadBlockIds() : getBasicRoadBlockIds();
    }

    /**
     * 获取基础模式道路方块ID列表
     * Get basic mode road block ID list
     */
    public static List<String> getBasicRoadBlockIds() {
        if (basicRoadBlockIds.isEmpty()) {
            basicRoadBlockIds.addAll(BASIC_ROAD_BLOCKS.get());
        }
        return basicRoadBlockIds;
    }

    /**
     * 获取高级模式道路方块ID列表
     * Get advanced mode road block ID list
     */
    public static List<String> getAdvancedRoadBlockIds() {
        if (advancedRoadBlockIds.isEmpty()) {
            advancedRoadBlockIds.addAll(ADVANCED_ROAD_BLOCKS.get());
        }
        return advancedRoadBlockIds;
    }

    /**
     * 重新加载道路方块列表
     * Reload road block lists
     */
    public static void reloadRoadBlocks() {
        basicRoadBlockIds.clear();
        basicRoadBlockIds.addAll(BASIC_ROAD_BLOCKS.get());

        advancedRoadBlockIds.clear();
        advancedRoadBlockIds.addAll(ADVANCED_ROAD_BLOCKS.get());
    }

    /**
     * 检查指定方块是否是道路方块（根据当前模式）
     * Check if specified block is a road block (based on current mode)
     */
    public static boolean isRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();

        if (isAdvancedFeaturesEnabled()) {
            // 高级模式：检查高级方块列表
            return getAdvancedRoadBlockIds().contains(blockId);
        } else {
            // 基础模式：检查基础方块列表
            return getBasicRoadBlockIds().contains(blockId);
        }
    }

    /**
     * 检查指定方块是否是基础模式道路方块
     * Check if specified block is a basic mode road block
     */
    public static boolean isBasicRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
        return getBasicRoadBlockIds().contains(blockId);
    }

    /**
     * 检查指定方块是否是高级模式道路方块
     * Check if specified block is an advanced mode road block
     */
    public static boolean isAdvancedRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
        return getAdvancedRoadBlockIds().contains(blockId);
    }

    /**
     * 添加道路方块到指定模式
     * Add road block to specified mode
     */
    public static void addRoadBlock(String blockId, boolean toAdvancedMode) {
        if (toAdvancedMode) {
            if (!advancedRoadBlockIds.contains(blockId)) {
                advancedRoadBlockIds.add(blockId);
            }
        } else {
            if (!basicRoadBlockIds.contains(blockId)) {
                basicRoadBlockIds.add(blockId);
            }
        }
    }

    /**
     * 从指定模式移除道路方块
     * Remove road block from specified mode
     */
    public static void removeRoadBlock(String blockId, boolean fromAdvancedMode) {
        if (fromAdvancedMode) {
            advancedRoadBlockIds.remove(blockId);
        } else {
            basicRoadBlockIds.remove(blockId);
        }
    }

    /**
     * 获取所有可能的道路方块（包括两个模式）
     * Get all possible road blocks (including both modes)
     */
    public static List<String> getAllRoadBlocks() {
        List<String> allBlocks = new java.util.ArrayList<>();
        allBlocks.addAll(getBasicRoadBlockIds());

        // 添加高级模式中独有的方块
        for (String blockId : getAdvancedRoadBlockIds()) {
            if (!allBlocks.contains(blockId)) {
                allBlocks.add(blockId);
            }
        }

        return allBlocks;
    }

    /**
     * 获取两个模式的方块差异
     * Get differences between two modes' block lists
     */
    public static List<String> getModeDifferences() {
        List<String> differences = new java.util.ArrayList<>();

        // 找出高级模式中有但基础模式中没有的方块
        for (String blockId : getAdvancedRoadBlockIds()) {
            if (!getBasicRoadBlockIds().contains(blockId)) {
                differences.add(blockId);
            }
        }

        return differences;
    }

    // ========== 配置验证和工具方法 ==========

    /**
     * 验证速度加成配置是否合理
     * Validate if speed multiplier configuration is reasonable
     */
    public static boolean validateSpeedMultipliers() {
        double lvl1 = SPEED_MULTIPLIER_1.get();
        double lvl2 = SPEED_MULTIPLIER_2.get();
        double lvl3 = SPEED_MULTIPLIER_3.get();

        // 检查是否在有效范围内
        if (lvl1 < 1.0 || lvl1 > 5.0)
            return false;
        if (lvl2 < 1.0 || lvl2 > 5.0)
            return false;
        if (lvl3 < 1.0 || lvl3 > 5.0)
            return false;

        // 检查是否递增
        if (lvl2 < lvl1)
            return false;
        if (lvl3 < lvl2)
            return false;

        return true;
    }

    /**
     * 验证方向检测配置
     * Validate directional detection configuration
     */
    public static boolean validateDirectionalDetection() {
        int min = getMinDirectionalLength();
        int max = getMaxDirectionalLength();

        // 检查最小值和最大值是否合理
        if (min < 1 || min > 20)
            return false;
        if (max < 5 || max > 100)
            return false;
        if (min >= max)
            return false; // 最小值必须小于最大值

        return true;
    }

    /**
     * 验证道路方块配置
     * Validate road block configuration
     */
    public static boolean validateRoadBlocks() {
        // 检查基础模式方块列表不为空
        if (getBasicRoadBlockIds().isEmpty()) {
            return false;
        }

        // 检查高级模式包含基础模式的所有方块
        for (String blockId : getBasicRoadBlockIds()) {
            if (!getAdvancedRoadBlockIds().contains(blockId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取配置摘要（用于日志）
     * Get configuration summary (for logging)
     */
    public static String getConfigSummary() {
        return String.format(
                "SpeedModConfig Summary: " +
                        "Debug=%s, LoginMsg=%s, SpeedMsg=%s, " +
                        "Interval=%dtick, " +
                        "Multipliers=[%.2f, %.2f, %.2f], " +
                        "Advanced=%s, AutoAdvanced=%s, Mode=%s, " +
                        "Directional=%s (min=%d, max=%d), " +
                        "BasicRoadBlocks=%d, AdvancedRoadBlocks=%d, ModeDiff=%d",
                isDebugMessagesEnabled(),
                isLoginMessagesEnabled(),
                isSpeedEffectMessagesEnabled(),
                getCheckInterval(),
                SPEED_MULTIPLIER_1.get(),
                SPEED_MULTIPLIER_2.get(),
                SPEED_MULTIPLIER_3.get(),
                isAdvancedFeaturesEnabled(),
                shouldAutoEnableAdvanced(),
                getRoadDetectionMode(),
                isDirectionalDetectionEnabled(),
                getMinDirectionalLength(),
                getMaxDirectionalLength(),
                getBasicRoadBlockIds().size(),
                getAdvancedRoadBlockIds().size(),
                getModeDifferences().size());
    }

    /**
     * 切换模式时获取提示信息
     * Get hint message when switching modes
     */
    public static String getModeSwitchHint() {
        if (isAdvancedFeaturesEnabled()) {
            return String.format(
                    "已切换到高级模式，使用 %d 种道路方块（包含基础模式的 %d 种方块）",
                    getAdvancedRoadBlockIds().size(),
                    getBasicRoadBlockIds().size());
        } else {
            return String.format(
                    "已切换到基础模式，使用 %d 种核心道路方块%s",
                    getBasicRoadBlockIds().size(),
                    isDirectionalDetectionEnabled() ? String.format(" (方向检测: %d-%d格)",
                            getMinDirectionalLength(), getMaxDirectionalLength()) : "");
        }
    }

    /**
     * 获取方向检测状态描述
     * Get directional detection status description
     */
    public static String getDirectionalDetectionStatus() {
        if (!isDirectionalDetectionEnabled()) {
            return "方向检测已禁用";
        }

        return String.format(
                "方向检测已启用，有效道路长度: %d-%d格\n" +
                        "• 小于%d格: 装饰/小平台\n" +
                        "• %d-%d格: 有效道路\n" +
                        "• 大于%d格: 地板/广场",
                getMinDirectionalLength(), getMaxDirectionalLength(),
                getMinDirectionalLength(),
                getMinDirectionalLength(), getMaxDirectionalLength(),
                getMaxDirectionalLength());
    }

    /**
     * 重置为默认配置
     * Reset to default configuration
     */
    public static void resetToDefaults() {
        // 重置基础模式方块列表
        basicRoadBlockIds.clear();
        basicRoadBlockIds.addAll(Arrays.asList(
                "minecraft:dirt_path",
                "minecraft:stone_bricks",
                "minecraft:sandstone",
                "minecraft:mud_bricks",
                "minecraft:packed_mud",
                "minecraft:mossy_stone_bricks",
                "minecraft:cracked_stone_bricks",
                "minecraft:polished_blackstone_bricks",
                "minecraft:nether_bricks",
                "minecraft:end_stone_bricks",
                "minecraft:stone_brick_slab",
                "minecraft:sandstone_slab",
                "minecraft:polished_andesite_slab",
                "minecraft:mud_brick_slab",
                "minecraft:mossy_stone_brick_slab",
                "minecraft:blackstone_slab",
                "minecraft:nether_brick_slab",
                "minecraft:end_stone_brick_slab",
                "minecraft:oak_slab",
                "minecraft:birch_slab",
                "minecraft:spruce_slab",
                "minecraft:cherry_slab",
                "minecraft:acacia_slab",
                "minecraft:dark_oak_slab",
                "minecraft:jungle_slab",
                "minecraft:mangrove_slab",
                "minecraft:crimson_slab",
                "minecraft:warped_slab",
                "minecraft:polished_andesite",
                "minecraft:polished_diorite",
                "minecraft:polished_granite",
                "minecraft:white_concrete",
                "minecraft:orange_concrete",
                "minecraft:magenta_concrete",
                "minecraft:light_blue_concrete",
                "minecraft:yellow_concrete",
                "minecraft:lime_concrete",
                "minecraft:pink_concrete",
                "minecraft:gray_concrete",
                "minecraft:light_gray_concrete",
                "minecraft:cyan_concrete",
                "minecraft:purple_concrete",
                "minecraft:blue_concrete",
                "minecraft:brown_concrete",
                "minecraft:green_concrete",
                "minecraft:red_concrete",
                "minecraft:black_concrete"));

        // 重置高级模式方块列表
        advancedRoadBlockIds.clear();
        advancedRoadBlockIds.addAll(Arrays.asList(
                "minecraft:dirt_path",
                "minecraft:dirt",
                "minecraft:stone_bricks",
                "minecraft:sandstone",
                "minecraft:mud_bricks",
                "minecraft:packed_mud",
                "minecraft:mossy_stone_bricks",
                "minecraft:cracked_stone_bricks",
                "minecraft:polished_blackstone_bricks",
                "minecraft:nether_bricks",
                "minecraft:end_stone_bricks",
                "minecraft:stone_brick_slab",
                "minecraft:sandstone_slab",
                "minecraft:polished_andesite_slab",
                "minecraft:mud_brick_slab",
                "minecraft:mossy_stone_brick_slab",
                "minecraft:blackstone_slab",
                "minecraft:nether_brick_slab",
                "minecraft:end_stone_brick_slab",
                "minecraft:oak_slab",
                "minecraft:birch_slab",
                "minecraft:spruce_slab",
                "minecraft:cherry_slab",
                "minecraft:acacia_slab",
                "minecraft:dark_oak_slab",
                "minecraft:jungle_slab",
                "minecraft:mangrove_slab",
                "minecraft:crimson_slab",
                "minecraft:warped_slab",
                "minecraft:polished_andesite",
                "minecraft:polished_diorite",
                "minecraft:polished_granite",
                "minecraft:white_concrete",
                "minecraft:orange_concrete",
                "minecraft:magenta_concrete",
                "minecraft:light_blue_concrete",
                "minecraft:yellow_concrete",
                "minecraft:lime_concrete",
                "minecraft:pink_concrete",
                "minecraft:gray_concrete",
                "minecraft:light_gray_concrete",
                "minecraft:cyan_concrete",
                "minecraft:purple_concrete",
                "minecraft:blue_concrete",
                "minecraft:brown_concrete",
                "minecraft:green_concrete",
                "minecraft:red_concrete",
                "minecraft:black_concrete",
                "minecraft:stone",
                "minecraft:andesite",
                "minecraft:diorite",
                "minecraft:granite",
                "minecraft:gravel",
                "minecraft:sand",
                "minecraft:coarse_dirt",
                "minecraft:moss_block",
                "minecraft:rooted_dirt",
                "minecraft:podzol",
                "minecraft:snow_block",
                "minecraft:packed_ice",
                "minecraft:red_sand",
                "minecraft:terracotta",
                "minecraft:mud",
                "minecraft:muddy_mangrove_roots"));
    }

}
