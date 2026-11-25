package com.nettakrim.signed_paintings.util.media;

import java.io.IOException;

/**
 * Extensible interface for media format decoders (GIF, APNG, WebP, static images, etc.).
 */
public interface MediaDecoder {
    String getFormatName();

    boolean canDecode(byte[] data);

    DecodedMedia decode(byte[] data, MediaConfig config) throws IOException;
}
