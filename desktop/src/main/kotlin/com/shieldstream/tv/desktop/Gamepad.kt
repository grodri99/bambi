package com.shieldstream.tv.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import java.awt.Robot
import java.awt.Window
import java.awt.event.KeyEvent
import kotlin.concurrent.thread

/**
 * Mando de Xbox (o cualquiera XInput) como el mando de la tele: la cruceta o el stick mueven el foco, A elige, B
 * vuelve, Start pausa, X omite la intro e Y pasa al siguiente episodio. Se lee el mando 60 veces por segundo y se
 * convierte en las mismas teclas que ya entiende Bambi, solo mientras la ventana de Bambi está activa.
 */
object Gamepad {
    @Structure.FieldOrder("wButtons", "bLeftTrigger", "bRightTrigger", "sThumbLX", "sThumbLY", "sThumbRX", "sThumbRY")
    class XInputGamepad : Structure() {
        @JvmField var wButtons: Short = 0
        @JvmField var bLeftTrigger: Byte = 0
        @JvmField var bRightTrigger: Byte = 0
        @JvmField var sThumbLX: Short = 0
        @JvmField var sThumbLY: Short = 0
        @JvmField var sThumbRX: Short = 0
        @JvmField var sThumbRY: Short = 0
    }

    @Structure.FieldOrder("dwPacketNumber", "Gamepad")
    class XInputState : Structure() {
        @JvmField var dwPacketNumber: Int = 0
        @JvmField var Gamepad: XInputGamepad = XInputGamepad()
    }

    private interface XInput : Library {
        fun XInputGetState(userIndex: Int, state: XInputState): Int
    }

    /** Botón (o dirección del stick) → tecla de Bambi. */
    val MAPPING = linkedMapOf(
        0x0001 to KeyEvent.VK_UP,
        0x0002 to KeyEvent.VK_DOWN,
        0x0004 to KeyEvent.VK_LEFT,
        0x0008 to KeyEvent.VK_RIGHT,
        0x0010 to KeyEvent.VK_SPACE, // Start
        0x0020 to KeyEvent.VK_ESCAPE, // Back
        0x1000 to KeyEvent.VK_ENTER, // A
        0x2000 to KeyEvent.VK_ESCAPE, // B
        0x4000 to KeyEvent.VK_S, // X: omitir intro
        0x8000 to KeyEvent.VK_N, // Y: siguiente episodio
        0x0100 to KeyEvent.VK_PAGE_UP, // LB
        0x0200 to KeyEvent.VK_PAGE_DOWN, // RB
    )

    private const val STICK_DEADZONE = 16_000

    /** Botones pulsados contando el stick izquierdo como cruceta. */
    fun buttons(raw: Int, lx: Int, ly: Int): Int {
        var b = raw and 0xFFFF
        if (ly > STICK_DEADZONE) b = b or 0x0001
        if (ly < -STICK_DEADZONE) b = b or 0x0002
        if (lx < -STICK_DEADZONE) b = b or 0x0004
        if (lx > STICK_DEADZONE) b = b or 0x0008
        return b
    }

    fun start(window: Window) {
        if (!SecretStore.WINDOWS) return
        val xinput = listOf("xinput1_4", "xinput1_3", "xinput9_1_0").firstNotNullOfOrNull { name ->
            runCatching { Native.load(name, XInput::class.java) }.getOrNull()
        } ?: return
        thread(name = "mando", isDaemon = true) {
            val robot = runCatching { Robot() }.getOrNull() ?: return@thread
            val state = XInputState()
            var previous = 0
            val heldSince = HashMap<Int, Long>()
            val lastRepeat = HashMap<Int, Long>()
            while (true) {
                Thread.sleep(16)
                val connected = (0 until 4).firstOrNull { xinput.XInputGetState(it, state) == 0 } != null
                val now = if (connected) buttons(state.Gamepad.wButtons.toInt(), state.Gamepad.sThumbLX.toInt(), state.Gamepad.sThumbLY.toInt()) else 0
                if (!window.isFocused) {
                    previous = now
                    continue
                }
                val time = System.currentTimeMillis()
                for ((mask, key) in MAPPING) {
                    val down = now and mask != 0
                    val was = previous and mask != 0
                    when {
                        down && !was -> {
                            press(robot, key)
                            heldSince[mask] = time
                            lastRepeat[mask] = time
                        }
                        // Mantener una dirección repite, como en la tele.
                        down && mask <= 0x0008 && time - (heldSince[mask] ?: time) > 400 && time - (lastRepeat[mask] ?: time) > 110 -> {
                            press(robot, key)
                            lastRepeat[mask] = time
                        }
                    }
                }
                previous = now
            }
        }
    }

    private fun press(robot: Robot, key: Int) {
        robot.keyPress(key)
        robot.keyRelease(key)
    }
}
