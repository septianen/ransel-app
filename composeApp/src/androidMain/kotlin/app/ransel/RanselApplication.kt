package app.ransel

import android.app.Application
import app.ransel.di.initKoin
import app.ransel.push.createNotificationChannels
import org.koin.android.ext.koin.androidContext

class RanselApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RanselApplication) }
        createNotificationChannels(this)
    }
}
