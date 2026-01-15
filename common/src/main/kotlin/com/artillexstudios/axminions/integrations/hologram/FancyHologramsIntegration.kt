package com.artillexstudios.axminions.integrations.hologram

import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.data.TextHologramData
import de.oliver.fancyholograms.api.data.property.Visibility
import de.oliver.fancyholograms.api.hologram.Hologram
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * FancyHolograms v2 集成
 * 用于替代原有的AxAPI全息系统，解决文本换行时位置重叠的问题
 */
object FancyHologramsIntegration {
    
    private var enabled = false
    private val holograms = ConcurrentHashMap<String, Hologram>()
    
    /**
     * 检查FancyHolograms插件是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            FancyHologramsPlugin.isEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 启用集成
     */
    fun enable(): Boolean {
        if (!isAvailable()) {
            return false
        }
        enabled = true
        return true
    }
    
    /**
     * 检查是否已启用
     */
    fun isEnabled(): Boolean = enabled
    
    /**
     * 创建文本全息图
     * @param id 唯一标识符
     * @param location 位置
     * @param text 文本内容（支持多行，用\n分隔）
     * @param seeThrough 是否穿透方块可见
     * @return 创建的全息图，如果失败返回null
     */
    fun createTextHologram(
        id: String,
        location: Location,
        text: String,
        seeThrough: Boolean = true
    ): Hologram? {
        if (!enabled) return null
        
        try {
            val plugin = FancyHologramsPlugin.get()
            val manager = plugin.hologramManager
            
            // 如果已存在同名全息图，先删除
            removeHologram(id)
            
            // 创建全息图数据
            val data = TextHologramData(id, location)
            data.setText(text.split("\n"))
            data.setSeeThrough(seeThrough)
            data.setBillboard(Display.Billboard.CENTER)
            data.setBackground(Color.fromARGB(0, 0, 0, 0)) // 透明背景
            data.setPersistent(false) // 不持久化保存
            data.setVisibility(Visibility.ALL)
            
            // 创建全息图
            val hologram = manager.create(data)
            hologram.createHologram()
            
            // 显示给所有在线玩家
            for (player in Bukkit.getOnlinePlayers()) {
                hologram.forceShowHologram(player)
            }
            
            holograms[id] = hologram
            return hologram
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 更新全息图文本
     * @param id 全息图ID
     * @param text 新的文本内容
     */
    fun updateText(id: String, text: String) {
        val hologram = holograms[id] ?: return
        
        try {
            val data = hologram.data
            if (data is TextHologramData) {
                data.setText(text.split("\n"))
                hologram.forceUpdate()
                hologram.refreshForViewers()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 更新全息图位置
     * @param id 全息图ID
     * @param location 新位置
     */
    fun updateLocation(id: String, location: Location) {
        val hologram = holograms[id] ?: return
        
        try {
            hologram.data.setLocation(location)
            hologram.forceUpdate()
            hologram.refreshForViewers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 删除全息图
     * @param id 全息图ID
     */
    fun removeHologram(id: String) {
        val hologram = holograms.remove(id) ?: return
        
        try {
            // 对所有观看者隐藏
            for (viewerUUID in hologram.viewers) {
                val player = Bukkit.getPlayer(viewerUUID)
                if (player != null) {
                    hologram.forceHideHologram(player)
                }
            }
            hologram.deleteHologram()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取全息图
     * @param id 全息图ID
     * @return 全息图实例，如果不存在返回null
     */
    fun getHologram(id: String): Hologram? = holograms[id]
    
    /**
     * 显示全息图给玩家
     * @param id 全息图ID
     * @param player 玩家
     */
    fun showToPlayer(id: String, player: Player) {
        val hologram = holograms[id] ?: return
        
        try {
            hologram.forceShowHologram(player)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 对玩家隐藏全息图
     * @param id 全息图ID
     * @param player 玩家
     */
    fun hideFromPlayer(id: String, player: Player) {
        val hologram = holograms[id] ?: return
        
        try {
            hologram.forceHideHologram(player)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 清理所有全息图
     */
    fun cleanup() {
        for ((id, _) in holograms) {
            removeHologram(id)
        }
        holograms.clear()
    }
}
