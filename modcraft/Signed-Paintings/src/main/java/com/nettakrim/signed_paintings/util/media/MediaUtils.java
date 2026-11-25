package com.nettakrim.signed_paintings.util.media;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;

/**
 * Shared RGBA conversion, scaling, and memory-budget helpers for media decoders.
 */
public final class MediaUtils {
    private MediaUtils() {}

    public static BufferedImage createRGBAImage(int width, int height) {
        width = Math.max(width, 1);
        height = Math.max(height, 1);
        int[] bandOffsets = {0, 1, 2, 3};

        ComponentColorModel colorModel = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{8, 8, 8, 8}, true, false,
                Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE
        );

        WritableRaster raster = Raster.createInterleavedRaster(
                DataBuffer.TYPE_BYTE, width, height, width * 4, 4, bandOffsets, null
        );

        return new BufferedImage(colorModel, raster, false, null);
    }

    public static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = createRGBAImage(src.getWidth(), src.getHeight());
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    public static BufferedImage scaleImage(BufferedImage referenceImage, int width, int height) {
        width = Math.max(width, 1);
        height = Math.max(height, 1);
        BufferedImage resizedImage = createRGBAImage(width, height);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setComposite(AlphaComposite.Src);
        graphics2D.drawImage(referenceImage, 0, 0, width, height, null);
        graphics2D.dispose();
        return resizedImage;
    }

    public static BufferedImage ensureRGBA(BufferedImage image) {
        if (image.getRaster().getDataBuffer() instanceof DataBufferByte
                && image.getColorModel().getNumComponents() == 4
                && image.getColorModel() instanceof ComponentColorModel) {
            return image;
        }
        return copyImage(image);
    }

    public static byte[] toRgbaBytes(BufferedImage rgbaImage) {
        BufferedImage img = ensureRGBA(rgbaImage);
        byte[] raw = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        byte[] copy = new byte[raw.length];
        System.arraycopy(raw, 0, copy, 0, raw.length);
        return copy;
    }

    public static boolean detectTranslucency(byte[] rgbaBytes) {
        for (int i = 3; i < rgbaBytes.length; i += 4) {
            int alpha = rgbaBytes[i] & 0xFF;
            if (alpha > 0 && alpha < 255) {
                return true;
            }
        }
        return false;
    }

    public static int[] fitWithinMaxDimension(int width, int height, int maxDim) {
        width = Math.max(width, 1);
        height = Math.max(height, 1);
        if (maxDim <= 0 || (width <= maxDim && height <= maxDim)) {
            return new int[]{width, height};
        }
        double scale = Math.min((double) maxDim / width, (double) maxDim / height);
        int outW = Math.max(1, (int) Math.round(width * scale));
        int outH = Math.max(1, (int) Math.round(height * scale));
        return new int[]{outW, outH};
    }

    /**
     * Computes an effective max dimension for animated frames so total decoded memory stays within budget.
     */
    public static int[] fitAnimatedDimensions(int width, int height, int estimatedFrames, MediaConfig config) {
        int[] dims = fitWithinMaxDimension(width, height, config.maxAnimatedDimension);
        int frames = Math.max(1, Math.min(estimatedFrames, config.maxFrames));
        long bytesPerFrame = (long) dims[0] * dims[1] * 4L;
        if (bytesPerFrame * frames > config.maxDecodedMemoryBytes && config.maxDecodedMemoryBytes > 0) {
            double maxBytesPerFrame = (double) config.maxDecodedMemoryBytes / frames;
            double scale = Math.sqrt(maxBytesPerFrame / bytesPerFrame);
            dims[0] = Math.max(1, (int) Math.floor(dims[0] * scale));
            dims[1] = Math.max(1, (int) Math.floor(dims[1] * scale));
        }
        return dims;
    }
}
