package com.commandblockeditor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record RequestEditorPayload(BlockPos pos) implements CustomPayload {
    public static final Id<RequestEditorPayload> ID =
            new Id<>(Identifier.of("commandblockeditor", "request_editor"));

    public static final PacketCodec<RegistryByteBuf, RequestEditorPayload> CODEC =
            CustomPayload.codecOf(RequestEditorPayload::write, RequestEditorPayload::new);

    private RequestEditorPayload(RegistryByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
