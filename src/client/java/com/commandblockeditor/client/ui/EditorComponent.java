package com.commandblockeditor.client.ui;

import com.commandblockeditor.client.mixin.ChatInputSuggestorAccessor;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorComponent extends BaseUIComponent {

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
    private double mouseX = 0;
    private double mouseY = 0;
    
    private final Consumer<String> changeListener = (s) -> {};
    private int lastWidth = 0;
    private int lastHeight = 0;

    private Suggestions suggestions;
    private int selectedSuggestion = 0;
    private int suggestionScroll = 0;
    private int suggestionX;
    private int suggestionY;
    private boolean cyclingSuggestions = false;
    private String cycleOriginalLineText = null;
    private java.util.concurrent.CompletableFuture<Suggestions> pendingSuggestions;

    private static final int VERTICAL_OVERSCROLL = 50;
    private static final int HORIZONTAL_OVERSCROLL = 50;
    private boolean initialScrollPositioned = false;

    record HistoryState(String text, int cursor) {}
    List<HistoryState> history = new ArrayList<>();
    int historyIndex = -1;

    public EditorComponent() {
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
        this.editBox = new CommandEditBox(this.textRenderer, 100);
        this.editBox.setCursorChangeListener(this::onCursorChange);
        this.editBox.setChangeListener(this.changeListener);
    }

    public void setText(String text) {
        this.editBox.setText(text.replace("\r", ""));
        this.initialScrollPositioned = false;

        history.clear();
        history.add(new HistoryState(getText(), editBox.getCursor()));
        historyIndex = 0;
    }

    public String getText() {
        return this.editBox.getText();
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.mouseX = mouseX - this.x;
        this.mouseY = mouseY - this.y;
        if (this.width != lastWidth || this.height != lastHeight) {
            this.lastWidth = this.width;
            this.lastHeight = this.height;
            initialScrollPositioned = false;
            scrollY = getMaxScrollY();
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

        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0xFF1E1E1E);
        
        graphics.enableScissor(contentX, this.y, this.x + this.width, this.y + this.height);

        int lineIdx = 0;
        for (CommandEditBox.Substring substring : this.editBox.getLines()) {
            int l = contentY + (lineIdx * 9) - (int)scrollY;
            
            if (l + 9 > this.y && l < this.y + this.height) {
                int lineX = contentX - (int)scrollX;
                String lineText = fullText.substring(substring.beginIndex(), substring.endIndex());
                
                drawStyledLine(graphics, lineText, lineX, l);
                
                if (isCursorVisible && lineIdx == currentLineIdx) {
                    int cursorOffsetInLine = cursorIdx - substring.beginIndex();
                    int cursorX = lineX + this.textRenderer.getWidth(lineText.substring(0, Math.max(0, Math.min(cursorOffsetInLine, lineText.length()))));
                    
                    if (cursorIdx == fullText.length() && cursorOffsetInLine == lineText.length() && lineIdx == this.editBox.getLineCount() - 1) {
                        graphics.drawTextWithShadow(this.textRenderer, Text.literal("_"), cursorX, l, 0xFFD0D0D0);
                    } else {
                        graphics.fill(cursorX, l - 1, cursorX + 1, l + 9, 0xFFD0D0D0);
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
                        
                        graphics.fill(startX, l, endX, l + 9, 0xFF4040FF);
                    }
                }
                lineIdx++;
            }
        }

        graphics.disableScissor();

        graphics.fill(this.x, this.y, this.x + gutterWidth, this.y + this.height, 0xFF252525);
        graphics.fill(this.x + gutterWidth - 1, this.y, this.x + gutterWidth, this.y + this.height, 0xFF404040);

        lineIdx = 0;
        for (CommandEditBox.Substring substring : this.editBox.getLines()) {
            int l = contentY + (lineIdx * 9) - (int)scrollY;
            if (l + 9 > this.y && l < this.y + this.height) {
                String lineNum = String.valueOf(lineIdx + 1);
                int lineNumX = this.x + gutterWidth - this.textRenderer.getWidth(lineNum) - 4;
                graphics.drawTextWithShadow(this.textRenderer, Text.literal(lineNum), lineNumX, l, 0xFF606060);
            }
            lineIdx++;
        }

        drawScrollbars(graphics);
        drawSuggestions(graphics);
    }

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("`([^`]+)`");

    private void drawStyledLine(OwoUIGraphics context, String line, int x, int y) {
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

            ParseResults<ClientCommandSource> parseResults = networkHandler.getCommandDispatcher().parse(reader, networkHandler.getCommandSource());
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

    private void drawScrollbars(OwoUIGraphics context) {
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
            float scrollRatio = getMaxScrollY() <= 0
                    ? 0
                    : (float) scrollY / getMaxScrollY();
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

            float scrollRatio = getMaxScrollX() <= 0
                    ? 0
                    : (float) scrollX / getMaxScrollX();
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

        if (!initialScrollPositioned) {
            initialScrollPositioned = true;

            scrollX = Math.max(
                    0,
                    cursorXInLine - (int)(visibleWidth * 0.8)
            );

            scrollX = Math.min(scrollX, getMaxScrollX());
        } else {
            if (cursorXInLine < scrollX) {
                scrollX = cursorXInLine;
            } else if (cursorXInLine > scrollX + visibleWidth) {
                scrollX = cursorXInLine - visibleWidth;
            }
        }

        scrollY = Math.max(0, scrollY);
        scrollX = Math.max(0, scrollX);

        suggestionX = this.x + gutterWidth + padding
                + cursorXInLine
                - (int) scrollX;

        suggestionY = this.y + padding
                + currentLineIdx * 9
                - (int) scrollY
                + 10;
        
        int totalHeight = this.editBox.getLineCount() * 9;
        int maxScrollY = Math.max(
                0,
                totalHeight - this.height + 8 + VERTICAL_OVERSCROLL
        );
        if (scrollY > maxScrollY) scrollY = maxScrollY;
        
        int maxWidth = this.editBox.getMaxLineWidth();
        int availableWidth = this.width - gutterWidth - 10;
        int maxScrollX = Math.max(
                0,
                maxWidth - availableWidth + HORIZONTAL_OVERSCROLL
        );
        if (scrollX > maxScrollX) scrollX = maxScrollX;

        if (!cyclingSuggestions) {
            updateSuggestions();
        }
    }

    @Override
    public boolean onMouseDown(Click click, boolean doubled) {
        this.mouseX = click.x();
        this.mouseY = click.y();

        if (click.button() == 0 && suggestions != null && !suggestions.isEmpty()) {
            if (isHoveringSuggestions(mouseX, mouseY)) {
                List<Suggestion> list = suggestions.getList();
                int maxVisible = 10;
                int visibleCount = Math.min(maxVisible, list.size());
                int rowHeight = 12;

                int sy = suggestionY - this.y - (visibleCount * rowHeight) - 12;
                if (sy < 0) {
                    sy = suggestionY - this.y;
                }

                int clicked = (int)((mouseY - sy) / rowHeight);
                int index = suggestionScroll + clicked;

                if (index >= 0 && index < list.size()) {
                    selectedSuggestion = index;
                    applySuggestion(0);
                    return true;
                }
            }
        }

        if (click.button() == 0) {
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
            this.editBox.setSelecting(GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
            this.editBox.moveCursor(d, e);
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(Click click, double deltaX, double deltaY) {
        this.mouseX = click.x();
        this.mouseY = click.y();
        if (click.button() == 0) {
            if (draggingVertical) {
                int totalHeight = this.editBox.getLineCount() * 9 + 8;
                int barHeight = Math.max(10, (int) ((float) this.height * this.height / totalHeight));
                double trackLength = this.height - barHeight;
                if (trackLength > 0) {
                    double deltaPos = mouseY - dragPosStart;
                    double deltaScroll = (deltaPos / trackLength) * (totalHeight - this.height);
                    scrollY = MathHelper.clamp(
                            dragScrollStart + deltaScroll,
                            0,
                            getMaxScrollY()
                    );
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
                    scrollX = MathHelper.clamp(
                            dragScrollStart + deltaScroll,
                            0,
                            getMaxScrollX()
                    );
                }
                return true;
            }

            double d = mouseX - getGutterWidth() - 4 + this.scrollX;
            double e = mouseY - 4 + this.scrollY;
            this.editBox.setSelecting(true);
            this.editBox.moveCursor(d, e);
            this.editBox.setSelecting(GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(Click click) {
        this.mouseX = click.x();
        this.mouseY = click.y();
        draggingVertical = false;
        draggingHorizontal = false;
        return super.onMouseUp(click);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        boolean shift = GLFW.glfwGetKey(
                MinecraftClient.getInstance().getWindow().getHandle(),
                GLFW.GLFW_KEY_LEFT_SHIFT
        ) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(
                MinecraftClient.getInstance().getWindow().getHandle(),
                GLFW.GLFW_KEY_RIGHT_SHIFT
        ) == GLFW.GLFW_PRESS;

        if (shift) {
            scrollX = Math.max(0, scrollX - amount * 15);
            int maxScrollX = getMaxScrollX();
            if (scrollX > maxScrollX) scrollX = maxScrollX;
        } else {
            scrollY = Math.max(0, scrollY - amount * 15);
            int maxScrollY = getMaxScrollY();
            if (scrollY > maxScrollY) scrollY = maxScrollY;
        }
        return true;
    }

    @Override
    public boolean onKeyPress(KeyInput input) {
        if (!this.focused) return false;

        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (input.modifiers() & GLFW.GLFW_MOD_ALT) != 0;

        if (ctrl) {
            if (input.getKeycode() == GLFW.GLFW_KEY_Z) {
                undo();
                return true;
            }

            if (input.getKeycode() == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }

            if (shift && input.getKeycode() == GLFW.GLFW_KEY_K) {

                int lineIndex = editBox.getCurrentLineIndex();
                CommandEditBox.Substring line = editBox.getLine(lineIndex);

                String text = editBox.getText();

                int start = line.beginIndex();
                int end = line.endIndex();

                if (end < text.length()) {
                    end++;
                }
                else if (start > 0) {
                    start--;
                }

                String newText = text.substring(0, start) + text.substring(end);

                editBox.setText(newText);
                editBox.setSelecting(false);

                int cursor = newText.indexOf('\n', start);

                if (cursor == -1) {
                    cursor = newText.length();
                }

                editBox.moveCursor(CursorMovement.ABSOLUTE, cursor);

                saveHistory();
                return true;
            }
        }

        if (alt) {

            if (shift && input.getKeycode() == GLFW.GLFW_KEY_DOWN) {
                int line = editBox.getCurrentLineIndex();
                List<String> lines = new ArrayList<>(List.of(editBox.getText().split("\n", -1)));
                CommandEditBox.Substring current = editBox.getLine(line);
                int column = editBox.getCursor() - current.beginIndex();
                lines.add(line+1, lines.get(line));
                String newText = String.join("\n", lines);
                editBox.setText(newText);
                CommandEditBox.Substring duplicatedLine = editBox.getLine(line + 1);
                editBox.setSelecting(false);
                editBox.moveCursor(
                        CursorMovement.ABSOLUTE,
                        Math.min(duplicatedLine.beginIndex() + column, duplicatedLine.endIndex())
                );
                saveHistory();
                return true;
            }

            if (input.getKeycode() == GLFW.GLFW_KEY_UP) {
                int line = editBox.getCurrentLineIndex();
                List<String> lines = new ArrayList<>(List.of(editBox.getText().split("\n", -1)));
                if (line == 0) return true;
                CommandEditBox.Substring current = editBox.getLine(line);
                int column = editBox.getCursor() - current.beginIndex();
                Collections.swap(lines, line, line - 1);
                String newText = String.join("\n", lines);
                editBox.setText(newText);
                CommandEditBox.Substring movedLine = editBox.getLine(line - 1);
                editBox.moveCursor(
                        CursorMovement.ABSOLUTE,
                        Math.min(movedLine.beginIndex() + column, movedLine.endIndex())
                );
                saveHistory();
                return true;
            } else if (input.getKeycode() == GLFW.GLFW_KEY_DOWN) {
                int line = editBox.getCurrentLineIndex();
                List<String> lines = new ArrayList<>(List.of(editBox.getText().split("\n", -1)));
                if (line >= lines.size() - 1) return true;
                CommandEditBox.Substring current = editBox.getLine(line);
                int column = editBox.getCursor() - current.beginIndex();
                Collections.swap(lines, line, line + 1);
                String newText = String.join("\n", lines);
                editBox.setText(newText);
                CommandEditBox.Substring movedLine = editBox.getLine(line + 1);
                editBox.moveCursor(
                        CursorMovement.ABSOLUTE,
                        Math.min(movedLine.beginIndex() + column, movedLine.endIndex())
                );
                saveHistory();
                return true;
            }
        }

        if (suggestions != null && !suggestions.isEmpty()) {
            if (input.getKeycode() == GLFW.GLFW_KEY_UP) {
                selectedSuggestion = (selectedSuggestion - 1 + suggestions.getList().size()) % suggestions.getList().size();
                cyclingSuggestions = false;
                return true;
            }
            if (input.getKeycode() == GLFW.GLFW_KEY_DOWN) {
                selectedSuggestion = (selectedSuggestion + 1) % suggestions.getList().size();
                cyclingSuggestions = false;
                return true;
            }
            if (input.getKeycode() == GLFW.GLFW_KEY_TAB) {
                applySuggestion(shift ? -1 : 1);
                return true;
            }
            if (input.getKeycode() == GLFW.GLFW_KEY_ENTER || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER) {
                applySuggestion(0);
                return true;
            }
            if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
                suggestions = null;
                cyclingSuggestions = false;
                return true;
            }
        } else if (input.getKeycode() == GLFW.GLFW_KEY_TAB) {
            updateSuggestions();
            return true;
        }

        if (this.editBox.handleSpecialKey(input.getKeycode(), input.modifiers())) {
            if (input.getKeycode() == GLFW.GLFW_KEY_BACKSPACE
                    || input.getKeycode() == GLFW.GLFW_KEY_DELETE
                    || input.getKeycode() == GLFW.GLFW_KEY_ENTER
                    || input.getKeycode() == GLFW.GLFW_KEY_KP_ENTER) {
                saveHistory();
            }

            cyclingSuggestions = false;
            return true;
        }

        return super.onKeyPress(input);
    }

    @Override
    public boolean onCharTyped(CharInput input) {
        if (!this.focused) return false;
        if (input.isValidChar()) {
            cyclingSuggestions = false;

            if (input.codepoint() == ')' || input.codepoint() == ']' || input.codepoint() == '}' ||
                    input.codepoint() == '"' || input.codepoint() == '\'' || input.codepoint() == '`') {

                int cursor = editBox.getCursor();
                String text = editBox.getText();

                if (cursor < text.length() && text.charAt(cursor) == input.codepoint()) {
                    editBox.setSelecting(false);
                    editBox.moveCursor(CursorMovement.RELATIVE, 1);
                    return true;
                }
            }

            switch (input.codepoint()) {
                case '(' -> insertPair("()");
                case '[' -> insertPair("[]");
                case '{' -> insertPair("{}");
                case '"' -> insertPair("\"\"");
                case '\'' -> insertPair("''");
                case '`' -> insertPair("``");
                default -> {
                    editBox.replaceSelection(Character.toString(input.codepoint()));
                    saveHistory();
                }
            }

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
        this.suggestions = null;
        this.cyclingSuggestions = false;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public CursorStyle cursorStyle() {
        if (isHoveringSuggestions(this.mouseX, this.mouseY) || isHoveringScrollbars(this.mouseX, this.mouseY)) {
            return CursorStyle.HAND;
        }
        return CursorStyle.TEXT;
    }

    private boolean isHoveringSuggestions(double mouseX, double mouseY) {
        if (suggestions == null || suggestions.isEmpty()) return false;

        List<Suggestion> list = suggestions.getList();
        int maxVisible = 10;
        int visibleCount = Math.min(maxVisible, list.size());
        int rowHeight = 12;

        int maxWidth = 150;
        for (int i = 0; i < visibleCount; i++) {
            maxWidth = Math.max(
                    maxWidth,
                    this.textRenderer.getWidth(list.get(suggestionScroll + i).getText()) + 10
            );
        }

        int sx = suggestionX - this.x;
        int sy = suggestionY - this.y - (visibleCount * rowHeight) - 12;

        if (sx + maxWidth > this.width) {
            sx = this.width - maxWidth;
        }
        if (sy < 0) {
            sy = suggestionY - this.y;
        }

        return mouseX >= sx && mouseX <= sx + maxWidth
                && mouseY >= sy && mouseY <= sy + visibleCount * rowHeight;
    }

    private boolean isHoveringScrollbars(double mouseX, double mouseY) {
        int scrollbarSize = 5;

        // Vertical scrollbar check
        int totalHeight = this.editBox.getLineCount() * 9;
        if (totalHeight > this.height - 8) {
            if (mouseX >= this.width - scrollbarSize && mouseX <= this.width && mouseY >= 0 && mouseY <= this.height) {
                return true;
            }
        }

        // Horizontal scrollbar check
        int maxWidth = this.editBox.getMaxLineWidth();
        int gutterWidth = getGutterWidth();
        int availableWidth = this.width - gutterWidth - 10;
        if (maxWidth > availableWidth) {
            return mouseY >= this.height - scrollbarSize && mouseY <= this.height && mouseX >= gutterWidth && mouseX <= this.width;
        }

        return false;
    }

    public void drawSuggestions(OwoUIGraphics context) {
        if (suggestions == null || suggestions.isEmpty()) return;

        List<Suggestion> list = suggestions.getList();
        int count = list.size();
        int maxVisible = 10;
        int visibleCount = Math.min(maxVisible, count);

        if (selectedSuggestion < 0) selectedSuggestion = 0;
        if (selectedSuggestion >= count) selectedSuggestion = count - 1;

        if (selectedSuggestion < suggestionScroll) {
            suggestionScroll = selectedSuggestion;
        } else if (selectedSuggestion >= suggestionScroll + maxVisible) {
            suggestionScroll = selectedSuggestion - maxVisible + 1;
        }

        int rowHeight = 12;
        int maxWidth = 150;
        for (int i = 0; i < visibleCount; i++) {
            maxWidth = Math.max(maxWidth, this.textRenderer.getWidth(list.get(suggestionScroll + i).getText()) + 10);
        }

        int x = suggestionX;
        int y = suggestionY - (visibleCount * rowHeight) - 12;

        if (x + maxWidth > this.x + this.width) {
            x = this.x + this.width - maxWidth;
        }
        if (y < this.y) {
            y = suggestionY;
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, 0);

        context.fill(x - 1, y - 1, x + maxWidth + 1, y + visibleCount * rowHeight + 1, 0xFF404040);
        context.fill(x, y, x + maxWidth, y + visibleCount * rowHeight, 0xFF101010);

        for (int i = 0; i < visibleCount; i++) {
            int index = suggestionScroll + i;
            Suggestion suggestion = list.get(index);
            int itemY = y + i * rowHeight;

            if (index == selectedSuggestion) {
                context.fill(x, itemY, x + maxWidth, itemY + rowHeight, 0xFF404080);
            }

            context.drawText(this.textRenderer, Text.literal(suggestion.getText()), x + 5, itemY + 2, 0xFFFFFFFF, true);
        }

        context.getMatrices().popMatrix();
    }

    private void updateSuggestions() {
        if (cyclingSuggestions) return;

        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) {
            suggestions = null;
            return;
        }

        int lineIndex = editBox.getCurrentLineIndex();
        if (lineIndex < 0) {
            suggestions = null;
            return;
        }

        CommandEditBox.Substring line = editBox.getLine(lineIndex);
        String lineText = editBox.getText().substring(line.beginIndex(), line.endIndex());
        int cursorInLine = editBox.getCursor() - line.beginIndex();

        int commandStart = getCommandStartInLine(lineText);
        String commandPart = lineText.substring(commandStart);
        cursorInLine = Math.max(0, cursorInLine - commandStart);

        String toParse = commandPart;
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

        StringReader reader = new StringReader(toParse);

        ParseResults<ClientCommandSource> parse = networkHandler.getCommandDispatcher().parse(reader, networkHandler.getCommandSource());

        if (pendingSuggestions != null) {
            pendingSuggestions.cancel(true);
        }

        pendingSuggestions = networkHandler.getCommandDispatcher().getCompletionSuggestions(parse, cursorInLine);
        pendingSuggestions.thenAccept(result -> {
            MinecraftClient.getInstance().execute(() -> {
                List<Suggestion> filtered = result.getList().stream()
                        .filter(s -> !s.getText().isBlank())
                        .toList();

                if (filtered.isEmpty()) {
                    suggestions = null;
                } else {
                    suggestions = new Suggestions(result.getRange(), filtered);
                    selectedSuggestion = 0;
                    suggestionScroll = 0;
                }
            });
        });
    }

    private static @NotNull StringReader getReader(String commandPart) {
        String toParse = commandPart;
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

        return new StringReader(toParse);
    }

    private static @NotNull StringReader getStringReader(String commandPart) {
        String toParse = commandPart;
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

        StringReader reader = new StringReader(toParse);
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        return reader;
    }

    private void applySuggestion(int offset) {
        if (suggestions == null || suggestions.isEmpty()) return;

        boolean wasCycling = cyclingSuggestions;
        if (!cyclingSuggestions) {
            cyclingSuggestions = true;
            int lineIdx = editBox.getCurrentLineIndex();
            CommandEditBox.Substring line = editBox.getLine(lineIdx);
            cycleOriginalLineText = editBox.getText().substring(line.beginIndex(), line.endIndex());
        }

        if (offset != 0) {
            if (wasCycling || offset < 0) {
                selectedSuggestion = (selectedSuggestion + offset + suggestions.getList().size()) % suggestions.getList().size();
            }
        }

        Suggestion suggestion = suggestions.getList().get(selectedSuggestion);
        int commandStart = getCommandStartInLine(cycleOriginalLineText);
        String commandPart = cycleOriginalLineText.substring(commandStart);
        String replacedCommand = suggestion.apply(commandPart);
        String newLine = cycleOriginalLineText.substring(0, commandStart) + replacedCommand;

        int lineIdx = editBox.getCurrentLineIndex();
        CommandEditBox.Substring currentLine = editBox.getLine(lineIdx);

        int oldCursorInLine = editBox.getCursor() - currentLine.beginIndex();

        editBox.setSelecting(false);
        editBox.moveCursor(CursorMovement.ABSOLUTE, currentLine.beginIndex());
        editBox.setSelecting(true);
        editBox.moveCursor(CursorMovement.ABSOLUTE, currentLine.endIndex());
        editBox.replaceSelection(newLine);

        editBox.setSelecting(false);

        int delta = newLine.length() - cycleOriginalLineText.length();
        int newCursor = currentLine.beginIndex() + oldCursorInLine + delta;

        editBox.moveCursor(CursorMovement.ABSOLUTE, newCursor);

        if (offset == 0) {
            saveHistory();
            suggestions = null;
            cyclingSuggestions = false;
        }
    }

    private int getCommandStartInLine(String lineText) {
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(lineText);
        int start = 0;
        if (prefixMatcher.find()) {
            start = prefixMatcher.end();
        }
        while (start < lineText.length() && Character.isWhitespace(lineText.charAt(start))) {
            start++;
        }
        return start;
    }

    private int getMaxScrollY() {
        int totalHeight = this.editBox.getLineCount() * 9;
        return Math.max(0, totalHeight - this.height + 8 + VERTICAL_OVERSCROLL);
    }

    private int getMaxScrollX() {
        int maxWidth = this.editBox.getMaxLineWidth();
        int availableWidth = this.width - getGutterWidth() - 10;

        return Math.max(
                0,
                maxWidth - availableWidth + HORIZONTAL_OVERSCROLL
        );
    }

    private void saveHistory() {
        HistoryState state = new HistoryState(getText(), editBox.getCursor());

        if (historyIndex >= 0 && history.get(historyIndex).equals(state)) {
            return;
        }

        while (history.size() > historyIndex + 1) {
            history.removeLast();
        }

        history.add(state);
        historyIndex++;
    }

    private void undo() {
        if (historyIndex <= 0) return;

        historyIndex--;

        HistoryState state = history.get(historyIndex);
        editBox.setText(state.text());
        editBox.moveCursor(CursorMovement.ABSOLUTE, state.cursor());
    }

    private void redo() {
        if (historyIndex >= history.size() - 1) return;

        historyIndex++;

        HistoryState state = history.get(historyIndex);
        editBox.setText(state.text());
        editBox.moveCursor(CursorMovement.ABSOLUTE, state.cursor());
    }

    private void insertPair(String pair) {
        editBox.setSelecting(false);
        editBox.replaceSelection(pair);
        editBox.moveCursor(CursorMovement.RELATIVE, -1);
        saveHistory();
    }
}
