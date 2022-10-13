/*
 * Copyright 2018 Google Inc. All rights reserved.
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

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidapp.CONSTANTS
import androidapp.ShiurWithAllFilterMetadata
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.common.MusicServiceConnection
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.id
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.isPlayEnabled
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.isPlaying
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.isPrepared
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import timber.log.Timber
import uamp.MediaItemData

/**
 * Small [ViewModel] that watches a [MusicServiceConnection] to become connected
 * and provides the root/initial media ID of the underlying [MediaBrowserCompat].
 */
class MainActivityViewModel(
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    /**
     * This method takes a [MediaItemData] and does one of the following:
     * - If the item is *not* the active item, then play it directly.
     * - If the item *is* the active item, check whether "pause" is a permitted command. If it is,
     *   then pause playback, otherwise send "play" to resume playback.
     */
    fun playMedia(
        shiur: ShiurWithAllFilterMetadata,
        addToQueue: Boolean = false
    ) {
        ld("Playing shiur(im): ${shiur.mediaId}")
        val nowPlaying = musicServiceConnection.nowPlaying.value
        val transportControls = musicServiceConnection.transportControls

        if (isPrepared() && shiur.mediaId == nowPlaying?.id) {
            togglePlayState(transportControls)
        } else {
            shiur.let {
                transportControls.playFromMediaId(
                    it.mediaId,
                    if (!addToQueue) bundleOf(CONSTANTS.DONT_ADD_TO_QUEUE_BUNDLE_KEY to true) else null
                )
            }
        }
    }

    /**
     * Play if paused and pause if playing
     * */
    fun togglePlayState(
        transportControls: MediaControllerCompat.TransportControls = musicServiceConnection.transportControls,
        isPrepared: Boolean = isPrepared()
    ) {
        if (isPrepared) musicServiceConnection.playbackState.value?.let { playbackState ->
            when {
                playbackState.isPlaying -> transportControls.pause()
                playbackState.isPlayEnabled -> transportControls.play()
                else -> {
                    Timber.e(
                        "Playable item clicked but neither play nor pause are enabled!"
                    )
                }
            }
        }
    }

    fun playMediaId(mediaId: String) {
        val nowPlaying = musicServiceConnection.nowPlaying.value
        val transportControls = musicServiceConnection.transportControls
        if (isPrepared() && mediaId == nowPlaying?.id) togglePlayState(transportControls, true)
        else transportControls.playFromMediaId(mediaId, null)
    }

    private fun isPrepared() =
        (musicServiceConnection.playbackState.value?.isPrepared == true).ld { "Is prepared: $it" }

    fun rewind() {
        ld("Called rewind()")
        if (isPrepared()) musicServiceConnection.transportControls.rewind()
    }

    fun previous() {
        ld("Calling previous()")
        if (isPrepared()) musicServiceConnection.transportControls.skipToPrevious()
    }

    fun next() {
        ld("Calling next()")
        if (isPrepared()) musicServiceConnection.transportControls.skipToNext()
    }

    fun fastForward() {
        ld("Calling fastForward()")
        if (isPrepared()) musicServiceConnection.transportControls.fastForward()
    }

    /** @param pos milliseconds into media*/
    fun seekTo(pos: Long) {
        ld("Calling seekTo($pos)")
        if (isPrepared()) musicServiceConnection.transportControls.seekTo(pos)
    }


    fun stop() {
        ld("Called stop()")
        if(isPrepared()) musicServiceConnection.transportControls.stop()
    }

    class Factory(
        private val musicServiceConnection: MusicServiceConnection
    ) : ViewModelProvider.NewInstanceFactory() {

        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainActivityViewModel(musicServiceConnection) as T
        }
    }
}
