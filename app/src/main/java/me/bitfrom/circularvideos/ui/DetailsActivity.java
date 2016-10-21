package me.bitfrom.circularvideos.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.vr.sdk.widgets.video.VrVideoEventListener;
import com.google.vr.sdk.widgets.video.VrVideoView;

import java.io.IOException;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import me.bitfrom.circularvideos.R;
import me.bitfrom.circularvideos.ui.base.BaseActivity;
import timber.log.Timber;

import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.LOAD_VIDEO_STATUS_ERROR;
import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.LOAD_VIDEO_STATUS_SUCCESS;
import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.LOAD_VIDEO_STATUS_UNKNOWN;
import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.STATE_IS_PAUSED;
import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.STATE_PROGRESS_TIME;
import static me.bitfrom.circularvideos.utils.ConstantsManager.Details.STATE_VIDEO_DURATION;

public class DetailsActivity extends BaseActivity {

    @BindView(R.id.video_view)
    protected VrVideoView videoWidgetView;
    @BindView(R.id.seek_bar)
    protected SeekBar seekBar;
    @BindView(R.id.status_text)
    protected TextView statusText;
    @BindView(R.id.volume_toggle)
    protected ImageButton volumeToggle;
    @BindString(R.string.details_activity_player_paused)
    protected String videoPaused;
    @BindString(R.string.details_activity_player_resumed)
    protected String videoResumed;
    @BindString(R.string.details_activity_playback_divider)
    protected String playbackDivider;
    @BindString(R.string.details_activity_player_seconds)
    protected String videoSeconds;
    @BindString(R.string.details_activity_error_loading_video)
    protected String errorLoadingVideo;
    @BindString(R.string.details_activity_error_opening_file)
    protected String errorOpeningFile;

    private int loadVideoStatus = LOAD_VIDEO_STATUS_UNKNOWN;

    /** Configuration information for the video. **/
    private VrVideoView.Options videoOptions = new VrVideoView.Options();

    private VideoLoaderTask backgroundVideoLoaderTask;

    private boolean isMuted;

