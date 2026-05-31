package com.commandblockeditor.client.ui;

import com.commandblockeditor.network.RequestEditorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class HelpScreen extends Screen {

    private final BlockPos rootPos;
    private HelpListWidget list;

    static final List<String> TEXT = List.of(
            "§eSyntax",
            "§b<number>§r - repeat command",
            "§b!§r - repeating command block (first command only)",
            "§b@§r - toggles auto mode",
            "   first command: enables auto",
            "   other commands: disables auto",
            "§b?§r - conditional command block",
            "§b`...`§r - expression",
            "    supports + - * / ( ) and i (current index)",
            "",
            "§eExample",
            "5: say `i+1`",
            "say hi",
            "?: say hi",
            "",
            "§eOutputs:",
            "say 1",
            "say 2",
            "say 3",
            "say 4",
            "say 5",
            "say hi",
            "say hi (conditional)"
    );

    public HelpScreen(BlockPos rootPos) {
        super(Text.literal("Command Block Editor Help"));
        this.rootPos = rootPos;
    }

    @Override
    protected void init() {

        list = new HelpListWidget(
                client,
                width,
                height,
                35,
                12
        );

        addSelectableChild(list);

        addDrawableChild(
                ButtonWidget.builder(
                                Text.literal("Back"),
                                b -> {
                                    assert client != null;
                                    ClientPlayNetworking.send(new RequestEditorPayload(rootPos));
                                }
                        )
                        .dimensions(8, 8, 90, 20)
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        list.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                textRenderer,
                title,
                width / 2,
                15,
                0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}