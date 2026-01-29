package com.example.qianmospeed.enchantment;

import com.example.qianmospeed.QianmoSpeedMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class QianmoSwiftjourneyEnchantment extends Enchantment {
    
    public QianmoSwiftjourneyEnchantment() {
        super(
            Enchantment.Rarity.COMMON,
            EnchantmentCategory.ARMOR_FEET,
            new EquipmentSlot[] { EquipmentSlot.FEET }
        );
    }
    
    @Override
    public int getMinCost(int level) {
        return 5 + (level - 1) * 8;
    }
    
    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 12;
    }
    
    @Override
    public int getMaxLevel() {
        return 3;
    }
    
    @Override
    public boolean isTreasureOnly() {
        return false;
    }
    
    @Override
    public boolean isTradeable() {
        return true;
    }
    
    @Override
    public boolean isDiscoverable() {
        return true;
    }
    
    @Override
    public net.minecraft.network.chat.Component getFullname(int level) {
        // 只返回基础名称，不添加后缀
        return super.getFullname(level);
    }
}