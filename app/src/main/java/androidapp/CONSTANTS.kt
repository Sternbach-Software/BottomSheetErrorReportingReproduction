package androidapp

import KotlinFunctionLibrary.with
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.example.bottomsheeterrorreportingreproduction.R
import com.example.bottomsheeterrorreportingreproduction.androidapp.mEntireApplicationContext
import kotlin.properties.Delegates

object CONSTANTS {
    val AUTHORITY by lazy { mEntireApplicationContext.getString(R.string.authority) }
    const val RETROFIT_MAX_CACHE_SIZE = 70L * 1024L * 1024L

    /**
     * A variable representing whether the user can connect to the internet. A callback is initialized
     * to change this variable as the state of the phone changes.
     * */
    var isOnline by Delegates.observable(false) { _, _, newValue ->
        isOnlineOneTimeObservers
            .toList() //TODO avoid extra loop by using asIterable() - except does that solve the concurrent modification issue?
            .with(isOnlineEternalObservers)
            .forEach { (oneTimeObserver, eternalObserver) ->
                oneTimeObserver(newValue)
                isOnlineOneTimeObservers.remove(oneTimeObserver)
                eternalObserver(newValue)
            }
    } //TODO add custom getter which returns `this` on API 24+ and returns the proper function call on earlier APIs
    var isOnlineEternalObservers = mutableListOf<(Boolean) -> Unit>()

    /**
     * Observers which should be removed after they are called once.
     * */
    var isOnlineOneTimeObservers = mutableListOf<(Boolean) -> Unit>()
    val OFFLINE_MESSAGE_POSSIBLY_BEING_FILTERED by lazy {
        mEntireApplicationContext.resources.getString(
            R.string.offline_message_possibly_being_filtered
        )
    } //TODO check direct uses of all of these messages and consider refactoring to AndroidFunctionLibrary.getErrorMessage(), because I found at least one place in which I assumed that the error was a connectivity issue and I found that it wasn't, and therefore displayed an incorrect error message to the user.
    val OFFLINE_MESSAGE_NO_MENTION_OF_CACHE by lazy {
        mEntireApplicationContext.resources.getString(
            R.string.offline_message_no_mention_of_cache_v2
        )
    }
    val OFFLINE_MESSAGE_MENTION_OF_CACHE by lazy { mEntireApplicationContext.resources.getString(R.string.offline_message_mention_of_cache_v2) }
    val REPORTABLE_ERROR_MESSAGE by lazy { mEntireApplicationContext.resources.getString(R.string.error_message) }

    const val DONT_ADD_TO_QUEUE_BUNDLE_KEY = "com.example.bottomsheeterrorreportingreproduction.androidapp.dont_add_to_queue"

    const val SHIUR_LAYOUT_RES: String = "com.example.bottomsheeterrorreportingreproduction.androidapp.default_shiur_layout"
    const val SAVED_INSTANCE_STATE_BUNDLE_KEY: String =
        "com.example.bottomsheeterrorreportingreproduction.androidapp.saved_instance_state_file"
    const val SAVED_INSTANCE_STATE_SELECTED_NAVGIATION_POSITION = "com.example.bottomsheeterrorreportingreproduction.androidapp.selected_navigation_position"

    const val SHIUR_LAYOUT_RES_DEFAULT: Int = R.layout.individual_shiur_card_layout

    const val COMMAND_SET_SKIP_SILENCE = "com.example.bottomsheeterrorreportingreproduction.androidapp.set_skip_silence"

