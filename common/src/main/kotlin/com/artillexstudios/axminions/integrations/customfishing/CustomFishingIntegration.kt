package com.artillexstudios.axminions.integrations.customfishing

import com.artillexstudios.axminions.api.minions.Minion
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin
import net.momirealms.customfishing.api.mechanic.context.Context
import net.momirealms.customfishing.api.mechanic.context.ContextKeys
import net.momirealms.customfishing.api.mechanic.effect.Effect
import net.momirealms.customfishing.api.mechanic.effect.EffectProperties
import org.bukkit.Location
import org.bukkit.inventory.ItemStack

object CustomFishingIntegration {
    private var enabled = false

    fun isEnabled(): Boolean = enabled

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    /**
     * 生成 CustomFishing 战利品
     * 使用 CustomFishing 自己的概率和区域系统
     * @param minion 小人实例（保留用于未来扩展）
     * @param waterLocation 水的位置，用于确定区域和条件
     * @return 战利品列表，如果返回空列表则应使用原版钓鱼
     */
    @Suppress("UNUSED_PARAMETER")
    fun generateLoot(minion: Minion, waterLocation: Location): List<ItemStack> {
        if (!enabled) return emptyList()
        
        return try {
            val plugin = BukkitCustomFishingPlugin.getInstance()
            val lootManager = plugin.lootManager
            val itemManager = plugin.itemManager
            
            // 创建一个空的 Effect（无额外加成）
            val effect = Effect.newInstance()
            
            // 创建 Context 并设置位置信息
            val context = Context.player(null)
            context.arg(ContextKeys.LOCATION, waterLocation)
            context.arg(ContextKeys.WORLD, waterLocation.world?.name ?: "world")
            context.arg(ContextKeys.X, waterLocation.blockX)
            context.arg(ContextKeys.Y, waterLocation.blockY)
            context.arg(ContextKeys.Z, waterLocation.blockZ)
            
            // 设置 SURROUNDING 为 "water"，这样 in-water 条件才能通过
            context.arg(ContextKeys.SURROUNDING, EffectProperties.WATER_FISHING.key())
            
            // 使用 LootManager.getNextLoot 来获取战利品
            val loot = lootManager.getNextLoot(effect, context) ?: return emptyList()
            
            val lootId = loot.id()
            
            // "vanilla" 是 CustomFishing 的特殊标记，表示应该使用原版钓鱼战利品
            if (lootId == "vanilla") {
                return emptyList()
            }
            
            // 设置 ID 到 context 中，buildInternal 需要用到
            context.arg(ContextKeys.ID, lootId)
            
            // 使用 ItemManager.buildInternal 来构建 CustomFishing 的物品
            val item = itemManager.buildInternal(context, lootId) ?: return emptyList()
            
            listOf(item)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
