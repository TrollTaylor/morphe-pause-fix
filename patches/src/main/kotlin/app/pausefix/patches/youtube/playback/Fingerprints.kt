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
    custom = { method, classDef ->
        // Use custom logic to check for landmarks, as constructor properties 
        // like 'strings' or 'literals' may cause compilation errors if not supported.
        val instructions = method.implementation?.instructions?.map { it.toString() } ?: emptyList()
        
        // Landmarks for playback start in YouTube 20.x (found in ReVanced)
        val hasPlayLog = instructions.any { it.contains("play() called when the player wasn't loaded.") }
        val hasApiPlayerStateLiteral = instructions.any { it.contains("45665455") }
        
        // Basic method signature check
        val isVoid = method.returnType == "V"
        
        // Match if any landmark is found, but safely exclude system classes/drawables
        (hasPlayLog || hasApiPlayerStateLiteral) && isVoid &&
        !classDef.type.contains("Drawable") && 
        !classDef.type.startsWith("Landroid/") && 
        method.implementation != null
    }
)
