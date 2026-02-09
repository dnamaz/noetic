package com.dnamaz.websearch.provider.fetcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChromiumDetector}.
 */
class ChromiumDetectorTest {

    @Test
    @DisplayName("resolve with explicit valid path returns that path")
    void explicitValidPath(@TempDir Path tempDir) throws IOException {
        Path fakeBinary = tempDir.resolve("chromium");
        Files.createFile(fakeBinary);
        fakeBinary.toFile().setExecutable(true);

        Optional<String> result = ChromiumDetector.resolve(fakeBinary.toString());

        assertTrue(result.isPresent());
        assertEquals(fakeBinary.toString(), result.get());
    }

    @Test
    @DisplayName("resolve with explicit invalid path returns empty")
    void explicitInvalidPath() {
        Optional<String> result = ChromiumDetector.resolve("/nonexistent/path/chromium");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("resolve with null defaults to auto-detect")
    void nullDefaultsToAutoDetect() {
        // This may or may not find Chrome depending on the machine.
        // We just verify it doesn't throw.
        Optional<String> result = ChromiumDetector.resolve(null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("resolve with 'auto' triggers auto-detection")
    void autoTriggersAutoDetect() {
        Optional<String> result = ChromiumDetector.resolve("auto");
        assertNotNull(result);
        // On CI with no Chrome installed, this will be empty -- that's fine
    }

    @Test
    @DisplayName("resolve with blank string triggers auto-detection")
    void blankTriggersAutoDetect() {
        Optional<String> result = ChromiumDetector.resolve("  ");
        assertNotNull(result);
    }

    @Test
    @DisplayName("auto-detect finds system Chrome if installed")
    void autoDetectFindsChrome() {
        Optional<String> result = ChromiumDetector.resolve("auto");

        if (result.isPresent()) {
            // If found, verify the path is executable
            Path path = Path.of(result.get());
            assertTrue(Files.isExecutable(path),
                    "Detected path should be executable: " + result.get());
        }
        // If not found, that's OK -- no Chrome installed in this environment
    }
}
