package app.ransel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.CoroutineContext

/**
 * Main between tests. A ViewModel's request can come back after its test has ended; resuming on a reset Main crashed
 * whichever test ran next, and resuming on the old test dispatcher let it keep working inside the next test. This
 * Main drops such leftovers: work of a finished test simply stops. Every test sets its own Main in `@BeforeTest`.
 */
private object StoppedMain : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
}

/** Use in `@AfterTest` instead of `Dispatchers.resetMain()`. */
@OptIn(ExperimentalCoroutinesApi::class)
fun stopTestMain() = Dispatchers.setMain(StoppedMain)
