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
    // Use stable landmarks from ReVanced Extended (Volume string, PSPS, etc.)
    // These are highly specific to the video player engine.
    custom = { method, classDef ->
        // 1. Safety exclusions
        !classDef.type.contains("Drawable") && 
        !classDef.type.startsWith("Landroid/") &&
        
        // 2. Look for stable identifiers used by ReVanced/Extended
        val instructions = method.implementation?.instructions?.map { it.toString() } ?: emptyList()
        
        val hasVolumeLandmark = instructions.any { it.contains("Volume: %f") }
        val hasPspsLandmark = instructions.any { it.contains("psps") }
        val hasLegacyLandmark = instructions.any { it.contains("play() called when the player wasn't loaded") }
        
        // 3. Check for specific parameter or return type if common
        val isVoid = method.returnType == "V"
        
        (hasVolumeLandmark || hasPspsLandmark || hasLegacyLandmark) && isVoid && method.implementation != null
    }
)
