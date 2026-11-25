package com.nettakrim.signed_paintings.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.nettakrim.signed_paintings.SignedPaintingsClient;
import com.nettakrim.signed_paintings.util.media.DecodedMedia;
import com.nettakrim.signed_paintings.util.media.MediaFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ImageData {
    private BufferedImage baseImage;
    private Identifier baseIdentifier;
    private Identifier workingIdentifier;
    private final ConcurrentHashMap<Vector2i, VariantData> images = new ConcurrentHashMap<>();
    private final Set<Identifier> loadingImages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private List<MediaFrame> animatedFrames;
    private int totalDurationMs = 0;
    private int currentUploadedFrame = -1;
    private volatile int lastAnimatedRenderTime = -1;

    public boolean ready = false;
    public boolean needsReload = false;

    public int width;
    public int height;

    private int workingWidth;
    private int workingHeight;
    private int workingRenderTime = -1;

    private int expiredAllAt = -1;

    private final Map<Identifier, Set<BlockEntity>> sectionUpdateListeners = new ConcurrentHashMap<>();
    private static final Set<Long> pendingSectionUpdates = ConcurrentHashMap.newKeySet();
    private static final int SECTION_UPDATES_PER_TICK = 5;

    public ImageData() {
    }

    public static void requestSectionUpdate(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getLevel() == null || blockEntity.isRemoved()) {
            return;
        }
        pendingSectionUpdates.add(SectionPos.asLong(blockEntity.getBlockPos()));
    }

    public static void tickDrainPendingSectionUpdates() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            pendingSectionUpdates.clear();
            return;
        }
        int remaining = SECTION_UPDATES_PER_TICK;
        Iterator<Long> it = pendingSectionUpdates.iterator();
        while (remaining-- > 0 && it.hasNext()) {
            long sectionLong = it.next();
            it.remove();
            BlockPos origin = SectionPos.of(sectionLong).origin();
            BlockState state = level.getBlockState(origin);
            level.sendBlockUpdated(origin, state, state, 3);
        }
    }

    public void addSectionUpdateListener(Identifier identifier, BlockEntity blockEntity) {
        if (identifier == null) return;
        sectionUpdateListeners
                .computeIfAbsent(identifier, _ -> Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>())))
                .add(blockEntity);
    }

    public void pruneSectionUpdateListeners() {
        sectionUpdateListeners.values().removeIf(Set::isEmpty);
    }

    public void removeSectionUpdateListener(Identifier identifier, BlockEntity blockEntity) {
        if (identifier == null) return;
        Set<BlockEntity> set = sectionUpdateListeners.get(identifier);
        if (set != null) {
            set.remove(blockEntity);
            if (set.isEmpty()) {
                sectionUpdateListeners.remove(identifier);
            }
        }
    }

    public void notifySectionUpdateListeners(Identifier identifier) {
        if (identifier == null) return;
        Set<BlockEntity> listeners = sectionUpdateListeners.get(identifier);
        if (listeners == null) return;

        synchronized (listeners) {
            for (BlockEntity blockEntity : listeners) {
                requestSectionUpdate(blockEntity);
            }
        }
    }

    public void onImageReady(BufferedImage image, Identifier baseIdentifier) {
        this.baseImage = image;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.baseIdentifier = baseIdentifier;
        this.workingIdentifier = baseIdentifier.withSuffix("_working");
        this.ready = true;
        notifySectionUpdateListeners(baseIdentifier);
    }

    public void onMediaReady(DecodedMedia media, Identifier baseIdentifier) {
        this.baseImage = media.previewImage;
        this.width = media.width;
        this.height = media.height;
        this.baseIdentifier = baseIdentifier;
        this.workingIdentifier = baseIdentifier.withSuffix("_working");

        if (SignedPaintingsClient.imageManager != null) {
            SignedPaintingsClient.imageManager.cacheTranslucency(baseIdentifier, media.hasTranslucency);
        }

        if (media.isAnimated()) {
            this.animatedFrames = media.frames;
            this.totalDurationMs = media.totalDurationMs;
            this.currentUploadedFrame = 0;
            this.lastAnimatedRenderTime = SignedPaintingsClient.imageManager != null
                    ? SignedPaintingsClient.imageManager.renderTime : 0;

            MediaFrame first = media.frames.getFirst();
            ImageManager.saveRgbaBytesAsIdentifier(first.rgbaBytes, media.width, media.height, baseIdentifier);
            images.put(new Vector2i(width, height), new VariantData(baseIdentifier));
        } else {
            this.animatedFrames = null;
            this.totalDurationMs = 0;
            this.currentUploadedFrame = -1;
        }

        this.ready = true;
        notifySectionUpdateListeners(baseIdentifier);
    }

    public boolean isAnimated() {
        return animatedFrames != null && animatedFrames.size() > 1;
    }

    /**
     * Advances and uploads the current animation frame to the single GPU DynamicTexture if needed.
     * Skips uploads when off-screen (not rendered recently) to save CPU/GPU bandwidth.
     */
    public void tickAnimation(int currentRenderTime, boolean pauseOffscreen) {
        if (!ready || !isAnimated() || baseIdentifier == null) {
            return;
        }

        if (pauseOffscreen && (currentRenderTime - lastAnimatedRenderTime) > 5) {
            return;
        }

        int frameIndex = computeCurrentFrameIndex();
        if (frameIndex == currentUploadedFrame) {
            return;
        }

        AbstractTexture tex = ImageManager.getTexture(baseIdentifier);
        if (!(tex instanceof DynamicTexture dynamicTexture)) {
            return;
        }

        NativeImage pixels = dynamicTexture.getPixels();
        if (pixels == null) {
            return;
        }

        MediaFrame frame = animatedFrames.get(frameIndex);
        if (frame.width != pixels.getWidth() || frame.height != pixels.getHeight()) {
            return;
        }

        ByteBuffer buf = MemoryUtil.memByteBuffer(pixels.getPointer(),
                pixels.getWidth() * pixels.getHeight() * pixels.format().components());
        buf.put(frame.rgbaBytes);
        dynamicTexture.upload();
        currentUploadedFrame = frameIndex;
    }

    private int computeCurrentFrameIndex() {
        int duration = Math.max(totalDurationMs, 50);
        int elapsed = (int) (System.currentTimeMillis() % duration);
        int acc = 0;
        for (int i = 0; i < animatedFrames.size(); i++) {
            acc += animatedFrames.get(i).delayMs;
            if (elapsed < acc) {
                return i;
            }
        }
        return 0;
    }

    public Identifier getBaseIdentifier() {
        return getIdentifier(width, height, false);
    }

    public Identifier getIdentifier(int width, int height, boolean working) {
        expiredAllAt = -1;

        if (isAnimated() && ready && baseIdentifier != null) {
            int now = SignedPaintingsClient.imageManager.renderTime;
            lastAnimatedRenderTime = now;
            if (working) {
                workingRenderTime = now;
            } else {
                VariantData variantData = images.get(new Vector2i(this.width, this.height));
                if (variantData != null) {
                    variantData.renderTime = now;
                }
            }
            return baseIdentifier;
        }

        Vector2i resolution = new Vector2i(width, height);
        VariantData variantData = images.get(resolution);
        if (variantData != null) {
            variantData.renderTime = SignedPaintingsClient.imageManager.renderTime;
            return variantData.identifier;
        }

        if (working) {
            if (workingIdentifier == null) return null;
            workingRenderTime = SignedPaintingsClient.imageManager.renderTime;
            if (width != workingWidth || height != workingHeight) {
                workingWidth = width;
                workingHeight = height;
                ImageManager.saveBufferedImageAsIdentifier(ImageManager.scaleImage(baseImage, width, height), workingIdentifier);
            }
            return workingIdentifier;
        } else {
            if (baseIdentifier == null) return null;
            Identifier identifier;
            BufferedImage bufferedImage;

            if (width == this.width && height == this.height) {
                identifier = baseIdentifier;
                bufferedImage = baseImage;
            } else {
                identifier = baseIdentifier.withSuffix("_" + width + "x" + height);
                bufferedImage = ImageManager.scaleImage(baseImage, width, height);
            }

            if (!loadingImages.add(identifier)) {
                return identifier;
            }

            ImageManager.saveBufferedImageAsIdentifierAsync(bufferedImage, identifier).handleAsync((v, e) -> {
                if (e != null) {
                    loadingImages.remove(identifier);
                    return null;
                }
                images.put(resolution, new VariantData(identifier));
                loadingImages.remove(identifier);
                notifySectionUpdateListeners(identifier);
                return null;
            });

            return identifier;
        }
    }

    public int reload() {
        ready = false;

        int i = 0;
        if (workingIdentifier != null && ImageManager.hasImage(workingIdentifier)) {
            i++;
            ImageManager.removeImage(workingIdentifier);
        }
        for (VariantData variantData : images.values()) {
            ImageManager.removeImage(variantData.identifier);
            i++;
        }
        if (baseIdentifier != null && ImageManager.hasImage(baseIdentifier)) {
            ImageManager.removeImage(baseIdentifier);
        }

        animatedFrames = null;
        currentUploadedFrame = -1;
        baseImage = null;

        needsReload = true;
        images.clear();
        return i;
    }

    public ImageStatus getStatus() {
        ImageStatus imageStatus = new ImageStatus();
        images.forEach((key, value) -> {
            AbstractTexture tex = ImageManager.getTexture(value.identifier);
            if (tex instanceof DynamicTexture dt && dt.getPixels() != null) {
                imageStatus.addResolution(key, getBytes(dt.getPixels()), !value.identifier.equals(baseIdentifier));
            }
        });
        imageStatus.ready = ready;
        return imageStatus;
    }

    private long getBytes(NativeImage image) {
        long bytesPerPixel = image.format().components();
        return (long) image.getWidth() * image.getHeight() * bytesPerPixel;
    }

    public boolean checkRenderTime(int expireVram, int expireFully) {
        if (!ready) {
            return false;
        }

        for (Iterator<VariantData> iterator = images.values().iterator(); iterator.hasNext(); ) {
            VariantData variantData = iterator.next();
            if (variantData.renderTime < expireVram) {
                SignedPaintingsClient.info("removing expired image variant " + variantData.identifier, false);
                ImageManager.removeImage(variantData.identifier);
                iterator.remove();
            }
        }

        if (workingRenderTime != -1 && workingRenderTime < expireVram) {
            SignedPaintingsClient.info("removing expired image variant " + workingIdentifier, false);
            if (workingIdentifier != null) {
                ImageManager.removeImage(workingIdentifier);
            }
            workingRenderTime = -1;
        }

        if (images.isEmpty() && workingRenderTime == -1) {
            if (expiredAllAt == -1) {
                expiredAllAt = SignedPaintingsClient.imageManager.renderTime;
            }

            if (expiredAllAt < expireFully) {
                SignedPaintingsClient.info("reloading fully expired image " + baseIdentifier, false);
                reload();
                return true;
            }
        }
        return false;
    }

    private static class VariantData {
        public Identifier identifier;
        public int renderTime;

        public VariantData(Identifier identifier) {
            this.identifier = identifier;
            renderTime = SignedPaintingsClient.imageManager.renderTime;
        }
    }
}
