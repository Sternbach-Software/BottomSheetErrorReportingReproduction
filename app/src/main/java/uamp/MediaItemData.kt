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

package uamp

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem

/**
 * Data class to encapsulate properties of a [MediaItem].
 *
 * If an item is [browsable] it means that it has a list of child media items that
 * can be retrieved by passing the mediaId to [MediaBrowserCompat.subscribe].
 *
 */
data class MediaItemData(
    val mediaId: String,
    val title: String,
    val subtitle: String,
    val albumArtUri: Uri,
    val browsable: Boolean,
    var playbackRes: Int
) {

    companion object {
        /**
         * Indicates [playbackRes] has changed.
         */
        const val PLAYBACK_RES_CHANGED = 1

    }
}

