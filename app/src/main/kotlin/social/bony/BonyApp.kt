package social.bony

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import social.bony.logging.FileLoggingTree
import social.bony.logging.LOG_FILE_NAME
import social.bony.notifications.createNotificationChannels
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class BonyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val logFile = File(filesDir, "logs/$LOG_FILE_NAME")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(FileLoggingTree(logFile))

        createNotificationChannels(this)
    }
}
