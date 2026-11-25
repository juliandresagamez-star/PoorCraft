package com.nettakrim.signed_paintings.util.media;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Pure-Java APNG (Animated PNG) decoder supporting acTL, fcTL, IDAT/fdAT, and dispose/blend ops.
 */
public class ApngMediaDecoder implements MediaDecoder {
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final int DISPOSE_NONE = 0;
    private static final int DISPOSE_BACKGROUND = 1;
    private static final int DISPOSE_PREVIOUS = 2;

    private static final int BLEND_SOURCE = 0;
    private static final int BLEND_OVER = 1;

    @Override
    public String getFormatName() {
        return "apng";
    }

    @Override
    public boolean canDecode(byte[] data) {
        if (data == null || data.length < 32) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        // Scan top-level chunks up to IDAT to check for acTL
        int pos = 8;
        while (pos + 12 <= data.length) {
            int len = readIntBE(data, pos);
            if (len < 0 || pos + 12L + len > data.length) {
                return false;
            }
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            if ("acTL".equals(type)) {
                return len >= 8 && readIntBE(data, pos + 8) > 1;
            }
            if ("IDAT".equals(type) || "IEND".equals(type)) {
                return false;
            }
            pos += 12 + len;
        }
        return false;
    }

    @Override
    public DecodedMedia decode(byte[] data, MediaConfig config) throws IOException {
        byte[] ihdrData = null;
        List<byte[]> sharedChunks = new ArrayList<>();
        List<FrameBuilder> rawFrames = new ArrayList<>();
        FrameBuilder currentFrame = null;
        boolean seenIdat = false;
        int expectedFrames = 1;

        int pos = 8;
        while (pos + 12 <= data.length) {
            int len = readIntBE(data, pos);
            if (len < 0 || pos + 12L + len > data.length) {
                break;
            }
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            byte[] chunkData = Arrays.copyOfRange(data, pos + 8, pos + 8 + len);

            switch (type) {
                case "IHDR" -> ihdrData = chunkData;
                case "acTL" -> {
                    if (chunkData.length >= 4) {
                        expectedFrames = Math.max(1, readIntBE(chunkData, 0));
                    }
                }
                case "fcTL" -> {
                    if (currentFrame != null && !currentFrame.idatPayloads.isEmpty()) {
                        rawFrames.add(currentFrame);
                    }
                    if (rawFrames.size() >= config.maxFrames) {
                        pos = data.length;
                        break;
                    }
                    currentFrame = parseFcTL(chunkData, config);
                }
                case "IDAT" -> {
                    seenIdat = true;
                    if (currentFrame != null) {
                        currentFrame.idatPayloads.add(chunkData);
                    }
                }
                case "fdAT" -> {
                    if (currentFrame != null && chunkData.length > 4) {
                        currentFrame.idatPayloads.add(Arrays.copyOfRange(chunkData, 4, chunkData.length));
                    }
                }
                case "IEND" -> {
                    if (currentFrame != null && !currentFrame.idatPayloads.isEmpty()) {
                        rawFrames.add(currentFrame);
                        currentFrame = null;
                    }
                }
                default -> {
                    if (!seenIdat && isSharedAncillaryChunk(type)) {
                        sharedChunks.add(buildRawChunk(type, chunkData));
                    }
                }
            }

            if ("IEND".equals(type)) {
                break;
            }
            pos += 12 + len;
        }

        if (currentFrame != null && !currentFrame.idatPayloads.isEmpty() && rawFrames.size() < config.maxFrames) {
            rawFrames.add(currentFrame);
        }

        if (ihdrData == null || ihdrData.length < 13 || rawFrames.isEmpty()) {
            throw new IOException("Invalid APNG structure");
        }

        int canvasW = readIntBE(ihdrData, 0);
        int canvasH = readIntBE(ihdrData, 4);
        int frameLimit = Math.min(rawFrames.size(), config.maxFrames);
        int[] targetDims = MediaUtils.fitAnimatedDimensions(canvasW, canvasH, Math.min(expectedFrames, frameLimit), config);
        int targetW = targetDims[0];
        int targetH = targetDims[1];
        boolean needsScale = targetW != canvasW || targetH != canvasH;

        BufferedImage canvas = MediaUtils.createRGBAImage(canvasW, canvasH);
        Graphics2D g = canvas.createGraphics();
        g.setBackground(new Color(0, 0, 0, 0));

        List<MediaFrame> frames = new ArrayList<>(frameLimit);
        BufferedImage preview = null;
        boolean anyTranslucency = false;

        try {
            for (int i = 0; i < frameLimit; i++) {
                FrameBuilder fb = rawFrames.get(i);
                byte[] singlePng = buildSingleFramePng(ihdrData, sharedChunks, fb);
                BufferedImage frameImg = ImageIO.read(new ByteArrayInputStream(singlePng));
                if (frameImg == null) {
                    continue;
                }

                BufferedImage previousCanvas = null;
                if (fb.disposeOp == DISPOSE_PREVIOUS) {
                    previousCanvas = MediaUtils.copyImage(canvas);
                }

                if (fb.blendOp == BLEND_SOURCE) {
                    g.setComposite(AlphaComposite.Src);
                } else {
                    g.setComposite(AlphaComposite.SrcOver);
                }
                g.drawImage(frameImg, fb.xOffset, fb.yOffset, null);

                BufferedImage snapshot = needsScale
                        ? MediaUtils.scaleImage(canvas, targetW, targetH)
                        : MediaUtils.copyImage(canvas);

                if (preview == null) {
                    preview = snapshot;
                }

                byte[] rgba = MediaUtils.toRgbaBytes(snapshot);
                if (!anyTranslucency && MediaUtils.detectTranslucency(rgba)) {
                    anyTranslucency = true;
                }
                frames.add(new MediaFrame(rgba, targetW, targetH, fb.delayMs));

                if (fb.disposeOp == DISPOSE_BACKGROUND) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(fb.xOffset, fb.yOffset, fb.width, fb.height);
                } else if (fb.disposeOp == DISPOSE_PREVIOUS && previousCanvas != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(previousCanvas, 0, 0, null);
                }
            }
        } finally {
            g.dispose();
        }