    /**
     * By default, the video will start playing as soon as it is loaded. This can be changed by using
     * {@link VrVideoView#pauseVideo()} after loading the video.
     */
    private boolean isPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.details_activity);

        ButterKnife.bind(this);

        seekBar.setOnSeekBarChangeListener(new SeekBarListener());
        videoWidgetView.setEventListener(new ActivityEventListener());
        volumeToggle.setOnClickListener(v -> setIsMuted(!isMuted));

        loadVideoStatus = LOAD_VIDEO_STATUS_UNKNOWN;

        // Initial launch of the app or an Activity recreation due to rotation.
        handleIntent(getIntent());
    }

    /**
     * Called when the Activity is already running and it's given a new intent.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        Timber.i("onNewIntent(), %s", this.hashCode());
        // Save the intent. This allows the getIntent() call in onCreate() to use this new Intent during
        // future invocations.
        setIntent(intent);
        // Load the new image.
        handleIntent(intent);
    }

    private void setIsMuted(boolean isMuted) {
        this.isMuted = isMuted;
        volumeToggle.setImageResource(isMuted ? R.mipmap.ic_volume_off : R.mipmap.ic_volume_on);
        videoWidgetView.setVolume(isMuted ? 0.0f : 1.0f);
    }

    /**
     * Load custom videos based on the Intent or load the default video. See the Javadoc for this
     * class for information on generating a custom intent via adb.
     */
    private void handleIntent(Intent intent) {
        // Determine if the Intent contains a file to load.
        /* Tracks the file to be loaded across the lifetime of this app. */
        Uri fileUri;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Timber.i("ACTION_VIEW Intent received");

            fileUri = intent.getData();
            if (fileUri == null) {
                Timber.w("No data uri specified. Use \"-d /path/filename\".");
            } else {
                Timber.i("Using file, %s ", fileUri.toString());
            }

            videoOptions.inputFormat = intent.getIntExtra("inputFormat", VrVideoView.Options.FORMAT_DEFAULT);
            videoOptions.inputType = intent.getIntExtra("inputType", VrVideoView.Options.TYPE_MONO);
        } else {
            Timber.i("Intent is not ACTION_VIEW. Using the default video.");
            fileUri = null;
        }

        // Load the bitmap in a background thread to avoid blocking the UI thread. This operation can
        // take 100s of milliseconds.
        if (backgroundVideoLoaderTask != null) {
            // Cancel any task from a previous intent sent to this activity.
            backgroundVideoLoaderTask.cancel(true);
        }
        backgroundVideoLoaderTask = new VideoLoaderTask();
        //noinspection unchecked
        backgroundVideoLoaderTask.execute(Pair.create(fileUri, videoOptions));
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong(STATE_PROGRESS_TIME, videoWidgetView.getCurrentPosition());
        savedInstanceState.putLong(STATE_VIDEO_DURATION, videoWidgetView.getDuration());
        savedInstanceState.putBoolean(STATE_IS_PAUSED, isPaused);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        long progressTime = savedInstanceState.getLong(STATE_PROGRESS_TIME);
        videoWidgetView.seekTo(progressTime);
        seekBar.setMax((int) savedInstanceState.getLong(STATE_VIDEO_DURATION));
        seekBar.setProgress((int) progressTime);

        isPaused = savedInstanceState.getBoolean(STATE_IS_PAUSED);
        if (isPaused) {
            videoWidgetView.pauseVideo();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Prevent the view from rendering continuously when in the background.
        videoWidgetView.pauseRendering();
        // If the video is playing when onPause() is called, the default behavior will be to pause
        // the video and keep it paused when onResume() is called.
        isPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume the 3D rendering.
        videoWidgetView.resumeRendering();
        // Update the text to account for the paused video in onPause().
        updateStatusText();
    }

    @Override
    protected void onDestroy() {
        // Destroy the widget and free memory.
        videoWidgetView.shutdown();
        super.onDestroy();
    }

    private void togglePause() {
        if (isPaused) {
            videoWidgetView.playVideo();
        } else {
            videoWidgetView.pauseVideo();
        }
        isPaused = !isPaused;
        updateStatusText();
    }

    @SuppressLint("DefaultLocale")
    private void updateStatusText() {
        String status = (isPaused ? videoPaused : videoResumed) +
                String.format("%.2f", videoWidgetView.getCurrentPosition() / 1000f) +
                playbackDivider +
                videoWidgetView.getDuration() / 1000f +
                videoSeconds;
        statusText.setText(status);
    }

    /**
     * When the user manipulates the seek bar, update the video position.
     */
    private class SeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                videoWidgetView.seekTo(progress);
                updateStatusText();
            } // else this was from the ActivityEventHandler.onNewFrame()'s seekBar.setProgress update.
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }

    /**
     * Listen to the important events from widget.
     */
    private class ActivityEventListener extends VrVideoEventListener {
        /**
         * Called by video widget on the UI thread when it's done loading the video.
         */
        @Override
        public void onLoadSuccess() {
            Timber.i("Successfully loaded video, %s ", videoWidgetView.getDuration());
            loadVideoStatus = LOAD_VIDEO_STATUS_SUCCESS;
            seekBar.setMax((int) videoWidgetView.getDuration());
            updateStatusText();
        }

        /**
         * Called by video widget on the UI thread on any asynchronous error.
         */
        @Override
        public void onLoadError(String errorMessage) {
            // An error here is normally due to being unable to decode the video format.
            loadVideoStatus = LOAD_VIDEO_STATUS_ERROR;
            Toast.makeText(DetailsActivity.this, errorLoadingVideo + errorMessage, Toast.LENGTH_LONG)
                    .show();
            Timber.e("Error loading video, %s", errorMessage);
        }

        @Override
        public void onClick() {
            togglePause();
        }

        /**
         * Update the UI every frame.
         */
        @Override
        public void onNewFrame() {
            updateStatusText();
            seekBar.setProgress((int) videoWidgetView.getCurrentPosition());
        }

        /**
         * Make the video play in a loop. This method could also be used to move to the next video in
         * a playlist.
         */
        @Override
        public void onCompletion() {
            videoWidgetView.seekTo(0);
        }
    }

    /**
     * Helper class to manage threading.
     */
    class VideoLoaderTask extends AsyncTask<Pair<Uri, VrVideoView.Options>, Void, Boolean> {
        @SafeVarargs
        @SuppressWarnings("WrongThread")
        @Override
        protected final Boolean doInBackground(Pair<Uri, VrVideoView.Options>... fileInformation) {
            try {
                if (fileInformation == null || fileInformation.length < 1
                        || fileInformation[0] == null || fileInformation[0].first == null) {
                    // No intent was specified, so we default to playing the local stereo-over-under video.
                    VrVideoView.Options options = new VrVideoView.Options();
                    options.inputType = VrVideoView.Options.TYPE_STEREO_OVER_UNDER;
                    videoWidgetView.loadVideoFromAsset("congo.mp4", options);
                } else {
                    videoWidgetView.loadVideo(fileInformation[0].first, fileInformation[0].second);
                }
            } catch (IOException e) {
                // An error here is normally due to being unable to locate the file.
                loadVideoStatus = LOAD_VIDEO_STATUS_ERROR;
                // Since this is a background thread, we need to switch to the main thread to show a toast.
                videoWidgetView.post(() ->
                        Toast.makeText(DetailsActivity.this, errorOpeningFile, Toast.LENGTH_LONG).show());
                Timber.e(e, "Could not open video");
            }

            return true;
        }
    }
}