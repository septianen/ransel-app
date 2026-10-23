package app.ransel.data.analytics

/**
 * Usage analytics. Android sends to Firebase Analytics, the web to the Firebase JS SDK (production host only), iOS has
 * none yet ([NoAnalytics]). Android sends from debug builds too (as the app.ransel.debug app); localhost sends nothing.
 *
 * Never pass personal data: no names, emails, free text, place names or amounts. The user id is the account UUID.
 */
interface Analytics {
    /** A screen was shown; [name] is the route's class name ([screenNameOf]). */
    fun screen(name: String)

    fun event(event: AnalyticsEvent)

    /** The signed-in account's id, or null after signing out. */
    fun setUser(userId: String?)

    /** A property reports can be split by ([UserProperty]); null removes it. */
    fun setUserProperty(name: String, value: String?)
}

object NoAnalytics : Analytics {
    override fun screen(name: String) = Unit
    override fun event(event: AnalyticsEvent) = Unit
    override fun setUser(userId: String?) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
}

/**
 * Crash reports. Android sends uncaught crashes to Crashlytics by itself; [record] adds caught errors that should not
 * happen (non-fatal). No crash reporting on web and iOS yet ([NoCrashReporter]).
 */
interface CrashReporter {
    fun setUser(userId: String?)

    fun record(error: Throwable)

    /** A breadcrumb: shown with the next crash or non-fatal, so the report says what happened before it. */
    fun log(message: String)

    /** A custom key on every following report ([CrashKey]). */
    fun setKey(key: String, value: String)
}

object NoCrashReporter : CrashReporter {
    override fun setUser(userId: String?) = Unit
    override fun record(error: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setKey(key: String, value: String) = Unit
}

/** Sends everything to [analytics] and leaves each screen and event as a breadcrumb in [crashes]. */
class BreadcrumbAnalytics(private val analytics: Analytics, private val crashes: CrashReporter) : Analytics {
    override fun screen(name: String) {
        crashes.log("screen $name")
        analytics.screen(name)
    }

    override fun event(event: AnalyticsEvent) {
        crashes.log(if (event.params.isEmpty()) event.name else "${event.name} ${event.params}")
        analytics.event(event)
    }

    override fun setUser(userId: String?) = analytics.setUser(userId)

    override fun setUserProperty(name: String, value: String?) = analytics.setUserProperty(name, value)
}

/**
 * The screen name for a navigation route pattern: the route class without package and arguments
 * ("app.ransel.navigation.TripRoute/{tripId}?tab={tab}" -> "TripRoute"). Argument values are never logged.
 */
fun screenNameOf(route: String): String =
    route.substringBefore('/').substringBefore('?').substringAfterLast('.')
