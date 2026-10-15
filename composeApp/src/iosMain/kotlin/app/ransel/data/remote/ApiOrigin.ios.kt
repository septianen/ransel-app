package app.ransel.data.remote

import platform.UIKit.UIDevice

// The iOS simulator reaches the host machine through localhost. Replace with the production origin on release.
actual fun apiOrigin(): String = "http://localhost:8080"

actual fun deviceName(): String = UIDevice.currentDevice.name
