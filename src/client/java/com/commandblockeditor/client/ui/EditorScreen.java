package com.commandblockeditor.client.ui;

import com.commandblockeditor.network.SaveEditorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class EditorScreen extends Screen {
    private static final int EDITOR_TOP_MARGIN = 34;
    private static final int EDITOR_SIDE_MARGIN = 12;
    private static final int MAX_EDITOR_WIDTH = 860;
    private static final int MAX_EDITOR_HEIGHT = 420;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final BlockPos rootPos;
    private final String initialText;
    private CommandEditBoxWidget editor;

    public EditorScreen(BlockPos rootPos, String initialText) {
        super(Text.literal("Command Block Editor"));
        this.rootPos = rootPos;
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        int totalEditorWidth = getEditorTotalWidth();
        int editorHeight = getEditorHeight();
        int editorX = getEditorX();
        int editorY = EDITOR_TOP_MARGIN;

        this.editor = new CommandEditBoxWidget(
                this.textRenderer,
                editorX,
                editorY,
                totalEditorWidth,
                editorHeight,
                Text.literal("Command text"),
                Text.literal("")
        );
        this.editor.setMaxLength(Integer.MAX_VALUE);
        this.editor.setText(this.initialText);
        this.addDrawableChild(this.editor);
        this.setInitialFocus(this.editor);

        int buttonY = editorY + editorHeight + 8;
        int buttonStartX = (this.width - (BUTTON_WIDTH * 3) - BUTTON_GAP) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            ClientPlayNetworking.send(new SaveEditorPayload(
                    this.rootPos,
                    trimTrailingEmptyLines(this.editor.getText())
            ));
            this.close();
        }).dimensions(buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close())
                .dimensions(buttonStartX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Help"), button -> {
            ClientPlayNetworking.send(new SaveEditorPayload(
                    this.rootPos,
                    trimTrailingEmptyLines(this.editor.getText())
            ));
            assert client != null;
            client.setScreen(new HelpScreen(this.rootPos));
        }).dimensions(buttonStartX + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    protected void setInitialFocus() {
        if (this.editor != null) {
            this.setInitialFocus(this.editor);
        }
    }

    @Override
    public void onDisplayed() {
        if (this.editor != null) {
            this.editor.setFocused(true);
            this.setInitialFocus(this.editor);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Widget now handles its own background for precise scrollbar alignment
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                12,
                0xFFFFFF
        );
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int getEditorTotalWidth() {
        return Math.min(this.width - (EDITOR_SIDE_MARGIN * 2), MAX_EDITOR_WIDTH);
    }

    private int getEditorHeight() {
        return Math.min(this.height - EDITOR_TOP_MARGIN - BUTTON_HEIGHT - 28, MAX_EDITOR_HEIGHT);
    }

    private int getEditorX() {
        return (this.width - getEditorTotalWidth()) / 2;
    }

    private static String trimTrailingEmptyLines(String text) {
        String trimmed = text;
        while (trimmed.endsWith("\n")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
