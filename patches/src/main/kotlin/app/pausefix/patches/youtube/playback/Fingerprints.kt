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
        // 1. Must NOT be an android system class or drawable (prevents FrameSequenceDrawable hook)
        !classDef.type.startsWith("Landroid/") && 
        !classDef.type.startsWith("Landroidx/") &&
        !classDef.type.contains("Drawable") &&
        
        // 2. Must contain fields related to YouTube player logic (descriptors, models, etc.)
        classDef.fields.any { f -> 
            f.type.contains("player") || f.type.contains("Descriptor") || f.type.contains("Response") || f.type.contains("innertube")
        } &&
        
        // 3. Method must take at least one object (usually the PlaybackStartDescriptor)
        method.parameterTypes.any { it.startsWith("L") } &&
        method.implementation != null
    }
)
