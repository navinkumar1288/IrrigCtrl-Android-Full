# IrrigCtrl-Android

Android app to control the Heltec irrigation controller via SMS (default), BLE, or MQTT.

Features:
- Settings page to persist controller phone number, BLE device name, MQTT broker/topic and credentials.
- Send compact SCH|... or CFG|... strings as SMS, BLE write, or MQTT publish.
- Simple Compose UI and logs.

Build:
1. Ensure `gradlew` and `gradle/wrapper/gradle-wrapper.jar` exist (or run `gradle wrapper` locally).
2. Open in Android Studio or push to GitHub and run the included workflow (it runs `./gradlew assembleRelease`).
