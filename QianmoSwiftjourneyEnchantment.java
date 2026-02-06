package com.example.qianmospeed.enchantment;

import com.example.qianmospeed.QianmoSpeedMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class QianmoSwiftjourneyEnchantment extends Enchantment {
    
    public QianmoSwiftjourneyEnchantment() {
        super(
            Rarity.COMMON,
            EnchantmentCategory.ARMOR_FEET,
            new EquipmentSlot[] { EquipmentSlot.FEET }
        );
    }
    
    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 10;
    }
    
    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }
    
    @Override
    public int getMaxLevel() {
        return 3;
    }
    
    @Override
    public boolean canEnchant(ItemStack stack) {
        // 允许书和靴子
        return stack.getItem() == Items.BOOK || 
               stack.getItem() == Items.ENCHANTED_BOOK ||
               super.canEnchant(stack);
    }
    
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // 允许在附魔台上对书和靴子使用
        return stack.getItem() == Items.BOOK || 
               stack.getItem() == Items.ENCHANTED_BOOK ||
               super.canApplyAtEnchantingTable(stack);
    }
    
    @Override
    public boolean isTreasureOnly() {
        return false;
    }
    
    @Override
    public boolean isTradeable() {
        return true; // 设为true以确保兼容性
    }
    
    @Override
    public boolean isDiscoverable() {
        return true;
    }
    
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }
    
    // 重写getMinLevel方法
    @Override
    public int getMinLevel() {
        return 1;
    }
}