package io.github.takusan23.himaridroid

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/** koin の初期化を行う */
class HimariDroidApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            // Log Koin into Android logger
            androidLogger()
            // Reference Android context
            androidContext(this@HimariDroidApplication)
            // Load modules
            modules(himariDroidAppModule)
        }
    }
}