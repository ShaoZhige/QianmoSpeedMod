package com.example.qianmospeed.event;

import com.example.qianmospeed.QianmoSpeedMod;
import net.minecraftforge.fml.common.Mod;

/**
 * 高级道路处理器 - 仅在RoadWeaver加载时注册
 * 目前是一个空的占位符，未来可以添加RoadWeaver集成
 */
@Mod.EventBusSubscriber(modid = QianmoSpeedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancedRoadHandler {
    // 空的类 - 不会导致编译错误
    // 以后需要时可以添加真正的事件处理器
}