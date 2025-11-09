package com.example.irrigctrl

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsManager
    private lateinit var ble: BleManager
    private var mqtt: MqttHelper? = null

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        ble = BleManager(this, settings.bleDeviceName)
        mqtt = MqttHelper(this)

        setContent {
            val scope = rememberCoroutineScope()
            var logs = remember { mutableStateListOf<String>() }
            var payload by remember { mutableStateOf("""SCH|ID=SC001,REC=D,T=06:00,SEQ=1:60;2:30""") }

            var mode by remember { mutableStateOf("SMS") } // "SMS" | "BLE" | "MQTT"

            var controllerPhone by remember { mutableStateOf(settings.controllerPhone) }
            var mqttBroker by remember { mutableStateOf(settings.mqttBroker) }
            var mqttTopic by remember { mutableStateOf(settings.mqttTopic) }
            var mqttUser by remember { mutableStateOf(settings.mqttUser) }
            var mqttPass by remember { mutableStateOf(settings.mqttPass) }
            var bleDeviceName by remember { mutableStateOf(settings.bleDeviceName) }

            var lastNotif by remember { mutableStateOf("") }

            ble.onLog = { msg -> scope.launch(Dispatchers.Main) { logs.add(0, "[BLE] $msg") } }
            ble.onNotification = { s -> scope.launch(Dispatchers.Main) { logs.add(0, "[NOTIF] $s"); lastNotif = s } }
            ble.onConnected = { c -> scope.launch(Dispatchers.Main) { logs.add(0, "[STATE] connected=$c") } }

            val perms = rememberMultiplePermissionsState(
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.SEND_SMS
                )
            )

            Scaffold(topBar = { TopAppBar(title = { Text("IrrigCtrl (SMS default)") }) }) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)) {

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DropdownMenuMode(mode) { newMode -> mode = newMode }

                        Button(onClick = {
                            if (!perms.allPermissionsGranted) {
                                perms.launchMultiplePermissionRequest()
                            } else {
                                if (mode == "BLE") {
                                    ble.updateDeviceName(bleDeviceName)
                                    ble.startScan()
                                }
                                logs.add(0, "[ACTION] Started scan/connect (BLE mode)")
                            }
                        }) {
                            Text("Scan & Connect")
                        }
                        Button(onClick = { ble.disconnect() }) { Text("Disconnect") }
                    }

                    Spacer(Modifier.height(10.dp))

                    Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Settings", style = MaterialTheme.typography.h6)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(value = controllerPhone, onValueChange = { controllerPhone = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Controller phone (SMS)") })
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(value = bleDeviceName, onValueChange = { bleDeviceName = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("BLE device name") })
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(value = mqttBroker, onValueChange = { mqttBroker = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("MQTT broker (tcp://host:port)") })
                            OutlinedTextField(value = mqttTopic, onValueChange = { mqttTopic = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("MQTT topic") })
                            OutlinedTextField(value = mqttUser, onValueChange = { mqttUser = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("MQTT username (optional)") })
                            OutlinedTextField(value = mqttPass, onValueChange = { mqttPass = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("MQTT password (optional)") })

                            Spacer(Modifier.height(8.dp))

                            Row {
                                Button(onClick = {
                                    settings.controllerPhone = controllerPhone
                                    settings.bleDeviceName = bleDeviceName
                                    settings.mqttBroker = mqttBroker
                                    settings.mqttTopic = mqttTopic
                                    settings.mqttUser = mqttUser
                                    settings.mqttPass = mqttPass

                                    ble.updateDeviceName(bleDeviceName)

                                    logs.add(0, "[SETTINGS] Saved")
                                }) {
                                    Text("Save Settings")
                                }

                                Spacer(Modifier.width(8.dp))

                                Button(onClick = {
                                    settings.resetDefaults()
                                    controllerPhone = settings.controllerPhone
                                    bleDeviceName = settings.bleDeviceName
                                    mqttBroker = settings.mqttBroker
                                    mqttTopic = settings.mqttTopic
                                    mqttUser = settings.mqttUser
                                    mqttPass = settings.mqttPass
                                    ble.updateDeviceName(bleDeviceName)
                                    logs.add(0, "[SETTINGS] Reset to defaults")
                                }) {
                                    Text("Reset Defaults")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Payload (SCH|... or JSON)") },
                        singleLine = false,
                        maxLines = 5
                    )

                    Spacer(Modifier.height(8.dp))

                    Row {
                        Button(onClick = {
                            if (!perms.allPermissionsGranted) {
                                perms.launchMultiplePermissionRequest()
                                logs.add(0,"[WARN] Please grant permissions")
                                return@Button
                            }

                            when (mode) {
                                "SMS" -> {
                                    val phone = settings.controllerPhone.trim()
                                    if (phone.isEmpty()) { logs.add(0,"[ERR] No phone number set"); return@Button }
                                    val ok = sendSms(phone, payload)
                                    logs.add(0, if (ok) "[SMS] Sent to $phone" else "[SMS] Failed to send")
                                }
                                "BLE" -> {
                                    ble.sendPayload(payload)
                                    logs.add(0, "[BLE] Sent payload")
                                }
                                "MQTT" -> {
                                    mqtt?.connect(settings.mqttBroker, settings.mqttUser.takeIf { it.isNotEmpty() }, settings.mqttPass.takeIf { it.isNotEmpty() }) { ok ->
                                        scope.launch(Dispatchers.Main) { logs.add(0, "[MQTT] connected=$ok") }
                                        if (ok) mqtt?.publish(settings.mqttTopic, payload)
                                    }
                                    logs.add(0, "[MQTT] Publish requested to ${settings.mqttTopic}")
                                }
                            }
                        }) { Text("Send") }

                        Spacer(Modifier.width(8.dp))

                        Button(onClick = {
                            payload = if (mode == "SMS") "CFG|MS=${settings.mqttBroker},MP=8883" else "SCH|ID=SC002,REC=D,T=18:00,SEQ=1:30;2:30"
                        }) { Text("Sample") }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Last notification: $lastNotif", style = MaterialTheme.typography.subtitle1)
                    Spacer(Modifier.height(8.dp))
                    Text("Logs:", style = MaterialTheme.typography.h6)
                    Divider()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(logs.size) { idx -> Text(text = logs[idx], modifier = Modifier.padding(6.dp)) }
                    }

                }
            }
        }
    }

    private fun sendSms(phone: String, message: String): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this as Activity, arrayOf(Manifest.permission.SEND_SMS), 1234)
                return false
            }
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.d(TAG, "SMS sent -> $phone : $message")
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "SMS send failed: ${ex.message}")
            return false
        }
    }
}

// Simple dropdown composable for mode
@Composable
fun DropdownMenuMode(current: String, onChange: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf("SMS","BLE","MQTT")
    Box {
        Button(onClick = { expanded = true }) { Text("Mode: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { m ->
                DropdownMenuItem(onClick = { onChange(m); expanded = false }) { Text(m) }
            }
        }
    }
}
