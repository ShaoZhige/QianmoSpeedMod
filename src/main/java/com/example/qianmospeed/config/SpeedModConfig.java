package com.example.qianmospeed.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class SpeedModConfig {
    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = specPair.getLeft();
        COMMON_SPEC = specPair.getRight();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC, "qianmospeed-common.toml");
    }

    public static class CommonConfig {
        // 聊天消息配置 / Chat Message Settings
        public final ForgeConfigSpec.BooleanValue enableLoginMessages;
        public final ForgeConfigSpec.BooleanValue enableDebugMessages;
        
        // 新增：速度效果提示开关 / Speed Effect Notifications Toggle
        public final ForgeConfigSpec.BooleanValue enableSpeedEffectMessages;

        // 功能配置 / Feature Settings
        public final ForgeConfigSpec.BooleanValue enableBasicRoadDetection;
        public final ForgeConfigSpec.BooleanValue enableAdvancedFeatures;

        // 速度设置 / Speed Settings
        public final ForgeConfigSpec.DoubleValue speedMultiplierLevel1;
        public final ForgeConfigSpec.DoubleValue speedMultiplierLevel2;
        public final ForgeConfigSpec.DoubleValue speedMultiplierLevel3;
        public final ForgeConfigSpec.IntValue checkInterval;

        // 基础模式道路方块配置 / Basic Mode Road Blocks Settings
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> basicRoadBlocks;
        
        // 高级模式道路方块配置 / Advanced Mode Road Blocks Settings
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> advancedRoadBlocks;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            // 配置标题 / Configuration title
            builder.comment("阡陌疾旅模组配置 / Qianmo Speed Mod Configuration")
                    .push("general");

            enableLoginMessages = builder
                    .comment("是否显示登录提示消息 / Whether to show login notification messages")
                    .define("enableLoginMessages", true);

            enableDebugMessages = builder
                    .comment("是否在控制台显示调试信息 / Whether to show debug messages in console")
                    .define("enableDebugMessages", false);
            
            // 速度效果提示开关
            enableSpeedEffectMessages = builder
                    .comment("是否显示速度加成/移除的提示消息 / Whether to show speed effect applied/removed notification messages")
                    .define("enableSpeedEffectMessages", false);

            enableBasicRoadDetection = builder
                    .comment("启用基础道路检测 / Enable basic road detection")
                    .define("enableBasicRoadDetection", true);

            enableAdvancedFeatures = builder
                    .comment("启用高级功能（需要RoadWeaver）/ Enable advanced features (requires RoadWeaver)")
                    .define("enableAdvancedFeatures", true);

            builder.pop();

            builder.push("speed_settings");

            speedMultiplierLevel1 = builder
                    .comment("I级附魔速度加成（1.5 = +50%）最小值：1.0 最大值：5.0 / Level I enchantment speed multiplier (1.5 = +50%) min:1.0, max:5.0")
                    .defineInRange("speedMultiplierLevel1", 1.5, 1.0, 5.0);

            speedMultiplierLevel2 = builder
                    .comment("II级附魔速度加成（2.0 = +100%）最小值：1.0 最大值：5.0 / Level II enchantment speed multiplier (2.0 = +100%) min:1.0, max:5.0")
                    .defineInRange("speedMultiplierLevel2", 2.0, 1.0, 5.0);

            speedMultiplierLevel3 = builder
                    .comment("III级附魔速度加成（2.5 = +150%）最小值：1.0 最大值：5.0 / Level III enchantment speed multiplier (2.5 = +150%) min:1.0, max:5.0")
                    .defineInRange("speedMultiplierLevel3", 2.5, 1.0, 5.0);

            checkInterval = builder
                    .comment("道路检测间隔（ticks，20 ticks = 1秒，默认50=2.5秒）最小值：10 最大值：200 / Road detection interval (ticks, 20 ticks = 1 second, default 50=2.5 seconds) min:10, max:200")
                    .defineInRange("checkInterval", 50, 10, 200);

            builder.pop();

            builder.push("road_blocks");

            // 基础模式道路方块列表 / Basic mode road blocks list
            basicRoadBlocks = builder
                    .comment("基础模式道路方块ID列表\n格式: \"modid:blockname\"\n基础模式只包含明确的人工道路方块，不包含自然方块 / Basic mode road block ID list\nFormat: \"modid:blockname\"\nBasic mode only includes clear artificial road blocks, excludes natural blocks")
                    .defineList("basicRoadBlocks",
                        Arrays.asList(
                            // === 明确的道路方块 === / Clear Road Blocks ===
                            "minecraft:dirt_path",           // 土径（最明确的道路）/ Dirt Path (most clear road)
                            
                            // === 建筑类道路方块 === / Construction Road Blocks ===
                            "minecraft:stone_bricks",        // 石砖 / Stone Bricks
                            "minecraft:sandstone",           // 砂岩 / Sandstone
                            "minecraft:oak_planks",          // 橡木木板 / Oak Planks
                            "minecraft:mud_bricks",          // 泥砖 / Mud Bricks
                            "minecraft:packed_mud",          // 泥坯 / Packed Mud
                            "minecraft:mossy_stone_bricks",  // 苔石砖 / Mossy Stone Bricks
                            "minecraft:cracked_stone_bricks", // 裂纹石砖 / Cracked Stone Bricks
                            "minecraft:polished_blackstone_bricks", // 磨制黑石砖 / Polished Blackstone Bricks
                            "minecraft:nether_bricks",       // 下界砖 / Nether Bricks
                            "minecraft:end_stone_bricks",    // 末地石砖 / End Stone Bricks
                            
                            // === 各种台阶（明确的道路表面）=== / Various Slabs (clear road surfaces) ===
                            "minecraft:stone_brick_slab",
                            "minecraft:sandstone_slab",
                            "minecraft:polished_andesite_slab",
                            "minecraft:mud_brick_slab",
                            "minecraft:mossy_stone_brick_slab",
                            "minecraft:blackstone_slab",
                            "minecraft:nether_brick_slab",
                            "minecraft:end_stone_brick_slab",
                            
                            // === 木板类台阶（人工铺设）=== / Planks slabs (artificially laid) ===
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
                            
                            // === 特别添加：明显的道路方块 === / Specifically added: obvious road blocks
                            "minecraft:polished_andesite",   // 磨制安山岩 / Polished Andesite
                            "minecraft:polished_diorite",    // 磨制闪长岩 / Polished Diorite
                            "minecraft:polished_granite",    // 磨制花岗岩 / Polished Granite
                            
                            // === 混凝土（明确的人工建筑）=== / Concrete (clear artificial construction) ===
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
                            "minecraft:black_concrete"
                        ),
                        obj -> obj instanceof String);

            // 高级模式道路方块列表 / Advanced mode road blocks list
            advancedRoadBlocks = builder
                    .comment("高级模式道路方块ID列表\n格式: \"modid:blockname\"\n高级模式包含更多可能的\"道路\"方块，利用RoadWeaver的智能上下文检测 / Advanced mode road block ID list\nFormat: \"modid:blockname\"\nAdvanced mode includes more possible \"road\" blocks, utilizing RoadWeaver's intelligent context detection")
                    .defineList("advancedRoadBlocks",
                        Arrays.asList(
                            // 包含所有基础模式方块 / Include all basic mode blocks
                            "minecraft:dirt_path",
                            "minecraft:cobblestone",
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
                            "minecraft:oak_planks",
                            
                            // 混凝土 / Concrete
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
                            
                            // === 高级模式额外添加的"类道路"方块 === / Advanced mode additional "road-like" blocks
                            "minecraft:stone",               // 石头（但需要上下文判断）/ Stone (requires context judgment)
                            "minecraft:andesite",            // 安山岩 / Andesite
                            "minecraft:diorite",             // 闪长岩 / Diorite
                            "minecraft:granite",             // 花岗岩 / Granite
                            "minecraft:gravel",              // 砂砾 / Gravel
                            "minecraft:sand",                // 沙子 / Sand
                            "minecraft:coarse_dirt",         // 砂土 / Coarse Dirt
                            "minecraft:moss_block",          // 苔藓块 / Moss Block
                            "minecraft:rooted_dirt",         // 缠根泥土 / Rooted Dirt
                            "minecraft:podzol",              // 灰化土 / Podzol
                            "minecraft:snow_block",          // 雪块 / Snow Block
                            "minecraft:packed_ice",          // 浮冰 / Packed Ice
                            "minecraft:red_sand",            // 红沙 / Red Sand
                            "minecraft:terracotta",          // 陶瓦 / Terracotta
                            "minecraft:mud",                 // 泥巴 / Mud
                            "minecraft:birch_planks",        // 白桦木板 / Birch Planks
                            "minecraft:acacia_planks",       // 金合欢木板 / Acacia Planks
                            "minecraft:dark_oak_planks",     // 深色橡木木板 / Dark Oak Planks
                            "minecraft:spruce_planks",       // 云杉木板 / Spruce Planks
                            "minecraft:cherry_planks",       // 樱花木板 / Cherry Planks
                            "minecraft:jungle_planks",       // 丛林木板 / Jungle Planks
                            "minecraft:mangrove_planks",     // 红树木板 / Mangrove Planks
                            "minecraft:crimson_planks",      // 绯红木板 / Crimson Planks
                            "minecraft:warped_planks",       // 诡异木板 / Warped Planks
                            "minecraft:muddy_mangrove_roots" // 沾泥的红树根 / Muddy Mangrove Roots
                        ),
                        obj -> obj instanceof String);

            builder.pop();
        }
    }

    // 配置获取方法 / Configuration getter methods

    public static boolean isLoginMessagesEnabled() {
        return COMMON.enableLoginMessages.get();
    }

    public static boolean isDebugMessagesEnabled() {
        return COMMON.enableDebugMessages.get();
    }
    
    public static boolean isSpeedEffectMessagesEnabled() {
        return COMMON.enableSpeedEffectMessages.get();
    }

    public static boolean isBasicDetectionEnabled() {
        return COMMON.enableBasicRoadDetection.get();
    }

    public static boolean isAdvancedFeaturesEnabled() {
        return COMMON.enableAdvancedFeatures.get();
    }

    public static double getSpeedMultiplier(int level) {
        return switch (level) {
            case 1 -> COMMON.speedMultiplierLevel1.get();
            case 2 -> COMMON.speedMultiplierLevel2.get();
            case 3 -> COMMON.speedMultiplierLevel3.get();
            default -> 1.0;
        };
    }

    public static int getCheckInterval() {
        return COMMON.checkInterval.get();
    }

    public static List<String> getBasicRoadBlocks() {
        return (List<String>) COMMON.basicRoadBlocks.get();
    }

    public static List<String> getAdvancedRoadBlocks() {
        return (List<String>) COMMON.advancedRoadBlocks.get();
    }
}