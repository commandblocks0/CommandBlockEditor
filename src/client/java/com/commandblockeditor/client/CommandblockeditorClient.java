package com.commandblockeditor.client;

import com.commandblockeditor.client.ui.EditorScreen;
import com.commandblockeditor.network.OpenEditorPayload;
import com.commandblockeditor.network.RequestEditorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.CommandBlock;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class CommandblockeditorClient implements ClientModInitializer {

    public static KeyBinding OpenEditorKey;
    private static final int KEY_EQUAL = 61;

    @Override
    public void onInitializeClient() {

        OpenEditorKey =
                KeyBindingHelper.registerKeyBinding(
                        new KeyBinding(
                                "key.commandblockeditor.open_editor",
                                InputUtil.Type.KEYSYM,
                                KEY_EQUAL,
                                "category.commandblockeditor.keys"
                        )
                );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (OpenEditorKey.wasPressed()) {
                if (client.player == null) {
                    continue;
                }

                BlockPos pos = client.player.getBlockPos();

                if (client.crosshairTarget instanceof BlockHitResult hit
                        && client.world != null
                        && client.world.getBlockState(hit.getBlockPos()).getBlock() instanceof CommandBlock) {
                    pos = hit.getBlockPos();
                }

                ClientPlayNetworking.send(new RequestEditorPayload(pos));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreen(new EditorScreen(payload.rootPos(),payload.editorText()))
                )
        );
    }
}