        if (frames.isEmpty()) {
            throw new IOException("No valid frames decoded from APNG");
        }

        DecodedMedia.Format format = frames.size() > 1 ? DecodedMedia.Format.APNG : DecodedMedia.Format.STATIC;
        return new DecodedMedia(format, targetW, targetH, anyTranslucency, preview, frames);
    }

    private static boolean isSharedAncillaryChunk(String type) {
        return "PLTE".equals(type) || "tRNS".equals(type) || "gAMA".equals(type)
                || "cHRM".equals(type) || "sRGB".equals(type) || "iCCP".equals(type);
    }

    private static FrameBuilder parseFcTL(byte[] data, MediaConfig config) throws IOException {
        if (data.length < 26) {
            throw new IOException("Invalid fcTL chunk length: " + data.length);
        }
        int width = readIntBE(data, 4);
        int height = readIntBE(data, 8);
        int xOffset = readIntBE(data, 12);
        int yOffset = readIntBE(data, 16);
        int delayNum = ((data[20] & 0xFF) << 8) | (data[21] & 0xFF);
        int delayDen = ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        if (delayDen == 0) {
            delayDen = 100;
        }
        int delayMs = config.normalizeDelayMs((int) Math.round(delayNum * 1000.0 / delayDen));
        int disposeOp = data[24] & 0xFF;
        int blendOp = data[25] & 0xFF;
        return new FrameBuilder(width, height, xOffset, yOffset, delayMs, disposeOp, blendOp);
    }

    private static byte[] buildSingleFramePng(byte[] baseIhdr, List<byte[]> sharedChunks, FrameBuilder frame) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        out.write(PNG_SIGNATURE);

        byte[] ihdr = Arrays.copyOf(baseIhdr, baseIhdr.length);
        writeIntBE(ihdr, 0, frame.width);
        writeIntBE(ihdr, 4, frame.height);
        out.write(buildRawChunk("IHDR", ihdr));

        for (byte[] chunk : sharedChunks) {
            out.write(chunk);
        }

        for (byte[] idat : frame.idatPayloads) {
            out.write(buildRawChunk("IDAT", idat));
        }

        out.write(buildRawChunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    private static byte[] buildRawChunk(String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buf = ByteBuffer.allocate(12 + data.length);
        buf.putInt(data.length);
        buf.put(typeBytes);
        buf.put(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        buf.putInt((int) crc.getValue());
        return buf.array();
    }

    private static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static void writeIntBE(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >>> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static final class FrameBuilder {
        final int width;
        final int height;
        final int xOffset;
        final int yOffset;
        final int delayMs;
        final int disposeOp;
        final int blendOp;
        final List<byte[]> idatPayloads = new ArrayList<>();

        FrameBuilder(int width, int height, int xOffset, int yOffset, int delayMs, int disposeOp, int blendOp) {
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.delayMs = delayMs;
            this.disposeOp = disposeOp;
            this.blendOp = blendOp;
        }
    }
}
