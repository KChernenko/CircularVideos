package me.bitfrom.circularvideos.utils;

import android.os.Bundle;

public final class ConstantsManager {

    public static class Details {
        /**
         * Preserve the video's state when rotating the phone.
         */
        public static final String STATE_IS_PAUSED = "isPaused";
        public static final String STATE_PROGRESS_TIME = "progressTime";
        /**
         * The video duration doesn't need to be preserved, but it is saved in this example. This allows
         * the seekBar to be configured during {@link android.app.Activity#onSaveInstanceState(Bundle)} rather than waiting
         * for the video to be reloaded and analyzed. This avoid UI jank.
         */
        public static final String STATE_VIDEO_DURATION = "videoDuration";

        /**
         * Arbitrary constants and variable to track load status. In this example, this variable should
         * only be accessed on the UI thread. In a real app, this variable would be code that performs
         * some UI actions when the video is fully loaded.
         */
        public static final int LOAD_VIDEO_STATUS_UNKNOWN = 0;
        public static final int LOAD_VIDEO_STATUS_SUCCESS = 1;
        public static final int LOAD_VIDEO_STATUS_ERROR = 2;
    }
}