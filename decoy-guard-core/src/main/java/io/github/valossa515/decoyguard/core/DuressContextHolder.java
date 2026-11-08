package io.github.valossa515.decoyguard.core;

/**
 * Binds the {@link DuressContext} to the thread serving the current request.
 * Integrations must call {@link #clear()} when the request ends.
 */
public final class DuressContextHolder {

    private static final ThreadLocal<DuressContext> CURRENT = new ThreadLocal<>();

    private DuressContextHolder() {
    }

    public static void set(DuressContext context) {
        CURRENT.set(context);
    }

    /** Returns the bound context, or an anonymous NORMAL context when none is bound. */
    public static DuressContext current() {
        DuressContext context = CURRENT.get();
        return context != null ? context : DuressContext.anonymous();
    }

    public static boolean isDuress() {
        return current().isDuress();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
