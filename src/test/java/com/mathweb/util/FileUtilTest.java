package com.mathweb.util;

import com.mathweb.exception.FileStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit test: no Spring context, no database, no Docker.
 * Run with:  ./mvnw test -Dtest=FileUtilTest   (Maven)
 *            ./gradlew test --tests FileUtilTest (Gradle)
 */
class FileUtilTest {

    // First 12 bytes of each format
    private static final byte[] MP4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] OGG = {'O', 'g', 'g', 'S', 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PLAYLIST =
            "#EXTM3U\nhttp://example.com/x.ts\n".getBytes(StandardCharsets.UTF_8);

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "upload.bin", contentType, bytes);
    }

    // ===== VIDEO =====

    @Test
    void mp4_withCorrectSignature_isAccepted() {
        assertDoesNotThrow(() -> FileUtil.validateVideo(file("video/mp4", MP4)));
    }

    @Test
    void webm_withCorrectSignature_isAccepted() {
        assertDoesNotThrow(() -> FileUtil.validateVideo(file("video/webm", WEBM)));
    }

    @Test
    void ogg_withCorrectSignature_isAccepted() {
        assertDoesNotThrow(() -> FileUtil.validateVideo(file("video/ogg", OGG)));
    }

    @Test
    void playlistLabelledAsMp4_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file("video/mp4", PLAYLIST)));
    }

    @Test
    void mp4ContentLabelledAsWebm_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file("video/webm", MP4)));
    }

    @Test
    void missingContentType_isRejectedWithoutNullPointerException() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file(null, MP4)));
    }

    @Test
    void unsupportedVideoType_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file("video/avi", MP4)));
    }

    @Test
    void emptyVideo_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file("video/mp4", new byte[0])));
    }

    @Test
    void tooShortVideo_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateVideo(file("video/mp4", new byte[]{0, 0, 0})));
    }

    // ===== IMAGE =====

    @Test
    void png_withCorrectSignature_isAccepted() {
        assertDoesNotThrow(() -> FileUtil.validateImage(file("image/png", PNG)));
    }

    @Test
    void jpeg_withCorrectSignature_isAccepted() {
        assertDoesNotThrow(() -> FileUtil.validateImage(file("image/jpeg", JPEG)));
    }

    @Test
    void textLabelledAsPng_isRejected() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateImage(file("image/png", PLAYLIST)));
    }

    @Test
    void svg_isRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateImage(file("image/svg+xml", svg)));
    }

    @Test
    void missingImageContentType_isRejectedWithoutNullPointerException() {
        assertThrows(FileStorageException.class,
                () -> FileUtil.validateImage(file(null, PNG)));
    }

    // ===== EXTENSIONS =====

    @Test
    void videoExtension_mapsKnownTypes_andFallsBackToBin() {
        assertEquals("mp4", FileUtil.videoExtension("video/mp4"));
        assertEquals("webm", FileUtil.videoExtension("video/webm"));
        assertEquals("bin", FileUtil.videoExtension("text/html"));
        assertEquals("bin", FileUtil.videoExtension(null));
    }

    @Test
    void imageExtension_mapsKnownTypes_andFallsBackToBin() {
        assertEquals("png", FileUtil.imageExtension("image/png"));
        assertEquals("jpg", FileUtil.imageExtension("image/jpeg"));
        assertEquals("bin", FileUtil.imageExtension(null));
    }
}