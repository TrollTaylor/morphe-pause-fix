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

    // Filter based on typical opcode sequence in playback start logic
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.INVOKE_VIRTUAL),
    ),

    custom = { method, classDef ->
        // 1. Must NOT be an android system class or drawable (prevents FrameSequenceDrawable hook)
        !classDef.type.startsWith("Landroid/") && 
        !classDef.type.startsWith("Landroidx/") &&
        !classDef.type.contains("Drawable") &&
        
        // 2. Look for stable identifiers used by ReVanced for v20.x
        // - Literal: 45665455 (common feature flag)
        // - String: "play() called when the player wasn't loaded."
        val instructions = method.implementation?.instructions?.map { it.toString() } ?: emptyList()
        val hasStableLiteral = instructions.any { it.contains("45665455") } || 
                               instructions.any { it.contains("play() called when the player wasn't loaded") }
        
        // 3. Fallback: Check for PlaybackStartDescriptor if not fully obfuscated
        val hasDescriptorParam = method.parameterTypes.any { it.contains("PlaybackStartDescriptor") }

        (hasStableLiteral || hasDescriptorParam) && method.implementation != null
    }
)
