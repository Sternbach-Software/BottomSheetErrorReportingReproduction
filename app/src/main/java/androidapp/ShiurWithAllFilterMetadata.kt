package androidapp

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.room.PrimaryKey
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.extensions.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.media.media.library.ShiurQueue
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.Util
import kotlinx.serialization.Contextual
import java.util.concurrent.TimeUnit

data class ShiurWithAllFilterMetadata(
    val shiurID: Int,
    val title: String,
    val speaker: String,
    val category: String,
    val series: String,
    val length: Int,
    val speakerID: Int,
    val categoryID: Int,
    val dateUploaded: String,
    var isFavorite: Boolean,
    var isManagedByFetch: Boolean,
    var isRecentlyAdded: Boolean,
    var isHistory: Boolean,
    var moreFromThisShiurIDsJSON: String?,//TODO make this a string like "5""105""93""555" so that it supports multiple ids without clashes, and because each number has the same char padding, searching using db is easy
    var dateAddedToFetch: Long?,//for ordering
    var dateAddedToFavorites: Long? = null,//for ordering
    var dateAddedToHistory: Long? = null,//for ordering
    var dateAddedToRecentlyAdded: Long? = null,//if updating a shiur with withContentOf(), the only way to tell whether it should be removed from recently added is if the date is null on the new shiur and it is not recently added, but this one is
    val searchPageQuery: String = "",//if from search page, this is the query that this shiur came from
    var uri: Uri? = null,//if downloaded, this is the file's uri
    var positionInQueue: Int = -1,
    var playbackProgress: Long = 0,
    val remoteURL: String = Util.getShiurLink(shiurID),
    @PrimaryKey
    var rowid: Int = shiurID
) : Shiur(shiurID.toString(), title, length.toString(), speaker) {

    val mediaId: String
        get() = uri?.toString()?.ld { "Local uri gotten: $it" } ?: Util.getShiurLink(shiurID)

    @Contextual//not sure what this means, but it makes the error go away. I have not tested whether it interferes with JSON parsing. I think it means not to look for it in the JSON
    var percentDownloaded =
        0 /*I know, this is probably bad practice, but it makes things much easier to keep things in this class instead of the model or controller*/
    var sizeInBytes: Long = 0

    override fun getShiurSerializedString(): String {
        return "ShiurWithAllFilterMetadata~" +
            "$shiurID~" +
            "$title~" +
            "$speaker~" +
            "$category~" +
            "$series~" +
            "$length~" +
            "$speakerID~" +
            "$categoryID~" +
            "$dateUploaded~" +
            "$isFavorite~" +
            "$isManagedByFetch~" +
            "$isRecentlyAdded~" +
            "$isHistory~" +
            "$moreFromThisShiurIDsJSON~" +
            "$dateAddedToFetch~" +
            "$dateAddedToFavorites~" +
            "$dateAddedToHistory~" +
            "$dateAddedToRecentlyAdded~" +
            "$searchPageQuery~" +
            "$uri~" +
            "$percentDownloaded~" +
            "$sizeInBytes"
    }

    companion object {

        fun blankShiur(shiurID: Int = 0) = ShiurWithAllFilterMetadata(
            shiurID,
            "",
            "",
            "",
            "",
            0,
            0,
            0,
            "",
            false,
            false,
            false,
            false,
            null,
            0L,
            0L,
            0L,
            0L,
        )
    }

    override fun toString(): String {
        return getShiurSerializedString()//"${this::class.simpleName}(shiurID=$shiurID, title=$title, speaker=$speaker, category=$category, series=$series, length=$length, imageURL=$imageURL)"
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is ShiurWithAllFilterMetadata && !nonContentIsDifferentThan(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }


    fun contentIsTheSame(other: ShiurWithAllFilterMetadata) = !contentIsDifferentThan(other)
    fun nonContentIsTheSame(other: ShiurWithAllFilterMetadata) = !nonContentIsDifferentThan(other)

    fun nonContentIsDifferentThan(other: ShiurWithAllFilterMetadata): Boolean {
        return isFavorite != other.isFavorite ||
            isHistory != other.isHistory ||
            isManagedByFetch != other.isManagedByFetch ||
            percentDownloaded != other.percentDownloaded
    }

    fun contentIsDifferentThan(other: ShiurWithAllFilterMetadata): Boolean {
        if (shiurID != other.shiurID) throw IllegalStateException("Shiur.contentIsDifferentThan() called on different shiur (shiurIDs not equal), so of course content will be different.")
        return title != other.title ||
            speaker != other.speaker ||
            category != other.category ||
            series != other.series ||
            length != other.length ||
            speakerID != other.speakerID ||
            categoryID != other.categoryID ||
            percentDownloaded != other.percentDownloaded //TODO should this be here? If it is
    }

    /**
     * Extension method for [MediaMetadataCompat.Builder] to set the fields from
     * our JSON constructed object (to make the code a bit easier to see).
     */
    private fun MediaMetadataCompat.Builder.from(shiur: ShiurWithAllFilterMetadata): MediaMetadataCompat.Builder {
        // The duration from the JSON is given in seconds, but the rest of the code works in
        // milliseconds. Here's where we convert to the proper units.
        val durationMs = TimeUnit.SECONDS.toMillis(shiur.length.toLong())

        id = shiur.shiurID.toString()
        title = shiur.title
        artist = shiur.speaker
        album = shiur.category
        duration = durationMs
        genre = "TorahDownloads" //TODO should this be the genre?
        mediaUri = Util.getShiurLink(shiur.shiurID)
        val imageURL = Util.getImageURL(shiur.speakerID, CONSTANTS.IMAGE_SIZE_150)
        albumArtUri = imageURL
        trackNumber = 1
        trackCount = 1
        flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE

        // To make things easier for *displaying* these, set the display properties as well.
        displayTitle = shiur.title
        displaySubtitle = shiur.speaker
        displayDescription = shiur.category
        displayIconUri = imageURL

        // Add downloadStatus to force the creation of an "extras" bundle in the resulting
        // MediaMetadataCompat object. This is needed to send accurate metadata to the
        // media session during updates.
        downloadStatus = MediaDescriptionCompat.STATUS_NOT_DOWNLOADED

        putLong(MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS, playbackProgress)
        // Allow it to be used in the typical builder style.
        return this
    }

    fun asMediaItemMetadata() = MediaMetadataCompat
        .Builder()
        .from(this)
        .build()
        .also {
            // Add description keys to be used by the ExoPlayer MediaSession extension when
            // announcing metadata changes.
            it.description.extras?.putAll(it.bundle) //note to self: description is generated by support.v4 library based on provided  metadata. Can also be set manually. See docs on it.description.
        }
}
