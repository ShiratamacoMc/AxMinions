package com.artillexstudios.axminions.api.minions

import com.artillexstudios.axapi.packetentity.PacketEntity
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.warnings.Warning
import java.util.UUID
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

interface Minion : InventoryHolder {

    fun getType(): MinionType

    fun spawn()

    fun tick()

    fun getLocation(): Location

    fun updateInventories()

    fun openInventory(player: Player)

    fun getAsItem(): ItemStack

    fun getLevel(): Int

    fun setActions(actions: Long)

    fun setStorage(storage: Double)

    fun setWarning(warning: Warning?)

    fun getWarning(): Warning?

    /**
     * 创建警告全息图
     * @param location 全息图位置
     * @param content 全息图内容
     */
    fun createWarningHologram(location: Location, content: String)

    /**
     * 移除警告全息图
     */
    fun removeWarningHologram()

    /**
     * 获取警告全息图ID
     */
    fun getWarningHologramId(): String?

    /**
     * 更新警告全息图位置（用于reload时更新）
     */
    fun updateWarningHologramLocation()

    fun getOwner(): OfflinePlayer?

    fun getOwnerUUID(): UUID

    fun setTool(tool: ItemStack, save: Boolean = true)

    fun getTool(): ItemStack?

    fun getEntity(): PacketEntity

    fun setLevel(level: Int)

    fun getData(key: String): String?

    fun hasData(key: String): Boolean

    fun getNextAction(): Int

    fun getActionAmount(): Long

    fun getStorage(): Double

    fun getRange(): Double

    fun resetAnimation()

    fun animate()

    fun setLinkedChest(location: Location?)

    fun getLinkedChest(): Location?

    fun setDirection(direction: Direction, save: Boolean = true)

    fun getDirection(): Direction

    fun remove()

    fun getLinkedInventory(): Inventory?

    fun addToContainerOrDrop(itemStack: ItemStack)

    fun addWithRemaining(itemStack: ItemStack): HashMap<Int, ItemStack>?

    fun addToContainerOrDrop(itemStack: Iterable<ItemStack>)

    fun updateArmour()

    fun getLocationId(): Int

    fun getChestLocationId(): Int

    fun removeOpenInventory(inventory: Inventory)

    fun isTicking(): Boolean

    fun setTicking(ticking: Boolean)

    fun setRange(range: Double)

    fun setNextAction(nextAction: Int)

    fun markDirty()

    fun damageTool(amount: Int = 1)

    fun canUseTool(): Boolean

    fun isOwnerOnline(): Boolean

    fun setOwnerOnline(online: Boolean)

    fun getCharge(): Long

    fun setCharge(charge: Long)
}
