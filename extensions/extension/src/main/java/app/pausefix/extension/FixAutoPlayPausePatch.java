package app.pausefix.extension;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Runtime extension for the YouTube Auto-Pause Fix patch.
 *
 * This class is injected into the patched YouTube APK and provides the logic
 * to selectively block automatic pause calls that occur during initial video load.
 *
 * <h2>How it works:</h2>
 * <ol>
 *   <li>When a new video starts loading, {@link #onVideoStarted()} is called,
 *       which enables pause-blocking for a short protection window.</li>
 *   <li>During this window, any calls to the player's pause method are intercepted
 *       by {@link #shouldBlockPause()}, which returns {@code true} to skip the pause.</li>
 *   <li>After the protection window expires (default 3 seconds), pauses are allowed
 *       normally — so user-initiated pauses work as expected.</li>
 * </ol>
 *
 * <h2>Why this works:</h2>
 * The YouTube auto-pause bug fires pause calls within the first 1-2 seconds of
 * video load when the user is logged in. By blocking pauses only during this narrow
 * window, we prevent the bug without interfering with normal playback controls.
 */
@SuppressWarnings("unused")
public final class FixAutoPlayPausePatch {

    private static final String TAG = "PauseFix";

    /**
     * Duration in milliseconds to block automatic pause calls after a video starts.
     * The bug typically fires within the first 1-2 seconds, so 3 seconds provides
     * comfortable headroom.
     */
    private static final long PROTECTION_WINDOW_MS = 3000L;

    /**
     * Maximum number of pause calls to block per protection window.
     * This acts as a safety valve — if something is genuinely trying to pause
     * (e.g., the user tapping pause very quickly), we don't want to block forever.
     */
    private static final int MAX_BLOCKED_PAUSES = 10;

    /**
     * Whether pause calls should currently be blocked.
     * Set to {@code true} when a video starts loading, reset after the protection window.
     */
    private static volatile boolean blockPause = false;

    /**
     * Counter for how many pause calls have been blocked in the current window.
     */
    private static volatile int blockedCount = 0;

    /**
     * Handler for scheduling the protection window timeout on the main thread.
     */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Runnable that disables pause blocking after the protection window expires.
     */
    private static final Runnable disableRunnable = () -> {
        blockPause = false;
        Log.d(TAG, "Protection window expired. Blocked " + blockedCount + " pause(s). Normal pause behavior restored.");
        blockedCount = 0;
    };

    /**
     * Called when a new video starts loading.
     * Enables the pause-blocking protection window.
     *
     * This method is invoked via the smali hook injected into YouTube's
     * playback initialization method by the bytecode patch.
     */
    public static void onVideoStarted() {
        Log.d(TAG, "Video started loading — enabling pause protection for " + PROTECTION_WINDOW_MS + "ms");

        // Reset state
        blockedCount = 0;
        blockPause = true;

        // Cancel any previous pending timeout and schedule a new one
        handler.removeCallbacks(disableRunnable);
        handler.postDelayed(disableRunnable, PROTECTION_WINDOW_MS);
    }

    /**
     * Checks whether the current pause call should be blocked.
     *
     * This method is invoked via the smali hook injected at the start of
     * YouTube's player pause method by the bytecode patch.
     *
     * @return {@code true} if the pause should be blocked (within protection window),
     *         {@code false} if the pause should proceed normally.
     */
    public static boolean shouldBlockPause() {
        if (blockPause) {
            blockedCount++;

            // Safety valve: if we've blocked too many pauses, something else
            // might be going on — allow pauses through
            if (blockedCount > MAX_BLOCKED_PAUSES) {
                Log.w(TAG, "Blocked " + MAX_BLOCKED_PAUSES + " pauses — safety limit reached, allowing pause through.");
                blockPause = false;
                return false;
            }

            Log.d(TAG, "Blocked auto-pause #" + blockedCount + " (within protection window)");
            return true;
        }

        // Outside protection window — allow normal pause behavior
        return false;
    }

    // Prevent instantiation
    private FixAutoPlayPausePatch() {
    }
}
