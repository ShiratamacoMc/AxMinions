package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.events.PreFisherMinionFishEvent
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.LocationUtils
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.integrations.customfishing.CustomFishingIntegration
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.nms.NMSHandler
import org.bukkit.Bukkit
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.ItemStack

class FisherMinionType : MinionType("fisher", AxMinionsPlugin.INSTANCE.getResource("minions/fisher.yml")!!) {

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = minion.getTool()?.getEnchantmentLevel(Enchantment.LURE)?.div(10.0) ?: 0.1
        val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel()) * efficiency).roundToInt())
    }

    override fun run(minion: Minion) {
        if (minion.getLinkedInventory() != null && minion.getLinkedInventory()?.firstEmpty() != -1) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        }

        if (minion.getLinkedChest() != null) {
            val type = minion.getLinkedChest()!!.block.type
            if (type == Material.CHEST && minion.getLinkedInventory() !is DoubleChestInventory && hasChestOnSide(minion.getLinkedChest()!!.block)) {
                minion.setLinkedChest(minion.getLinkedChest())
            }

            if (type == Material.CHEST && minion.getLinkedInventory() is DoubleChestInventory && !hasChestOnSide(minion.getLinkedChest()!!.block)) {
                minion.setLinkedChest(minion.getLinkedChest())
            }

            if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
                minion.setLinkedChest(null)
            }
        }

        if (minion.getLinkedInventory() == null) {
            minion.setLinkedChest(null)
        }

        if (minion.getLinkedInventory()?.firstEmpty() == -1) {
            Warnings.CONTAINER_FULL.display(minion)
            return
        }

        var waterLocation: Location? = null
        run breaking@{
            LocationUtils.getAllBlocksInRadius(minion.getLocation(), 2.0, false).fastFor {
                if (it.block.type != Material.WATER) return@fastFor

                waterLocation = it
                return@breaking
            }
        }

        if (waterLocation == null) {
            Warnings.NO_WATER_NEARBY.display(minion)
            return
        }
        Warnings.remove(minion, Warnings.NO_WATER_NEARBY)

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        var loot: List<ItemStack> = generateFishingLoot(minion, waterLocation!!)

        val preFishEvent = PreFisherMinionFishEvent(minion, loot)
        Bukkit.getPluginManager().callEvent(preFishEvent)
        if (preFishEvent.isCancelled) {
            return
        }
        if (preFishEvent.item != loot) {
            loot = preFishEvent.item
        }
        val xp = ThreadLocalRandom.current().nextInt(6) + 1

        minion.addToContainerOrDrop(loot)
        val coerced = (minion.getStorage() + xp).coerceIn(0.0, minion.getType().getLong("storage", minion.getLevel()).toDouble())
        minion.setStorage(coerced)

        minion.setActions(minion.getActionAmount() + 1)
        minion.damageTool()
    }

    /**
     * 生成钓鱼战利品，支持 CustomFishing 集成
     * CustomFishing 的概率和区域条件由 CustomFishing 插件自己控制
     */
    private fun generateFishingLoot(minion: Minion, waterLocation: Location): List<ItemStack> {
        // 检查是否启用 CustomFishing 并且小人等级达到要求
        if (AxMinionsPlugin.integrations.customFishingIntegration) {
            val enableCustomFishing = getConfig().getBoolean("custom-fishing.enabled", false)
            val requiredLevel = getConfig().getInt("custom-fishing.required-level", 5)
            
            if (enableCustomFishing && minion.getLevel() >= requiredLevel) {
                // 使用 CustomFishing 的概率和区域系统生成战利品
                val customLoot = CustomFishingIntegration.generateLoot(minion, waterLocation)
                if (customLoot.isNotEmpty()) {
                    return customLoot
                }
                // 如果 CustomFishing 没有返回战利品（可能是区域条件不满足），则使用原版
            }
        }
        
        // 默认使用原版钓鱼战利品
        return NMSHandler.get().generateRandomFishingLoot(minion, waterLocation)
    }
}