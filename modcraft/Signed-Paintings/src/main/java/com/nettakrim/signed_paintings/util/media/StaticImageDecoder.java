package com.nettakrim.signed_paintings.util.media;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Fallback decoder for static image formats supported by ImageIO (PNG, JPEG, BMP, etc.).
 */
public class StaticImageDecoder implements MediaDecoder {
    @Override
    public String getFormatName() {
        return "static";
    }

    @Override
    public boolean canDecode(byte[] data) {
        return data != null && data.length > 0;
    }

    @Override
    public DecodedMedia decode(byte[] data, MediaConfig config) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unsupported or corrupt image data");
            }
            int[] dims = MediaUtils.fitWithinMaxDimension(image.getWidth(), image.getHeight(), config.maxStaticDimension);
            BufferedImage processed = (dims[0] != image.getWidth() || dims[1] != image.getHeight())
                    ? MediaUtils.scaleImage(image, dims[0], dims[1])
                    : MediaUtils.ensureRGBA(image);
            return DecodedMedia.ofStatic(processed);
        }
    }
}
