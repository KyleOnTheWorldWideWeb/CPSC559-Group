package io.github.cpsc559.team16.common.utilities;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for standardized logging with independent module-level control.
 * <p>
 * Individual modules can be configured via environment variables following the pattern:
 * {@code [MODULE]_DEBUG_LEVEL}. For example, {@code REGISTRY_DEBUG_LEVEL=3}.
 * </p>
 * @version 1.0
 */
public final class Debug {

    /**
     * Cache for module-specific debug levels to avoid repeated environment lookups.
     */
    private static final Map<String, Integer> moduleLevels = new HashMap<>();

    /**
     * The current threshold for logging.
     * Messages with a level higher than this value will be suppressed.
     * <p>
     * 0 = Off, 1 = Info (Lifecycle), 2 = Debug (Logic), 3 = Trace (Network/IO)
     * </p>
     */
    private static final int DEBUG_LEVEL = Integer.parseInt(
            System.getenv().getOrDefault("AS_DEBUG_LEVEL", "1")
    );



    /**
     * Private constructor to prevent instantiation of this utility class.
     * * @throws UnsupportedOperationException if an attempt is made to instantiate.
     */
    private Debug() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Prints a message if the module's specific level or the global level allows it.
     *
     * @param level   Severity of the message (1=Info, 2=Debug, 3=Trace).
     * @param module  The component name (e.g., "REGISTRY", "NETWORK").
     * @param message The text to log.
     */
    public static void log(int level, String module, String message) {
        int threshold = getThresholdForModule(module.toUpperCase());

        if (level <= threshold) {
            String timestamp = java.time.LocalTime.now().toString();
            String threadName = Thread.currentThread().getName();
            System.out.printf("[%s] [%s] [%-10s] %s%n",
                    timestamp, threadName, module.toUpperCase(), message);
        }
    }

    /**
     * Resolves the debug level for a specific module.
     * Looks for {@code MODULE_DEBUG_LEVEL} first, then falls back to {@code AS_DEBUG_LEVEL}.
     */
    private static int getThresholdForModule(String module) {
        return moduleLevels.computeIfAbsent(module, m -> {
            String envVar = m + "_DEBUG_LEVEL";
            String val = System.getenv(envVar);
            return (val != null) ? Integer.parseInt(val) : DEBUG_LEVEL;
        });
    }
}
