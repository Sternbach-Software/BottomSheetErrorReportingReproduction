/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bottomsheeterrorreportingreproduction.androidapp.media.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidapp.CONSTANTS
import androidapp.ShiurAdapter
import androidapp.ShiurWithAllFilterMetadata
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot.EXTRA_RECENT
import com.example.bottomsheeterrorreportingreproduction.R
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.library.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.setCurrentlyPlayingShiurInMemoryAndPersist
import com.example.bottomsheeterrorreportingreproduction.androidapp.shiurQueue
import com.example.bottomsheeterrorreportingreproduction.androidapp.shiurWaitingToBePlayed
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.contentsToString
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.le
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.Util
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.util.Util.constrainValue
import kotlinx.coroutines.*

/**
 * This class is the entry point for browsing and playback commands from the APP's UI
 * and other apps that wish to play music via UAMP (for example, Android Auto or
 * the Google Assistant).
 *
 * Browsing begins with the method [MusicService.onGetRoot], and continues in
 * the callback [MusicService.onLoadChildren].
 *
 * For more information on implementing a MediaBrowserService,
 * visit [https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowserservice.html](https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowserservice.html).
 *
 * This class also handles playback for Cast sessions.
 * When a Cast session is active, playback commands are passed to a
 * [CastPlayer](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/ext/cast/CastPlayer.html),
 * otherwise they are passed to an ExoPlayer for local playback.
 */
open class MusicService : MediaBrowserServiceCompat() {

    private lateinit var notificationManager: UampNotificationManager
    private lateinit var shiurMediaSource: ShiurQueue
    private lateinit var packageValidator: PackageValidator

    // The current player will either be an ExoPlayer (for local playback) or a CastPlayer (for
    // remote playback through a Cast device).
    private lateinit var currentPlayer: Player


    private var _playbackSpeed = 1.0F
    private var _isSkipSilence: Boolean = false //TODO get from shared preferences


    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    protected lateinit var mediaSession: MediaSessionCompat
    protected lateinit var mediaSessionConnector: MediaSessionConnector
    private var currentPlaylistItems: List<MediaMetadataCompat> =
        emptyList() //TODO how is this synced with ShiurQueue?
    private var currentMediaItemIndex: Int = 0

    private lateinit var storage: PersistentStorage

    /**
     * This must be `by lazy` because the source won't initially be ready.
     * See [MusicService.onLoadChildren] to see where it's accessed (and first
     * constructed).
     */
    private val browseTree: BrowseTree by lazy {
        BrowseTree(applicationContext, shiurMediaSource)
    }

    private var isForegroundService = false

    private val uAmpAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = PlayerEventListener()

    /**
     * Configure ExoPlayer to handle audio focus for us.
     * See [Player.AudioComponent.setAudioAttributes] for details.
     */
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
            .apply {
                setAudioAttributes(uAmpAudioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                addListener(playerListener)
            }
    }

    @ExperimentalCoroutinesApi
    override fun onCreate() {
        super.onCreate()

        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        // Create a new MediaSession.
        mediaSession = MediaSessionCompat(this, "MusicService")
            .apply {
                setSessionActivity(sessionActivityPendingIntent)
                isActive = true
            }
        /**
         * In order for [MediaBrowserCompat.ConnectionCallback.onConnected] to be called,
         * a [MediaSessionCompat.Token] needs to be set on the [MediaBrowserServiceCompat].
         *
         * It is possible to wait to set the session token, if required for a specific use-case.
         * However, the token *must* be set by the time [MediaBrowserServiceCompat.onGetRoot]
         * returns, or the connection will fail silently. (The system will not even call
         * [MediaBrowserCompat.ConnectionCallback.onConnectionFailed].)
         */
        sessionToken = mediaSession.sessionToken

        /**
         * The notification manager will use our player and media session to decide when to post
         * notifications. When notifications are posted or removed our listener will be called, this
         * allows us to promote the service to foreground (required so that we're not killed if
         * the main UI is not visible).
         */
        notificationManager = UampNotificationManager(
            this,
            mediaSession.sessionToken,
            PlayerNotificationListener()
        )

        // The media library is built from a remote JSON file. We'll create the source here,
        // and then use a suspend function to perform the download off the main thread.
        shiurMediaSource =
            shiurQueue //TODO replace this with the global queue, and whenever the user wants to play a shiur or playlist, just add it to that queue
        serviceScope.launch {
            shiurMediaSource.load()
        }

        // ExoPlayer will manage the MediaSession for us.
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(UampPlaybackPreparer())
        mediaSessionConnector.setQueueNavigator(UampQueueNavigator(mediaSession))

        switchToPlayer(
            previousPlayer = null,
            newPlayer = exoPlayer
        )
        notificationManager.showNotificationForPlayer(currentPlayer)

        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)

