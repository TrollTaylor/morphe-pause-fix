package app.pausefix.patches.youtube.playback

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import app.morphe.patcher.opcode

/**
 * Fingerprint for the method called when a new video starts loading/playing.
 *
 * This is the ONLY fingerprint we use. Rather than trying to find and hook
 * the pause method (which is unreliable due to obfuscation), we hook the
 * playback start method and pass the player object to our extension.
 * The extension then uses reflection to auto-resume playback when
 * YouTube's bug auto-pauses it.
 */
object PlaybackStartFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    // Returns void — it's a setup/initialization method
    returnType = "V",

    // Filter based on the initialization sequence
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),

    custom = { method, classDef ->
        // The class must NOT be a drawable (common false positive)
        !classDef.type.contains("Drawable") &&
            // The class should contain fields related to YouTube player models
            classDef.fields.any { it.type.contains("player") || it.type.contains("innertube") } &&
            // The method should take at least one object parameter
            method.parameterTypes.any { it.startsWith("L") } &&
            method.implementation != null
    }
)
