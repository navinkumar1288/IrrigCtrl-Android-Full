package com.example.irrigctrl

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("irrig_prefs", Context.MODE_PRIVATE)

    var mqttBroker: String
        get() = prefs.getString("mqtt_broker", "tcp://39aff691b9b5421ab98adc2addedbd83.s1.eu.hivemq.cloud:1883")!!
        set(v) = prefs.edit().putString("mqtt_broker", v).apply()

    var mqttTopic: String
        get() = prefs.getString("mqtt_topic", "irrigation/site01/schedule/set")!!
        set(v) = prefs.edit().putString("mqtt_topic", v).apply()

    var mqttUser: String
        get() = prefs.getString("mqtt_user", "")!!
        set(v) = prefs.edit().putString("mqtt_user", v).apply()

    var mqttPass: String
        get() = prefs.getString("mqtt_pass", "")!!
        set(v) = prefs.edit().putString("mqtt_pass", v).apply()

    var controllerPhone: String
        get() = prefs.getString("controller_phone", "+919944272647")!!
        set(v) = prefs.edit().putString("controller_phone", v).apply()

    var bleDeviceName: String
        get() = prefs.getString("ble_device_name", "Wireless_Bridge")!!
        set(v) = prefs.edit().putString("ble_device_name", v).apply()

    fun resetDefaults() {
        mqttBroker = "tcp://39aff691b9b5421ab98adc2addedbd83.s1.eu.hivemq.cloud:1883"
        mqttTopic = "irrigation/site01/schedule/set"
        mqttUser = ""
        mqttPass = ""
        controllerPhone = "+919944272647"
        bleDeviceName = "Wireless_Bridge"
    }
}
