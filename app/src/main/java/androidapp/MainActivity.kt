package androidapp

import android.graphics.drawable.Animatable
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.mediarouter.app.MediaRouteButton
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import uamp.InjectorUtils
import com.example.android.media.viewmodels.MainActivityViewModel
import com.example.android.media.viewmodels.NowPlayingFragmentViewModel
import com.example.bottomsheeterrorreportingreproduction.R
import com.example.bottomsheeterrorreportingreproduction.androidapp.*
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.dp
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.lv
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var bottomSheet: View
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var header: ViewGroup
    private lateinit var headerProgressBar: LinearProgressIndicator
    private lateinit var headerPlayerLoadingCircle: CircularProgressIndicator
    private lateinit var playerPlayerLoadingCircle: CircularProgressIndicator
    private lateinit var headerPlayButton: ImageView
    private lateinit var headerShiurName: TextView
    private lateinit var headerRewindButton: TextView
    private lateinit var headerSpeakerName: TextView
    private lateinit var playerPlayButton: ImageView
    private lateinit var playerSeekbar: Slider
    private lateinit var playerOverflowMenu: ImageView
    private lateinit var playerSpeakerName: TextView

    private var isPlaying: Boolean? =
        null //needs to be null, because it will be initialized to false, and the play/pause icon won't be initialized properly
    private var isDragging = false
    private var bottomSheetIsVisible = true
    private var playbackState: Int? = null

    private val activityViewModel by viewModels<MainActivityViewModel> {
        InjectorUtils.provideMainActivityViewModel(this)
    }
    val nowPlayingViewModel by viewModels<NowPlayingFragmentViewModel> {
        InjectorUtils.provideNowPlayingFragmentViewModel(this)
    }
    val pausePlayAnim by lazy {
        AnimatedVectorDrawableCompat.create(this, R.drawable.anim_pause_play)!!
    }
    val playPauseAnim by lazy {
        AnimatedVectorDrawableCompat.create(this, R.drawable.anim_play_pause)!!
    }

    // Perform initialization of all fragments.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        castContext =
