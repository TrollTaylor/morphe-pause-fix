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
    // The pause method is typically public and declared final in the player class
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    // Pause method returns void
    returnType = "V",
    // No parameters — the simple pause() call
    parameters = listOf(),

    // Instruction pattern to match the pause method body.
    // YouTube's pause implementation typically:
    // 1. Gets the current playback state field
    // 2. Calls a method to update the state (set to paused)
    // 3. Calls a method to notify listeners
    filters = listOf(
        // Gets the playback state field (an int or enum)
        opcode(Opcode.IGET),
        // Invokes a method to set player state
        methodCall(
            name = "set",
        ),
        // Invokes a method on a listener/callback interface
        opcode(Opcode.INVOKE_INTERFACE),
    ),

    // Additional check: the class should have fields related to player state
    custom = { method, classDef ->
        // Match classes that have boolean and int fields (typical for player state tracking)
        // and the method itself is short (pause methods are usually concise)
        classDef.fields.any { it.type == "Z" } &&
            classDef.fields.any { it.type == "I" } &&
            method.implementation != null &&
            method.implementation!!.registerCount <= 8
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
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    // Returns void — it's a setup/initialization method
    returnType = "V",
    // Takes a String parameter (the video ID) and potentially other params
    parameters = listOf("Ljava/lang/String;"),

    filters = listOf(
        // Stores the video ID string
        opcode(Opcode.IPUT_OBJECT),
        // Initializes playback state (sets a boolean or int)
        opcode(Opcode.CONST_4),
        // Stores the initial state
        opcode(Opcode.IPUT),
    ),

    custom = { method, classDef ->
        // The class should have a String field (for video ID storage)
        classDef.fields.any { it.type == "Ljava/lang/String;" } &&
            method.implementation != null
    }
)
