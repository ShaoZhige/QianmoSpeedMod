package com.example.qianmospeed.config;

import com.example.qianmospeed.QianmoSpeedMod;
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

    // 常驻道路加速配置
    public static final ForgeConfigSpec.BooleanValue ENABLE_PERMANENT_SPEED;
    public static final ForgeConfigSpec.DoubleValue PERMANENT_SPEED_MULTIPLIER;

    // 高级功能配置
    public static final ForgeConfigSpec.BooleanValue ADVANCED_FEATURES;
    public static final ForgeConfigSpec.BooleanValue AUTO_ENABLE_ADVANCED;

    // 阡陌交通集成开关
    public static final ForgeConfigSpec.BooleanValue ENABLE_ROADWEAVER_INTEGRATION;
    // RoadWeaver 速度分级倍率
    public static final ForgeConfigSpec.DoubleValue RW_HIGHWAY_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RW_COMPLETED_ROAD_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue RW_PLANNED_ROAD_SPEED_MULTIPLIER;

    // 道路检测模式枚举
    public enum RoadDetectionMode {
        BASIC,
        ENHANCED,
        SMART
    }

    public static final ForgeConfigSpec.EnumValue<RoadDetectionMode> ROAD_DETECTION_MODE;

    // ========== 方向检测配置 ==========
    public static final ForgeConfigSpec.BooleanValue DIRECTIONAL_DETECTION;
    public static final ForgeConfigSpec.IntValue MIN_DIRECTIONAL_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_DIRECTIONAL_LENGTH;

    // ========== 道路方块配置 ==========
    private static List<String> basicRoadBlockIds = new java.util.ArrayList<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BASIC_ROAD_BLOCKS;

    private static List<String> advancedRoadBlockIds = new java.util.ArrayList<>();
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADVANCED_ROAD_BLOCKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // ========== 客户端配置 ==========
        builder.push("client");
        DEBUG_MESSAGES = builder
                .comment("启用调试消息", "Enable debug messages", "默认/Default: false")
                .define("debugMessages", false);
        LOGIN_MESSAGES = builder
                .comment("启用登录欢迎消息", "Enable login welcome messages", "默认/Default: true")
                .define("loginMessages", true);
        SPEED_EFFECT_MESSAGES = builder
                .comment("启用速度效果消息提示", "Enable speed effect message prompts", "默认/Default: false")
                .define("speedEffectMessages", false);
        builder.pop();

        // ========== 游戏性配置 ==========
        builder.push("gameplay");
        CHECK_INTERVAL = builder
                .comment("检查道路间隔（tick）", "Check interval (ticks)", "20 tick = 1秒", "范围: 1-200", "默认: 20")
                .defineInRange("checkInterval", 20, 1, 200);
        builder.pop();

        // ========== 常驻道路加速配置 ==========
        builder.push("permanent_speed");
        ENABLE_PERMANENT_SPEED = builder
                .comment(
                        "启用常驻道路加速（无需附魔）",
                        "Enable permanent road speed (no enchantment required)",
                        "玩家只要站在道路上就会获得恒定速度加成",
                        "Players will get constant speed boost when standing on roads",
                        "与旅途祝福附魔互斥，附魔生效时自动禁用常驻加速",
                        "Mutually exclusive with Travel Blessings enchantment",
                        "默认/Default: false")
                .define("enablePermanentSpeed", false);
        PERMANENT_SPEED_MULTIPLIER = builder
                .comment(
                        "常驻道路加速倍率",
                        "Permanent road speed multiplier",
                        "范围/Range: 1.0 - 3.0",
                        "1.0 = 无加成, 1.3 = 30% 加速",
                        "默认/Default: 1.3")
                .defineInRange("permanentSpeedMultiplier", 1.3, 1.0, 3.0);
        builder.pop();

        // ========== 速度加成配置 ==========
        builder.push("speed_multipliers");
        SPEED_MULTIPLIER_1 = builder
                .comment("旅行的祝福 I 级", "范围: 1.0-5.0", "默认: 1.4")
                .defineInRange("speedMultiplier1", 1.4, 1.0, 5.0);
        SPEED_MULTIPLIER_2 = builder
                .comment("旅行的祝福 II 级", "范围: 1.0-5.0", "默认: 1.8")
                .defineInRange("speedMultiplier2", 1.8, 1.0, 5.0);
        SPEED_MULTIPLIER_3 = builder
                .comment("旅行的祝福 III 级", "范围: 1.0-5.0", "默认: 2.2")
                .defineInRange("speedMultiplier3", 2.2, 1.0, 5.0);
        builder.pop();

        // ========== 方向检测配置 ==========
        builder.push("directional_detection");
        DIRECTIONAL_DETECTION = builder
                .comment("启用方向检测", "默认: true")
                .define("directionalDetection", true);
        MIN_DIRECTIONAL_LENGTH = builder
                .comment(
                        "道路最小连续长度（格数）",
                        "Minimum continuous length for roads (in blocks)",
                        "范围: 1-20",
                        "默认: 2")
                .defineInRange("minDirectionalLength", 2, 1, 20);
        MAX_DIRECTIONAL_LENGTH = builder
                .comment(
                        "道路最大连续长度（格数）",
                        "Maximum continuous length for roads (in blocks)",
                        "范围: 1-100",
                        "默认: 5")
                .defineInRange("maxDirectionalLength", 5, 1, 100);
        builder.pop();

        // ========== 高级功能配置 ==========
        builder.push("advanced_features");
        ADVANCED_FEATURES = builder
                .comment("启用高级道路检测", "默认: false")
                .define("advancedFeaturesEnabled", false);
        AUTO_ENABLE_ADVANCED = builder
                .comment("自动启用高级模式", "默认: false")
                .define("autoEnableAdvanced", false);
        ROAD_DETECTION_MODE = builder
                .comment("道路检测模式", "BASIC/ENHANCED/SMART", "默认: SMART")
                .defineEnum("roadDetectionMode", RoadDetectionMode.SMART);
        builder.pop();

        // ========== 阡陌交通集成配置 ==========
        builder.push("roadweaver_integration");
        ENABLE_ROADWEAVER_INTEGRATION = builder
                .comment(
                        "启用阡陌交通（RoadWeaver）集成，读取规划数据优化检测",
                        "Enable RoadWeaver integration to read planning data for optimized detection",
                        "默认开启，无 RoadWeaver 时自动降级为普通检测",
                        "Default ON, auto-fallback to standard detection when RoadWeaver is absent",
                        "默认/Default: true")
                .define("enableRoadWeaverIntegration", true);
        RW_HIGHWAY_SPEED_MULTIPLIER = builder
                .comment(
                        "高速公路速度倍率",
                        "Highway speed multiplier",
                        "当玩家在 RoadWeaver 高速公路上时的速度加成",
                        "Speed boost when player is on RoadWeaver highway",
                        "范围/Range: 1.0 - 5.0",
                        "默认/Default: 2.0")
                .defineInRange("highwaySpeedMultiplier", 2.0, 1.0, 5.0);
        RW_COMPLETED_ROAD_SPEED_MULTIPLIER = builder
                .comment(
                        "已完成道路速度倍率",
                        "Completed road speed multiplier",
                        "当玩家在 RoadWeaver 已完成的道路时的速度加成",
                        "Speed boost when player is on completed RoadWeaver road",
                        "范围/Range: 1.0 - 5.0",
                        "默认/Default: 1.6")
                .defineInRange("completedRoadSpeedMultiplier", 1.6, 1.0, 5.0);
        RW_PLANNED_ROAD_SPEED_MULTIPLIER = builder
                .comment(
                        "规划中道路速度倍率",
                        "Planned road speed multiplier",
                        "当玩家在 RoadWeaver 规划中的区块时的速度加成",
                        "Speed boost when player is in RoadWeaver planned chunk",
                        "范围/Range: 1.0 - 3.0",
                        "默认/Default: 1.2")
                .defineInRange("plannedRoadSpeedMultiplier", 1.2, 1.0, 3.0);
        builder.pop();

        // ========== 基础模式道路方块配置 ==========
        builder.push("basic_road_blocks");
        BASIC_ROAD_BLOCKS = builder
                .comment("基础模式道路方块ID列表")
                .defineList("basicRoadBlocks",
                        getDefaultBasicRoadBlocks(),
                        o -> o instanceof String);
        builder.pop();

        // ========== 高级模式道路方块配置 ==========
        builder.push("advanced_road_blocks");
        ADVANCED_ROAD_BLOCKS = builder
                .comment("高级模式道路方块ID列表")
                .defineList("advancedRoadBlocks",
                        getDefaultAdvancedRoadBlocks(),
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

    // ========== 常驻道路加速配置获取方法 ==========
    public static boolean isPermanentSpeedEnabled() {
        boolean value = ENABLE_PERMANENT_SPEED.get();
        return value;
    }

    public static double getPermanentSpeedMultiplier() {
        double value = PERMANENT_SPEED_MULTIPLIER.get();
        return value;
    }

    public static boolean isRoadWeaverIntegrationEnabled() {
        return ENABLE_ROADWEAVER_INTEGRATION.get();
    }

    public static double getRWHighwaySpeedMultiplier() {
        return RW_HIGHWAY_SPEED_MULTIPLIER.get();
    }

    public static double getRWCompletedRoadSpeedMultiplier() {
        return RW_COMPLETED_ROAD_SPEED_MULTIPLIER.get();
    }

    public static double getRWPlannedRoadSpeedMultiplier() {
        return RW_PLANNED_ROAD_SPEED_MULTIPLIER.get();
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

    // 方向检测相关方法
    public static boolean isDirectionalDetectionEnabled() {
        return DIRECTIONAL_DETECTION.get();
    }

    public static int getMinDirectionalLength() {
        return MIN_DIRECTIONAL_LENGTH.get();
    }

    public static int getMaxDirectionalLength() {
        return MAX_DIRECTIONAL_LENGTH.get();
    }

    public static boolean isValidRoadLength(int length) {
        int min = getMinDirectionalLength();
        int max = getMaxDirectionalLength();
        return length >= min && length <= max;
    }

    public static boolean isFloorOrPlaza(int length) {
        return !isValidRoadLength(length);
    }

    // ========== 道路方块相关方法 ==========

    public static List<String> getCurrentRoadBlockIds() {
        return isAdvancedFeaturesEnabled() ? getAdvancedRoadBlockIds() : getBasicRoadBlockIds();
    }

    public static List<String> getBasicRoadBlockIds() {
        if (basicRoadBlockIds.isEmpty()) {
            basicRoadBlockIds.addAll(BASIC_ROAD_BLOCKS.get());
        }
        return basicRoadBlockIds;
    }

    public static List<String> getAdvancedRoadBlockIds() {
        if (advancedRoadBlockIds.isEmpty()) {
            advancedRoadBlockIds.addAll(ADVANCED_ROAD_BLOCKS.get());
        }
        return advancedRoadBlockIds;
    }

    public static void reloadRoadBlocks() {
        basicRoadBlockIds.clear();
        basicRoadBlockIds.addAll(BASIC_ROAD_BLOCKS.get());
        advancedRoadBlockIds.clear();
        advancedRoadBlockIds.addAll(ADVANCED_ROAD_BLOCKS.get());
    }

    public static boolean isRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
        return isAdvancedFeaturesEnabled() ? getAdvancedRoadBlockIds().contains(blockId)
                : getBasicRoadBlockIds().contains(blockId);
    }

    public static boolean isBasicRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
        return getBasicRoadBlockIds().contains(blockId);
    }

    public static boolean isAdvancedRoadBlock(net.minecraft.world.level.block.Block block) {
        String blockId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString();
        return getAdvancedRoadBlockIds().contains(blockId);
    }

    // ========== 配置验证和工具方法 ==========

    public static boolean validateSpeedMultipliers() {
        double lvl1 = SPEED_MULTIPLIER_1.get();
        double lvl2 = SPEED_MULTIPLIER_2.get();
        double lvl3 = SPEED_MULTIPLIER_3.get();
        return lvl1 >= 1.0 && lvl1 <= 5.0 &&
                lvl2 >= 1.0 && lvl2 <= 5.0 &&
                lvl3 >= 1.0 && lvl3 <= 5.0 &&
                lvl2 >= lvl1 && lvl3 >= lvl2;
    }

    public static boolean validateDirectionalDetection() {
        int min = getMinDirectionalLength();
        int max = getMaxDirectionalLength();
        return min >= 1 && min <= 20 && max >= 1 && max <= 100 && min <= max;
    }

    public static boolean validateRoadBlocks() {
        if (getBasicRoadBlockIds().isEmpty())
            return false;
        return true;
    }

    public static String getConfigSummary() {
        return String.format(
                "Debug=%s, LoginMsg=%s, SpeedMsg=%s, " +
                        "Interval=%dtick, " +
                        "永久加速=%s (%.2fx), " +
                        "Multipliers=[%.2f, %.2f, %.2f], " +
                        "Advanced=%s, Mode=%s, " +
                        "Directional=%s (min=%d, max=%d), " +
                        "BasicBlocks=%d, AdvancedBlocks=%d, " +
                        "RW集成=%s (公路=%.2fx, 道路=%.2fx, 规划=%.2fx)",
                isDebugMessagesEnabled(),
                isLoginMessagesEnabled(),
                isSpeedEffectMessagesEnabled(),
                getCheckInterval(),
                isPermanentSpeedEnabled() ? "启用" : "禁用",
                getPermanentSpeedMultiplier(),
                SPEED_MULTIPLIER_1.get(),
                SPEED_MULTIPLIER_2.get(),
                SPEED_MULTIPLIER_3.get(),
                isAdvancedFeaturesEnabled() ? "启用" : "禁用",
                getRoadDetectionMode(),
                isDirectionalDetectionEnabled() ? "启用" : "禁用",
                getMinDirectionalLength(),
                getMaxDirectionalLength(),
                getBasicRoadBlockIds().size(),
                getAdvancedRoadBlockIds().size(),
                isRoadWeaverIntegrationEnabled() ? "启用" : "禁用",
                getRWHighwaySpeedMultiplier(),
                getRWCompletedRoadSpeedMultiplier(),
                getRWPlannedRoadSpeedMultiplier());
    }

    // ========== 🔥 调试方法：打印配置状态 ==========
    public static void debugPrintConfig() {
        System.out.println("========== 阡陌疾旅配置状态 ==========");
        System.out.println("常驻加速启用: " + isPermanentSpeedEnabled());
        System.out.println("常驻加速倍率: " + getPermanentSpeedMultiplier());
        System.out.println("调试模式: " + isDebugMessagesEnabled());
        System.out.println("检查间隔: " + getCheckInterval());
        System.out.println("方向检测: " + isDirectionalDetectionEnabled());
        System.out.println("方向检测最小长度: " + getMinDirectionalLength());
        System.out.println("方向检测最大长度: " + getMaxDirectionalLength());
        System.out.println("高级功能: " + isAdvancedFeaturesEnabled());
        System.out.println("检测模式: " + getRoadDetectionMode());
        System.out.println("基础道路方块数: " + getBasicRoadBlockIds().size());
        System.out.println("高级道路方块数: " + getAdvancedRoadBlockIds().size());
        System.out.println("======================================");
    }

    // ========== 📢 配置变更监听 ==========

    /**
     * 当配置变更时调用，清除检测器缓存
     */
    public static void onConfigChanged() {
        // 清除道路检测器缓存
        com.example.qianmospeed.road.RoadDetectionFactory.invalidateCache();

        // 重新加载道路方块列表
        reloadRoadBlocks();

        if (isDebugMessagesEnabled()) {
            QianmoSpeedMod.LOGGER.info("配置已变更，检测器缓存已清除");
        }
    }

    // ========== 默认方块列表 ==========

    private static List<String> getDefaultBasicRoadBlocks() {
        return Arrays.asList(
                "minecraft:dirt_path",
                "minecraft:stone_bricks",
                "minecraft:oak_planks",
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
                "minecraft:black_concrete");
    }

    private static List<String> getDefaultAdvancedRoadBlocks() {
        return Arrays.asList(
                "minecraft:dirt_path",
                "minecraft:dirt",
                "minecraft:cobblestone",
                "minecraft:stone_bricks",
                "minecraft:smooth_sandstone",
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
                "minecraft:muddy_mangrove_roots",
                "minecraft:oak_planks",
                "minecraft:spruce_planks",
                "minecraft:birch_planks",
                "minecraft:jungle_planks",
                "minecraft:acacia_planks",
                "minecraft:dark_oak_planks",
                "minecraft:mangrove_planks",
                "minecraft:cherry_planks",
                "minecraft:bamboo_planks",
                "minecraft:crimson_planks",
                "minecraft:warped_planks");
    }
}