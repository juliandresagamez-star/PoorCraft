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
    private volatile BufferedImage baseImage;
    private volatile Identifier baseIdentifier;
    private volatile Identifier workingIdentifier;
    private final ConcurrentHashMap<Vector2i, VariantData> images = new ConcurrentHashMap<>();
    private final Set<Identifier> loadingImages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile List<MediaFrame> animatedFrames;
    private int totalDurationMs = 0;
    private volatile int currentUploadedFrame = -1;
    private volatile int lastAnimatedRenderTime = -1;

    public volatile boolean ready = false;
    public volatile boolean needsReload = false;

    public int width;
    public int height;

    private volatile int workingWidth;
    private volatile int workingHeight;
    private volatile int workingRenderTime = -1;

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
        List<MediaFrame> frames = animatedFrames;
        return frames != null && frames.size() > 1;
    }

    /**
     * Advances and uploads the current animation frame to the single GPU DynamicTexture if needed.
     * Skips uploads when off-screen (not rendered recently) to save CPU/GPU bandwidth.
     */
    public void tickAnimation(int currentRenderTime, boolean pauseOffscreen) {
        List<MediaFrame> frames = this.animatedFrames;
        Identifier targetId = this.baseIdentifier;
        if (!ready || frames == null || frames.size() <= 1 || targetId == null) {
            return;
        }

        if (pauseOffscreen && (currentRenderTime - lastAnimatedRenderTime) > 5) {
            return;
        }

        int frameIndex = computeCurrentFrameIndex(frames);
        if (frameIndex == currentUploadedFrame) {
            return;
        }

        AbstractTexture tex = ImageManager.getTexture(targetId);
        if (!(tex instanceof DynamicTexture dynamicTexture)) {
            return;
        }

        NativeImage pixels = dynamicTexture.getPixels();
        if (pixels == null) {
            return;
        }

        MediaFrame frame = frames.get(frameIndex);
        if (frame.width != pixels.getWidth() || frame.height != pixels.getHeight()) {
            return;
        }

        ByteBuffer buf = MemoryUtil.memByteBuffer(pixels.getPointer(),
                pixels.getWidth() * pixels.getHeight() * pixels.format().components());
        buf.put(frame.rgbaBytes);
        dynamicTexture.upload();
        currentUploadedFrame = frameIndex;
    }

    private int computeCurrentFrameIndex(List<MediaFrame> frames) {
        int duration = Math.max(totalDurationMs, 50);
        int elapsed = (int) (System.currentTimeMillis() % duration);
        int acc = 0;
        for (int i = 0; i < frames.size(); i++) {
            acc += frames.get(i).delayMs;
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
        if (!ready) {
            return null;
        }

        expiredAllAt = -1;

        if (isAnimated() && baseIdentifier != null) {
            int now = SignedPaintingsClient.imageManager.renderTime;
            lastAnimatedRenderTime = now;
            if (working) {
                workingRenderTime = now;
            }

            Vector2i resolution = new Vector2i(this.width, this.height);
            VariantData variantData = images.get(resolution);
            Identifier targetId = this.baseIdentifier;
            if (variantData != null) {
                if (ImageManager.hasImage(targetId)) {
                    variantData.renderTime = now;
                    return targetId;
                }
                images.remove(resolution, variantData);
            }

            List<MediaFrame> frames = this.animatedFrames;
            if (frames != null && !frames.isEmpty() && targetId != null && loadingImages.add(targetId)) {
                int frameIndex = computeCurrentFrameIndex(frames);
                MediaFrame frame = frames.get(frameIndex);
                ImageManager.saveRgbaBytesAsIdentifierAsync(frame.rgbaBytes, frame.width, frame.height, targetId)
                        .handleAsync((v, e) -> {
                            if (e != null) {
                                loadingImages.remove(targetId);
                                return null;
                            }
                            if (!ready) {
                                ImageManager.removeImage(targetId);
                                loadingImages.remove(targetId);
                                return null;
                            }
                            currentUploadedFrame = frameIndex;
                            images.put(resolution, new VariantData(targetId));
                            loadingImages.remove(targetId);
                            notifySectionUpdateListeners(targetId);
                            return null;
                        });
            }
            return targetId;
        }

        Vector2i resolution = new Vector2i(width, height);
        VariantData variantData = images.get(resolution);
        if (variantData != null) {
            if (ImageManager.hasImage(variantData.identifier)) {
                variantData.renderTime = SignedPaintingsClient.imageManager.renderTime;
                return variantData.identifier;
            }
            images.remove(resolution, variantData);
        }

        BufferedImage currentBase = this.baseImage;
        if (currentBase == null) {
            return null;
        }

        if (working) {
            Identifier currentWorkingId = this.workingIdentifier;
            if (currentWorkingId == null) return null;
            workingRenderTime = SignedPaintingsClient.imageManager.renderTime;
            if (width != workingWidth || height != workingHeight || !ImageManager.hasImage(currentWorkingId)) {
                if (Minecraft.getInstance().isSameThread()) {
                    workingWidth = width;
                    workingHeight = height;
                    ImageManager.saveBufferedImageAsIdentifier(ImageManager.scaleImage(currentBase, width, height), currentWorkingId);
                } else if (loadingImages.add(currentWorkingId)) {
                    BufferedImage scaled = ImageManager.scaleImage(currentBase, width, height);
                    ImageManager.saveBufferedImageAsIdentifierAsync(scaled, currentWorkingId).handleAsync((v, e) -> {
                        if (e != null || !ready) {
                            if (!ready) {
                                ImageManager.removeImage(currentWorkingId);
                            }
                            loadingImages.remove(currentWorkingId);
                            return null;
                        }
                        workingWidth = width;
                        workingHeight = height;
                        loadingImages.remove(currentWorkingId);
                        notifySectionUpdateListeners(currentWorkingId);
                        return null;
                    });
                }
            }
            return currentWorkingId;
        } else {
            Identifier currentBaseId = this.baseIdentifier;
            if (currentBaseId == null) return null;
            Identifier identifier = (width == this.width && height == this.height)
                    ? currentBaseId
                    : currentBaseId.withSuffix("_" + width + "x" + height);

            if (!loadingImages.add(identifier)) {
                return identifier;
            }

            BufferedImage bufferedImage = (width == this.width && height == this.height)
                    ? currentBase
                    : ImageManager.scaleImage(currentBase, width, height);

            ImageManager.saveBufferedImageAsIdentifierAsync(bufferedImage, identifier).handleAsync((v, e) -> {
                if (e != null) {
                    loadingImages.remove(identifier);
                    return null;
                }
                if (!ready) {
                    ImageManager.removeImage(identifier);
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

        workingWidth = 0;
        workingHeight = 0;
        workingRenderTime = -1;
        loadingImages.clear();

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
                if (isAnimated() && variantData.identifier.equals(baseIdentifier)) {
                    currentUploadedFrame = -1;
                }
            }
        }

        if (workingRenderTime != -1 && workingRenderTime < expireVram) {
            SignedPaintingsClient.info("removing expired image variant " + workingIdentifier, false);
            if (workingIdentifier != null) {
                ImageManager.removeImage(workingIdentifier);
            }
            workingWidth = 0;
            workingHeight = 0;
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
