package com.artillexstudios.axminions.integrations.customcrops

import com.artillexstudios.axminions.api.minions.Minion
import net.momirealms.customcrops.api.BukkitCustomCropsPlugin
import net.momirealms.customcrops.api.core.Registries
import net.momirealms.customcrops.api.core.block.BreakReason
import net.momirealms.customcrops.api.core.block.CropBlock
import net.momirealms.customcrops.api.core.world.Pos3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack

object CustomCropsIntegration {
    private var enabled = false
    private var debug = false
    
    // 存储待收获的位置和对应的小人，用于拦截掉落物
    private val pendingHarvests = mutableMapOf<Location, HarvestContext>()
    
    data class HarvestContext(
        val minion: Minion,
        val cropId: String,
        val drops: MutableList<ItemStack> = mutableListOf()
    )

    fun isEnabled(): Boolean = enabled

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }
    
    fun setDebug(debug: Boolean) {
        this.debug = debug
    }
    
    private fun log(message: String) {
        if (debug) {
            Bukkit.getLogger().info("[AxMinions-CC-Debug] $message")
        }
    }

    /**
     * 获取位置的 CustomCrops ID（支持方块和家具形式）
     * CustomCrops 作物可以是方块形式或家具形式
     */
    private fun getCustomCropsId(location: Location): String? {
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val itemManager = plugin.itemManager
            
            // 使用 anyID 同时检查方块和家具
            val id = itemManager.anyID(location)
            log("anyID 返回: '$id' at $location")
            
            // 过滤掉空气和原版方块
            if (id.isEmpty() || id == "AIR" || id.startsWith("minecraft:") || id == location.block.type.name) {
                null
            } else {
                id
            }
        } catch (e: Exception) {
            log("获取CustomCrops ID时出错: ${e.message}")
            null
        }
    }

    /**
     * 检查位置是否有 CustomCrops 的作物
     * 支持方块形式和家具形式的作物
     * 
     * @param location 要检查的位置
     */
    fun isCustomCrop(location: Location): Boolean {
        if (!enabled) return false
        
        return try {
            val id = getCustomCropsId(location)
            
            if (id == null) {
                return false
            }
            
            // 检查是否在作物注册表中
            val result = Registries.STAGE_TO_CROP_UNSAFE.containsKey(id)
            if (result && debug) {
                log("检测到CustomCrops作物: $id at $location")
            }
            result
        } catch (e: Exception) {
            if (debug) {
                log("检查作物时出错: ${e.message}")
            }
            false
        }
    }

    /**
     * 检查位置是否有成熟的 CustomCrops 作物
     * 优化版本：一次性检查作物和成熟度，避免重复调用
     * 
     * @param location 要检查的位置
     * @return 如果是成熟的 CustomCrops 作物返回 true
     */
    fun isMatureCrop(location: Location): Boolean {
        if (!enabled) return false
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val worldManager = plugin.worldManager
            
            // 获取 CustomCrops ID
            val id = getCustomCropsId(location) ?: return false
            
            // 检查是否在作物注册表中
            val cropConfigs = Registries.STAGE_TO_CROP_UNSAFE.get(id)
            if (cropConfigs == null || cropConfigs.isEmpty()) {
                return false
            }
            
            // 获取 CustomCrops 世界
            val ccWorld = worldManager.getWorld(location.world).orElse(null) ?: return false
            
            // 获取方块状态
            val pos3 = Pos3.from(location)
            val blockState = ccWorld.getBlockState(pos3).orElse(null) ?: return false
            
            // 检查是否是作物方块
            val blockType = blockState.type()
            if (blockType !is CropBlock) {
                return false
            }
            val cropBlock = blockType
            
            // 获取作物配置
            val cropConfig = cropBlock.config(blockState) ?: return false
            
            // 检查是否达到最大生长点数
            val currentPoint = cropBlock.point(blockState)
            val maxPoints = cropConfig.maxPoints()
            val isMature = currentPoint >= maxPoints
            
            if (debug) {
                log("作物 ${cropConfig.id()}: 当前点数=$currentPoint, 最大点数=$maxPoints, 成熟=$isMature")
            }
            
            isMature
        } catch (e: Exception) {
            if (debug) {
                log("检查成熟度时出错: ${e.message}")
                e.printStackTrace()
            }
            false
        }
    }

    /**
     * 收获 CustomCrops 作物并返回掉落物
     * 
     * @param minion 小人实例
     * @param location 要收获的位置
     * @return 掉落物列表，如果收获失败返回空列表
     */
    fun harvestCropWithDrops(minion: Minion, location: Location): List<ItemStack> {
        if (!enabled) return emptyList()
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val api = plugin.api
            val worldManager = plugin.worldManager
            
            log("=== 开始收获CustomCrops作物 ===")
            log("位置: $location")
            
            // 获取作物配置用于回种
            val id = getCustomCropsId(location)
            if (id == null) {
                log("无法获取作物ID")
                return emptyList()
            }
            
            val cropConfigs = Registries.STAGE_TO_CROP_UNSAFE.get(id)
            if (cropConfigs == null || cropConfigs.isEmpty()) {
                log("未找到作物配置")
                return emptyList()
            }
            
            // 获取 CustomCrops 世界和方块状态
            val ccWorld = worldManager.getWorld(location.world).orElse(null)
            if (ccWorld == null) {
                log("未找到CustomCrops世界")
                return emptyList()
            }
            
            val pos3 = Pos3.from(location)
            val blockState = ccWorld.getBlockState(pos3).orElse(null)
            if (blockState == null) {
                log("未找到方块状态")
                return emptyList()
            }
            
            val blockType = blockState.type()
            if (blockType !is CropBlock) {
                log("方块类型不是CropBlock")
                return emptyList()
            }
            
            val cropConfig = blockType.config(blockState)
            if (cropConfig == null) {
                log("未找到作物配置")
                return emptyList()
            }
            
            val cropId = cropConfig.id()
            log("作物ID: $cropId")
            
            // 记录收获前附近的掉落物
            val world = location.world ?: return emptyList()
            val nearbyItemsBefore = world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0)
                .filterIsInstance<Item>()
                .map { it.uniqueId }
                .toSet()
            
            // 使用 API 模拟破坏作物
            api.simulatePlayerBreakCrop(null, null, location, BreakReason.ACTION)
            
            // 收集新产生的掉落物
            val drops = mutableListOf<ItemStack>()
            val nearbyItemsAfter = world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0)
                .filterIsInstance<Item>()
            
            for (item in nearbyItemsAfter) {
                if (!nearbyItemsBefore.contains(item.uniqueId)) {
                    // 这是新产生的掉落物
                    val itemStack = item.itemStack.clone()
                    drops.add(itemStack)
                    item.remove() // 移除掉落物实体
                    log("收集掉落物: ${itemStack.type} x ${itemStack.amount}")
                }
            }
            
            // 检查掉落物中是否有种子
            val seedIds = cropConfig.seeds() // 获取所有可能的种子ID
            log("作物种子ID列表: $seedIds")
            
            var hasSeed = false
            var seedFound: ItemStack? = null
            
            // 遍历掉落物，查找种子
            for (drop in drops) {
                val itemManager = plugin.itemManager
                val dropId = itemManager.id(drop)
                log("检查掉落物ID: $dropId")
                
                if (seedIds.contains(dropId)) {
                    log("找到种子: $dropId")
                    hasSeed = true
                    seedFound = drop
                    break
                }
            }
            
            // 如果有种子，扣除一个种子并回种
            if (hasSeed && seedFound != null) {
                if (seedFound.amount > 1) {
                    seedFound.amount -= 1
                    log("扣除1个种子，剩余: ${seedFound.amount}")
                } else {
                    drops.remove(seedFound)
                    log("扣除最后1个种子")
                }
                
                // 重新种植作物（从点数0开始）
                val replanted = api.placeCrop(location, cropId, 0)
                log("重新种植: $replanted")
            } else {
                log("掉落物中没有种子，不回种")
            }
            
            log("=== CustomCrops作物收获完成，掉落物数量: ${drops.size} ===")
            
            drops
        } catch (e: Exception) {
            log("收获作物时出错: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 收获 CustomCrops 作物
     * 
     * @param minion 小人实例
     * @param location 要收获的位置
     * @param drops 用于收集掉落物的列表
     * @return 是否成功收获
     */
    fun harvestCrop(minion: Minion, location: Location, drops: MutableList<ItemStack>): Boolean {
        val harvestedDrops = harvestCropWithDrops(minion, location)
        if (harvestedDrops.isNotEmpty()) {
            drops.addAll(harvestedDrops)
            return true
        }
        return false
    }

    /**
     * 检查位置是否可以种植 CustomCrops 作物
     * 条件：位置下方是 CustomCrops 的花盆，且位置上方是空的
     * 
     * @param location 要检查的位置（作物将种植在这里）
     * @return 如果可以种植返回 true
     */
    fun canPlantAt(location: Location): Boolean {
        if (!enabled) return false
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val itemManager = plugin.itemManager
            
            // 检查当前位置是否为空（没有作物）
            val currentId = itemManager.anyID(location)
            if (currentId != "AIR" && !currentId.startsWith("minecraft:air")) {
                // 位置不为空，检查是否已经有作物
                if (Registries.STAGE_TO_CROP_UNSAFE.containsKey(currentId)) {
                    return false // 已经有作物了
                }
            }
            
            // 检查下方是否是 CustomCrops 的花盆
            val potLocation = location.clone().subtract(0.0, 1.0, 0.0)
            val potId = itemManager.anyID(potLocation)
            
            if (potId.isEmpty() || potId == "AIR") {
                return false
            }
            
            // 检查是否是花盆
            val potConfig = Registries.ITEM_TO_POT.get(potId)
            if (potConfig != null) {
                log("检测到可种植位置: $location (花盆: $potId)")
                return true
            }
            
            false
        } catch (e: Exception) {
            if (debug) {
                log("检查可种植位置时出错: ${e.message}")
            }
            false
        }
    }

    /**
     * 从物品栏中查找 CustomCrops 种子
     * 
     * @param inventory 要搜索的物品栏
     * @return 找到的种子信息（物品栏槽位、物品、作物ID），如果没找到返回 null
     */
    fun findSeedInInventory(inventory: org.bukkit.inventory.Inventory): SeedInfo? {
        if (!enabled) return null
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val itemManager = plugin.itemManager
            
            for (slot in 0 until inventory.size) {
                val item = inventory.getItem(slot) ?: continue
                if (item.type.isAir) continue
                
                val itemId = itemManager.id(item)
                val cropConfig = Registries.SEED_TO_CROP.get(itemId)
                
                if (cropConfig != null) {
                    log("找到种子: $itemId -> 作物: ${cropConfig.id()}")
                    return SeedInfo(slot, item, cropConfig.id(), itemId)
                }
            }
            
            null
        } catch (e: Exception) {
            if (debug) {
                log("查找种子时出错: ${e.message}")
            }
            null
        }
    }

    /**
     * 种子信息
     */
    data class SeedInfo(
        val slot: Int,
        val item: ItemStack,
        val cropId: String,
        val seedId: String
    )

    /**
     * 在指定位置种植 CustomCrops 作物
     * 
     * @param location 种植位置
     * @param cropId 作物ID
     * @return 是否成功种植
     */
    fun plantCrop(location: Location, cropId: String): Boolean {
        if (!enabled) return false
        
        return try {
            val plugin = BukkitCustomCropsPlugin.getInstance()
            val api = plugin.api
            
            // 检查作物配置是否存在
            val cropConfig = Registries.CROP.get(cropId)
            if (cropConfig == null) {
                log("作物配置不存在: $cropId")
                return false
            }
            
            // 检查花盆白名单
            val potLocation = location.clone().subtract(0.0, 1.0, 0.0)
            val potId = plugin.itemManager.anyID(potLocation)
            val potWhitelist = cropConfig.potWhitelist()
            
            if (potWhitelist.isNotEmpty() && !potWhitelist.contains(potId)) {
                log("花盆 $potId 不在作物 $cropId 的白名单中")
                return false
            }
            
            // 种植作物
            val result = api.placeCrop(location, cropId, 0)
            log("种植作物 $cropId 在 $location: $result")
            
            result
        } catch (e: Exception) {
            if (debug) {
                log("种植作物时出错: ${e.message}")
                e.printStackTrace()
            }
            false
        }
    }

    /**
     * 尝试在指定位置种植作物（从小人的容器中获取种子）
     * 
     * @param minion 小人实例
     * @param location 种植位置
     * @return 是否成功种植
     */
    fun tryPlantFromInventory(minion: Minion, location: Location): Boolean {
        if (!enabled) return false
        
        val inventory = minion.getLinkedInventory() ?: return false
        
        // 查找种子
        val seedInfo = findSeedInInventory(inventory) ?: return false
        
        // 检查花盆白名单
        val plugin = BukkitCustomCropsPlugin.getInstance()
        val cropConfig = Registries.CROP.get(seedInfo.cropId)
        if (cropConfig != null) {
            val potLocation = location.clone().subtract(0.0, 1.0, 0.0)
            val potId = plugin.itemManager.anyID(potLocation)
            val potWhitelist = cropConfig.potWhitelist()
            
            if (potWhitelist.isNotEmpty() && !potWhitelist.contains(potId)) {
                log("花盆 $potId 不在作物 ${seedInfo.cropId} 的白名单中，跳过")
                return false
            }
        }
        
        // 种植作物
        if (plantCrop(location, seedInfo.cropId)) {
            // 扣除种子
            val item = inventory.getItem(seedInfo.slot)
            if (item != null) {
                if (item.amount > 1) {
                    item.amount -= 1
                    inventory.setItem(seedInfo.slot, item)
                } else {
                    inventory.setItem(seedInfo.slot, null)
                }
                log("扣除种子: ${seedInfo.seedId}")
            }
            return true
        }
        
        return false
    }
}
