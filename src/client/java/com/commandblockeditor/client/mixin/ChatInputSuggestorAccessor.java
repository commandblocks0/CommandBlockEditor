package com.commandblockeditor.client.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {
    @Invoker("highlight")
    static OrderedText invokeHighlight(ParseResults<ClientCommandSource> parse, String original, int firstCharacterIndex) {
        throw new AssertionError();
    }
}
