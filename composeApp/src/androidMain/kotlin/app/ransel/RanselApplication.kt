package app.ransel

import android.app.Application
import app.ransel.di.initKoin
import org.koin.android.ext.koin.androidContext

class RanselApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RanselApplication) }
    }
}
