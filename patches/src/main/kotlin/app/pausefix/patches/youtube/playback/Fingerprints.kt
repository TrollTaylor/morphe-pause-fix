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
    // Match any public void method that acts as a landmark
    custom = { method, classDef ->
        val instructions = method.implementation?.instructions?.map { it.toString() } ?: emptyList()
        
        // aggregate ALL known landmarks for the playback engine
        val hasLandmark = instructions.any { 
            it.contains("play() called when the player wasn't loaded.") || 
            it.contains("play() blocked because Background Playability failed") ||
            it.contains("Volume: %f") ||
            it.contains("psps") ||
            it.contains("45665455") || // apiPlayerState
            it.contains("45380134")    // videoId feature flag
        }
        
        // Safety: Must be a method with implementation in a YouTube (non-android) class
        method.implementation != null && 
        !classDef.type.contains("Drawable") && 
        !classDef.type.startsWith("Landroid/") &&
        !classDef.type.startsWith("Landroidx/")
    }
)
