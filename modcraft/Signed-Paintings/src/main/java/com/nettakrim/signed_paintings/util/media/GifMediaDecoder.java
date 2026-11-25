package com.nettakrim.signed_paintings.util.media;

import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodes animated and static GIF images using ImageIO metadata and proper frame disposal.
 */
public class GifMediaDecoder implements MediaDecoder {
    @Override
    public String getFormatName() {
        return "gif";
    }

    @Override
    public boolean canDecode(byte[] data) {
        return data != null && data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    @Override
    public DecodedMedia decode(byte[] data, MediaConfig config) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(stream, false, false);
            int numImages = reader.getNumImages(true);
            if (numImages <= 0) {
                throw new IOException("GIF contains no frames");
            }

            int canvasW = reader.getWidth(0);
            int canvasH = reader.getHeight(0);
            try {
                IIOMetadata streamMeta = reader.getStreamMetadata();
                if (streamMeta != null) {
                    IIOMetadataNode root = (IIOMetadataNode) streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
                    NodeList lsdList = root.getElementsByTagName("LogicalScreenDescriptor");
                    if (lsdList.getLength() > 0) {
                        IIOMetadataNode lsd = (IIOMetadataNode) lsdList.item(0);
                        int lsw = Integer.parseInt(lsd.getAttribute("logicalScreenWidth"));
                        int lsh = Integer.parseInt(lsd.getAttribute("logicalScreenHeight"));
                        if (lsw > 0 && lsh > 0) {
                            canvasW = lsw;
                            canvasH = lsh;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            int frameLimit = Math.min(numImages, Math.max(1, config.maxFrames));
            int[] targetDims = MediaUtils.fitAnimatedDimensions(canvasW, canvasH, frameLimit, config);
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
                    BufferedImage rawFrame = reader.read(i);
                    if (rawFrame == null) {
                        continue;
                    }

                    int delayMs = config.defaultZeroDelayMs;
                    String disposal = "none";
                    int x = 0;
                    int y = 0;

                    try {
                        IIOMetadata imageMeta = reader.getImageMetadata(i);
                        if (imageMeta != null) {
                            IIOMetadataNode root = (IIOMetadataNode) imageMeta.getAsTree("javax_imageio_gif_image_1.0");
                            NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
                            if (gceList.getLength() > 0) {
                                IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);
                                String delayAttr = gce.getAttribute("delayTime");
                                if (delayAttr != null && !delayAttr.isEmpty()) {
                                    delayMs = Integer.parseInt(delayAttr) * 10;
                                }
                                String dispAttr = gce.getAttribute("disposalMethod");
                                if (dispAttr != null && !dispAttr.isEmpty()) {
                                    disposal = dispAttr;
                                }
                            }
                            NodeList descList = root.getElementsByTagName("ImageDescriptor");
                            if (descList.getLength() > 0) {
                                IIOMetadataNode desc = (IIOMetadataNode) descList.item(0);
                                String left = desc.getAttribute("imageLeftPosition");
                                String top = desc.getAttribute("imageTopPosition");
                                if (left != null && !left.isEmpty()) x = Integer.parseInt(left);
                                if (top != null && !top.isEmpty()) y = Integer.parseInt(top);
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    delayMs = config.normalizeDelayMs(delayMs);

                    BufferedImage previousCanvas = null;
                    if ("restoreToPrevious".equalsIgnoreCase(disposal)) {
                        previousCanvas = MediaUtils.copyImage(canvas);
                    }

                    g.setComposite(AlphaComposite.SrcOver);
                    g.drawImage(rawFrame, x, y, null);

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
                    frames.add(new MediaFrame(rgba, targetW, targetH, delayMs));

                    if ("restoreToBackgroundColor".equalsIgnoreCase(disposal)) {
                        g.setComposite(AlphaComposite.Clear);
                        g.fillRect(x, y, rawFrame.getWidth(), rawFrame.getHeight());
                    } else if ("restoreToPrevious".equalsIgnoreCase(disposal) && previousCanvas != null) {
                        g.setComposite(AlphaComposite.Src);
                        g.drawImage(previousCanvas, 0, 0, null);
                    }
                }
            } finally {
                g.dispose();
            }

            if (frames.isEmpty()) {
                throw new IOException("No valid frames decoded from GIF");
            }

            DecodedMedia.Format format = frames.size() > 1 ? DecodedMedia.Format.GIF : DecodedMedia.Format.STATIC;
            return new DecodedMedia(format, targetW, targetH, anyTranslucency, preview, frames);
        } finally {
            reader.dispose();
        }
    }
}
