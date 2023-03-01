package com.example.bottomsheeterrorreportingreproduction.androidapp

import KotlinFunctionLibrary
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import androidapp.CONSTANTS
import androidapp.ShiurAdapter
import androidapp.ShiurWithAllFilterMetadata
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.example.android.media.viewmodels.MainActivityViewModel
import com.example.android.media.viewmodels.NowPlayingFragmentViewModel
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.library.ShiurQueue
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import timber.log.Timber
import java.io.File


lateinit var mEntireApplicationContext: Context

lateinit var preferencesManager: SharedPreferences
lateinit var downloadManager: DownloadManager
lateinit var mAppScope: CoroutineScope
lateinit var tdApplication: TorahDownloadsApplication

//Set default just in case they are accessed by the first network fragment before they are fetched from preference manager
var keepCache by KotlinFunctionLibrary.LazyMutable { true }
var cacheTimeInSeconds by KotlinFunctionLibrary.LazyMutable { defaultCacheStaleInSeconds.toInt() }
val defaultCacheStaleInSeconds by lazy { "2419200" }

//val shiurDownloadPreferenceOnlyWiFI by lazy { mEntireApplicationContext.getString(R.string.only_over_wifi_code) }
lateinit var mainActivityViewModel: MainActivityViewModel
lateinit var nowPlayingFragmentViewModel: NowPlayingFragmentViewModel
val shiurQueue = ShiurQueue()
private lateinit var shiurCurrentlyBeingPlayed: ShiurWithAllFilterMetadata
var shiurWaitingToBePlayed: ShiurWithAllFilterMetadata? =
    null //the previously playing shiur and its metadata needs to be saved to the db (in the function named saveRecentSongAndQueueToStorage) before shiurCurrentlyBeingPlayed can be updated - and because that is multithreaded, it uncertain when that will finish - so once that is done, it will read this variable, call setCurrentlyPlayingShiurInMemoryAndPersist(), and set this variable to null.

fun getCurrentlyPlayingShiur() =
    runCatching { shiurCurrentlyBeingPlayed }.getOrNull() //I don't want the backing field to be accessed directly, because it could throw an uninitialized exception.

fun setCurrentlyPlayingShiurInMemoryAndPersist(shiur: ShiurWithAllFilterMetadata) {
    Util.ld("setCurrentlyPlayingShiurInMemoryAndPersist(shiur=$shiur)")
    if (::shiurCurrentlyBeingPlayed.isInitialized) {
        Util.ld("shiurCurrentlyBeingPlayed initialized, saving progress: $shiurCurrentlyBeingPlayed")
    }
    shiur.isHistory = true
    shiurCurrentlyBeingPlayed = shiur
}

//val pastShiurSerializationVersions = listOf<String>()
class TorahDownloadsApplication : Application() {

    init {
        StrictMode.enableDefaults();
    }

    @ExperimentalSerializationApi
    override fun onCreate() {
        super.onCreate()
        mEntireApplicationContext = this
        tdApplication = this
        mAppScope = CoroutineScope(SupervisorJob())
        preferencesManager =
            PreferenceManager.getDefaultSharedPreferences(this@TorahDownloadsApplication)
        mAppScope.launch(Dispatchers.IO) {
            keepCache = true
            cacheTimeInSeconds = defaultCacheStaleInSeconds.toInt()
        }
        mAppScope.launch(Dispatchers.Default) {//launch all pre-processing on a separate thread to improve startup time
            launch(Dispatchers.Default) {
                val networkCallback = object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        //TODO when adding ability to only use wifi or mobile data, here should probably be where to check wether this Network object is WiFi, if that is possible
                        ld("User gained network connection.")
                        CONSTANTS.isOnline = true
                    }

                    override fun onLost(network: Network) {
                        ld("User lost network connection.")
                        CONSTANTS.isOnline = false
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(
                        networkCallback
                    )
                } else {
                    (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                        .registerNetworkCallback(
                            NetworkRequest
                                .Builder()
                                .build(),
                            networkCallback
                        )
                }
                downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val shiur = ShiurAdapter
                    .shiurim
                    .first()
                val localURI =
                    Uri.fromFile(
                        File(
                            getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                            "${shiur.shiurID}.mp3"
                        )
                    )
                shiur.localURI = localURI
                downloadManager.enqueue(
                    DownloadManager
                        .Request(
                            shiur
                                .remoteURL
                                .toUri()
                        )
                        .setAllowedOverMetered(false)
                        .setDestinationUri(localURI)
                )
            }
        }
        configureLogging()
    }

    /**
     * Initialize the logging mechanism.
     *
     * In development, this is a wrapper around the regular Android Log class so the logs can be seen in logcat/AndroidStudio.
     * In production, it logs to Sentry so that we can catch crashes and fix them.
     */
    private fun configureLogging(reportInDebug: Boolean = false) {
        Timber.plant(Timber.DebugTree())
    }
}
