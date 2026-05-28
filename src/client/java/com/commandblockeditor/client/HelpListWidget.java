package com.commandblockeditor.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;

public class HelpListWidget extends ElementListWidget<HelpListWidget.Entry> {

    public HelpListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, itemHeight);

        for (String line : HelpScreen.TEXT) {
            addEntry(new Entry(line));
        }
    }

    public static class Entry extends ElementListWidget.Entry<Entry> {

        private final String text;

        public Entry(String text) {
            this.text = text;
        }

        @Override
        public void render(
                DrawContext context,
                int index,
                int y,
                int x,
                int entryWidth,
                int entryHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float tickDelta
        ) {
            MinecraftClient client = MinecraftClient.getInstance();

            context.drawText(
                    client.textRenderer,
                    text,
                    x + 4,
                    y + 2,
                    0xFFFFFF,
                    false
            );
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of();
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }
    }

    @Override
    protected int getScrollbarX() {
        return width - 6;
    }
}