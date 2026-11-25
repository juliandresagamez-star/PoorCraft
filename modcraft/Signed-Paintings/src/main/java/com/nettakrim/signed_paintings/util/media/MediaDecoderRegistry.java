package com.nettakrim.signed_paintings.util.media;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extensible registry of MediaDecoders tried in priority order.
 */
public final class MediaDecoderRegistry {
    private static final List<MediaDecoder> DECODERS = new CopyOnWriteArrayList<>();

    static {
        DECODERS.add(new GifMediaDecoder());
        DECODERS.add(new ApngMediaDecoder());
        DECODERS.add(new WebpMediaDecoder());
        DECODERS.add(new StaticImageDecoder());
    }

    private MediaDecoderRegistry() {}

    public static void registerBeforeFallback(MediaDecoder decoder) {
        if (decoder == null) return;
        int insertIdx = Math.max(0, DECODERS.size() - 1);
        DECODERS.add(insertIdx, decoder);
    }

    public static DecodedMedia decode(byte[] data, MediaConfig config) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Empty media payload");
        }
        IOException lastError = null;
        for (MediaDecoder decoder : DECODERS) {
            if (decoder.canDecode(data)) {
                try {
                    return decoder.decode(data, config);
                } catch (IOException | IllegalArgumentException e) {
                    lastError = (e instanceof IOException ioe) ? ioe : new IOException(e);
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("No decoder could handle media payload");
    }
}
