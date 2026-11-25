package com.nettakrim.signed_paintings;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class SignedPaintingsMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("signed_paintings");

    private static final String BBE_SIGN_RENDERER_MIXIN =
            "com.nettakrim.signed_paintings.mixin.compat.bbe.BBEAbstractSignRendererMixin";
    private static final String BBE_MANAGER_SHOULD_RENDER_MIXIN =
            "com.nettakrim.signed_paintings.mixin.compat.bbe.SpecialBlockEntityManagerMixin";
    private static final String BBE_MANAGER_EXTRACT_MIXIN =
            "com.nettakrim.signed_paintings.mixin.compat.bbe.SpecialBlockEntityManagerExtractMixin";

    private static final String BBE_SHOULD_RENDER_DESC =
            "(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z";
    private static final String BBE_EXTRACT_MANAGED_STATE_DESC =
            "(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("mixin.compat.fabrication.")) {
            return FabricLoader.getInstance().isModLoaded("fabrication");
        }
        if (mixinClassName.contains("mixin.compat.bbe.")) {
            if (!FabricLoader.getInstance().isModLoaded("betterblockentities")) {
                return false;
            }
            return shouldApplyBbeMixin(targetClassName, mixinClassName);
        }
        if (mixinClassName.contains("mixin.compat.sodium")) {
            return FabricLoader.getInstance().isModLoaded("sodium");
        }

        return true;
    }

    private static boolean shouldApplyBbeMixin(String targetClassName, String mixinClassName) {
        if (BBE_SIGN_RENDERER_MIXIN.equals(mixinClassName)) {
            return getClassNode(targetClassName) != null;
        }
        if (BBE_MANAGER_SHOULD_RENDER_MIXIN.equals(mixinClassName)) {
            return hasMethod(targetClassName, "shouldRender", BBE_SHOULD_RENDER_DESC);
        }
        if (BBE_MANAGER_EXTRACT_MIXIN.equals(mixinClassName)) {
            if (hasMethod(targetClassName, "extractManagedState", BBE_EXTRACT_MANAGED_STATE_DESC)) {
                return true;
            }
            if (!hasMethod(targetClassName, "shouldRender", BBE_SHOULD_RENDER_DESC)) {
                LOGGER.warn("Unrecognized BetterBlockEntities SpecialBlockEntityManager signature in {}; skipping BBE manager compatibility mixin.", targetClassName);
            }
            return false;
        }
        return getClassNode(targetClassName) != null;
    }

    private static ClassNode getClassNode(String className) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(
                    className,
                    false,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
        } catch (ClassNotFoundException | IOException e) {
            return null;
        }
    }

    private static boolean hasMethod(String className, String methodName, String methodDesc) {
        ClassNode classNode = getClassNode(className);
        if (classNode == null || classNode.methods == null) {
            return false;
        }
        for (MethodNode method : classNode.methods) {
            if (methodName.equals(method.name) && methodDesc.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
