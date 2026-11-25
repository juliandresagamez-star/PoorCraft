package com.nettakrim.signed_paintings.util.media;

/**
 * Configurable limits and behavior for downloading, decoding, and animating media.
 */
public class MediaConfig {
    public int maxStaticDimension = 2048;
    public int maxAnimatedDimension = 512;
    public int maxFrames = 120;
    public int maxDownloadBytes = 32 * 1024 * 1024;
    public long maxDecodedMemoryBytes = 64L * 1024L * 1024L;
    public boolean pauseOffscreen = true;
    public int defaultZeroDelayMs = 100;
    public int minFrameDelayMs = 20;

    public int normalizeDelayMs(int rawDelayMs) {
        if (rawDelayMs <= 10) {
            return defaultZeroDelayMs;
        }
        return Math.max(minFrameDelayMs, rawDelayMs);
    }
}