        storage = PersistentStorage.getInstance(applicationContext)
    }

    /**
     * This is the code that causes UAMP to stop playing when swiping the activity away from
     * recents. The choice to do this is app specific. Some apps stop playback, while others allow
     * playback to continue and allow users to stop it with the notification.
     */
    override fun onTaskRemoved(rootIntent: Intent) {
        ld("onTaskRemoved(rootIntent=$rootIntent)")
        saveRecentSongAndQueueToStorage()
        super.onTaskRemoved(rootIntent)

        /**
         * By stopping playback, the player will transition to [Player.STATE_IDLE] triggering
         * [Player.EventListener.onPlayerStateChanged] to be called. This will cause the
         * notification to be hidden and trigger
         * [PlayerNotificationManager.NotificationListener.onNotificationCancelled] to be called.
         * The service will then remove itself as a foreground service, and will call
         * [stopSelf].
         */
        currentPlayer.clearMediaItems()
        currentPlayer.stop()
    }

    override fun onDestroy() {
        ld("musicService.onDestroy()")
        mediaSession.run {
            isActive = false
            release()
        }

        // Cancel coroutines when the service is going away.
        serviceJob.cancel()

        // Free ExoPlayer resources.
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    /**
     * Returns the "root" media ID that the client should request to get the list of
     * [MediaItem]s to browse/play.
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {

        /*
         * By default, all known clients are permitted to search, but only tell unknown callers
         * about search if permitted by the [BrowseTree].
         */
        val isKnownCaller = packageValidator.isKnownCaller(clientPackageName, clientUid)
        val rootExtras = Bundle().apply {
            putBoolean(
                MEDIA_SEARCH_SUPPORTED,
                isKnownCaller || browseTree.searchableByUnknownCaller
            )
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
        }

        return if (isKnownCaller) {
            /**
             * By default return the browsable root. Treat the EXTRA_RECENT flag as a special case
             * and return the recent root instead.
             */
            val isRecentRequest = rootHints?.getBoolean(EXTRA_RECENT) ?: false
            val browserRootPath = if (isRecentRequest) UAMP_RECENT_ROOT else UAMP_BROWSABLE_ROOT
            BrowserRoot(browserRootPath, rootExtras)
        } else {
            /**
             * Unknown caller. There are two main ways to handle this:
             * 1) Return a root without any content, which still allows the connecting client
             * to issue commands.
             * 2) Return `null`, which will cause the system to disconnect the app.
             *
             * UAMP takes the first approach for a variety of reasons, but both are valid
             * options.
             */
            BrowserRoot(UAMP_EMPTY_ROOT, rootExtras)
        }
    }

    /**
     * Returns (via the [result] parameter) a list of [MediaItem]s that are child
     * items of the provided [parentMediaId]. See [BrowseTree] for more details on
     * how this is build/more details about the relationships.
     */
    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaItem>>
    ) {

        /**
         * If the caller requests the recent root, return the most recently played song.
         */
        if (parentMediaId == UAMP_RECENT_ROOT) {
            result.sendResult(storage.loadRecentSong()?.let { song -> listOf(song) })
        } else {
            // If the media source is ready, the results will be set synchronously here.
            val resultsSent = shiurMediaSource.whenReady { successfullyInitialized ->
                if (successfullyInitialized) {
                    val children = browseTree[parentMediaId]?.map { item ->
                        MediaItem(item.description, item.flag)
                    }
                    result.sendResult(children)
                } else {
                    mediaSession.sendSessionEvent(NETWORK_FAILURE, null)
                    result.sendResult(null)
                }
            }

            // If the results are not ready, the service must "detach" the results before
            // the method returns. After the source is ready, the lambda above will run,
            // and the caller will be notified that the results are ready.
            //
            // See [MediaItemFragmentViewModel.subscriptionCallback] for how this is passed to the
            // UI/displayed in the [RecyclerView].
            if (!resultsSent) {
                result.detach()
            }
        }
    }

    /**
     * Returns a list of [MediaItem]s that match the given search query
     */
    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaItem>>
    ) {

        val resultsSent = shiurMediaSource.whenReady { successfullyInitialized ->
            if (successfullyInitialized) {
                val resultsList = shiurMediaSource.search(query, extras ?: Bundle.EMPTY)
                    .map { mediaMetadata ->
                        MediaItem(mediaMetadata.description, mediaMetadata.flag)
                    }
                result.sendResult(resultsList)
            }
        }

        if (!resultsSent) {
            result.detach()
        }
    }

    /**
     * Load the supplied list of songs and the song to play into the current player.
     */
    private fun preparePlaylist(
        metadataList: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat,
        playWhenReady: Boolean,
        playbackStartPositionMs: Long
    ) {
        ld(
            "called preparePlaylist(\n" +
                    "        metadataList=$metadataList,\n" +
                    "        itemToPlay=$itemToPlay,\n" +
                    "        playWhenReady=$playWhenReady,\n" +
                    "        playbackStartPositionMs=$playbackStartPositionMs\n" +
                    "    )"
        )
        // Since the playlist was probably based on some ordering (such as tracks
        // on an album), find which window index to play first so that the song the
        // user actually wants to hear plays first.
        val initialWindowIndex = /*if (itemToPlay == null) 0 else*/
            metadataList.indexOf(itemToPlay).coerceAtLeast(0)

        currentPlayer.playWhenReady = playWhenReady
//        currentPlayer.stop()
        // Set playlist and prepare.
        val list = (metadataList + itemToPlay).distinct()
            .filter { it.description != null/*is this a good way to check if it is NOTHING_PLAYING?*/ }//TODO can theoretically be optimimzed because if metadataList only has one item, and it is itemToPlay, then the user just wants to play that one song, so no need to create all of these immutable copied objects with distinct().filter()
        ld("Setting media items: ${list.map { it.description }}")
        if (list.isNotEmpty()) {
        }//TODO don't remember what I was going to do here, but I think it had to do with filtering out the NOTHING_PLAYING item
        currentPlayer.setMediaItems(
            list.map { meta ->
                meta.toMediaItem(
                    meta
                        .getLong(MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS)
                        .ld { "Got offset from bundle for id ${meta.title?.take(10)}: $it" }
                        .coerceAtLeast(0)
                )
            },
            initialWindowIndex,
            playbackStartPositionMs
        )
        currentPlaylistItems = list
        currentPlayer.prepare()
    }

    private fun switchToPlayer(previousPlayer: Player?, newPlayer: Player) {
        if (previousPlayer == newPlayer) {
            return
        }
        currentPlayer = newPlayer
        if (previousPlayer != null) {
            val playbackState = previousPlayer.playbackState
            if (currentPlaylistItems.isEmpty()) {
                // We are joining a playback session. Loading the session from the new player is
                // not supported, so we stop playback.
                currentPlayer.clearMediaItems()
                currentPlayer.stop()
            } else if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
                preparePlaylist(
                    metadataList = currentPlaylistItems,
                    itemToPlay = currentPlaylistItems[currentMediaItemIndex],
                    playWhenReady = previousPlayer.playWhenReady,
                    playbackStartPositionMs = previousPlayer.currentPosition
                )
            }
        }
        mediaSessionConnector.setPlayer(newPlayer)
        //update new player's previous attributes
        newPlayer.setSkipSilence(_isSkipSilence)
        newPlayer.setPlaybackSpeed(_playbackSpeed)

        previousPlayer?.clearMediaItems()
        previousPlayer?.stop()
    }

    private fun saveRecentSongAndQueueToStorage() {
        ld("saveRecentSongAndQueueToStorage()")

        // Obtain the current song details *before* saving them on a separate thread, otherwise
        // the current player may have been unloaded by the time the save routine runs.
        if (currentPlaylistItems.isEmpty()) {
            return
        }
        val description = currentPlaylistItems[currentMediaItemIndex].description
        val position = currentPlayer.currentPosition

        serviceScope.launch {
            storage.saveRecentSong(
                description,
                position
            )
        }
    }

    fun Player.setSkipSilence(shouldSkip: Boolean) {
        (this as? ExoPlayer)?.skipSilenceEnabled = shouldSkip
        _isSkipSilence = shouldSkip
    }

    private inner class UampQueueNavigator(
        mediaSession: MediaSessionCompat
    ) : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            if (windowIndex < currentPlaylistItems.size) {
                return currentPlaylistItems[windowIndex].description.ld { "Getting media description: $it" }
            }
            return MediaDescriptionCompat.Builder().build()
        }
    }

    private inner class UampPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {

        /**
         * UAMP supports preparing (and playing) from search, as well as media ID, so those
         * capabilities are declared here.
         *
         * TODO: Add support for ACTION_PREPARE and ACTION_PLAY, which mean "prepare/play something".
         */
        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH /*or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE*/

        override fun onPrepare(playWhenReady: Boolean) {
            val recentSong = storage.loadRecentSong() ?: return
            onPrepareFromMediaId(
                recentSong.mediaId!!,
                playWhenReady,
                recentSong.description.extras
            )
        }

        override fun onPrepareFromMediaId(
            mediaId: String,
            playWhenReady: Boolean,
            extras: Bundle?
        ) {
            ld(
                "onPrepareFromMediaId(\n" +
                        "            mediaId = $mediaId,\n" +
                        "            playWhenReady = $playWhenReady,\n" +
                        "            extras = ${extras?.contentsToString()}\n" +
                        "        )"
            )
            shiurMediaSource.whenReady {
                var itemToPlay: MediaMetadataCompat? = shiurMediaSource.find { item ->
                    item.id == mediaId
                }

                if (itemToPlay == null) {
                    serviceScope.launch(Dispatchers.IO) {
                        itemToPlay = ShiurAdapter.shiurim.find {
                            it.shiurID == Util.getShiurID(mediaId).toInt()
                        }!!.asMediaItemMetadata()
                        if (itemToPlay == null) {
                            Util.displayErrorToast(NullPointerException("Error playing $mediaId"))
                        } else {

                            val playbackStartPositionMs = 0L
//                                getPlaybackOffset(shiurByID, extras, itemToPlay)

                            launch(Dispatchers.Main) {
                                preparePlaylist(
                                    //buildPlaylist(itemToPlay),
                                    listOf(itemToPlay) as List<MediaMetadataCompat>,
                                    itemToPlay!!,
                                    playWhenReady,
                                    playbackStartPositionMs
                                )
                            }
                        }
                    }
                } else {
                    preparePlaylist( //TODO DRY up
                        //buildPlaylist(itemToPlay),
                        listOf<MediaMetadataCompat?>(itemToPlay) as List<MediaMetadataCompat>,
                        itemToPlay!!,
                        playWhenReady,
                        0L//getPlaybackOffset(shiurByID, extras, itemToPlay)
                    )
                }
            }
        }

        private fun getPlaybackOffset(
            shiurByID: ShiurWithAllFilterMetadata?,
            extras: Bundle?,
            itemToPlay: MediaMetadataCompat?
        ) = (shiurByID?.let {
            it.playbackProgress.ld { "Got playback progress from shiur: $it" }
//                .getNormalizedShiurProgressOffset(it.length.secondsToMilliseconds())
        }
            ?: extras?.getLong(
                MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS,
                C.TIME_UNSET
            )?.let {
                ld("Got playback progress from extras: $it, itemToPlay.duration=${itemToPlay?.duration}")
                itemToPlay?.duration/*?.let { it1 ->
                    it.getNormalizedShiurProgressOffset(it1)
                        .ld { "Normalized offset: $it" }
                }*/
            }
            ?: C.TIME_UNSET)

        /**
         * This method is used by the Google Assistant to respond to requests such as:
         * - Play Geisha from Wake Up on UAMP
         * - Play electronic music on UAMP
         * - Play music on UAMP
         *
         * For details on how search is handled, see [AbstractMusicSource.search].
         */
        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
            shiurMediaSource.whenReady {
                val metadataList = shiurMediaSource.search(query, extras ?: Bundle.EMPTY)
                if (metadataList.isNotEmpty()) {
                    preparePlaylist(
                        metadataList,
                        metadataList[0],
                        playWhenReady,
                        playbackStartPositionMs = C.TIME_UNSET
                    )
                }
            }
        }

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean {
            ld(
                "onCommand(\n" +
                        "            player$player,\n" +
                        "            command=$command,\n" +
                        "            extras=${extras?.contentsToString()},\n" +
                        "            cb=$cb\n" +
                        "        )"
            )
            val commandSpeedAndPitch = COMMAND_SET_SPEED_AND_PITCH.toString()
            val commandSeek = COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM.toString()
            return when (command) {
                commandSpeedAndPitch -> {
                    setPlaybackSpeed(player, extras!!.getFloat(commandSpeedAndPitch))
                    true
                }
                CONSTANTS.COMMAND_SET_SKIP_SILENCE -> {
                    player.setSkipSilence(extras!!.getBoolean(CONSTANTS.COMMAND_SET_SKIP_SILENCE))
                    true
                }
                commandSeek -> {
                    val pos = extras!!.getLong(commandSeek)
                    ld("Received seek command in music service, seeking to $pos")
                    player.seekTo(pos)
                    true
                }
                else -> false
            }
        }

        /**
         * Builds a playlist based on a [MediaMetadataCompat].
         *
         * TODO: Support building a playlist by artist, genre, etc...
         *
         * @param item Item to base the playlist on.
         * @return a [List] of [MediaMetadataCompat] objects representing a playlist.
         */
        private fun buildPlaylist(item: MediaMetadataCompat): List<MediaMetadataCompat> =
            shiurMediaSource.filter { it.album == item.album }.sortedBy { it.trackNumber }
    }

    private fun setPlaybackSpeed(
        player: Player,
        speed: Float
    ) {
        _playbackSpeed = speed
        player.setPlaybackSpeed(speed)
    }

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener :
        PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, this@MusicService.javaClass)
                )

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            ld("onNotificationCancelled(notificationId=$notificationId, dismissedByUser=$dismissedByUser)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.Listener {

        var playWhenReady = true

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_READY -> {
                    notificationManager.showNotificationForPlayer(currentPlayer)
                    if (playbackState == Player.STATE_READY) {

                        // When playing/paused save the current media item in persistent
                        // storage so that playback can be resumed between device reboots.
                        // Search for "media resumption" for more information.
                        saveRecentSongAndQueueToStorage()

                        shiurWaitingToBePlayed?.let {
                            setCurrentlyPlayingShiurInMemoryAndPersist(it)
                            ld("Setting shiurWaitingToBePlayed to null")
                            shiurWaitingToBePlayed = null
//                            onShiurChangeCompleteListeners.runAndRemoveAll()
                        }

                        if (!playWhenReady) {
                            // If playback is paused we remove the foreground state which allows the
                            // notification to be dismissed. An alternative would be to provide a
                            // "close" button in the notification which stops playback and clears
                            // the notification.
                            stopForeground(false)
                            isForegroundService = false
                        }
                    }
                }
                else -> {
                    notificationManager.hideNotification()
                }
            }
        }
        inline val Int.reasonCodeString
            get() = when (this) {
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST"
                PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS"
                PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY"
                PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "PLAY_WHEN_READY_CHANGE_REASON_REMOTE"
                PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM"
                else -> this.toString()
            }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            ld("Play when ready changed to $playWhenReady; reason: ${reason.reasonCodeString}")
