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
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.basic", "§6[Qianmo Speed] §fIntelligent basic mode loaded (interval: %s ticks)");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.login.advanced", "§6[Qianmo Speed] §fRoadWeaver advanced mode enabled");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.speed.applied", "§a+ Speed bonus applied (level: %s)");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.speed.removed", "§c- Speed bonus removed");
        FALLBACK_LOCALIZATIONS.put("qianmospeed.message.roadweaver.not_found", "§eRoadWeaver not detected, using basic mode");
        
        // 附魔名称（从你的语言文件）
        FALLBACK_LOCALIZATIONS.put("enchantment.qianmospeed.travel_blessings", "Travel Blessings");
        FALLBACK_LOCALIZATIONS.put("enchantment.qianmospeed.travel_blessings.desc", "Grants significant speed boost when walking on roads");
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
     * 简化版本：只使用回退值（用于服务器端）
     */
    public static String getLocalizedSimple(String key, Object... args) {
        String template = FALLBACK_LOCALIZATIONS.get(key);
        if (template != null) {
            // 安全地格式化字符串，如果参数为空则不格式化
            if (args != null && args.length > 0) {
                try {
                    return String.format(template, args);
                } catch (Exception e) {
                    // 如果格式化失败，返回模板本身
                    QianmoSpeedMod.LOGGER.error("格式化本地化字符串失败: key={}, template={}", key, template, e);
                    return template;
                }
            } else {
                // 如果没有参数，直接返回模板
                return template;
            }
        }
        return key; // 返回原始key
    }
    
    /**
     * 获取带格式的罗马数字
     */
    public static String getFormattedRomanNumeral(int number) {
        String roman = getRomanNumeral(number);
        return "§e" + roman + "§f"; // 黄色罗马数字
    }
    
    /**
     * 将数字转换为罗马数字
     */
    public static String getRomanNumeral(int number) {
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(number);
        }
    }
}