package com.commandblockeditor.client.ui;

import com.commandblockeditor.network.RequestEditorPayload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

public class HelpScreen extends BaseOwoScreen<FlowLayout> {
    private final BlockPos rootPos;

    public HelpScreen(BlockPos rootPos) {
        this.rootPos = rootPos;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.blur(3, 5))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        var mainPanel = UIContainers.verticalFlow(Sizing.fill(95), Sizing.fill(95));
        mainPanel.surface(Surface.DARK_PANEL);
        mainPanel.padding(Insets.of(15));

        var titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.child(UIComponents.label(Text.literal("Command Block Editor Help"))
                .shadow(true)
                .horizontalSizing(Sizing.content())
                .margins(Insets.bottom(10)));
        mainPanel.child(titleRow);

        var content = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        content.verticalAlignment(VerticalAlignment.TOP);

        var leftColumn = UIContainers.verticalFlow(Sizing.fill(48), Sizing.content());
        leftColumn.padding(Insets.right(5));

        leftColumn.child(header("COMMAND STRUCTURE"));
        leftColumn.child(codeBox("[!][@][?][n]: <command>", 0xFF4EC9B0));

        leftColumn.child(header("FLAGS").margins(Insets.top(12)));
        leftColumn.child(flag("!", 0xFFF44747, "Repeating type", "First command only"));
        leftColumn.child(flag("@", 0xFF4FC1FF, "Toggle auto mode", "First command: enable auto", "Other commands: disable auto"));
        leftColumn.child(flag("?", 0xFFDCDCAA, "Conditional", (String) null));
        leftColumn.child(flag("n", 0xFFCE9178, "Repeat N times", (String) null));

        leftColumn.child(header("EXPRESSIONS").margins(Insets.top(12)));
        leftColumn.child(flag("i", 0xFF4FC1FF, "Iteration index", (String) null));
        leftColumn.child(UIComponents.label(Text.literal("Supported operations: + - * / ( )"))
                .color(Color.ofArgb(0xFF4EC9B0))
                .margins(Insets.top(2)));

        var divider = UIContainers.verticalFlow(Sizing.fixed(1), Sizing.fill(100));
        divider.surface(Surface.flat(0xFF404040));
        divider.margins(Insets.horizontal(5));

        var rightColumn = UIContainers.verticalFlow(Sizing.fill(48), Sizing.content());
        rightColumn.padding(Insets.left(5));

        rightColumn.child(header("EXAMPLE"));
        rightColumn.child(subheader("Input"));
        rightColumn.child(codeBox("5: say `i+1`\nsay hi\n?: say hi", 0xFFD4D4D4));

        rightColumn.child(subheader("Output").margins(Insets.top(12)));
        rightColumn.child(codeBox("say 1\nsay 2\nsay 3\nsay 4\nsay 5\nsay hi\nsay hi (conditional)", 0xFFD4D4D4));

        content.child(leftColumn);
        content.child(divider);
        content.child(rightColumn);

        var scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xFF404040))); // Dark visible scrollbar
        mainPanel.child(scroll);

        var buttonRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);
        buttonRow.margins(Insets.top(10));
        buttonRow.child(UIComponents.button(Text.literal("Back"), button -> ClientPlayNetworking
                .send(new RequestEditorPayload(rootPos)))
                .sizing(Sizing.fixed(60), Sizing.fixed(20)));

        mainPanel.child(buttonRow);
        rootComponent.child(mainPanel);
    }

    private UIComponent header(String text) {
        return UIComponents.label(Text.literal(text))
                .color(Color.ofArgb(0xFFE0C24B))
                .margins(Insets.bottom(4));
    }

    private UIComponent subheader(String text) {
        return UIComponents.label(Text.literal(text))
                .color(Color.ofArgb(0xFF808080))
                .margins(Insets.bottom(2));
    }

    private UIComponent codeBox(String text, int textColor) {
        var container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.surface(Surface.flat(0xFF1E1E1E));
        container.padding(Insets.of(8));
        container.child(UIComponents.label(Text.literal(text))
                .color(Color.ofArgb(textColor)));
        return container;
    }

    private UIComponent flag(String symbol, int symbolColor, String title, String... subtext) {
        var container = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        container.margins(Insets.top(8));
        container.verticalAlignment(VerticalAlignment.TOP);

        var symbolLabel = UIComponents.label(Text.literal(symbol))
                .color(Color.ofArgb(symbolColor))
                .horizontalSizing(Sizing.fixed(15));

        var textColumn = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        textColumn.child(UIComponents.label(Text.literal(title)));

        if (subtext != null) {
            for (String line : subtext) {
                if (line == null) continue;
                textColumn.child(UIComponents.label(Text.literal(line))
                        .color(Color.ofArgb(0xFF808080))
                        .margins(Insets.top(1)));
            }
        }

        container.child(symbolLabel);
        container.child(textColumn);

        return container;
    }
}
