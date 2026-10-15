package app.ransel.data.remote

/** Where the API lives: scheme + host (+ port), without `/api/v1`. Per platform. */
expect fun apiOrigin(): String

/** Human-readable name of this device, stored with the refresh token ("Pixel 8", "Chrome"). */
expect fun deviceName(): String
