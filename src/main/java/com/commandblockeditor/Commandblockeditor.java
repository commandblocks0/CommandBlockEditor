package com.commandblockeditor;

import com.commandblockeditor.network.OpenEditorPayload;
import com.commandblockeditor.network.RequestEditorPayload;
import com.commandblockeditor.network.SaveEditorPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class Commandblockeditor
        implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(RequestEditorPayload.ID, RequestEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveEditorPayload.ID, SaveEditorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.ID, OpenEditorPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestEditorPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (!context.player().isCreativeLevelTwoOp()) {
                        context.player().sendMessage(Text.literal("You need operator permissions to edit command blocks.").formatted(Formatting.RED), true);
                        return;
                    }

                    var rootPos = CommandBlockChainService.findRoot(context.player().getServerWorld(), payload.pos());
                    List<String> editorLines = CommandParser.toEditor(
                            CommandBlockChainService.read(context.player().getServerWorld(), rootPos)
                    );
                    ServerPlayNetworking.send(
                            context.player(),
                            new OpenEditorPayload(rootPos, String.join("\n", editorLines))
                    );
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SaveEditorPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (!context.player().isCreativeLevelTwoOp()) {
                        context.player().sendMessage(Text.literal("You need operator permissions to edit command blocks.").formatted(Formatting.RED), true);
                        return;
                    }

                    CommandBlockChainService.write(context.player(), payload.rootPos(), payload.editorText());
                })
        );
    }
}
