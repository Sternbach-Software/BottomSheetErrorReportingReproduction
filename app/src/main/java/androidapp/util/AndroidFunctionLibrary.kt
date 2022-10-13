package com.example.bottomsheeterrorreportingreproduction.androidapp.util

//import com.example.bottomsheeterrorreportingreproduction.androidapp.activities.listOfAllSpeakersFromJson

//import com.example.bottomsheeterrorreportingreproduction.androidapp.support.GlideApp
import android.os.Bundle
import android.util.TypedValue
import android.widget.*
import androidapp.CONSTANTS
import androidapp.Shiur
import androidapp.ShiurWithAllFilterMetadata
import androidx.appcompat.app.AppCompatDelegate
import com.example.bottomsheeterrorreportingreproduction.BuildConfig
import kotlinx.coroutines.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.*
import com.example.bottomsheeterrorreportingreproduction.R
import timber.log.Timber
import java.io.*
import java.util.*
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties


/**
 * This is a library for functions used throughout the app, such a filter() for a RecyclerView, to facilitate DRYness.
 * */
object AndroidFunctionLibrary {


    fun getID(shiur: Shiur) =
        when (shiur) {
            is ShiurWithAllFilterMetadata -> shiur.shiurID
            else -> shiur.baseId?.toInt() ?: 0
        }

    fun getTitle(shiur: Shiur): String {
        return when (shiur) {
            is ShiurWithAllFilterMetadata -> shiur.title
            else -> shiur.baseTitle.toString()
        }
    }

    fun getSpeakerName(shiur: Shiur): String {
        return when (shiur) {
            is ShiurWithAllFilterMetadata -> shiur.speaker
            else -> shiur.baseSpeaker.toString()
        }
    }

    fun getShiurLink(shiurID: Int) = "https://torahcdn.net/tdn/$shiurID.mp3"


    val Int.dp: Int
        get() = getPXFromDP(toFloat())

