package com.virtualgamepad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {
    
    companion object {
        private const val TAG = "VirtualGamepad"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2
        private val DEVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    // UI组件
    private lateinit var txtStatus: TextView
    private lateinit var txtSensor: TextView
    private lateinit var txtConnection: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnCalibrate: Button
    private lateinit var connectionSpinner: Spinner
    private lateinit var deviceListView: ListView
    
    // 传感器
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    // 传感器数据
    private val gyroData = FloatArray(3)
    private val accelData = FloatArray(3)
    private val magnetData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var calibrationOffset = FloatArray(3)
    
    // 蓝牙
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothService: BluetoothService? = null
    private var connectedDevice: BluetoothDevice? = null
    private val deviceList = mutableListOf<String>()
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()
    
    // 控制数据
    private var controlData = ControlData()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var connectionType = "蓝牙"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initSensors()
        initBluetooth()
        setupListeners()
        
        handler.postDelayed(dataSendRunnable, 16)
    }
    
    private fun initViews() {
        txtStatus = findViewById(R.id.txtStatus)
        txtSensor = findViewById(R.id.txtSensor)
        txtConnection = findViewById(R.id.txtConnection)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        connectionSpinner = findViewById(R.id.connectionSpinner)
        deviceListView = findViewById(R.id.deviceListView)
        
        ArrayAdapter.createFromResource(
            this,
            R.array.connection_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            connectionSpinner.adapter = adapter
        }
        
        val deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        deviceListView.adapter = deviceAdapter
    }
    
    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    private fun initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
    }
    
    private fun setupListeners() {
        btnConnect.setOnClickListener {
            Toast.makeText(this, "请在Android设备上测试连接功能", Toast.LENGTH_SHORT).show()
        }
        
        btnDisconnect.setOnClickListener {
            isConnected = false
            txtStatus.text = "已断开连接"
        }
        
        btnCalibrate.setOnClickListener {
            calibrationOffset[0] = gyroData[0]
            calibrationOffset[1] = gyroData[1]
            calibrationOffset[2] = gyroData[2]
            Toast.makeText(this, "传感器已校准", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, gyroData, 0, 3)
                    val deadZone = 0.1f
                    val scale = 2.0f
                    
                    val pitch = (gyroData[1] - calibrationOffset[1]).coerceIn(-scale, scale)
                    val roll = (gyroData[0] - calibrationOffset[0]).coerceIn(-scale, scale)
                    
                    controlData.leftX = if (Math.abs(roll) > deadZone) roll / scale else 0f
                    controlData.leftY = if (Math.abs(pitch) > deadZone) pitch / scale else 0f
                    
                    txtSensor.text = String.format("陀螺仪: X=%.2f, Y=%.2f, Z=%.2f", 
                        gyroData[0], gyroData[1], gyroData[2])
                }
            }
        }
    }
    
    private fun updateControlData() {
        if (isConnected) {
            sendControlData()
        }
    }
    
    private fun sendControlData() {
        val json = gson.toJson(controlData)
        Log.d(TAG, "发送数据: $json")
    }
    
    private val dataSendRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                sendControlData()
            }
            handler.postDelayed(this, 16)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(dataSendRunnable)
    }
}

