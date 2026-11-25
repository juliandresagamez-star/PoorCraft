package com.nettakrim.signed_paintings.util.media;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * Result of decoding static or animated media off the main thread.
 */
public final class DecodedMedia {
    public enum Format {
        STATIC,
        GIF,
        APNG,
        WEBP
    }

    public final Format format;
    public final int width;
    public final int height;
    public final boolean hasTranslucency;
    public final BufferedImage previewImage;
    public final List<MediaFrame> frames;
    public final int totalDurationMs;

    public DecodedMedia(Format format, int width, int height, boolean hasTranslucency, BufferedImage previewImage, List<MediaFrame> frames) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.hasTranslucency = hasTranslucency;
        this.previewImage = previewImage;
        this.frames = frames == null ? Collections.emptyList() : Collections.unmodifiableList(frames);

        int sum = 0;
        for (MediaFrame frame : this.frames) {
            sum += frame.delayMs;
        }
        this.totalDurationMs = Math.max(sum, 50);
    }

    public boolean isAnimated() {
        return frames.size() > 1;
    }

    public long totalMemoryBytes() {
        long total = 0L;
        for (MediaFrame frame : frames) {
            total += frame.memoryBytes();
        }
        return total;
    }

    public static DecodedMedia ofStatic(BufferedImage image) {
        BufferedImage rgba = MediaUtils.ensureRGBA(image);
        byte[] bytes = MediaUtils.toRgbaBytes(rgba);
        boolean translucent = MediaUtils.detectTranslucency(bytes);
        MediaFrame frame = new MediaFrame(bytes, rgba.getWidth(), rgba.getHeight(), 0);
        return new DecodedMedia(Format.STATIC, rgba.getWidth(), rgba.getHeight(), translucent, rgba, List.of(frame));
    }
}
