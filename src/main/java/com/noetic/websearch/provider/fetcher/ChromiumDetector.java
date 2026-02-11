package com.noetic.websearch.provider.fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Detects available Chromium or Chrome installations on the system.
 *
 * <p>When the configured path is {@code "auto"}, scans well-known install
 * locations for macOS and Linux. Returns the first executable found.</p>
 */
public final class ChromiumDetector {

    private static final Logger log = LoggerFactory.getLogger(ChromiumDetector.class);

    private ChromiumDetector() {}

    /**
     * Well-known Chrome/Chromium paths per platform.
     * Ordered by preference (Chrome for Testing > Chrome > Chromium).
     */
    private static final List<String> MACOS_PATHS = List.of(
            "/Applications/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
    );

    private static final List<String> LINUX_PATHS = List.of(
            "/usr/bin/google-chrome-stable",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium-browser",
            "/usr/bin/chromium",
            "/snap/bin/chromium",
            "/usr/bin/microsoft-edge"
    );

    /**
     * Resolves the Chromium executable path.
     *
     * <p>If {@code configuredPath} is {@code "auto"}, scans well-known locations
     * and the system PATH. Otherwise, validates that the configured path exists
     * and is executable.</p>
     *
     * @param configuredPath the configured path, or "auto" for auto-detection
     * @return the resolved executable path, or empty if not found
     */
    public static Optional<String> resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank() || configuredPath.equalsIgnoreCase("auto")) {
            return autoDetect();
        }

        // Explicit path -- validate it
        Path path = Path.of(configuredPath);
        if (Files.isExecutable(path)) {
            log.info("Using configured Chromium path: {}", configuredPath);
            return Optional.of(configuredPath);
        }

        log.warn("Configured Chromium path does not exist or is not executable: {}", configuredPath);
        return Optional.empty();
    }

    private static Optional<String> autoDetect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> candidates = os.contains("mac") ? MACOS_PATHS : LINUX_PATHS;

        // Check well-known paths
        for (String candidate : candidates) {
            if (Files.isExecutable(Path.of(candidate))) {
                log.info("Auto-detected Chromium at: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Try system PATH via `which`
        Optional<String> fromPath = findInPath("google-chrome-stable")
                .or(() -> findInPath("google-chrome"))
                .or(() -> findInPath("chromium-browser"))
                .or(() -> findInPath("chromium"));

        if (fromPath.isPresent()) {
            log.info("Found Chromium on PATH: {}", fromPath.get());
            return fromPath;
        }

        log.warn("No Chromium installation found. Dynamic fetcher will fall back to Jsoup. "
                + "Install Chrome or Chromium, or set websearch.fetcher.dynamic.chromium-path explicitly.");
        return Optional.empty();
    }

    /**
     * Runs {@code which <binary>} to find the executable on the system PATH.
     */
    private static Optional<String> findInPath(String binary) {
        try {
            Process process = new ProcessBuilder("which", binary)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exit = process.waitFor();
            if (exit == 0 && !output.isBlank() && Files.isExecutable(Path.of(output))) {
                return Optional.of(output);
            }
        } catch (IOException | InterruptedException e) {
            // Silently ignore -- which is not available or binary not found
        }
        return Optional.empty();
    }
}
