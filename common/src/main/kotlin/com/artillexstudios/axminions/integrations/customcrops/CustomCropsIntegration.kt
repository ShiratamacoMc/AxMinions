package com.artillexstudios.axminions.integrations.customcrops

import com.artillexstudios.axminions.api.minions.Minion
import net.momirealms.customcrops.api.BukkitCustomCropsPlugin
import net.momirealms.customcrops.api.core.Registries
import net.momirealms.customcrops.api.core.block.BreakReason
import net.momirealms.customcrops.api.core.block.CropBlock
import net.momirealms.customcrops.api.core.world.Pos3
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

object CustomCropsIntegration {
    private var enabled = false

    fun isEnabled(): Boolean = enabled

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    /**
     * 检查方块是否是 CustomCrops 的作物
     */
    fun isCustomCrop(block: Block): Boolean {
        if (!enabled) return false
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val itemManager = plugin.itemManager
            
            // 获取方块的 ID
            val blockId = itemManager.blockID(block)
            
            // 检查是否在作物注册表中
            Registries.STAGE_TO_CROP_UNSAFE.containsKey(blockId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查作物是否成熟
     */
    fun isMature(block: Block): Boolean {
        if (!enabled) return false
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val itemManager = plugin.itemManager
            val worldManager = plugin.worldManager
            
            // 获取方块的 ID
            val blockId = itemManager.blockID(block)
            
            // 获取作物配置列表
            val cropConfigs = Registries.STAGE_TO_CROP_UNSAFE.get(blockId) ?: return false
            if (cropConfigs.isEmpty()) return false
            
            // 获取 CustomCrops 世界
            val ccWorld = worldManager.getWorld(block.world).orElse(null) ?: return false
            
            // 获取方块状态
            val pos3 = Pos3.from(block.location)
            val blockState = ccWorld.getBlockState(pos3).orElse(null) ?: return false
            
            // 检查是否是作物方块
            val cropBlock = blockState.type() as? CropBlock ?: return false
            
            // 获取作物配置
            val cropConfig = cropBlock.config(blockState) ?: return false
            
            // 检查是否达到最大生长点数
            val currentPoint = cropBlock.point(blockState)
            currentPoint >= cropConfig.maxPoints()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 收获 CustomCrops 作物并返回掉落物
     * 使用 API 模拟玩家破坏作物
     * @param minion 小人实例（保留用于未来扩展）
     */
    @Suppress("UNUSED_PARAMETER")
    fun harvestCrop(minion: Minion, block: Block): List<ItemStack> {
        if (!enabled) return emptyList()
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val api = plugin.api
            
            // 使用 API 模拟破坏作物（不需要玩家）
            api.simulatePlayerBreakCrop(null, null, block.location, BreakReason.ACTION)
            
            // 由于 CustomCrops 的 breakActions 会处理掉落物
            // 我们返回空列表，让 CustomCrops 自己处理掉落
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
