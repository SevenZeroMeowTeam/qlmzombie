package com.qlm.zombie.player

import com.qlm.zombie.QLMZombieMod
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.feature.ThirstFeature
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.gui.overlay.ForgeGui
import net.minecraftforge.client.gui.overlay.IGuiOverlay
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ThirstBarOverlay : IGuiOverlay {
    private val ICON = ResourceLocation(QLMZombieMod.MOD_ID, "textures/gui/water_drop.png")
    private val BAR_BACKGROUND = ResourceLocation("minecraft", "textures/gui/icons.png")

    private var cachedThirst = 100
    private var lastUpdateTime = 0L
    private var cachedBarWidth = 0
    private var registered = false

    private const val BAR_CAPACITY = 10
    private const val UPDATE_INTERVAL_MS = 200L

    fun register() {
        if (registered) return
        registered = true
        FMLJavaModLoadingContext.get().modEventBus.addListener { event: RegisterGuiOverlaysEvent ->
            event.registerAboveAll("thirst_bar", this)
        }
    }

    override fun render(gui: ForgeGui, guiGraphics: GuiGraphics, partialTick: Float, screenWidth: Int, screenHeight: Int) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (!QLMConfig.enableThirst) return
        if (player.isCreative || player.isSpectator) return

        updateCache(player)

        val barWidth = cachedBarWidth
        val barHeight = 10
        val x = screenWidth / 2 + 91
        val y = screenHeight - 49

        renderBackground(guiGraphics, x, y, barWidth, barHeight)

        val filled = cachedThirst
        val ratio = filled.toFloat() / ThirstFeature.MAX_THIRST
        val fillWidth = (barWidth * ratio).toInt().coerceAtLeast(0)

        renderFill(guiGraphics, x, y, fillWidth, barHeight, ratio)

        renderIcon(guiGraphics, x - 12, y - 1)
    }

    private fun updateCache(player: Player) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            lastUpdateTime = currentTime
            cachedThirst = ThirstFeature.getThirst(player)
            cachedBarWidth = (ThirstFeature.MAX_THIRST / BAR_CAPACITY) * 2
            if (cachedBarWidth < 0) cachedBarWidth = 0
        }
    }

    private fun renderBackground(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        guiGraphics.fill(x, y, x + width, y + height, 0x33000000)
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y, 0xFF555555.toInt())
        guiGraphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF555555.toInt())
        guiGraphics.fill(x - 1, y, x, y + height, 0xFF555555.toInt())
        guiGraphics.fill(x + width, y, x + width + 1, y + height, 0xFF555555.toInt())
    }

    private fun renderFill(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, ratio: Float) {
        val color = when {
            ratio <= 0.2f -> 0xFFFF0000.toInt()
            ratio <= 0.5f -> 0xFFFFAA00.toInt()
            else -> 0xFF3399FF.toInt()
        }
        guiGraphics.fill(x, y, x + width, y + height, color)

        val highlightHeight = (height * 0.3f).toInt().coerceAtLeast(1)
        guiGraphics.fill(x, y, x + width, y + highlightHeight, 0x66FFFFFF.toInt())
    }

    private fun renderIcon(guiGraphics: GuiGraphics, x: Int, y: Int) {
        guiGraphics.fill(x, y, x + 9, y + 9, 0xFF3399FF.toInt())
        guiGraphics.fill(x + 3, y - 2, x + 6, y + 2, 0xFF3399FF.toInt())
        guiGraphics.fill(x + 4, y - 3, x + 5, y + 1, 0xFF3399FF.toInt())
        guiGraphics.fill(x + 2, y + 4, x + 7, y + 8, 0xFF3399FF.toInt())

        guiGraphics.fill(x + 2, y + 1, x + 4, y + 3, 0xCCFFFFFF.toInt())
    }
}