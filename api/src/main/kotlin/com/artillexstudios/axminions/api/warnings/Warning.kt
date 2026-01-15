package com.artillexstudios.axminions.api.warnings

import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.Minion

abstract class Warning(private val name: String) {

    fun getName(): String {
        return this.name
    }

    abstract fun getContent(): String

    fun display(minion: Minion) {
        if (!Config.DISPLAY_WARNINGS()) return

        if (minion.getWarning() == null) {
            // 使用配置的高度偏移创建警告全息图
            val height = Config.WARNING_HOLOGRAM_HEIGHT()
            val hologramLocation = minion.getLocation().clone().add(0.0, height, 0.0)
            minion.createWarningHologram(hologramLocation, this.getContent())
            minion.setWarning(this)
        }
    }
}
