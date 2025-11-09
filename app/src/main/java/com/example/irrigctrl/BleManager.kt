package com.example.irrigctrl

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import kotlin.concurrent.schedule

class BleManager(private val ctx: Context, private var deviceNameToFind: String = "Wireless_Bridge") {
    companion object {
        private const val TAG = "BleManager"

        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHAR_UUID_RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write (phone -> device)
        val CHAR_UUID_TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // notify (device -> phone)
    }

    private val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    var onLog: ((String) -> Unit)? = null
    var onNotification: ((String) -> Unit)? = null
    var onConnected: ((Boolean) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { dev ->
                val name = dev.name ?: result.scanRecord?.deviceName
                if (name != null && (name.contains(deviceNameToFind) || result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true)) {
                    log("Found device: ${name} (${dev.address}) — connecting...")
                    scanner?.stopScan(this)
                    connectToDevice(dev)
                }
            }
        }
        override fun onScanFailed(errorCode: Int) { log("Scan failed: $errorCode") }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = 8000) {
        if (adapter == null || !adapter.isEnabled) { log("Bluetooth adapter not available or disabled"); return }
        val filters = listOf(ScanFilter.Builder().setDeviceName(deviceNameToFind).setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(filters, settings, scanCallback)
        log("BLE scan started for name='$deviceNameToFind'")
        Timer().schedule(timeoutMs) { stopScan() }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try { scanner?.stopScan(scanCallback); log("BLE scan stopped") } catch (t: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        gatt = device.connectGatt(ctx, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT connected — discovering services")
                onConnected?.invoke(true)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT disconnected")
                onConnected?.invoke(false)
                cleanup()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Services discovered")
                val svc = g.getService(SERVICE_UUID)
                if (svc != null) {
                    rxChar = svc.getCharacteristic(CHAR_UUID_RX)
                    txChar = svc.getCharacteristic(CHAR_UUID_TX)
                    if (txChar != null) {
                        g.setCharacteristicNotification(txChar, true)
                        val desc = txChar?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (desc != null) {
                            g.writeDescriptor(desc)
                        }
                    }
                    log("RX/TX characteristics ready")
                } else { log("Expected service not found") }
            } else log("Service discovery failed: $status")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_UUID_TX) {
                val bytes = characteristic.value
                val s = bytes?.let { String(it) } ?: ""
                log("NOTIF <- $s")
                onNotification?.invoke(s)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            log("Descriptor write status: $status")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("Characteristic write status: $status")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPayload(payload: String) {
        val char = rxChar ?: run { log("RX char not ready"); return }
        val g = gatt ?: run { log("No GATT"); return }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var offset = 0
        val mtu = 20 // conservative default; on modern devices you can request larger
        while (offset < bytes.size) {
            val end = minOf(bytes.size, offset + mtu)
            val slice = bytes.copyOfRange(offset, end)
            char.value = slice
            g.writeCharacteristic(char)
            offset = end
            // slight delay for reliability
            try { Thread.sleep(50) } catch (_: InterruptedException) {}
        }
        log("TX -> $payload")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try { gatt?.disconnect() } catch (t: Throwable) {}
    }

    fun cleanup() {
        try { gatt?.close() } catch (t: Throwable) {}
        gatt = null; rxChar = null; txChar = null
    }

    fun updateDeviceName(newName: String) {
        deviceNameToFind = newName
        log("BLE device name updated to: $deviceNameToFind")
    }

    private fun log(msg: String) { Log.d(TAG, msg); onLog?.invoke(msg) }
}
