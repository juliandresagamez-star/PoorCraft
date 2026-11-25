package com.nettakrim.signed_paintings.util.media;

/**
 * A single decoded RGBA frame with its display duration.
 */
public final class MediaFrame {
    public final byte[] rgbaBytes;
    public final int width;
    public final int height;
    public final int delayMs;

    public MediaFrame(byte[] rgbaBytes, int width, int height, int delayMs) {
        this.rgbaBytes = rgbaBytes;
        this.width = width;
        this.height = height;
        this.delayMs = delayMs;
    }

    public long memoryBytes() {
        return rgbaBytes != null ? rgbaBytes.length : 0L;
    }
}
