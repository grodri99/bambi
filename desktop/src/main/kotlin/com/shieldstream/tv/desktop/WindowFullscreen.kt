package com.shieldstream.tv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Pantalla completa de Bambi, una sola para toda la app (F11, el botón del reproductor, Escape, al abrir).
 *
 * En Windows no se usa la de Compose: con la pantalla escalada (125 %, 150 %…) dejaba franjas blancas abajo y a la
 * derecha, no siempre volvía a la ventana con F11 y al abrir Bambi en pantalla completa salía mal colocada (lo
 * contaron en el foro). Se hace como Chrome o VLC: se quita el marco de la ventana y se le da exactamente el
 * rectángulo del monitor en el que está, en píxeles de verdad; al salir, se devuelven el marco y la posición de antes
 * (también si estaba maximizada).
 */
class WindowFullscreen(private val state: WindowState) {
    var window: ComposeWindow? = null

    /** Si está a pantalla completa ahora mismo. */
    var active by mutableStateOf(false)
        private set

    /** Tamaño y sitio de la ventana antes de ponerla a pantalla completa (para guardarlos al cerrar). */
    var windowed: Triple<androidx.compose.ui.unit.DpSize, androidx.compose.ui.window.WindowPosition, WindowPlacement>? = null
        private set

    private var savedStyle = 0
    private var savedPlacement: WinUser.WINDOWPLACEMENT? = null

    fun toggle() = set(!active)

    fun set(on: Boolean) {
        if (on == active) return
        val w = window
        if (on) windowed = Triple(state.size, state.position, state.placement)
        val done = if (w != null && SecretStore.WINDOWS) runCatching { native(w, on) }.onFailure {
            System.err.println("Pantalla completa (Windows) falló: ${it.message}")
        }.isSuccess else false
        if (!done) {
            // Fuera de Windows (o si algo falla), la de Compose.
            state.placement = if (on) WindowPlacement.Fullscreen else windowed?.third?.takeIf { it != WindowPlacement.Fullscreen } ?: WindowPlacement.Floating
        }
        active = on
        if (!on) windowed = null
    }

    private fun native(window: ComposeWindow, on: Boolean) {
        val user = User32.INSTANCE
        val hwnd = WinDef.HWND(Pointer(window.windowHandle))
        if (on) {
            // Se guarda cómo estaba (normal o maximizada, y dónde) para volver igual al salir.
            val placement = WinUser.WINDOWPLACEMENT()
            user.GetWindowPlacement(hwnd, placement)
            savedPlacement = placement
            savedStyle = user.GetWindowLong(hwnd, GWL_STYLE)
            val monitor = user.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
            val info = WinUser.MONITORINFO()
            user.GetMonitorInfo(monitor, info)
            val r = info.rcMonitor
            user.SetWindowLong(hwnd, GWL_STYLE, savedStyle and (WS_CAPTION or WS_THICKFRAME or WS_MAXIMIZE).inv())
            user.SetWindowPos(hwnd, null, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_NOOWNERZORDER or SWP_FRAMECHANGED or SWP_SHOWWINDOW)
        } else {
            // El marco de antes, pero sin «maximizada»: eso se vuelve a pedir a Windows al final (si no, Windows cree
            // que ya lo está y la deja del tamaño del monitor sin maximizar).
            val style = savedStyle.takeIf { it != 0 } ?: (user.GetWindowLong(hwnd, GWL_STYLE) or WS_CAPTION or WS_THICKFRAME)
            user.SetWindowLong(hwnd, GWL_STYLE, style and WS_MAXIMIZE.inv())
            val placement = savedPlacement
            val wasMaximized = placement?.showCmd == SW_SHOWMAXIMIZED || style and WS_MAXIMIZE != 0
            placement?.let {
                it.showCmd = SW_SHOWNORMAL
                user.SetWindowPlacement(hwnd, it)
            }
            user.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED)
            if (wasMaximized) user.ShowWindow(hwnd, SW_MAXIMIZE)
        }
        window.validate()
        window.repaint()
    }

    private companion object {
        const val GWL_STYLE = -16
        const val WS_CAPTION = 0x00C00000
        const val WS_THICKFRAME = 0x00040000
        const val WS_MAXIMIZE = 0x01000000
        const val MONITOR_DEFAULTTONEAREST = 2
        const val SW_SHOWNORMAL = 1
        const val SW_SHOWMAXIMIZED = 3
        const val SW_MAXIMIZE = 3
        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_FRAMECHANGED = 0x0020
        const val SWP_SHOWWINDOW = 0x0040
        const val SWP_NOOWNERZORDER = 0x0200
    }
}
