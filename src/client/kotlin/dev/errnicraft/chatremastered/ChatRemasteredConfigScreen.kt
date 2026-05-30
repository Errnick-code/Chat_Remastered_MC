package dev.errnicraft.chatremastered

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ChatRemasteredConfigScreen(private val parent: Screen?) : Screen(
    Component.translatable("chat-remastered.config_title")
) {
    private val MIN = 0.5f
    private val MAX = 2.0f
    private val STEP = 0.05f
    private val CLOSED_MIN = 8
    private val CLOSED_MAX = 20

    private val PANEL_W   = 280
    private val CTRL_W    = 240
    private val CTRL_H    = 20
    private val CTRL_GAP  = 4    // между контролами одной секции
    private val SEC_LABEL_H = 10 // высота подписи секции
    private val SEC_GAP   = 12   // отступ между секциями

    // Цвета
    private val COL_BG         = 0xEE0D0D12.toInt()
    private val COL_BORDER      = 0xFF252530.toInt()
    private val COL_ACCENT      = 0xFF4A8FD4.toInt()
    private val COL_SEC_BG      = 0xFF141418.toInt()
    private val COL_SEC_BORDER  = 0xFF1E1E26.toInt()
    private val COL_SEC_LABEL   = 0xFF6A9FCC.toInt()
    private val COL_TITLE       = 0xFFEEEEEE.toInt()

    // Запоминаем Y-позиции секций для рендера
    private var sec1Y = 0
    private var sec1H = 0
    private var sec2Y = 0
    private var sec2H = 0
    private var panelY0 = 0
    private var panelY1 = 0

    override fun init() {
        val cx = width / 2
        val ctrlX = cx - CTRL_W / 2

        // Считаем высоту:
        // Заголовок: 20px (с отступами)
        // Секция 1 (Фото): label + 2 слайдера + зазоры + padding
        // Секция 2 (Чат): label + 2 контрола + зазоры + padding
        // Кнопка Done + отступ
        val titleH = 20
        val sec1CtrlH = CTRL_H * 2 + CTRL_GAP       // 2 слайдера
        val sec2CtrlH = CTRL_H * 2 + CTRL_GAP       // 1 слайдер + 1 кнопка
        val sec1TotalH = SEC_LABEL_H + 6 + sec1CtrlH + 8
        val sec2TotalH = SEC_LABEL_H + 6 + sec2CtrlH + 8
        val doneH = CTRL_H
        val totalH = 8 + titleH + 10 + sec1TotalH + SEC_GAP + sec2TotalH + 12 + doneH + 10

        val panelX0 = cx - PANEL_W / 2
        panelY0 = height / 2 - totalH / 2
        val panelX1 = cx + PANEL_W / 2
        panelY1 = panelY0 + totalH

        var y = panelY0 + 8 + titleH + 10

        // === Секция 1: Внешний вид фото ===
        sec1Y = y
        sec1H = sec1TotalH
        y += SEC_LABEL_H + 6  // под лейблом

        val initChat = ((ChatRemasteredConfig.previewScale - MIN) / (MAX - MIN)).toDouble().coerceIn(0.0, 1.0)
        addRenderableWidget(object : AbstractSliderButton(ctrlX, y, CTRL_W, CTRL_H, Component.empty(), initChat) {
            init { updateMessage() }
            override fun updateMessage() { setMessage(Component.translatable("chat-remastered.preview_scale_slider", "%.0f%%".format(snap() * 100))) }
            override fun applyValue() { ChatRemasteredConfig.previewScale = snap(); ChatRemasteredConfig.saveConfig() }
            private fun snap(): Float { var s = MIN + value.toFloat() * (MAX - MIN); s = (Math.round(s / STEP) * STEP).coerceIn(MIN, MAX); return s }
        })
        y += CTRL_H + CTRL_GAP

        val initInput = ((ChatRemasteredConfig.inputPreviewScale - MIN) / (MAX - MIN)).toDouble().coerceIn(0.0, 1.0)
        addRenderableWidget(object : AbstractSliderButton(ctrlX, y, CTRL_W, CTRL_H, Component.empty(), initInput) {
            init { updateMessage() }
            override fun updateMessage() { setMessage(Component.translatable("chat-remastered.input_scale_slider", "%.0f%%".format(snap() * 100))) }
            override fun applyValue() { ChatRemasteredConfig.inputPreviewScale = snap(); ChatRemasteredConfig.saveConfig() }
            private fun snap(): Float { var s = MIN + value.toFloat() * (MAX - MIN); s = (Math.round(s / STEP) * STEP).coerceIn(MIN, MAX); return s }
        })
        y += CTRL_H + 8 + SEC_GAP

        // === Секция 2: Высота чата ===
        sec2Y = y
        sec2H = sec2TotalH
        y += SEC_LABEL_H + 6

        val initClosed = ((ChatRemasteredConfig.closedChatLines - CLOSED_MIN).toDouble() / (CLOSED_MAX - CLOSED_MIN)).coerceIn(0.0, 1.0)
        addRenderableWidget(object : AbstractSliderButton(ctrlX, y, CTRL_W, CTRL_H, Component.empty(), initClosed) {
            init { updateMessage() }
            override fun updateMessage() { setMessage(Component.translatable("chat-remastered.closed_chat_lines_slider", snap())) }
            override fun applyValue() { ChatRemasteredConfig.closedChatLines = snap(); ChatRemasteredConfig.saveConfig(); minecraft?.gui?.chat?.rescaleChat() }
            private fun snap() = (CLOSED_MIN + Math.round(value * (CLOSED_MAX - CLOSED_MIN))).toInt().coerceIn(CLOSED_MIN, CLOSED_MAX)
        })
        y += CTRL_H + CTRL_GAP

        fun heightLabel() = Component.translatable(if (ChatRemasteredConfig.fullscreenChat) "chat-remastered.chat_height_fullscreen" else "chat-remastered.chat_height_vanilla")
        addRenderableWidget(Button.builder(heightLabel()) {
            ChatRemasteredConfig.fullscreenChat = !ChatRemasteredConfig.fullscreenChat
            ChatRemasteredConfig.saveConfig()
            it.message = heightLabel()
            minecraft?.gui?.chat?.rescaleChat()
        }.bounds(ctrlX, y, CTRL_W, CTRL_H).build())
        y += CTRL_H + 8 + 12

        // === Кнопка Done ===
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) {
            minecraft!!.setScreen(parent)
        }.bounds(cx - 75, y, 150, CTRL_H).build())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // renderBackground вызывает blur который уже применён при открытии из игры → краш
        // Рисуем лёгкое затемнение вручную
        graphics.fill(0, 0, width, height, 0x60000000.toInt())
        val cx = width / 2
        val px0 = cx - PANEL_W / 2
        val px1 = cx + PANEL_W / 2

        // === Основная панель ===
        graphics.fill(px0, panelY0, px1, panelY1, COL_BG)
        // Рамка
        graphics.fill(px0,     panelY0,     px1,     panelY0 + 1, COL_BORDER)
        graphics.fill(px0,     panelY1 - 1, px1,     panelY1,     COL_BORDER)
        graphics.fill(px0,     panelY0,     px0 + 1, panelY1,     COL_BORDER)
        graphics.fill(px1 - 1, panelY0,     px1,     panelY1,     COL_BORDER)
        // Акцент сверху
        graphics.fill(px0 + 1, panelY0 + 1, px1 - 1, panelY0 + 3, COL_ACCENT)

        // === Заголовок ===
        graphics.drawCenteredString(font, title, cx, panelY0 + 10, COL_TITLE)

        // === Секция 1 ===
        drawSection(graphics, cx, sec1Y, sec1H,
            Component.translatable("chat-remastered.section_photo"))

        // === Секция 2 ===
        drawSection(graphics, cx, sec2Y, sec2H,
            Component.translatable("chat-remastered.section_chat_height"))

        super.render(graphics, mouseX, mouseY, delta)
    }

    private fun drawSection(graphics: GuiGraphics, cx: Int, y: Int, h: Int, label: Component) {
        val px0 = cx - PANEL_W / 2
        val px1 = cx + PANEL_W / 2
        val secX0 = px0 + 8
        val secX1 = px1 - 8

        // Фон секции
        graphics.fill(secX0, y, secX1, y + h, COL_SEC_BG)
        // Рамка секции
        graphics.fill(secX0,     y,         secX1,     y + 1,     COL_SEC_BORDER)
        graphics.fill(secX0,     y + h - 1, secX1,     y + h,     COL_SEC_BORDER)
        graphics.fill(secX0,     y,         secX0 + 1, y + h,     COL_SEC_BORDER)
        graphics.fill(secX1 - 1, y,         secX1,     y + h,     COL_SEC_BORDER)
        // Акцент слева
        graphics.fill(secX0, y + 1, secX0 + 2, y + h - 1, COL_ACCENT)

        // Лейбл секции
        graphics.drawString(font, label, secX0 + 6, y + 2, COL_SEC_LABEL, false)
        // Линия под лейблом
        graphics.fill(secX0 + 2, y + SEC_LABEL_H + 2, secX1 - 2, y + SEC_LABEL_H + 3, COL_SEC_BORDER)
    }

    override fun onClose() {
        minecraft!!.setScreen(parent)
    }
}
