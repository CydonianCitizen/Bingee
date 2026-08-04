package com.cydoniancitizen.bingee

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.cydoniancitizen.bingee.app.StartupWorkCoordinator
import com.cydoniancitizen.bingee.data.importexport.BackupShareFileStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class BingeeApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    internal lateinit var startupWorkCoordinator: StartupWorkCoordinator

    @Inject
    internal lateinit var backupShareFileStore: BackupShareFileStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            try {
                backupShareFileStore.cleanupStale()
            } catch (_: Exception) {
                // Temporary cleanup is best effort and must not block or crash startup.
            }
        }
        applicationScope.launch { startupWorkCoordinator.reconcile() }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
        }.build()
}
