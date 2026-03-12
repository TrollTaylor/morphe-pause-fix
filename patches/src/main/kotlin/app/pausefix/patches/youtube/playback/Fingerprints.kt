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
    returnType = "V",
    strings = listOf(
        "Volume: %f",
        "psps",
        "play() called when the player wasn't loaded"
    ),
    custom = { method, classDef ->
        // 1. Safety exclusions
        !classDef.type.contains("Drawable") && 
        !classDef.type.startsWith("Landroid/") &&
        
        // 2. Ensure it's not an interface or abstract method
        method.implementation != null
    }
)