    fun getPXFromDP(yourdpmeasure: Float): Int = TypedValue
        .applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            yourdpmeasure,
            mEntireApplicationContext.resources.displayMetrics
        ).toInt()

    fun <T> runOnUI(lambda: suspend () -> T): Job {
        return mAppScope.launch(Dispatchers.Main) {
            lambda()
        }
    }

    fun getImageURL(speakerId: Int, imageSize: Int): String =
        BuildConfig.api_server +
            "/assets/speakers/" +
            if (imageSize == CONSTANTS.IMAGE_SIZE_UNBOUNDED) "${speakerId}.jpg" /*full size*/
            else "thumb-${imageSize}/${speakerId}.jpg"

    fun isCancellationError(t: Throwable): Boolean =
        t is CancellationException //&& t !is kotlinx.coroutines.JobCancellationException

    fun getErrorMessageAndReportIfNecessary(
        t: Throwable,
        mentionCache: Boolean
    ) = when {
        isFirewallError(t) -> when {
            CONSTANTS.isOnline -> CONSTANTS.OFFLINE_MESSAGE_POSSIBLY_BEING_FILTERED //sometimes this is shown from a simple timeout
            mentionCache -> CONSTANTS.OFFLINE_MESSAGE_MENTION_OF_CACHE.ld("Was alleged to be a firewall error, but user was offline.")
            else -> CONSTANTS.OFFLINE_MESSAGE_NO_MENTION_OF_CACHE
        }
        isOfflineError(t) -> if (mentionCache) CONSTANTS.OFFLINE_MESSAGE_MENTION_OF_CACHE else CONSTANTS.OFFLINE_MESSAGE_NO_MENTION_OF_CACHE
        isCancellationError(t) -> "Network request cancelled."
        else -> "Error"
    }

    fun isOfflineError(t: Throwable) =
        t is java.net.UnknownHostException || t is java.net.SocketException

    fun isFirewallError(t: Throwable) = (
        t is javax.net.ssl.SSLHandshakeException
            || t is javax.net.ssl.SSLProtocolException
            || t is java.net.ConnectException
            || t is java.security.cert.CertPathValidatorException).also {
        if (it) Timber.d(
            t,
            "Was firewall error"
        )
    }

    //log debug/verbose
    fun <T, R> T.lv(message: R) = this.apply { Timber.v(message.toString()) }

    fun <T> T.ld() = this.apply { Timber.d(this.toString()) }
    fun <T, R> T.ld(message: R) = this.apply { Timber.d(message.toString()) }
    fun <T> T.ld(message: (T) -> String) = this.apply { Timber.d(message(this)) }

    fun <T, R> T.le(message: R) = this.apply { Timber.e(message.toString()) }

    fun <T, R> T.le(t: Throwable, message: R) = this.apply { Timber.e(t, message.toString()) }

    fun getShiurID(file: File): String {
        return file.nameWithoutExtension.substringAfterLast("-TD")
            .let { if (file.extension == "temp") it.substringBefore(".mp3") else it }
    }

    fun getShiurID(uri: String): String {
        return if (uri.startsWith("http")) uri.substringAfterLast('/').dropLast(4)
        else getShiurID(File(uri)).also { ld("Shiur path returned: $it") }
    }


    fun playShiur(
        shiur: ShiurWithAllFilterMetadata,
        addToQueue: Boolean = false
    ) {
        ld("shiurWaitingToBePlayed = $shiur")
        shiurWaitingToBePlayed = shiur
//        if(nowPlayingFragmentViewModel.isPlaying.value == true) mainActivityViewModel.pause() //so that the progress on the next shiur doesn't flicker
        if (addToQueue) {
            if (shiurQueue.none()) {
                _playShiur(shiur, true)
            } else {
                addNowPlayingShiurToQueueAndPlayThisInstead(shiur)
                //TODO if they didn't already answer, ask user what they want to do with the queue when playing this shiur (e.g. remove all shiurim from the queue, add this shiur to beginning of queue, etc.)
            }
        } else _playShiur(shiur, false)
    }

    /**
     * TODO I think this doesn't work correctly. And, how should this respond if [shiur] is
     *      already in the queue? Consider the case where a shiur that is part of a series was in
     *      the queue in sequential order in the series (e.g. part 2 was inserted after part 1),
     *      and they clicked the shiur several shiurim before that series appeared in the quue.
     *      On the one hand, if they played the shiur now, it means they want to hear it now and
     *      not in several shiurim when it arrives that shiur's turn, and don't want to listen to
     *      it again (see my comment to [playShiur] as to why they would not want this). On the
     *      other hand, they may want to (also) listen to the shiur in the order that it came in
     *      the series. So should it be removed from its spot? Maybe the user should be asked what they want to do?
     * */
    private fun addNowPlayingShiurToQueueAndPlayThisInstead(shiur: ShiurWithAllFilterMetadata) {
        shiurQueue.catalog.add(0, shiur)
        _playShiur(shiur, true)
    }

    /**
     * @param addToQueue If true, passes queue to player TODO add this not to all relevant functions (e.g. playMedia())
     * */
    private fun _playShiur(shiur: ShiurWithAllFilterMetadata, addToQueue: Boolean) {
        mainActivityViewModel.playMedia(
            shiur,
            addToQueue
        )
    }


    fun setThemeFromSettings() {
        val followSystem = mEntireApplicationContext.getString(R.string.follow_system_theme_code)
        setThemeFromSettings(
            preferencesManager
                .getString(
                    mEntireApplicationContext.getString(R.string.dark_mode_on),
                    followSystem
                ),
            followSystem
        )
    }

    fun setThemeFromSettings(
        code: String?,
        followSystem: String? = mEntireApplicationContext.getString(R.string.follow_system_theme_code)
    ) {
        if (code != followSystem)
            setTheme(code == mEntireApplicationContext.getString(R.string.dark_theme_code))
        else { //reset to system default
            setTheme(null)
        }
    }

    private fun setTheme(nightMode: Boolean?) {
        runOnUI {
            AppCompatDelegate.setDefaultNightMode(
                when {
                    nightMode == null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    nightMode -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
            )
        }
    }

    fun <T> MutableList<T>.toSynchronizedList(): MutableList<T> = Collections.synchronizedList(this)

    fun dateOfCacheStale(): Long = getRightNowInSeconds() - cacheTimeInSeconds

    fun getRightNowInSeconds(): Long = 1L///Instant.now().epochSecond

    fun Any.toStringLikeDataClass(): String {
        val propsString =
            this::class
                .memberProperties
//            .filter { exclude.isEmpty() || !exclude.contains(it.name) }
                .filter { it.visibility == KVisibility.PUBLIC }
                .joinToString(", ") {
                    val value =
//                    if (!mask.isEmpty() && mask.contains(it.name)) "****"
//                    else
                        it.getter.call(this).toString()
                    "${it.name}=${value}"
                }

        return "${this::class.simpleName}(${propsString})"
    }
    fun Bundle.contentsToString() = keySet()?.joinToString { key -> "$key=${get(key)}" }

    fun displayErrorToast(t: Throwable) {
        runOnUI {
            Toast.makeText(
                mEntireApplicationContext,
                getErrorMessageAndReportIfNecessary(t, false),
                Toast.LENGTH_LONG
            )
        }
    }
}
