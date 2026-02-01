package com.example.qianmospeed.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        return false; // 不是宝藏附魔，可以在附魔台获得
    }
    
    @Override
    public boolean isTradeable() {
        return false; // 不可和村民交易
    }
    
    @Override
    public boolean isDiscoverable() {
        return true; // 可以在附魔台发现
    }
    
    /**
     * 重写此方法以限制附魔台中的可用性
     * 只在靴子上可用
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        // 只允许给靴子附魔
        return canApplyAtEnchantingTable(stack) && 
               stack.getItem().canBeDepleted(); // 确保是可消耗的物品
    }
    
    /**
     * 重写此方法以控制附魔台中的显示
     * 只有在靴子上才会显示
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // 在附魔台中，只对靴子显示这个附魔
        return isBoot(stack);
    }
    
    /**
     * 检查是否是靴子
     */
    private boolean isBoot(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        // 检查原版靴子
        if (stack.getItem() == Items.LEATHER_BOOTS || 
            stack.getItem() == Items.CHAINMAIL_BOOTS || 
            stack.getItem() == Items.IRON_BOOTS || 
            stack.getItem() == Items.GOLDEN_BOOTS || 
            stack.getItem() == Items.DIAMOND_BOOTS || 
            stack.getItem() == Items.NETHERITE_BOOTS) {
            return true;
        }
        
        // 检查附魔类别
        if (category != null && category.canEnchant(stack.getItem())) {
            // 如果是脚部盔甲类别，也接受
            return category == EnchantmentCategory.ARMOR_FEET;
        }
        
        return false;
    }
    
    /**
     * 重写此方法以确保兼容性
     */
    @Override
    public boolean checkCompatibility(Enchantment other) {
        // 允许与其他靴子附魔共存
        return super.checkCompatibility(other);
    }
    
    @Override
    public int getMinLevel() {
        return 1;
    }
    
    @Override
    public net.minecraft.world.item.enchantment.Enchantment.Rarity getRarity() {
        return Enchantment.Rarity.COMMON;
    }
    
    @Override
    public net.minecraft.network.chat.Component getFullname(int level) {
        return super.getFullname(level);
    }
}