package com.commandblockeditor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record OpenEditorPayload(BlockPos rootPos, String editorText) implements CustomPayload {
    public static final Id<OpenEditorPayload> ID =
            new Id<>(Identifier.of("commandblockeditor", "open_editor"));

    public static final PacketCodec<RegistryByteBuf, OpenEditorPayload> CODEC =
            CustomPayload.codecOf(OpenEditorPayload::write, OpenEditorPayload::new);

    private OpenEditorPayload(RegistryByteBuf buf) {
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
