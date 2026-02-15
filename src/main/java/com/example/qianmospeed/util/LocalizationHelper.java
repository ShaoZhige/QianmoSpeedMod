package com.example.qianmospeed.util;

import com.example.qianmospeed.QianmoSpeedMod;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.HashMap;
import java.util.Map;

public class LocalizationHelper {

    private static final Map<String, String> FALLBACK_LOCALIZATIONS = new HashMap<>();

    static {
        // 从你的语言文件加载回退值
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.basic",
                "§6[Qianmo Speed] §fIntelligent basic mode loaded (interval: %s ticks)");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.advanced",
                "§6[Qianmo Speed] §fRoadWeaver advanced mode enabled");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.speed.applied", "§a+ Speed bonus applied (level: %s)");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.speed.removed", "§c- Speed bonus removed");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.roadweaver.not_found",
                "§eRoadWeaver not detected, using basic mode");

        // 常驻加速登录消息
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.permanent_speed",
                "§7Permanent road speed enabled (§e%s%%§7)");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.permanent_speed_priority", " §8(Enchantment priority)");

        // 模组前缀和检测消息
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.prefix", "§a[Qianmo Speed] §f");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.detected_mods", "§7Detected road mods: ");

        // 附魔名称
        FALLBACK_LOCALIZATIONS.put("enchantment.qianmospeed.travel_blessings", "Travel Blessings");
        FALLBACK_LOCALIZATIONS.put("enchantment.qianmospeed.travel_blessings.desc",
                "Grants significant speed boost when walking on roads");
    }

    /**
     * 获取本地化字符串（安全版本）
     */
    public static String getLocalized(String key, Object... args) {
        String result = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> {
            try {
                return I18n.get(key, args);
            } catch (Exception e) {
                return null;
            }
        });

        if (result == null || result.equals(key)) {
            // 使用回退值
            return getLocalizedSimple(key, args);
        }

        return result;
    }

    /**
     * 只使用回退值（用于服务器端）
     */
    public static String getLocalizedSimple(String key, Object... args) {
        String template = FALLBACK_LOCALIZATIONS.get(key);
        if (template != null) {
            if (args != null && args.length > 0) {
                try {
                    return String.format(template, args);
                } catch (Exception e) {
                    QianmoSpeedMod.LOGGER.error("格式化本地化字符串失败: key={}, template={}", key, template, e);
                    return template;
                }
            } else {
                return template;
            }
        }
        return key;
    }

    /**
     * 获取带格式的罗马数字
     */
    public static String getFormattedRomanNumeral(int number) {
        String roman = getRomanNumeral(number);
        return "§e" + roman + "§f";
    }

    /**
     * 将数字转换为罗马数字
     */
    public static String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}