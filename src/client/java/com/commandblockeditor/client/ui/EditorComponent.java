package com.commandblockeditor.client.ui;

import com.commandblockeditor.mixin.client.ChatInputSuggestorAccessor;
import com.mojang.brigadier.ParseResults;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.command.CommandSource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorComponent extends BaseComponent {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^([!?@\\d\\s]*):");
    private static final int COLOR_PREFIX = 0xFFFFD481;

    private final TextRenderer textRenderer;
    private final CommandEditBox editBox;
    private long lastSwitchFocusTime = Util.getMeasuringTimeMs();
    private boolean focused = false;
    
    private double scrollX = 0;
    private double scrollY = 0;
    
    private boolean draggingVertical = false;
    private boolean draggingHorizontal = false;
    private double dragScrollStart = 0;
    private double dragPosStart = 0;
    
    private Consumer<String> changeListener = (s) -> {};
    private int lastWidth = 0;
    private int lastHeight = 0;

    public EditorComponent() {
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
        this.editBox = new CommandEditBox(this.textRenderer, 100);
        this.editBox.setCursorChangeListener(this::onCursorChange);
        this.editBox.setChangeListener(s -> this.changeListener.accept(s));
    }

    public void setText(String text) {
        this.editBox.setText(text.replace("\r", ""));
        this.onCursorChange();
    }

    public String getText() {
        return this.editBox.getText();
    }

    public void setChangeListener(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.width != lastWidth || this.height != lastHeight) {
            this.lastWidth = this.width;
            this.lastHeight = this.height;
            this.onCursorChange();
        }

        int padding = 4;
        int gutterWidth = getGutterWidth();
        int contentX = this.x + gutterWidth + padding;
        int contentY = this.y + padding;
        
        boolean isCursorVisible = this.focused && (Util.getMeasuringTimeMs() - this.lastSwitchFocusTime) / 300L % 2L == 0L;
        
        String fullText = this.editBox.getText();
        int currentLineIdx = this.editBox.getCurrentLineIndex();
        int cursorIdx = this.editBox.getCursor();

        context.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0xFF1E1E1E);
        
        context.enableScissor(contentX, this.y, this.x + this.width, this.y + this.height);

        int lineIdx = 0;
        for (CommandEditBox.Substring substring : this.editBox.getLines()) {
            int l = contentY + (lineIdx * 9) - (int)scrollY;
            
            if (l + 9 > this.y && l < this.y + this.height) {
                int lineX = contentX - (int)scrollX;
                String lineText = fullText.substring(substring.beginIndex(), substring.endIndex());
                
                drawStyledLine(context, lineText, lineX, l);
                
                if (isCursorVisible && lineIdx == currentLineIdx) {
                    int cursorOffsetInLine = cursorIdx - substring.beginIndex();
                    int cursorX = lineX + this.textRenderer.getWidth(lineText.substring(0, Math.max(0, Math.min(cursorOffsetInLine, lineText.length()))));
                    
                    if (cursorIdx == fullText.length() && cursorOffsetInLine == lineText.length() && lineIdx == this.editBox.getLineCount() - 1) {
                        context.drawTextWithShadow(this.textRenderer, Text.literal("_"), cursorX, l, 0xFFD0D0D0);
                    } else {
                        context.fill(cursorX, l - 1, cursorX + 1, l + 9, 0xFFD0D0D0);
                    }
                }
            }
            lineIdx++;
        }

        if (this.editBox.hasSelection()) {
            CommandEditBox.Substring selection = this.editBox.getSelection();
            lineIdx = 0;
            for (CommandEditBox.Substring line : this.editBox.getLines()) {
                int l = contentY + (lineIdx * 9) - (int)scrollY;
                if (l + 9 > this.y && l < this.y + this.height) {
                    int selStartInLine = Math.max(selection.beginIndex(), line.beginIndex());
                    int selEndInLine = Math.min(selection.endIndex(), line.endIndex());
                    if (selStartInLine < selEndInLine) {
                        int startX = contentX - (int)scrollX + this.textRenderer.getWidth(fullText.substring(line.beginIndex(), selStartInLine));
                        int endX = contentX - (int)scrollX + this.textRenderer.getWidth(fullText.substring(line.beginIndex(), selEndInLine));
                        
                        context.fill(RenderLayer.getGuiTextHighlight(), startX, l, endX, l + 9, 0xFF4040FF);
                    }
                }
                lineIdx++;
            }
        }

        context.disableScissor();

        context.fill(this.x, this.y, this.x + gutterWidth, this.y + this.height, 0xFF252525);
        context.fill(this.x + gutterWidth - 1, this.y, this.x + gutterWidth, this.y + this.height, 0xFF404040);

        lineIdx = 0;
        for (CommandEditBox.Substring substring : this.editBox.getLines()) {
            int l = contentY + (lineIdx * 9) - (int)scrollY;
            if (l + 9 > this.y && l < this.y + this.height) {
                String lineNum = String.valueOf(lineIdx + 1);
                int lineNumX = this.x + gutterWidth - this.textRenderer.getWidth(lineNum) - 4;
                context.drawTextWithShadow(this.textRenderer, Text.literal(lineNum), lineNumX, l, 0xFF606060);
            }
            lineIdx++;
        }
        
        drawScrollbars(context);
    }

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("`([^`]+)`");

    private void drawStyledLine(OwoUIDrawContext context, String line, int x, int y) {
        if (line.isEmpty()) return;

        int currentX = x;
        int lastMatchEnd = 0;

        Matcher prefixMatcher = PREFIX_PATTERN.matcher(line);
        if (prefixMatcher.find()) {
            String prefix = prefixMatcher.group();
            context.drawTextWithShadow(this.textRenderer, Text.literal(prefix), currentX, y, COLOR_PREFIX);
            currentX += this.textRenderer.getWidth(prefix);
            lastMatchEnd = prefixMatcher.end();
        }

        String remaining = line.substring(lastMatchEnd);
        if (remaining.isEmpty()) return;

        String trimmed = remaining.stripLeading();
        int leadingSpaces = remaining.length() - trimmed.length();
        if (leadingSpaces > 0) {
            String spaces = remaining.substring(0, leadingSpaces);
            context.drawTextWithShadow(this.textRenderer, Text.literal(spaces), currentX, y, 0xFFFFFFFF);
            currentX += this.textRenderer.getWidth(spaces);
        }

        if (trimmed.isEmpty()) return;

        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler != null) {
            String toParse = trimmed;
            Matcher backtickMatcher = EXPRESSION_PATTERN.matcher(toParse);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (backtickMatcher.find()) {
                sb.append(toParse, last, backtickMatcher.start());
                sb.append("0".repeat(backtickMatcher.end() - backtickMatcher.start()));
                last = backtickMatcher.end();
            }
            sb.append(toParse.substring(last));
            toParse = sb.toString();

            com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(toParse);
            if (reader.canRead() && reader.peek() == '/') {
                reader.skip();
            }

            ParseResults<CommandSource> parseResults = networkHandler.getCommandDispatcher().parse(reader, networkHandler.getCommandSource());
            OrderedText highlightedText = ChatInputSuggestorAccessor.invokeHighlight(parseResults, trimmed, 0);
            context.drawTextWithShadow(this.textRenderer, highlightedText, currentX, y, 0xFFFFFFFF);

            backtickMatcher = EXPRESSION_PATTERN.matcher(trimmed);
            while (backtickMatcher.find()) {
                int startX = currentX + this.textRenderer.getWidth(trimmed.substring(0, backtickMatcher.start()));
                String expression = backtickMatcher.group();
                context.drawTextWithShadow(this.textRenderer, Text.literal(expression), startX, y, 0xFF55FFFF);
            }
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal(trimmed), currentX, y, 0xFFFFFFFF);
        }
    }

    private void drawScrollbars(OwoUIDrawContext context) {
        int scrollbarSize = 5;
        int trackColor = 0xFF2A2A2A;
        int handleColor = 0xFF606060;
        int activeHandleColor = 0xFF909090;

        int totalHeight = this.editBox.getLineCount() * 9;
        int padding = 8;
        if (totalHeight > this.height - padding) {
            int trackX = this.x + this.width - scrollbarSize;
            context.fill(trackX, this.y, this.x + this.width, this.y + this.height, trackColor);
            
            int visibleHeight = this.height;
            float scrollRatio = (float) scrollY / (totalHeight - visibleHeight + padding);
            int barHeight = Math.max(10, (int) ((float) visibleHeight * visibleHeight / (totalHeight + padding)));
            int barY = (int) (scrollRatio * (visibleHeight - barHeight));
            
            context.fill(trackX, this.y + barY, this.x + this.width, this.y + barY + barHeight, draggingVertical ? activeHandleColor : handleColor);
        }
        
        int maxWidth = this.editBox.getMaxLineWidth();
        int gutterWidth = getGutterWidth();
        int availableWidth = this.width - gutterWidth - 10;
        if (maxWidth > availableWidth) {
            int trackY = this.y + this.height - scrollbarSize;
            context.fill(this.x + gutterWidth, trackY, this.x + this.width, this.y + this.height, trackColor);
            
            float scrollRatio = (float) scrollX / (maxWidth - availableWidth);
            int barWidth = Math.max(10, (int) ((float) availableWidth * availableWidth / maxWidth));
            int barX = (int) (scrollRatio * (availableWidth - barWidth));
            
            context.fill(this.x + gutterWidth + barX, trackY, this.x + gutterWidth + barX + barWidth, this.y + this.height, draggingHorizontal ? activeHandleColor : handleColor);
        }
    }

    private int getGutterWidth() {
        int lineCount = Math.max(1, this.editBox.getLineCount());
        int digits = Math.max(2, String.valueOf(lineCount).length());
        return this.textRenderer.getWidth("0".repeat(digits)) + 12;
    }

    private void onCursorChange() {
        int gutterWidth = getGutterWidth();
        int padding = 4;
        int visibleWidth = this.width - gutterWidth - padding * 2 - 6;
        int visibleHeight = this.height - padding * 2 - 6;
        
        int currentLineIdx = this.editBox.getCurrentLineIndex();
        if (currentLineIdx < 0) return;
        
        int cursorYInContent = currentLineIdx * 9;
        
        if (cursorYInContent < scrollY) {
            scrollY = cursorYInContent;
        } else if (cursorYInContent + 9 > scrollY + visibleHeight) {
            scrollY = cursorYInContent + 9 - visibleHeight;
        }
        
        CommandEditBox.Substring currentLine = this.editBox.getLine(currentLineIdx);
        int cursorOffsetInLine = this.editBox.getCursor() - currentLine.beginIndex();
        String lineText = this.editBox.getText().substring(currentLine.beginIndex(), currentLine.endIndex());
        int cursorXInLine = this.textRenderer.getWidth(lineText.substring(0, Math.max(0, Math.min(cursorOffsetInLine, lineText.length()))));
        
        if (cursorXInLine < scrollX) {
            scrollX = cursorXInLine;
        } else if (cursorXInLine > scrollX + visibleWidth) {
            scrollX = cursorXInLine - visibleWidth;
        }
        
        scrollY = Math.max(0, scrollY);
        scrollX = Math.max(0, scrollX);
        
        int totalHeight = this.editBox.getLineCount() * 9;
        int maxScrollY = Math.max(0, totalHeight - this.height + 8);
        if (scrollY > maxScrollY) scrollY = maxScrollY;
        
        int maxWidth = this.editBox.getMaxLineWidth();
        int availableWidth = this.width - gutterWidth - 10;
        int maxScrollX = Math.max(0, maxWidth - availableWidth);
        if (scrollX > maxScrollX) scrollX = maxScrollX;
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int gutterWidth = getGutterWidth();
            
            int totalHeight = this.editBox.getLineCount() * 9;
            if (totalHeight > this.height && mouseX >= this.width - 10) {
                draggingVertical = true;
                dragScrollStart = scrollY;
                dragPosStart = mouseY;
                return true;
            }
            
            int maxWidth = this.editBox.getMaxLineWidth();
            int availableWidth = this.width - gutterWidth - 10;
            if (maxWidth > availableWidth && mouseY >= this.height - 10 && mouseX >= gutterWidth) {
                draggingHorizontal = true;
                dragScrollStart = scrollX;
                dragPosStart = mouseX;
                return true;
            }

            this.focused = true;
            this.lastSwitchFocusTime = Util.getMeasuringTimeMs();
            
            double d = mouseX - gutterWidth - 4 + this.scrollX;
            double e = mouseY - 4 + this.scrollY;
            this.editBox.setSelecting(Screen.hasShiftDown());
            this.editBox.moveCursor(d, e);
            return true;
        }
        return super.onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        if (button == 0) {
            if (draggingVertical) {
                int totalHeight = this.editBox.getLineCount() * 9 + 8;
                int barHeight = Math.max(10, (int) ((float) this.height * this.height / totalHeight));
                double trackLength = this.height - barHeight;
                if (trackLength > 0) {
                    double deltaPos = mouseY - dragPosStart;
                    double deltaScroll = (deltaPos / trackLength) * (totalHeight - this.height);
                    scrollY = MathHelper.clamp(dragScrollStart + deltaScroll, 0, totalHeight - this.height);
                }
                return true;
            }
            
            if (draggingHorizontal) {
                int maxWidth = this.editBox.getMaxLineWidth();
                int availableWidth = this.width - getGutterWidth() - 10;
                int barWidth = Math.max(10, (int) ((float) availableWidth * availableWidth / maxWidth));
                double trackLength = availableWidth - barWidth;
                if (trackLength > 0) {
                    double deltaPos = mouseX - dragPosStart;
                    double deltaScroll = (deltaPos / trackLength) * (maxWidth - availableWidth);
                    scrollX = MathHelper.clamp(dragScrollStart + deltaScroll, 0, maxWidth - availableWidth);
                }
                return true;
            }

            double d = mouseX - getGutterWidth() - 4 + this.scrollX;
            double e = mouseY - 4 + this.scrollY;
            this.editBox.setSelecting(true);
            this.editBox.moveCursor(d, e);
            this.editBox.setSelecting(Screen.hasShiftDown());
            return true;
        }
        return super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
    }

    @Override
    public boolean onMouseUp(double mouseX, double mouseY, int button) {
        draggingVertical = false;
        draggingHorizontal = false;
        return super.onMouseUp(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (Screen.hasShiftDown()) {
            scrollX = Math.max(0, scrollX - amount * 15);
            int maxWidth = this.editBox.getMaxLineWidth();
            int gutterWidth = getGutterWidth();
            int availableWidth = this.width - gutterWidth - 10;
            int maxScrollX = Math.max(0, maxWidth - availableWidth);
            if (scrollX > maxScrollX) scrollX = maxScrollX;
        } else {
            scrollY = Math.max(0, scrollY - amount * 15);
            int maxScrollY = Math.max(0, this.editBox.getLineCount() * 9 - this.height + 8);
            if (scrollY > maxScrollY) scrollY = maxScrollY;
        }
        return true;
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!this.focused) return false;
        if (this.editBox.handleSpecialKey(keyCode)) return true;
        return super.onKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        if (!this.focused) return false;
        if (net.minecraft.util.StringHelper.isValidChar(chr)) {
            this.editBox.replaceSelection(Character.toString(chr));
            return true;
        }
        return false;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        this.focused = true;
        this.lastSwitchFocusTime = Util.getMeasuringTimeMs();
    }

    @Override
    public void onFocusLost() {
        this.focused = false;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public CursorStyle cursorStyle() {
        return CursorStyle.TEXT;
    }
}
