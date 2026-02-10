package com.virtualgamepad

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(
    private val context: Context,
    private val callback: ConnectionCallback
) {
    
    interface ConnectionCallback {
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onDataReceived(data: String)
        fun onError(message: String)
    }
    
    companion object {
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null
        
        init {
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                callback.onError("Socket创建失败: ${e.message}")
            }
        }
        
        override fun run() {
            try {
                socket?.connect()
                Handler(Looper.getMainLooper()).post {
                    callback.onConnected(device.name ?: "未知设备")
                }
            } catch (e: IOException) {
                callback.onError("连接失败: ${e.message}")
            }
        }
        
        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                // 忽略关闭异常
            }
        }
    }
    
    private var connectThread: ConnectThread? = null
    
    fun connect(device: BluetoothDevice) {
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }
    
    fun disconnect() {
        connectThread?.cancel()
        connectThread = null
    }
}

