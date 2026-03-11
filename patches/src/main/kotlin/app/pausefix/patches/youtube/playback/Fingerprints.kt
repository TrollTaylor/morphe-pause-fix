package app.pausefix.patches.youtube.playback

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patcher.opcode
import app.morphe.patcher.methodCall

/**
 * Fingerprint for YouTube's internal video player pause method.
 *
 * This targets the method responsible for pausing video playback in YouTube's
 * ExoPlayer-based player implementation. The method is called both by user-initiated
 * pause actions and by internal logic (which is what causes the auto-pause bug).
 *
 * The fingerprint uses broad patterns to remain resilient across YouTube versions,
 * since YouTube obfuscates method/class names with each release.
 */
object PlayerPauseFingerprint : Fingerprint(
    // The play/pause toggle method is typically public
    accessFlags = listOf(AccessFlags.PUBLIC),
    // Returns void
    returnType = "V",
    // Takes a BOOLEAN parameter — this is the playWhenReady/pause toggle
    // (true = play, false = pause)
    parameters = listOf("Z"),

    // The method stores the boolean to a field and then invokes handlers
    filters = listOf(
        opcode(Opcode.IPUT_BOOLEAN),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),

    // The class should have multiple boolean fields (player state tracking)
    // and at least one int field (e.g., playback state enum)
    custom = { method, classDef ->
        classDef.fields.count { it.type == "Z" } >= 2 &&
            classDef.fields.any { it.type == "I" } &&
            method.implementation != null &&
            method.implementation!!.registerCount <= 10
    }
)

/**
 * Fingerprint for the method called when a new video starts loading/playing.
 *
 * This is used to set the "recently started" flag so we know when to block
 * auto-pause calls. It targets the method that initializes playback for a
 * new video (typically called when a video ID changes).
 */
object PlaybackStartFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    // Returns void — it's a setup/initialization method
    returnType = "V",
    // We've removed strict parameter types to widen the match

    // Filter based on the initialization sequence
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),

    custom = { method, classDef ->
        // The class should have a String field (for video ID storage)
        // and the method should take at least one object parameter
        classDef.fields.any { it.type == "Ljava/lang/String;" } &&
            method.parameterTypes.any { it.startsWith("L") } &&
            method.implementation != null
    }
)
