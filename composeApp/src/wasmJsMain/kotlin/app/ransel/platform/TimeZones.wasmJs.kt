package app.ransel.platform

/** Loads the IANA zone database into kotlinx-datetime; referenced from main() so the bundler keeps it. */
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

internal val jsJodaTimeZones: Any = JsJodaTimeZoneModule
