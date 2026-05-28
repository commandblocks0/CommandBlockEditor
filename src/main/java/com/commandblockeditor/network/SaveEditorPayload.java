package com.commandblockeditor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record SaveEditorPayload(BlockPos rootPos, String editorText) implements CustomPayload {
    public static final Id<SaveEditorPayload> ID =
            new Id<>(Identifier.of("commandblockeditor", "save_editor"));

    public static final PacketCodec<RegistryByteBuf, SaveEditorPayload> CODEC =
            CustomPayload.codecOf(SaveEditorPayload::write, SaveEditorPayload::new);

    private SaveEditorPayload(RegistryByteBuf buf) {
        this(buf.readBlockPos(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(this.rootPos);
        buf.writeString(this.editorText);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
