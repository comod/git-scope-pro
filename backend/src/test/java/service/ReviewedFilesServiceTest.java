package service;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ReviewedFilesServiceTest {

    @Test
    public void fingerprintFollowsSizeAndTimestamp() throws Exception {
        File file = File.createTempFile("reviewed", ".txt");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "a");
        long timestamp = file.lastModified();
        String stored = ReviewedFilesService.fingerprint(file);
        assertEquals(stored, ReviewedFilesService.fingerprint(file));
        file.setLastModified(timestamp - 10_000);
        assertNotEquals("other timestamp", stored, ReviewedFilesService.fingerprint(file));
        Files.writeString(file.toPath(), "ab");
        file.setLastModified(timestamp);
        assertNotEquals("other size", stored, ReviewedFilesService.fingerprint(file));
    }
}
