/*
 * Copyright 2020 Google Inc. All rights reserved.
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

import KotlinFunctionLibrary
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.*
import com.example.bottomsheeterrorreportingreproduction.R
import androidapp.CONSTANTS
import androidapp.CONSTANTS.missingImageBitmap
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld


const val NOW_PLAYING_CHANNEL_ID = "com.example.bottomsheeterrorreportingreproduction.androidapp.media.NOW_PLAYING"
const val NOW_PLAYING_NOTIFICATION_ID = 0xb339 // Arbitrary number used to identify our notification

/**
 * A wrapper class for ExoPlayer's PlayerNotificationManager. It sets up the notification shown to
 * the user during audio playback and provides track metadata, such as track title and icon image.
 */
internal class UampNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener
) {

//    private var player: Player? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val notificationManager: PlayerNotificationManager
//    private val platformNotificationManager: NotificationManager =
//        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val mediaController = MediaControllerCompat(context, sessionToken)

        val builder = PlayerNotificationManager.Builder(
            context,
            NOW_PLAYING_NOTIFICATION_ID,
            NOW_PLAYING_CHANNEL_ID
        )
        with(builder) {
            setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            setNotificationListener(notificationListener)
            setChannelNameResourceId(R.string.notification_channel)
            setChannelDescriptionResourceId(R.string.notification_channel_description)
//            setRewindActionIconResourceId(R.drawable.anim_rewind)
//            setFastForwardActionIconResourceId(R.drawable.anim_fast_forward) //TODO these icons are too big for the player and are being cut off. I think they need to be changed to 24dp vectors and they will look right in the notification, but I need to make sure that it still looks good everywhere else when doing so
        }
        notificationManager = builder.build()
        notificationManager.setMediaSessionToken(sessionToken)
        notificationManager.setSmallIcon(R.drawable.ic_notification) //TODO change
        notificationManager.setUseRewindAction(true)
        notificationManager.setUseFastForwardAction(true)
        notificationManager.setUseChronometer(true)
        notificationManager.setColorized(true)
        notificationManager.setUseFastForwardActionInCompactView(true)
        notificationManager.setUseRewindActionInCompactView(true)
        notificationManager.setUseNextAction(true)
        notificationManager.setUseNextActionInCompactView(true)
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    fun showNotificationForPlayer(player: Player) {
        notificationManager.setPlayer(player)
    }

    private inner class DescriptionAdapter(private val controller: MediaControllerCompat) :
        PlayerNotificationManager.MediaDescriptionAdapter {

        var currentIconUri: Uri? = null
        var currentBitmap: Bitmap? = null

        override fun createCurrentContentIntent(player: Player): PendingIntent? =
            controller.sessionActivity

        override fun getCurrentContentText(player: Player) =
            controller.metadata.description.subtitle.toString()

        override fun getCurrentContentTitle(player: Player) =
            controller.metadata.description.title.toString()

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? { //TODO this is called multiple times for the same shiur, and may be because the callback isn't called soon enough. Should this be mutexed?
            ld("Calling getCurrentLargeIcon()")
            ld("Queue: ${controller.queue}")
            val iconUri = controller.metadata.description.iconUri
            ld("IconUri=$iconUri, currentIconUri=$currentIconUri, currentBitmap=$currentBitmap")
            return if (currentIconUri != iconUri || currentBitmap == null) {

                // Cache the bitmap for the current song so that successive calls to
                // `getCurrentLargeIcon` don't cause the bitmap to be recreated.
                currentIconUri = iconUri
                serviceScope.launch {
                    currentBitmap = iconUri?.let {
                        resolveUriAsBitmap(it)
                    }
                    currentBitmap?.let {
                        ld("Calling callback.onBitmap()")
                        callback.onBitmap(it)
                    }
                }
                null
            } else {
                currentBitmap
            }
        }

        private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? {
            var bitmap: Bitmap? = null
            for (size in CONSTANTS.ICON_SIZES) {
                ld("Loading uri as bitmap: $uri")
                return try {
                    withContext(Dispatchers.IO) {
                        // Block on downloading artwork.
                        KotlinFunctionLibrary.tryAndReturn(true, { a, b ->
                            ld("Failed getting $uri for size $size.")
                            if (size == CONSTANTS.IMAGE_SIZE_UNBOUNDED) {
                                ld("All failed, getting missing image")
                                missingImageBitmap
                            }
                        }) {
                            ld("Getting bitmap") //TODO utilize missing image bitmap

                            bitmap = Glide
                                .with(context)
                                .applyDefaultRequestOptions(glideOptions)
                                .asBitmap()
                                .load(uri)
                                .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                                .get()
                                .ld("Got bitmap")
                            bitmap
                        }
                    }
                } catch (t: Throwable) {
                    continue
                }
            }
            return bitmap
        }
    }
}

const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
    .fallback(R.drawable.missing_image)
//    .diskCacheStrategy(DiskCacheStrategy.DATA)