//            CastContext.getSharedInstance(this) // Initialize the Cast context. This is required so that the media route button can be created in the bottom sheet TODO may not have to be before setContentView now that it moved from the app bar to the bottom sheet
        volumeControlStream =
            AudioManager.STREAM_MUSIC //the volume controls should adjust the music volume while in the app.
        setContentView(R.layout.activity_main)
        fragmentContainer = findViewById(R.id.fragment_container)
        setupNowPlayingBottomSheet()
        mainActivityViewModel = activityViewModel
        nowPlayingFragmentViewModel = nowPlayingViewModel

        supportFragmentManager.beginTransaction().replace(
            R.id.container_fragment,
            ShiurimFragment::class.java,
            null
        ).commit()
    }

    override fun onSupportNavigateUp() =
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {//TODO not working
            ld("Bottom sheet is expanded and onSupportNavigteUp() was called")
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            false
        } else
            super.onSupportNavigateUp()

    private fun setupNowPlayingBottomSheet() {
        val nowPlayingArrowView = findViewById<ImageView>(R.id.bb_expand)
//        val rewindHeaderIcon = findViewById(R.id.bb_rewind_icon)
        bottomSheet = findViewById(R.id.player_bottom_sheet_first_child)

        //Header views
        header = findViewById(R.id.player_bottom_sheet_header)
        headerProgressBar = findViewById(R.id.header_progress)
        headerPlayerLoadingCircle = findViewById(R.id.header_player_progress_circle)
        playerPlayerLoadingCircle = findViewById(R.id.player_player_progress_circle)
        headerPlayButton = findViewById(R.id.header_play_icon)
        headerShiurName = findViewById(R.id.shiur_name)
        headerRewindButton = findViewById(R.id.header_rewind_icon)
        headerSpeakerName = findViewById(R.id.speaker_name)

        //Player views
        playerPlayButton = findViewById(R.id.player_play_pause_button)
        playerSeekbar = findViewById(R.id.player_seekbar)
        playerOverflowMenu = findViewById(R.id.player_more_options)
        playerSpeakerName = findViewById(R.id.speaker_name_player)

        val mediaRouteButton = findViewById<MediaRouteButton?>(R.id.mediaRouteButton)


        //make the text animate so the user can see long names
        headerShiurName.isSelected = true
        findViewById<TextView>(R.id.shiur_name_player).isSelected = true
        CastButtonFactory.setUpMediaRouteButton(this, mediaRouteButton)


        //Setup header

        setupBottomSheetBehavior(nowPlayingArrowView)

        // TODO passing all these views as parameters is somewhat of an anti-pattern -- it certainly isn't used in any of the official android samples. Just for convention's sake (and consistency), it may be appropriate to extract these to class variables
        setupClickListeners(
            nowPlayingArrowView,
            findViewById(R.id.player_rewind_button),
            findViewById(R.id.player_skip_button),
            findViewById(R.id.player_previous_button),
            findViewById(R.id.player_next_button),
        )
        setupPlayerLiveDataObservers(
            findViewById(R.id.total_time_of_shiur),
            findViewById(R.id.shiur_name_player),
            playerSpeakerName,
            findViewById(R.id.player_current_position),
        )
    }

    private fun setupBottomSheetBehavior(
        nowPlayingArrowView: ImageView,
    ) {
        behavior = BottomSheetBehavior.from(bottomSheet)
        val bottomsheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        ld("Player bottom sheet expanded")
//                        nowPlayingArrowView
//                            .setImageState(
//                                intArrayOf(-android.R.attr.state_checked),
//                                true
//                            )
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        ld("Player bottom sheet collapsed")
//                        nowPlayingArrowView
//                            .setImageState(
//                                intArrayOf(android.R.attr.state_checked),
//                                true
//                            )
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        ld("Player bottom sheet hidden")
                        removePlayerBottomSheetSpace()
                        bottomSheetIsVisible = false
                        mainActivityViewModel.stop()
                        ld("Bottom sheet is visible: ${bottomSheet.isVisible}")
                    }

                    else -> {
                        ld("Bottom sheet state: ${newState}; states: BottomSheetBehavior.STATE_DRAGGING=${BottomSheetBehavior.STATE_DRAGGING}, BottomSheetBehavior.STATE_SETTLING=${BottomSheetBehavior.STATE_SETTLING}, BottomSheetBehavior.STATE_HALF_EXPANDED=${BottomSheetBehavior.STATE_HALF_EXPANDED}")
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                ld("onSlide($slideOffset)")
                val inverse =
                    1 - slideOffset //did I label this correctly as inverse? code copied from VLC

                val translationOffset = min(1f, max(0f, (slideOffset * 1.4f) - 0.2f))
                //                        playlistSearch.translationY = -(1 - translationOffset) * 48.dp
                //                        playlistSwitch.translationY = -(1 - translationOffset) * 48.dp
                val dp = header.height
                val translation = -(1 - translationOffset) * dp
                //views disappearing in full player
                val disappearingViews = arrayOf(
                    headerPlayerLoadingCircle,
                    headerPlayButton,
                    headerRewindButton,
                    headerProgressBar,
                    headerSpeakerName,
                    headerShiurName,
                )
                headerProgressBar.alpha = inverse
                headerProgressBar.layoutParams.height = (inverse * 4.dp).toInt()
                playerOverflowMenu.alpha = slideOffset
                playerOverflowMenu.translationY = translation
                nowPlayingArrowView.rotation =
                    (slideOffset * 180).coerceIn(0F, 180F).ld { "Setting arrow rotation: $it" }
                disappearingViews.forEach {
                    it.translationY = translationOffset * dp
                    it.alpha = inverse
                }
                headerProgressBar.requestLayout()
            }
        }
        behavior.addBottomSheetCallback(bottomsheetCallback)
        bottomsheetCallback.onSlide(header, 0F)
    }

    private fun setupClickListeners(
        nowPlayingArrowView: ImageView,
        playerRewindButton: TextView,
        playerFastForwardButton: TextView,
        playerPreviousButton: ImageView,
        playerNextButton: ImageView,
    ) {
        headerPlayButton.setOnClickListener {
            lv("$it clicked")
            mainActivityViewModel.togglePlayState()
        }
        playerPlayButton.setOnClickListener {
            lv("$it clicked")
            mainActivityViewModel.togglePlayState()
        }

        val toggleBottomSheet = {
            behavior.state =
                if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) BottomSheetBehavior.STATE_EXPANDED
                else BottomSheetBehavior.STATE_COLLAPSED
        }

        nowPlayingArrowView.setOnClickListener {
            lv("$it clicked")
            toggleBottomSheet()
        }
        header.setOnClickListener {
            lv("$it clicked")
            toggleBottomSheet()
        }

        //Setup player


        headerRewindButton.setOnClickListener {
            it.post { (headerRewindButton.background as Animatable).start() }
            mainActivityViewModel.rewind()
        }
        playerRewindButton.setOnClickListener {
            (playerRewindButton.background as Animatable).start()
            mainActivityViewModel.rewind()
        }
        playerFastForwardButton.setOnClickListener {
            (playerFastForwardButton.background as Animatable).start()
            mainActivityViewModel.fastForward()
        }
        playerPreviousButton.setOnClickListener {
            (playerPreviousButton.drawable as Animatable).start()
            mainActivityViewModel.previous()
        }
        playerNextButton.setOnClickListener {
            (playerNextButton.drawable as Animatable).start()
            mainActivityViewModel.next()
        }

        playerSeekbar.apply {
            setLabelFormatter { value ->
                value.roundToInt().toString()
            }
            valueFrom = 0F
            addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        ld("Drag started")
                        isDragging = true
                    }

                    override fun onStopTrackingTouch(slider: Slider) {
                        ld("Drag stopped")
                        isDragging = false
                        mainActivityViewModel.seekTo((value * 1E3).toLong())
                    }
                }
            )
        }
    }

    private fun setupPlayerLiveDataObservers(
        playerTotalTime: TextView,
        playerShiurName: TextView,
        playerSpeakerName: TextView,
        playerCurrentTime: TextView,
    ) {
        nowPlayingViewModel.mediaMetadata.observe(this) { //TODO does this need a mutex, being that it relies on the if(state == BUFFERING) circle.hide() statement being called before the playback state is overriden? It could lead to the player not being hidden until the next time the observer is called.
            ld("mediaMetadata observer called: $it.")
            if (it?.state != null) {
                if (it.state > PlaybackStateCompat.STATE_STOPPED/*same as: state != (STATE_NONE || STATE_STOPPED)*/)
                    showBottomSheetIfNotVisible(false)
                when {
                    it.state == PlaybackStateCompat.STATE_BUFFERING -> {
                        headerPlayerLoadingCircle.show()
                        playerPlayerLoadingCircle.show()
                    }
                    headerPlayerLoadingCircle.isVisible || playerPlayerLoadingCircle.isVisible -> {
                        headerPlayerLoadingCircle.hide()
                        playerPlayerLoadingCircle.hide()
                    } //if player isn't buffering or stopped (and therefore bottomsheet hidden) and it is visible, then hide it
                }
                ld("Received media metadata: $it")
                val secondsFromMillis = floor(it.duration?.div(1E3) ?: 0.0)
                val secondsInt = secondsFromMillis.toInt()
                ld("Media metadata updated: id ${it.id} duration ${it.duration}, numSeconds: $secondsFromMillis")
                playerSeekbar.valueTo = secondsFromMillis.toFloat()
                playerTotalTime.text = secondsInt.toString()
                headerShiurName.text = it.title
                playerShiurName.text = it.title
                headerSpeakerName.text = it.subtitle
                playerSpeakerName.text = it.subtitle
                playbackState = it.state
            } else hideBottomSheetIfVisible()
        }
        val mutex = Mutex()
        nowPlayingViewModel.isPlaying.observe(this@MainActivity) { isPlaying ->
            ld("isPlaying observer called: isPlaying=$isPlaying, this.isPlaying=${this.isPlaying}")
            mAppScope.launch(Dispatchers.Main) {
                mutex.withLock { //make sure that it is only updated once
                    if (this@MainActivity.isPlaying != isPlaying) {
                        this@MainActivity.isPlaying = isPlaying
                        ld("Updating play/pause button. Is playing: $isPlaying")
                        updateView(isPlaying, playerPlayButton)
                        updateView(isPlaying, headerPlayButton)
                    }
                }
            }
            ld("Is playing changed: $isPlaying")
        }

        nowPlayingViewModel.mediaPosition.observe(this) { (id, it) ->
            val currentlyPlayingShiur = getCurrentlyPlayingShiur()
            if (id == currentlyPlayingShiur?.shiurID) {//if shiur has changed, don't pass on a UI update from the previous shiur
                val numSeconds = floor(it / 1E3).toInt()
                    .coerceAtMost(currentlyPlayingShiur.length) //for some reason, it goes 1 second above the duration
                ld("Media position updated: $it, numSeconds: $numSeconds")
                val formatted = numSeconds.toString()

                kotlin.runCatching { //to avoid race condition of metadata not updating in time so crashes because defies "valueFrom <= progress <= valueTo"
//                    headerProgressBar.progress = Util.getProgressAsPercentDone(
//                        numSeconds,
//                        currentlyPlayingShiur.length,
//                        true
//                    ).toInt()
                    val toFloat = numSeconds.toFloat()
                    if (!isDragging/*don't update it while user is trying to manually update it*/ &&
                        playerSeekbar.valueTo == currentlyPlayingShiur.length.toFloat() &&
                        toFloat in playerSeekbar.valueFrom..playerSeekbar.valueTo
                    ) {
                        playerSeekbar.value =
                            toFloat
                    }
                }

                playerCurrentTime.text = formatted
                currentlyPlayingShiur.playbackProgress = it
            }
        }
    }

    private fun hideBottomSheetIfVisible() {
        ld("calling hideBottomSheetIfVisible")
        if (bottomSheetIsVisible) {
            bottomSheetIsVisible = false
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
            removePlayerBottomSheetSpace()
        }
    }

    private fun showBottomSheetIfNotVisible(expand: Boolean) {
        ld("calling showBottomSheetIfNotVisible(expand=$expand). bottomSheetIsVisible=$bottomSheetIsVisible, isHideable=${behavior.isHideable}")
        if (!bottomSheetIsVisible) {
            bottomSheetIsVisible = true
//            bottomSheet.isVisible = true
//            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            behavior.state =
                if (expand) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
            addPlayerBottomSheetSpace()
        }
    }

    fun updateView(
        isPlaying: Boolean,
        it: View,
    ) {
        val drawable = if (isPlaying) playPauseAnim else pausePlayAnim
        if ((it as ImageView).drawable != drawable) {
            it.setImageDrawable(drawable)
            it.post { drawable.start() }
        }
    }

    fun removePlayerBottomSheetSpace() {
        val params = fragmentContainer.layoutParams as ConstraintLayout.LayoutParams
        params.bottomMargin = 0
        fragmentContainer.layoutParams = params
    }

    fun addPlayerBottomSheetSpace() {
        val params = fragmentContainer.layoutParams as ConstraintLayout.LayoutParams
        params.bottomMargin = resources.getDimension(R.dimen.bottomsheet_header_height).toInt()
        fragmentContainer.layoutParams = params
    }
}
