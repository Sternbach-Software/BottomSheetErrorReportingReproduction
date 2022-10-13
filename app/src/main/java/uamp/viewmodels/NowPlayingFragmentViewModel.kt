/*
 * Copyright 2019 Google Inc. All rights reserved.
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

package com.example.android.media.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.*
import androidx.media.utils.MediaConstants
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.currentPlayBackPosition
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.common.EMPTY_PLAYBACK_STATE
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.common.MusicServiceConnection
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.contentsToString
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import kotlin.math.floor


/**
 * [ViewModel] for [NowPlayingFragment] which displays the album art in full size.
 * It extends AndroidViewModel and uses the [Application]'s context to be able to reference string
 * resources.
 */
class NowPlayingFragmentViewModel(
    app: Application,
    musicServiceConnection: MusicServiceConnection
) : AndroidViewModel(app) {

    /**
     * Utility class used to represent the metadata necessary to display the
     * media item currently being played.
     */
    data class NowPlayingMetadata(
        val id: String?,
        val state: Int?,
        val albumArtUri: Uri?,
        val title: String?,
        val subtitle: String?,
        val duration: Long?,
    ) {

        companion object {
            /**
             * Utility method to convert milliseconds to a display of minutes and seconds
             */
            fun timestampToMSS(position: Long): String {
                val totalSeconds = floor(position / 1E3).toInt()
                val minutes = totalSeconds / 60
                val remainingSeconds = totalSeconds - (minutes * 60)
                return remainingSeconds.toString()
            }
        }
    }

    private val SHIUR_ID_UNSET = -1
    private var playbackState: PlaybackStateCompat = EMPTY_PLAYBACK_STATE
    val mediaMetadata = MutableLiveData<NowPlayingMetadata?>().apply {
        postValue(null)
    }
    val mediaPosition = MutableLiveData<Pair<Int, Long>>().apply { //shiur id to position
        postValue(SHIUR_ID_UNSET to 0L)
    }
    val isPlaying = MutableLiveData<Boolean>().apply {
        postValue(false)
    }

    private var updatePosition = true
    private val handler = Handler(Looper.getMainLooper())

    /**
     * When the session's [PlaybackStateCompat] changes, the [mediaItems] need to be updated
     * so the correct [MediaItemData.playbackRes] is displayed on the active item.
     * (i.e.: play/pause button or blank)
     */
    private val playbackStateObserver = Observer<PlaybackStateCompat> {
        ld("playbackStateObserver called: $it")
        playbackState = it ?: EMPTY_PLAYBACK_STATE
        updateState(playbackState, musicServiceConnection.nowPlaying.value)
    }

    /**
     * When the session's [MediaMetadataCompat] changes, the [mediaItems] need to be updated
     * as it means the currently active item has changed. As a result, the new, and potentially
     * old item (if there was one), both need to have their [MediaItemData.playbackRes]
     * changed. (i.e.: play/pause button or blank)
     */
    private val mediaMetadataObserver = Observer<MediaMetadataCompat> {
        ld("mediaMetadataObserver called: $it")
        updateState(playbackState, it)
    }

    /**
     * Because there's a complex dance between this [ViewModel] and the [MusicServiceConnection]
     * (which is wrapping a [MediaBrowserCompat] object), the usual guidance of using
     * [Transformations] doesn't quite work.
     *
     * Specifically there's three things that are watched that will cause the single piece of
     * [LiveData] exposed from this class to be updated.
     *
     * [MusicServiceConnection.playbackState] changes state based on the playback state of
     * the player, which can change the [MediaItemData.playbackRes]s in the list.
     *
     * [MusicServiceConnection.nowPlaying] changes based on the item that's being played,
     * which can also change the [MediaItemData.playbackRes]s in the list.
     */
    val musicServiceConnection = musicServiceConnection.also {
        it.playbackState.observeForever(playbackStateObserver)
        it.nowPlaying.observeForever(mediaMetadataObserver)
        checkPlaybackPosition()
    }

    /**
     * Internal function that recursively calls itself every [POSITION_UPDATE_INTERVAL_MILLIS] ms
     * to check the current playback position and updates the corresponding LiveData object when it
     * has changed.
     */
    private fun checkPlaybackPosition(): Boolean =
        handler.postDelayed({ //TODO can this be improved by using coroutines?
            val currPosition = playbackState.currentPlayBackPosition
            if (
//                currentShiurID != null &&
//                (mediaPosition.value?.first == currentShiurID || mediaPosition.value?.first == SHIUR_ID_UNSET/*same shiur or shiur was never set*/) &&
                mediaPosition.value?.second != currPosition
            )
                mediaPosition.postValue(
                    (playbackState
                        .extras
                        ?.getString(MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID)
                        ?.removeSuffix(".mp3")
                        ?.substringAfterLast("/")
                        ?.toInt() ?: SHIUR_ID_UNSET) to currPosition
                )
            if (updatePosition)
                checkPlaybackPosition()
        }, POSITION_UPDATE_INTERVAL_MILLIS)

    /**
     * Since we use [LiveData.observeForever] above (in [musicServiceConnection]), we want
     * to call [LiveData.removeObserver] here to prevent leaking resources when the [ViewModel]
     * is not longer in use.
     *
     * For more details, see the kdoc on [musicServiceConnection] above.
     */
    override fun onCleared() {
        super.onCleared()

        // Remove the permanent observers from the MusicServiceConnection.
        musicServiceConnection.playbackState.removeObserver(playbackStateObserver)
        musicServiceConnection.nowPlaying.removeObserver(mediaMetadataObserver)
        // Stop updating the position
        updatePosition = false
    }

    var _id: String? = null
    var _albumArtUri: Uri? = null
    var _title: String? = null
    var _displaySubtitle: String? = null
    var _duration: Long? = null
    var _state: Int? = null
    var _isPlaying: Boolean? = null

    private fun updateState(
        playbackState: PlaybackStateCompat,
        mediaMetadata: MediaMetadataCompat?
    ) {
        ld(
            "updateState(\n" +
                "        playbackState=$playbackState (bundle is ${playbackState.extras?.contentsToString()}),\n" +
                "        mediaMetadata=$mediaMetadata\n" +
                "    )"
        )

        val state = playbackState.state
        val id = mediaMetadata?.id
        val albumArtUri = mediaMetadata?.albumArtUri
        val title = mediaMetadata?.title?.trim()
        val displaySubtitle = mediaMetadata?.displaySubtitle?.trim()
        val duration = mediaMetadata?.duration
        val isPlaying = playbackState.isPlaying
        if(isPlaying != _isPlaying) {
            this.isPlaying.postValue(isPlaying)
            _isPlaying = isPlaying
        }
        if (
        // Only update media item once we have duration available
            mediaMetadata?.duration?.compareTo(0L)
                ?.equals(1) == true && //TODO will a shiur ever have 0 duration? If so, the user will not be notified, the UI just won't respond.
            mediaMetadata.id != null &&
            //and content has changed TODO I know there is some information here that will determine whether the other checks are redundant(e.g. checking for the id)
            (
                (id != _id).also { if (it) ld("ID changed. Previous: $_id, new: $id") } ||
                    (state != _state).also { if (it) ld("state changed. Previous: $_state, new: $state") } ||
                    (albumArtUri != _albumArtUri).also { if (it) ld("Album URI changed. Previous: $_albumArtUri, new: $albumArtUri") } ||
                    (title != _title).also { if (it) ld("Title changed. Previous: $_title, new: $title") } ||
                    (displaySubtitle != _displaySubtitle).also { if (it) ld("Subtitle changed. Previous: $_displaySubtitle, new: $displaySubtitle") } ||
                    (duration != _duration).also { if (it) ld("Duration changed. Previous: $_duration, new: $duration") }
                )
        ) {
            _id = id
            _state = state
            _albumArtUri = albumArtUri
            _title = title
            _displaySubtitle = displaySubtitle
            _duration = duration

            this.mediaMetadata.postValue(
                NowPlayingMetadata(
                    _id,
                    _state,
                    _albumArtUri,
                    _title,
                    _displaySubtitle,
                    _duration,
                ) //TODO also include some flag for what changed. If the state changed, and the position before the state change is different than the current position, then a weird bug occurred where the user was up to  e.g. second 100 in the shiur, they attempt to play the shiur, it buffers, and when it starts playing, it starts from second 27. The state goes from 6 (buffering) to 3 (playing). When this happens, call a function to fast forward to second 100. If execution arrives at this function again and it is still not at 100, try again. This will continue until it reaches 100. Presumably there should be a limit on how many times that can happen so that it doesn't enter an infinite loop, but I can't imagine that would happen. It is too much state to worry about. If that issue gets reported we will solve it.
            )
        }
    }

    class Factory(
        private val app: Application,
        private val musicServiceConnection: MusicServiceConnection
    ) : ViewModelProvider.NewInstanceFactory() {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NowPlayingFragmentViewModel(app, musicServiceConnection) as T
        }
    }
}

private const val POSITION_UPDATE_INTERVAL_MILLIS = 100L
