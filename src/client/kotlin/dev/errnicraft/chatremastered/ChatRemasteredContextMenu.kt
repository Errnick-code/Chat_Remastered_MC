package dev.errnicraft.chatremastered

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class ChatRemasteredContextMenu(
    private val items: List<MenuItem>,
    private val anchorX: Int,
    private val anchorY: Int,
    private val parent: Screen
) : Screen(Component.empty()) {

    data class MenuItem(val label: String, val icon: String = "", val action: () -> Unit)

    companion object {
        @JvmStatic
        fun forImage(imageId: String, ax: Int, ay: Int, parent: Screen) = ChatRemasteredContextMenu(
            listOf(
                MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_save_as"), "💾") {
                    ChatRemasteredClient.saveImageAs(imageId)
                },
                MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_copy_id"), "📋") {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    mc.keyboardHandler.setClipboard(imageId)
                    mc.gui.chat.addMessage(
                        Component.literal("§8[Chat Remastered] §7" + ChatRemasteredConfig.tr("chat-remastered.id_copied", imageId, imageId))
                    )
                }
            ), ax, ay, parent
        )

        @JvmStatic
        fun forMessage(text: String, ax: Int, ay: Int, parent: Screen) = ChatRemasteredContextMenu(
            listOf(
                MenuItem(ChatRemasteredConfig.tr("chat-remastered.ctx_copy_message"), "📋") {
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text)
                }
            ), ax, ay, parent
        )
    }

    private val ITEM_W = 180
    private val ITEM_H = 20
    private val PAD = 6
    private val RADIUS = 6f
    private val ANIM_MS = 120L

    private var menuX = 0
    private var menuY = 0
    private var openTime = 0L
    private var hoveredIndex = -1

    private val itemColors = IntArray(items.size) { 0 }

    override fun init() {
        openTime = System.currentTimeMillis()
        val menuH = PAD * 2 + items.size * ITEM_H + (items.size - 1) * 2
        menuX = anchorX.coerceAtMost(width - ITEM_W - PAD * 2)
        menuY = anchorY.coerceAtMost(height - menuH - PAD * 2)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val menuH = PAD * 2 + items.size * ITEM_H + (items.size - 1) * 2

        val elapsed = (System.currentTimeMillis() - openTime).toFloat()
        val t = (elapsed / ANIM_MS).coerceAtMost(1f)
        val ease = 1f - (1f - t) * (1f - t)

        graphics.pose().pushMatrix()
        val cx = (menuX + ITEM_W / 2).toFloat()
        val cy = menuY.toFloat()
        graphics.pose().translate(cx, cy)
        graphics.pose().scale(ease, ease)
        graphics.pose().translate(-cx, -cy)

        // Тень
        graphics.fill(menuX + 3, menuY + 3, menuX + ITEM_W + 3, menuY + menuH + 3, 0x44000000)
        graphics.fill(menuX + 2, menuY + 2, menuX + ITEM_W + 2, menuY + menuH + 2, 0x22000000)

        // Фон панели
        graphics.fill(menuX, menuY, menuX + ITEM_W, menuY + menuH, 0xF52C2C2C.toInt())
        // Рамка
        graphics.fill(menuX, menuY, menuX + ITEM_W, menuY + 1, 0xFF555555.toInt())
        graphics.fill(menuX, menuY + menuH - 1, menuX + ITEM_W, menuY + menuH, 0xFF555555.toInt())
        graphics.fill(menuX, menuY, menuX + 1, menuY + menuH, 0xFF555555.toInt())
        graphics.fill(menuX + ITEM_W - 1, menuY, menuX + ITEM_W, menuY + menuH, 0xFF555555.toInt())

        hoveredIndex = -1
        items.forEachIndexed { i, item ->
            val iy = menuY + PAD + i * (ITEM_H + 2)
            val hovered = mouseX >= menuX + 1 && mouseX < menuX + ITEM_W - 1 && mouseY >= iy && mouseY < iy + ITEM_H

            if (hovered) hoveredIndex = i

            val targetBg = if (hovered) 0xFF3D6099.toInt() else 0x002C2C2C
            itemColors[i] = lerpColor(itemColors[i], targetBg, 0.25f)
            if (hovered || itemColors[i].ushr(24) > 5) {
                graphics.fill(menuX + 2, iy, menuX + ITEM_W - 2, iy + ITEM_H, itemColors[i])
            }

            val textColor = if (hovered) 0xFFFFFFFF.toInt() else 0xFFDDDDDD.toInt()
            val textX = menuX + PAD + if (item.icon.isNotEmpty()) 14 else 0
            val textY = iy + (ITEM_H - mc.font.lineHeight) / 2
            if (item.icon.isNotEmpty()) {
                graphics.drawString(mc.font, item.icon, menuX + PAD - 1, textY, textColor, false)
            }
            graphics.drawString(mc.font, item.label, textX, textY, textColor, false)
        }

        graphics.pose().popMatrix()
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val fa = (from ushr 24) and 0xFF
        val fr = (from ushr 16) and 0xFF
        val fg = (from ushr 8) and 0xFF
        val fb = from and 0xFF
        val ta = (to ushr 24) and 0xFF
        val tr = (to ushr 16) and 0xFF
        val tg = (to ushr 8) and 0xFF
        val tb = to and 0xFF
        val a = (fa + (ta - fa) * t).toInt().coerceIn(0, 255)
        val r = (fr + (tr - fr) * t).toInt().coerceIn(0, 255)
        val g = (fg + (tg - fg) * t).toInt().coerceIn(0, 255)
        val b = (fb + (tb - fb) * t).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val menuH = PAD * 2 + items.size * ITEM_H + (items.size - 1) * 2
        val inside = mouseX >= menuX && mouseX <= menuX + ITEM_W && mouseY >= menuY && mouseY <= menuY + menuH
        if (!inside) {
            minecraft!!.setScreen(parent)
            return true
        }
        items.forEachIndexed { i, item ->
            val iy = menuY + PAD + i * (ITEM_H + 2)
            if (mouseX >= menuX + 1 && mouseX < menuX + ITEM_W - 1 && mouseY >= iy && mouseY < iy + ITEM_H) {
                item.action()
                minecraft!!.setScreen(parent)
                return true
            }
        }
        return true
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft!!.setScreen(parent)
            return true
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
