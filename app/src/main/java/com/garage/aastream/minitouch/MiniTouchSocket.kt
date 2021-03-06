package com.garage.aastream.minitouch

import android.graphics.Point
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.garage.aastream.interfaces.OnMinitouchCallback
import com.garage.aastream.utils.DevLog
import java.io.InputStream
import java.io.OutputStream

/**
 * Created by Endy Rubbin on 22.05.2019 13:25.
 * For project: AAStream
 */
class MiniTouchSocket {

    private var socketLocal: LocalSocket? = null
    private var outputStream: OutputStream? = null

    private var version: Int = 0
    private var maxContact: Int = 0
    private var maxX: Double = 0.toDouble()
    private var maxY: Double = 0.toDouble()
    private var maxPressure: Double = 0.toDouble()
    private var pid: Int = 0

    private var projectionOffsetX: Double = 0.toDouble()
    private var projectionOffsetY: Double = 0.toDouble()
    private var projectionWidth: Double = 0.toDouble()
    private var projectionHeight: Double = 0.toDouble()
    private var touchXScale: Double = 0.toDouble()
    private var touchYScale: Double = 0.toDouble()

    internal fun disconnect() {
        DevLog.d("Mini Touch disconnect")
        if (isConnected()) {
            try {
                socketLocal!!.close()
            } catch (e: Exception) {
                DevLog.d("Failed to disconnect socket: $e")
            }
            outputStream = null
            socketLocal = null
        }
    }

    internal fun connect(callback: OnMinitouchCallback?): Boolean {
        DevLog.d("Mini Touch connect socket")
        disconnect()
        val socket = LocalSocket()
        try {
            socket.connect(LocalSocketAddress(DEFAULT_SOCKET_NAME))
            if (inputReadParams(socket.inputStream)) {
                outputStream = socket.outputStream
                socketLocal = socket
            } else {
                socket.close()
            }
        } catch (e: Exception) {
            DevLog.d("Failed to connect socket: $e")
            socketLocal = null
            callback?.onFailed()
        }
        return isConnected()
    }

    fun isConnected(): Boolean {
        return socketLocal != null
    }

    internal fun getPid(): Int {
        DevLog.d("Mini Touch pid: $pid")
        return pid
    }

    private fun inputReadParams(stream: InputStream): Boolean {
        DevLog.d("Mini Touch read")
        val buffer = ByteArray(128)
        pid = 0

        try {
            if (stream.read(buffer) == -1) {
                DevLog.d("Mini Touch read error")
                return false
            }
        } catch (e: Exception) {
            DevLog.d("Mini Touch read exception: $e")
            return false
        }

        val dataLine = String(buffer)
        val lines = dataLine.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (lines.size < 3) {
            DevLog.d("Error: less then 3 lines")
            return false
        }

        val versionLine = lines[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (versionLine.size == 2) {
            version = Integer.parseInt(versionLine[1])
        }
        val limitsLine = lines[1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (limitsLine.size == 5) {
            maxContact = Integer.parseInt(limitsLine[1])
            maxX = Integer.parseInt(limitsLine[2]).toDouble()
            maxY = Integer.parseInt(limitsLine[3]).toDouble()
            maxPressure = Integer.parseInt(limitsLine[4]).toDouble()
        }
        val pidLine = lines[2].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (pidLine.size == 2) {
            pid = Integer.parseInt(pidLine[1])
        }

        DevLog.d("Read pid $pid")
        return true
    }

    private fun writeOutput(command: String): Boolean {
        if (outputStream == null)
            return false
        try {
            outputStream!!.write(command.toByteArray())
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun validateBounds(x: Double, y: Double): Boolean {
        return x >= 0.0 && x < maxX && y >= 0.0 && y < maxY
    }

    internal fun updateTouchTransformations(screenWidth: Double, screenHeight: Double, displaySize: Point) {
        val displayWidth = displaySize.x.toDouble()
        val displayHeight = displaySize.y.toDouble()
        val factX = displayWidth / screenWidth
        val factY = displayHeight / screenHeight

        val fact = if (factX < factY) factX else factY

        projectionWidth = fact * screenWidth
        projectionHeight = fact * screenHeight

        projectionOffsetX = (displayWidth - projectionWidth) / 2.0
        projectionOffsetY = (displayHeight - projectionHeight) / 2.0

        touchXScale = maxX / displayWidth
        touchYScale = maxY / displayHeight
    }

    internal fun touchDown(id: Int, x: Double, y: Double, pressure: Double): Boolean {
        val touchX = (projectionOffsetX + x * projectionWidth) * touchXScale
        val touchY = (projectionOffsetY + y * projectionHeight) * touchYScale
        val touchPressure = pressure * maxPressure

        if (!validateBounds(touchX, touchY))
            return true
        return writeOutput(String.format("d %d %d %d %d\n", id, touchX.toInt(), touchY.toInt(), touchPressure.toInt()))
    }

    internal fun touchMove(id: Int, x: Double, y: Double, pressure: Double): Boolean {
        val touchX = (projectionOffsetX + x * projectionWidth) * touchXScale
        val touchY = (projectionOffsetY + y * projectionHeight) * touchYScale
        val touchPressure = pressure * maxPressure

        if (!validateBounds(touchX, touchY))
            return true
        return writeOutput(String.format("m %d %d %d %d\n", id, touchX.toInt(), touchY.toInt(), touchPressure.toInt()))
    }

    internal fun touchUp(id: Int): Boolean {
        return writeOutput(String.format("u %d\n", id))
    }

    internal fun touchUpAll(): Boolean {
        var ok = true
        for (i in 0 until maxContact)
            ok = ok && touchUp(i)
        return ok
    }

    internal fun touchCommit() {
        writeOutput("c\n")
    }

    companion object {
        private const val DEFAULT_SOCKET_NAME = "minitouch"
    }
}