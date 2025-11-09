package com.example.irrigctrl

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.android.service.MqttAndroidClient

class MqttHelper(private val ctx: Context) {
    companion object {
        private const val TAG = "MqttHelper"
    }

    private var client: MqttAndroidClient? = null

    fun connect(brokerUrl: String, clientId: String = MqttClient.generateClientId(),
                user: String? = null, pass: String? = null,
                onConnected: ((Boolean)->Unit)? = null) {

        try {
            client?.close(true)
        } catch (_: Exception) {}
        client = MqttAndroidClient(ctx, brokerUrl, clientId)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            if (!user.isNullOrEmpty()) {
                userName = user
                password = pass?.toCharArray()
            }
        }

        client?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) { Log.d(TAG, "Conn lost: ${cause?.message}") }
            override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {}
            override fun deliveryComplete(token: IMqttToken?) {}
        })

        client?.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d(TAG,"MQTT Connected")
                onConnected?.invoke(true)
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.d(TAG,"MQTT Connect failed: ${exception?.message}")
                onConnected?.invoke(false)
            }
        })
    }

    fun publish(topic: String, payload: String, qos: Int = 0) {
        val c = client ?: run { Log.d(TAG,"MQTT client not connected"); return }
        try {
            val msg = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply { this.qos = qos; isRetained = false }
            c.publish(topic, msg)
            Log.d(TAG,"MQTT published to $topic")
        } catch (ex: Exception) {
            Log.d(TAG,"Publish error: ${ex.message}")
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
            client?.close()
        } catch (_: Exception) {}
    }
}
