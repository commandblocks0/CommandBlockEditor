package com.commandblockeditor.client.ui;

import com.commandblockeditor.network.RequestEditorPayload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class HelpScreen extends BaseOwoScreen<FlowLayout> {
    private final BlockPos rootPos;

    public HelpScreen(BlockPos rootPos) {
        this.rootPos = rootPos;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.blur(3, 5))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var mainPanel = Containers.verticalFlow(Sizing.fill(95), Sizing.fill(95));
        mainPanel.surface(Surface.DARK_PANEL);
        mainPanel.padding(Insets.of(15));

        var titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.child(Components.label(Text.literal("Command Block Editor Help"))
                .shadow(true)
                .horizontalSizing(Sizing.content())
                .margins(Insets.bottom(10)));
        mainPanel.child(titleRow);

        var content = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        content.verticalAlignment(VerticalAlignment.TOP);

        var leftColumn = Containers.verticalFlow(Sizing.fill(48), Sizing.content());
        leftColumn.padding(Insets.right(5));

        leftColumn.child(header("COMMAND STRUCTURE"));
        leftColumn.child(codeBox("[!][@][?][n]: <command>", 0xFF4EC9B0));

        leftColumn.child(header("FLAGS").margins(Insets.top(12)));
        leftColumn.child(flag("!", 0xFFF44747, "Repeating type", "First command only"));
        leftColumn.child(flag("@", 0xFF4FC1FF, "Toggle auto mode", "First command: enable auto", "Other commands: disable auto"));
        leftColumn.child(flag("?", 0xFFDCDCAA, "Conditional", null));
        leftColumn.child(flag("n", 0xFFCE9178, "Repeat N times", null));

        leftColumn.child(header("EXPRESSIONS").margins(Insets.top(12)));
        leftColumn.child(flag("i", 0xFF4FC1FF, "Iteration index", null));
        leftColumn.child(Components.label(Text.literal("Supported operations: + - * / ( )"))
                .color(Color.ofArgb(0xFF4EC9B0))
                .margins(Insets.top(2)));

        var divider = Containers.verticalFlow(Sizing.fixed(1), Sizing.fill(100));
        divider.surface(Surface.flat(0xFF404040));
        divider.margins(Insets.horizontal(5));

        var rightColumn = Containers.verticalFlow(Sizing.fill(48), Sizing.content());
        rightColumn.padding(Insets.left(5));

        rightColumn.child(header("EXAMPLE"));
        rightColumn.child(subheader("Input"));
        rightColumn.child(codeBox("5: say `i+1`\nsay hi\n?: say hi", 0xFFD4D4D4));

        rightColumn.child(subheader("Output").margins(Insets.top(12)));
        rightColumn.child(codeBox("say 1\nsay 2\nsay 3\nsay 4\nsay 5\nsay hi\nsay hi (conditional)", 0xFFD4D4D4));

        content.child(leftColumn);
        content.child(divider);
        content.child(rightColumn);

        var scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFF404040))); // Dark visible scrollbar
        mainPanel.child(scroll);

        var buttonRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);
        buttonRow.margins(Insets.top(10));
        buttonRow.child(Components.button(Text.literal("Back"), button -> {
            ClientPlayNetworking.send(new RequestEditorPayload(rootPos));
        }).sizing(Sizing.fixed(60), Sizing.fixed(20)));

        mainPanel.child(buttonRow);
        rootComponent.child(mainPanel);
    }

    private Component header(String text) {
        return Components.label(Text.literal(text))
                .color(Color.ofArgb(0xFFE0C24B))
                .margins(Insets.bottom(4));
    }

    private Component subheader(String text) {
        return Components.label(Text.literal(text))
                .color(Color.ofArgb(0xFF808080))
                .margins(Insets.bottom(2));
    }

    private ParentComponent codeBox(String text, int textColor) {
        var container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.surface(Surface.flat(0xFF1E1E1E));
        container.padding(Insets.of(8));
        container.child(Components.label(Text.literal(text))
                .color(Color.ofArgb(textColor)));
        return container;
    }

    private ParentComponent flag(String symbol, int symbolColor, String title, String... subtext) {
        var container = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        container.margins(Insets.top(8));
        container.verticalAlignment(VerticalAlignment.TOP);

        var symbolLabel = Components.label(Text.literal(symbol))
                .color(Color.ofArgb(symbolColor))
                .horizontalSizing(Sizing.fixed(15));

        var textColumn = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        textColumn.child(Components.label(Text.literal(title)));

        if (subtext != null) {
            for (String line : subtext) {
                if (line == null) continue;
                textColumn.child(Components.label(Text.literal(line))
                        .color(Color.ofArgb(0xFF808080))
                        .margins(Insets.top(1)));
            }
        }

        container.child(symbolLabel);
        container.child(textColumn);

        return container;
    }
}
