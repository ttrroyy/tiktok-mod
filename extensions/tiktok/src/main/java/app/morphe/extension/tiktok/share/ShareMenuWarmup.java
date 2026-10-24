/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.share;

import android.content.Context;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Start the host's cached share configuration before the first share sheet needs its order. */
public final class ShareMenuWarmup {
    private static final AtomicBoolean scheduled = new AtomicBoolean();
    public static void install(Context context) {
        if (!scheduled.compareAndSet(false, true)) return;
        Utils.runOnMainThreadDelayed(() -> {
            try { warmNative(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                Logger.printInfo(() -> "Share menu warm-up unavailable; native lazy initialization retained: " + failure);
            }
        }, 500);
    }
    private static void warmNative() throws ReflectiveOperationException {
        // shortcut: reviewed 47.5.3 host bindings; resolve them again when changing the base APK.
        Class<?> config = Class.forName("X.0GCM");
        for (Method m : config.getDeclaredMethods())
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 0 && m.getReturnType() == config)
                m.invoke(null);
        Class<?> order = Class.forName("X.0G4h");
        if (order.getDeclaredField("LJ").getBoolean(null)) return;
        Object singleton = null;
        for (Field f : order.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == order) singleton = f.get(null);
        if (singleton == null) throw new IllegalStateException("No native share order singleton");
        for (Method m : order.getDeclaredMethods())
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class
                    && java.util.Arrays.equals(m.getParameterTypes(), new Class<?>[]{order})) m.invoke(null, singleton);
    }
}
