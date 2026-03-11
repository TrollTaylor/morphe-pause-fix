package app.pausefix.patches.youtube.playback

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Descriptor for the runtime extension class that contains the pause-blocking logic.
 * This class is compiled into the patched APK via the extensions module.
 */
private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/pausefix/extension/FixAutoPlayPausePatch;"

/**
 * Morphe patch that fixes the YouTube auto-pause bug.
 *
 * When logged into YouTube, videos may auto-pause immediately after opening
 * and keep re-pausing when the user tries to play. This patch intercepts
 * the player's internal pause method and blocks automatic pause calls
 * during the initial video load window (first ~3 seconds).
 *
 * User-initiated pauses (tapping the pause button after the video has started)
 * still work normally.
 */
@Suppress("unused")
val fixAutoPlayPausePatch = bytecodePatch(
    name = "Fix Auto-Play Pause",
    description = "Fixes YouTube videos auto-pausing immediately after opening when logged in.",
) {
    // Compatible with YouTube. No version constraint — fingerprints handle matching.
    // If you want to lock to specific versions, use: "com.google.android.youtube"("19.0.0", "20.0.0")
    compatibleWith("com.google.android.youtube")

    // Include the runtime extension code in the patched APK
    extendWith("extensions/extension.rve")

    execute {
        // --- Hook 1: Intercept the play/pause toggle ---
        // The matched method takes a boolean (p1): true = play, false = pause.
        // We intercept p1 and override it to true (play) if we're in the
        // protection window, preventing the auto-pause.
        PlayerPauseFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->filterPlayWhenReady(Z)Z
                move-result p1
            """
        )

        // --- Hook 2: Mark when a new video starts ---
        // At the start of the playback initialization method, notify our extension
        // that a new video is loading. This starts the protection window.
        PlaybackStartFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->onVideoStarted()V
            """
        )
    }
}
