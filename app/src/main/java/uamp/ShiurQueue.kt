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

package com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.library

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
import android.support.v4.media.MediaMetadataCompat
import androidapp.ShiurWithAllFilterMetadata
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.Util
import uamp.MediaItemData
import java.util.concurrent.TimeUnit

/**
 * Source of [MediaMetadataCompat] objects created from a basic JSON stream.
 *
 * The definition of the JSON is specified in the docs of [JsonMusic] in this file,
 * which is the object representation of it.
 */
class ShiurQueue : AbstractMusicSource() {

    companion object {
        const val ORIGINAL_ARTWORK_URI_KEY = "com.example.bottomsheeterrorreportingreproduction.androidapp.JSON_ARTWORK_URI"
    }

    //TODO this should be a HashSet, but there are several issues with that (a LinkedHashSet may solve some of these issues).
    // 1. The user will click on a shiur, and the add function will return fine
    //      (does it return false on a set if the set already contained it),
    //      but theb shiur wouldn'['t have actually been added, and I would have
    //      no way of knowing that so I can communicate it to the user. Spotify allows
    //      duplicate songs in the queue, but I wonder if that is unique to songs.
    //      I don't think I would want duplicate shiurim in my queue - I dislike realizing
    //      that I have heard this shiur before and I have been listening for 30 minutes,
    //      especially when I am catching up on a series.
    // 2. One of the options for how to respond when playing a shiur is to add the shiur to the beggining of the queue.
    //      A HashSeet does not guarantee iteration order, so that would not be an option.
    // 3. The user should have the ability to sort the queue (manually or automatically)
    val catalog = mutableListOf<ShiurWithAllFilterMetadata>()

    init {
        state = STATE_INITIALIZING
    }

    override fun iterator(): Iterator<MediaMetadataCompat> =
        catalog.asSequence().map { it.asMediaItemMetadata() }.iterator()

    override suspend fun load() {
        state = STATE_INITIALIZED
    }
}

/**
 * Extension method for [MediaMetadataCompat.Builder] to set the fields from
 * our JSON constructed object (to make the code a bit easier to see).
 */
fun MediaMetadataCompat.Builder.from(jsonMusic: JsonMusic): MediaMetadataCompat.Builder {
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.
    val durationMs = TimeUnit.SECONDS.toMillis(jsonMusic.duration)

    id = jsonMusic.id
    title = jsonMusic.title
    artist = jsonMusic.artist
    album = jsonMusic.album
    duration = durationMs
    genre = jsonMusic.genre
    mediaUri = jsonMusic.source
    albumArtUri = jsonMusic.image
    trackNumber = jsonMusic.trackNumber
    trackCount = jsonMusic.totalTrackCount
    flag = MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = jsonMusic.title
    displaySubtitle = jsonMusic.artist
    displayDescription = jsonMusic.album
    displayIconUri = jsonMusic.image

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = STATUS_NOT_DOWNLOADED

    // Allow it to be used in the typical builder style.
    return this
}

fun MediaMetadataCompat.Builder.from(mediaItemData: MediaItemData): MediaMetadataCompat.Builder {
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.

    val shiur = Util.getShiurID(mediaItemData.mediaId)

    id = shiur
    title = mediaItemData.title
    artist = mediaItemData.subtitle
    mediaUri = mediaItemData.mediaId
    val albumArt = mediaItemData.albumArtUri.toString()
    albumArtUri = albumArt
    flag = MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = mediaItemData.title
    displaySubtitle = mediaItemData.subtitle
    displayIconUri = albumArt

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = STATUS_NOT_DOWNLOADED

    // Allow it to be used in the typical builder style.
    return this
}

/**
 * An individual piece of music included in our JSON catalog.
 * The format from the server is as specified:
 * ```
 *     { "music" : [
 *     { "title" : // Title of the piece of music
 *     "album" : // Album title of the piece of music
 *     "artist" : // Artist of the piece of music
 *     "genre" : // Primary genre of the music
 *     "source" : // Path to the music, which may be relative
 *     "image" : // Path to the art for the music, which may be relative
 *     "trackNumber" : // Track number
 *     "totalTrackCount" : // Track count
 *     "duration" : // Duration of the music in seconds
 *     "site" : // Source of the music, if applicable
 *     }
 *     ]}
 * ```
 *
 * `source` and `image` can be provided in either relative or
 * absolute paths. For example:
 * ``
 *     "source" : "https://www.example.com/music/ode_to_joy.mp3",
 *     "image" : "ode_to_joy.jpg"
 * ``
 *
 * The `source` specifies the full URI to download the piece of music from, but
 * `image` will be fetched relative to the path of the JSON file itself. This means
 * that if the JSON was at "https://www.example.com/json/music.json" then the image would be found
 * at "https://www.example.com/json/ode_to_joy.jpg".
 */
@Suppress("unused")
class JsonMusic {
    var id: String = ""
    var title: String = ""
    var album: String = ""
    var artist: String = ""
    var genre: String = ""
    var source: String = ""
    var image: String = ""
    var trackNumber: Long = 0
    var totalTrackCount: Long = 0
    var duration: Long = -1
    var site: String = ""
    var iconUri: Uri? = null
}