    const val INTENT_EXTRA_SHIURIM_PAGE_TITLE = "com.example.bottomsheeterrorreportingreproduction.androidapp.shiurim_page.title"
    const val INTENT_EXTRA_SHIUR = "com.example.bottomsheeterrorreportingreproduction.androidapp.shiurim_page.shiur"
    const val INTENT_EXTRA_SHIUR_ADAPTER_POSITION_CLICKED =
        "com.example.bottomsheeterrorreportingreproduction.androidapp.shiurim_page.shiur_position_clicked"
    const val INTENT_EXTRA_DATABASE_FILTERS = "com.example.bottomsheeterrorreportingreproduction.androidapp.shiurim_page.filters"
    const val INTENT_EXTRA_DATABASE_SORTS = "com.example.bottomsheeterrorreportingreproduction.androidapp.shiurim_page.sorts"

    const val INTENT_EXTRA_PAGE_TITLE = "com.example.bottomsheeterrorreportingreproduction.androidapp.pageTitle"
    const val INTENT_EXTRA_SEARCH_QUERY = "com.example.bottomsheeterrorreportingreproduction.androidapp.searchQuery"
    const val INTENT_EXTRA_PLAYLIST_NAME = "com.example.bottomsheeterrorreportingreproduction.androidapp.playlistName"
    const val INTENT_EXTRA_SHIUR_ID = "com.example.bottomsheeterrorreportingreproduction.androidapp.shiur_id"
    const val INTENT_EXTRA_CATEGORY_ID = "com.example.bottomsheeterrorreportingreproduction.androidapp.categoryId"
    const val INTENT_EXTRA_SPEAKER_ID = "com.example.bottomsheeterrorreportingreproduction.androidapp.speakerId"
    const val INTENT_EXTRA_SPEAKER_DESCRIPTION = "com.example.bottomsheeterrorreportingreproduction.androidapp.speakerDescription"
    const val INTENT_EXTRA_FORCE_REFRESH = "com.example.bottomsheeterrorreportingreproduction.androidapp.force_refresh"
    const val INTENT_EXTRA_SELECT_ALL_CLICKED = "com.example.bottomsheeterrorreportingreproduction.androidapp.selected_all"

    val PREFERENCE_PLAYBACK_SPEED by lazy { mEntireApplicationContext.getString(R.string.playback_speed_key) }


    const val NETWORK_ACTIVITIES_SPEAKER_PAGE = 5

    const val SHIURIM_TYPE_RECENTS = 10
    const val SHIURIM_TYPE_CATEGORY = 11
    const val SHIURIM_TYPE_SPEAKER = 12
    const val SHIURIM_TYPE_SEARCH = 13
    const val SHIURIM_TYPE_MORE_FROM_THIS = 14
    const val SHIURIM_TYPE_DOWNLOADS = 15
    const val SHIURIM_TYPE_FAVORITE = 16
    const val SHIURIM_TYPE_HISTORY = 17
    const val SHIURIM_TYPE_PLAYLIST = 18
    val SHIURIM_TYPE_OFFLINE = SHIURIM_TYPE_DOWNLOADS..SHIURIM_TYPE_PLAYLIST

    val missingImageDrawable by lazy { AppCompatResources.getDrawable(mEntireApplicationContext, R.drawable.missing_image)!! }

    val FALLBACK_IMAGE_DRAWABLE by lazy { missingImageDrawable }
    val PLACEHOLDER_IMAGE_DRAWABLE by lazy { missingImageDrawable }//R.drawable.speaker_placeholder_1

    val missingImageBitmap by lazy { missingImageDrawable.toBitmap() }

    const val FREQUENT_SPEAKER_THRESHOLD = 2

    const val IMAGE_SIZE_30 = 30
    const val IMAGE_SIZE_100 = 100
    const val IMAGE_SIZE_150 = 150
    const val IMAGE_SIZE_300 = 300
    const val IMAGE_SIZE_UNBOUNDED = -1
    val ICON_SIZES = intArrayOf(
        IMAGE_SIZE_30,
        IMAGE_SIZE_100,
        IMAGE_SIZE_150,
        IMAGE_SIZE_300,
        IMAGE_SIZE_UNBOUNDED //I rely on this being the last element in several places (e.g. where I check if it is the last one; where I want to try the biggest image as a last resort, etc.)
    )
}
