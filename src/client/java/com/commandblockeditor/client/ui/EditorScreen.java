package com.commandblockeditor.client.ui;

import com.commandblockeditor.network.SaveEditorPayload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class EditorScreen extends BaseOwoScreen<FlowLayout> {

    private final BlockPos rootPos;
    private final String initialText;
    private EditorComponent editor;

    public EditorScreen(BlockPos rootPos, String initialText) {
        this.rootPos = rootPos;
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        super.init();

        if (this.editor != null) {
            this.editor.focusHandler().focus(this.editor, Component.FocusSource.MOUSE_CLICK);
        }
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.blur(3, 5))
                .verticalAlignment(VerticalAlignment.CENTER)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        var mainPanel = Containers.verticalFlow(Sizing.fill(90), Sizing.fill(90));
        mainPanel.surface(Surface.DARK_PANEL);
        mainPanel.padding(Insets.of(10));
        
        mainPanel.child(Components.label(Text.literal("Command Block Editor"))
                        .shadow(true)
                        .margins(Insets.bottom(10))
                        .horizontalSizing(Sizing.content()));

        this.editor = new EditorComponent();
        this.editor.setText(this.initialText);
        this.editor.horizontalSizing(Sizing.fill(100));
        this.editor.verticalSizing(Sizing.fill(100));
        
        var editorContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        editorContainer.surface(Surface.outline(0xFF404040));
        editorContainer.padding(Insets.of(1));
        editorContainer.child(this.editor);

        mainPanel.child(editorContainer);

        var buttonRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.top(10));

        buttonRow.child(Components.button(Text.literal("Save"), button -> {
            ClientPlayNetworking.send(new SaveEditorPayload(this.rootPos, trimTrailingEmptyLines(this.editor.getText())));
            this.close();
        }).sizing(Sizing.fixed(80), Sizing.fixed(20)));

        buttonRow.child(Components.button(Text.literal("Cancel"), button -> this.close())
                .sizing(Sizing.fixed(80), Sizing.fixed(20))
                .margins(Insets.left(8)));

        buttonRow.child(Components.button(Text.literal("Help"), button -> {
            ClientPlayNetworking.send(new SaveEditorPayload(this.rootPos, trimTrailingEmptyLines(this.editor.getText())));
            if (this.client != null) {
                this.client.setScreen(new HelpScreen(this.rootPos));
            }
        }).sizing(Sizing.fixed(80), Sizing.fixed(20))
                .margins(Insets.left(8)));

        mainPanel.child(buttonRow);
        rootComponent.child(mainPanel);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (editor != null &&
                editor.onKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (hasControlDown() && keyCode == 83) { //ctrl s
            ClientPlayNetworking.send(
                    new SaveEditorPayload(
                            this.rootPos,
                            trimTrailingEmptyLines(this.editor.getText())
                    )
            );
            this.close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String trimTrailingEmptyLines(String text) {
        String trimmed = text;
        while (trimmed.endsWith("\n")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
