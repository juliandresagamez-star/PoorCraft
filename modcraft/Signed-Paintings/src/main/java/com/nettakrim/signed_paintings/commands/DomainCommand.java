package com.nettakrim.signed_paintings.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.nettakrim.signed_paintings.SignedPaintingsClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Domain trust commands are retained for backwards compatibility but
 * are now no-ops. All valid HTTPS URLs are automatically trusted;
 * HTTP is rejected at the security-validator level.
 */
public class DomainCommand {

    public static LiteralCommandNode<FabricClientCommandSource> getCommandNode() {
        LiteralCommandNode<FabricClientCommandSource> domainNode = ClientCommands
                .literal("paintings:domain")
                .executes(DomainCommand::info)
                .build();

        LiteralCommandNode<FabricClientCommandSource> trustNode = ClientCommands
                .literal("trust")
                .executes(DomainCommand::info)
                .build();

        LiteralCommandNode<FabricClientCommandSource> untrustNode = ClientCommands
                .literal("untrust")
                .executes(DomainCommand::info)
                .build();

        LiteralCommandNode<FabricClientCommandSource> listNode = ClientCommands
                .literal("list")
                .executes(DomainCommand::info)
                .build();

        domainNode.addChild(trustNode);
        domainNode.addChild(untrustNode);
        domainNode.addChild(listNode);
        return domainNode;
    }

    private static int info(CommandContext<FabricClientCommandSource> context) {
        SignedPaintingsClient.sayRaw(
                Component.literal("§6[Signed Paintings]§r All valid HTTPS URLs are now automatically trusted. " +
                        "HTTP is rejected. The domain trust/untrust system has been removed for security.")
        );
        return 1;
    }
}
