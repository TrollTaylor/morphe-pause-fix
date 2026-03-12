package app.pausefix.extension;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Runtime extension for the YouTube Auto-Pause Fix patch.
 *
 * <h2>Strategy: Reflection-based auto-resume</h2>
 * Instead of trying to intercept the pause method (which is unreliable due
 * to YouTube's obfuscation), we take the player/controller object from the
 * playback start hook and use reflection to force-resume playback after
 * the auto-pause bug fires.
 *
 * <h2>How it works:</h2>
 * <ol>
 *   <li>When a new video starts loading, {@link #onVideoStarted(Object)} is called
 *       with a reference to the player/controller object.</li>
 *   <li>We schedule multiple resume attempts over the next few seconds.</li>
 *   <li>Each attempt tries to call play-related methods via reflection
 *       (setPlayWhenReady, play, etc.) to override the auto-pause.</li>
 * </ol>
 */
@SuppressWarnings("unused")
public final class FixAutoPlayPausePatch {

    private static final String TAG = "PauseFix";

    /**
     * How many times to attempt auto-resume after a video starts.
     */
    private static final int RESUME_ATTEMPTS = 6;

    /**
     * Delay between resume attempts in milliseconds.
     */
    private static final long RESUME_INTERVAL_MS = 500L;

    /**
     * Handler for scheduling resume attempts on the main thread.
     */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Tracks whether we're currently in a resume cycle.
     */
    private static volatile boolean resuming = false;

    /**
     * Called when a new video starts loading.
     * Receives the player/controller object from the bytecode hook.
     *
     * This method is invoked via the smali hook injected into YouTube's
     * playback initialization method by the bytecode patch.
     *
     * @param playerObject the 'this' reference from the hooked method
     */
    public static void onVideoStarted(Object playerObject) {
        Log.d(TAG, "Video started — player class: " + playerObject.getClass().getName());

        // Cancel any previous resume cycle
        handler.removeCallbacksAndMessages(TAG);
        resuming = true;

        // Log available methods for debugging (only first time)
        logPlayerMethods(playerObject);

        // Schedule multiple resume attempts
        for (int i = 0; i < RESUME_ATTEMPTS; i++) {
            final int attempt = i + 1;
            handler.postDelayed(() -> {
                if (!resuming) return;
                tryForceResume(playerObject, attempt);
            }, RESUME_INTERVAL_MS * (i + 1));
        }

        // After all attempts, stop the resume cycle
        handler.postDelayed(() -> {
            resuming = false;
            Log.d(TAG, "Resume cycle complete.");
        }, RESUME_INTERVAL_MS * (RESUME_ATTEMPTS + 1));
    }

    /**
     * Try to force-resume playback using reflection.
     * Attempts multiple known method names that control playback.
     */
    private static void tryForceResume(Object playerObject, int attempt) {
        Log.d(TAG, "Resume attempt #" + attempt + " on " + playerObject.getClass().getSimpleName());

        // Try known ExoPlayer/YouTube player method names
        // These methods may or may not exist depending on obfuscation
        boolean success = false;

        // Strategy 1: Try setPlayWhenReady(true)
        success = tryCallMethod(playerObject, "setPlayWhenReady", new Class[]{boolean.class}, true);
        if (success) {
            Log.d(TAG, "  ✓ setPlayWhenReady(true) succeeded!");
            return;
        }

        // Strategy 2: Try play()
        success = tryCallMethod(playerObject, "play", new Class[]{}, (Object[]) null);
        if (success) {
            Log.d(TAG, "  ✓ play() succeeded!");
            return;
        }

        // Strategy 3: Scan for void methods with a single boolean parameter
        // and try calling them with true (play = true, pause = false pattern)
        try {
            for (Method m : playerObject.getClass().getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && params[0] == boolean.class
                        && m.getReturnType() == void.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(playerObject, true);
                        Log.d(TAG, "  ✓ Called " + m.getName() + "(true) via scan");
                        // Don't return — we want to try all boolean methods
                        // since we don't know which one is the play toggle
                    } catch (Exception e) {
                        // Ignore — method might not be the right one
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "  Method scan failed: " + e.getMessage());
        }

        // Strategy 4: Try on superclass methods too
        try {
            Class<?> superClass = playerObject.getClass().getSuperclass();
            if (superClass != null && superClass != Object.class) {
                success = tryCallMethodOnClass(playerObject, superClass, "setPlayWhenReady",
                        new Class[]{boolean.class}, true);
                if (success) {
                    Log.d(TAG, "  ✓ super.setPlayWhenReady(true) succeeded!");
                    return;
                }
                success = tryCallMethodOnClass(playerObject, superClass, "play",
                        new Class[]{}, (Object[]) null);
                if (success) {
                    Log.d(TAG, "  ✓ super.play() succeeded!");
                    return;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Log available methods on the player object for debugging.
     */
    private static boolean logged = false;
    private static void logPlayerMethods(Object playerObject) {
        if (logged) return;
        logged = true;

        try {
            Log.d(TAG, "Player class: " + playerObject.getClass().getName());
            Log.d(TAG, "Superclass: " + playerObject.getClass().getSuperclass());

            // Log methods that take a boolean parameter or have play-related names
            for (Method m : playerObject.getClass().getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                String paramStr = "";
                for (Class<?> p : params) {
                    paramStr += p.getSimpleName() + ", ";
                }
                if (params.length <= 2) {
                    Log.d(TAG, "  Method: " + m.getName() + "(" + paramStr + ") -> " + m.getReturnType().getSimpleName());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to log methods: " + e.getMessage());
        }
    }

    /**
     * Try to call a method by name on the given object.
     */
    private static boolean tryCallMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... args) {
        return tryCallMethodOnClass(obj, obj.getClass(), methodName, paramTypes, args);
    }

    /**
     * Try to call a method by name using a specific class definition.
     */
    private static boolean tryCallMethodOnClass(Object obj, Class<?> clazz, String methodName,
                                                 Class<?>[] paramTypes, Object... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            if (args != null) {
                method.invoke(obj, args);
            } else {
                method.invoke(obj);
            }
            return true;
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, try getMethod (includes inherited)
            try {
                Method method = clazz.getMethod(methodName, paramTypes);
                method.setAccessible(true);
                if (args != null) {
                    method.invoke(obj, args);
                } else {
                    method.invoke(obj);
                }
                return true;
            } catch (Exception e2) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "  Failed to call " + methodName + ": " + e.getMessage());
            return false;
        }
    }

    // Prevent instantiation
    private FixAutoPlayPausePatch() {
    }
}
