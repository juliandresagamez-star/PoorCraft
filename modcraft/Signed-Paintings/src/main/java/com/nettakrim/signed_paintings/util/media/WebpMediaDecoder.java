package com.nettakrim.signed_paintings.util.media;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes static and animated WebP images (RIFF/VP8X/ANIM/ANMF) using TwelveMonkeys imageio-webp
 * for individual VP8/VP8L/ALPH frames and a pure-Java container/compositor for animation.
 */
public class WebpMediaDecoder implements MediaDecoder {
    @Override
    public String getFormatName() {
        return "webp";
    }

    @Override
    public boolean canDecode(byte[] data) {
        return data != null && data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    @Override
    public DecodedMedia decode(byte[] data, MediaConfig config) throws IOException {
        int canvasW = 0;
        int canvasH = 0;
        boolean isAnimated = false;
        List<AnmfFrame> anmfFrames = new ArrayList<>();

        int pos = 12;
        while (pos + 8 <= data.length) {
            String fourcc = new String(data, pos, 4, StandardCharsets.US_ASCII);
            int chunkSize = readIntLE(data, pos + 4);
            if (chunkSize < 0 || pos + 8L + chunkSize > data.length) {
                break;
            }
            int payloadStart = pos + 8;

            if ("VP8X".equals(fourcc) && chunkSize >= 10) {
                int flags = data[payloadStart] & 0xFF;
                isAnimated = (flags & 0x02) != 0;
                canvasW = readUInt24LE(data, payloadStart + 4) + 1;
                canvasH = readUInt24LE(data, payloadStart + 7) + 1;
            } else if ("ANIM".equals(fourcc)) {
                isAnimated = true;
            } else if ("ANMF".equals(fourcc) && chunkSize > 16) {
                isAnimated = true;
                if (anmfFrames.size() < config.maxFrames) {
                    int fx = readUInt24LE(data, payloadStart) * 2;
                    int fy = readUInt24LE(data, payloadStart + 3) * 2;
                    int fw = readUInt24LE(data, payloadStart + 6) + 1;
                    int fh = readUInt24LE(data, payloadStart + 9) + 1;
                    int duration = config.normalizeDelayMs(readUInt24LE(data, payloadStart + 12));
                    int flags = data[payloadStart + 15] & 0xFF;
                    boolean disposeToBg = (flags & 0x01) != 0;
                    boolean noBlend = (flags & 0x02) != 0;
                    byte[] subChunks = Arrays.copyOfRange(data, payloadStart + 16, payloadStart + chunkSize);
                    anmfFrames.add(new AnmfFrame(fx, fy, fw, fh, duration, disposeToBg, noBlend, subChunks));
                }
            }

            // RIFF chunks are padded to even byte boundaries
            pos = payloadStart + chunkSize + (chunkSize & 1);
        }

        if (!isAnimated || anmfFrames.isEmpty()) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) {
                throw new IOException("Failed to decode static WebP image");
            }
            int[] dims = MediaUtils.fitWithinMaxDimension(img.getWidth(), img.getHeight(), config.maxStaticDimension);
            BufferedImage scaled = (dims[0] != img.getWidth() || dims[1] != img.getHeight())
                    ? MediaUtils.scaleImage(img, dims[0], dims[1])
                    : MediaUtils.ensureRGBA(img);
            byte[] rgba = MediaUtils.toRgbaBytes(scaled);
            boolean translucent = MediaUtils.detectTranslucency(rgba);
            return new DecodedMedia(DecodedMedia.Format.WEBP, scaled.getWidth(), scaled.getHeight(), translucent, scaled,
                    List.of(new MediaFrame(rgba, scaled.getWidth(), scaled.getHeight(), 0)));
        }

        if (canvasW <= 0 || canvasH <= 0) {
            canvasW = anmfFrames.getFirst().width;
            canvasH = anmfFrames.getFirst().height;
        }

        int[] targetDims = MediaUtils.fitAnimatedDimensions(canvasW, canvasH, anmfFrames.size(), config);
        int targetW = targetDims[0];
        int targetH = targetDims[1];
        boolean needsScale = targetW != canvasW || targetH != canvasH;

        BufferedImage canvas = MediaUtils.createRGBAImage(canvasW, canvasH);
        Graphics2D g = canvas.createGraphics();
        g.setBackground(new Color(0, 0, 0, 0));

        List<MediaFrame> frames = new ArrayList<>(anmfFrames.size());
        BufferedImage preview = null;
        boolean anyTranslucency = false;

        try {
            for (AnmfFrame af : anmfFrames) {
                byte[] singleWebp = wrapAsSingleWebp(af);
                BufferedImage frameImg = ImageIO.read(new ByteArrayInputStream(singleWebp));
                if (frameImg == null) {
                    continue;
                }

                g.setComposite(af.noBlend ? AlphaComposite.Src : AlphaComposite.SrcOver);
                g.drawImage(frameImg, af.x, af.y, null);

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
                frames.add(new MediaFrame(rgba, targetW, targetH, af.durationMs));

                if (af.disposeToBg) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(af.x, af.y, af.width, af.height);
                }
            }
        } finally {
            g.dispose();
        }

        if (frames.isEmpty()) {
            throw new IOException("No valid frames decoded from animated WebP");
        }

        DecodedMedia.Format format = frames.size() > 1 ? DecodedMedia.Format.WEBP : DecodedMedia.Format.STATIC;
        return new DecodedMedia(format, targetW, targetH, anyTranslucency, preview, frames);
    }

    private static byte[] wrapAsSingleWebp(AnmfFrame frame) throws IOException {
        boolean hasAlpha = containsFourcc(frame.subChunks, "ALPH");
        ByteArrayOutputStream body = new ByteArrayOutputStream(frame.subChunks.length + 24);
        body.write(new byte[]{'W', 'E', 'B', 'P'});

        if (hasAlpha) {
            body.write(new byte[]{'V', 'P', '8', 'X'});
            writeIntLE(body, 10);
            body.write(0x10); // Alpha flag
            body.write(0);
            body.write(0);
            body.write(0);
            writeUInt24LE(body, frame.width - 1);
            writeUInt24LE(body, frame.height - 1);
        }

        body.write(frame.subChunks);
        byte[] bodyBytes = body.toByteArray();

        ByteArrayOutputStream riff = new ByteArrayOutputStream(8 + bodyBytes.length);
        riff.write(new byte[]{'R', 'I', 'F', 'F'});
        writeIntLE(riff, bodyBytes.length);
        riff.write(bodyBytes);
        return riff.toByteArray();
    }

    private static boolean containsFourcc(byte[] subChunks, String target) {
        int pos = 0;
        while (pos + 8 <= subChunks.length) {
            String fourcc = new String(subChunks, pos, 4, StandardCharsets.US_ASCII);
            if (target.equals(fourcc)) {
                return true;
            }
            int size = readIntLE(subChunks, pos + 4);
            if (size < 0) break;
            pos += 8 + size + (size & 1);
        }
        return false;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readUInt24LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeUInt24LE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
    }

    private static final class AnmfFrame {
        final int x, y, width, height, durationMs;
        final boolean disposeToBg, noBlend;
        final byte[] subChunks;

        AnmfFrame(int x, int y, int width, int height, int durationMs, boolean disposeToBg, boolean noBlend, byte[] subChunks) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
            this.disposeToBg = disposeToBg;
            this.noBlend = noBlend;
            this.subChunks = subChunks;
        }
    }
}