//            if(reason == PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) Toast.makeText(applicationContext, "Another app has requested to play audio. Cannot play shiur.", Toast.LENGTH_SHORT).show()
            this.playWhenReady = playWhenReady
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(EVENT_POSITION_DISCONTINUITY)
                || events.contains(EVENT_MEDIA_ITEM_TRANSITION)
                || events.contains(EVENT_PLAY_WHEN_READY_CHANGED)
            ) {
                currentMediaItemIndex = if (currentPlaylistItems.isNotEmpty()) {
                    constrainValue(
                        player.currentMediaItemIndex,
                        /* min= */ 0,
                        /* max= */ currentPlaylistItems.size - 1
                    )
                } else 0
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            var message = R.string.generic_error;
            le("Player error: " + error.errorCodeName + " (" + error.errorCode + "). Stack trace: ${error.stackTraceToString()}");
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            ) {
                message = R.string.error_media_not_found;
            }
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/*
 * (Media) Session events
 */
const val NETWORK_FAILURE = "tech.torah.aldis.androidapp.media.session.NETWORK_FAILURE"

/** Content styling constants */
private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
private const val CONTENT_STYLE_LIST = 1
private const val CONTENT_STYLE_GRID = 2

private const val UAMP_USER_AGENT = "uamp.next"

const val MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS = "playback_start_position_ms"

private const val TAG = "MusicService"
