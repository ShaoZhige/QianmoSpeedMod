package com.example.qianmospeed.registry;

import com.example.qianmospeed.QianmoSpeedMod;
import com.example.qianmospeed.enchantment.QianmoSwiftjourneyEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EnchantmentRegistry {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = 
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, QianmoSpeedMod.MODID);
    
    public static final RegistryObject<Enchantment> QIANMO_SWIFTJOURNEY = ENCHANTMENTS.register(
        "qianmo_swiftjourney",
        QianmoSwiftjourneyEnchantment::new  // 使用无参构造函数引用
    );
    
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
        QianmoSpeedMod.LOGGER.info("附魔注册完成");
    }
}