package com.commandblockeditor.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.util.StringHelper;

public class CommandEditBox {
    public static final int UNLIMITED_LENGTH = Integer.MAX_VALUE;
    private final TextRenderer textRenderer;
    private final List<Substring> lines = new ArrayList<>();
    private String text;
    private int cursor;
    private int selectionEnd;
    private boolean selecting;
    private int maxLength = Integer.MAX_VALUE;
    private final int width;
    private Consumer<String> changeListener = (text) -> {
    };
    private Runnable cursorChangeListener = () -> {
    };

    public CommandEditBox(TextRenderer textRenderer, int width) {
        this.textRenderer = textRenderer;
        this.width = width;
        this.setText("");
    }

    public int getMaxLength() {
        return this.maxLength;
    }

    public void setMaxLength(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Character limit cannot be negative");
        } else {
            this.maxLength = maxLength;
        }
    }

    public boolean hasMaxLength() {
        return this.maxLength != Integer.MAX_VALUE;
    }

    public void setChangeListener(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    public void setCursorChangeListener(Runnable cursorChangeListener) {
        this.cursorChangeListener = cursorChangeListener;
    }

    public void setText(String text) {
        this.text = this.truncateForReplacement(text);
        this.cursor = this.text.length();
        this.selectionEnd = this.cursor;
        this.onChange();
    }

    public String getText() {
        return this.text;
    }

    public void replaceSelection(String string) {
        if (!string.isEmpty() || this.hasSelection()) {
            String string2 = this.truncate(StringHelper.stripInvalidChars(string, true));
            Substring substring = this.getSelection();
            this.text = (new StringBuilder(this.text)).replace(substring.beginIndex(), substring.endIndex(), string2).toString();
            this.cursor = substring.beginIndex() + string2.length();
            this.selectionEnd = this.cursor;
            this.onChange();
        }
    }

    public void delete(int offset) {
        if (!this.hasSelection()) {
            this.selectionEnd = Math.max(0, Math.min(this.text.length(), this.cursor + offset));
        }

        this.replaceSelection("");
    }

    public int getCursor() {
        return this.cursor;
    }

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
    }

    public Substring getSelection() {
        return new Substring(Math.min(this.selectionEnd, this.cursor), Math.max(this.selectionEnd, this.cursor));
    }

    public int getLineCount() {
        return this.lines.size();
    }

    public int getCurrentLineIndex() {
        for(int i = 0; i < this.lines.size(); ++i) {
            Substring substring = this.lines.get(i);
            if (this.cursor >= substring.beginIndex() && this.cursor <= substring.endIndex()) {
                return i;
            }
        }

        return -1;
    }

    public Substring getLine(int index) {
        return this.lines.get(Math.max(0, Math.min(this.lines.size() - 1, index)));
    }

    public void moveCursor(CursorMovement movement, int amount) {
        switch (movement) {
            case ABSOLUTE -> this.cursor = amount;
            case RELATIVE -> this.cursor += amount;
            case END -> this.cursor = this.text.length() + amount;
        }

        this.cursor = Math.max(0, Math.min(this.text.length(), this.cursor));
        this.cursorChangeListener.run();
        if (!this.selecting) {
            this.selectionEnd = this.cursor;
        }

    }

    public void moveCursorLine(int offset) {
        if (offset != 0) {
            int i = this.textRenderer.getWidth(this.text.substring(this.getCurrentLine().beginIndex(), this.cursor)) + 2;
            Substring substring = this.getOffsetLine(offset);
            int j = this.textRenderer.trimToWidth(this.text.substring(substring.beginIndex(), substring.endIndex()), i).length();
            this.moveCursor(CursorMovement.ABSOLUTE, substring.beginIndex() + j);
        }
    }

    public void moveCursor(double x, double y) {
        int i = (int)Math.floor(x);
        int j = (int)Math.floor(y / 9.0);
        Substring substring = this.lines.get(Math.max(0, Math.min(this.lines.size() - 1, j)));
        int k = this.textRenderer.trimToWidth(this.text.substring(substring.beginIndex(), substring.endIndex()), i).length();
        this.moveCursor(CursorMovement.ABSOLUTE, substring.beginIndex() + k);
    }

    public boolean handleSpecialKey(int keyCode) {
        this.selecting = Screen.hasShiftDown();
        if (Screen.isSelectAll(keyCode)) {
            this.cursor = this.text.length();
            this.selectionEnd = 0;
            return true;
        } else if (Screen.isCopy(keyCode)) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
            return true;
        } else if (Screen.isPaste(keyCode)) {
            this.replaceSelection(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        } else if (Screen.isCut(keyCode)) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
            this.replaceSelection("");
            return true;
        } else {
            switch (keyCode) {
                case 257:
                case 335:
                    this.replaceSelection("\n");
                    return true;
                case 259:
                    if (Screen.hasControlDown()) {
                        Substring substring = this.getPreviousWordAtCursor();
                        this.delete(substring.beginIndex() - this.cursor);
                    } else {
                        this.delete(-1);
                    }

                    return true;
                case 261:
                    if (Screen.hasControlDown()) {
                        Substring substring = this.getNextWordAtCursor();
                        this.delete(substring.beginIndex() - this.cursor);
                    } else {
                        this.delete(1);
                    }

                    return true;
                case 262:
                    if (Screen.hasControlDown()) {
                        Substring substring = this.getNextWordAtCursor();
                        this.moveCursor(CursorMovement.ABSOLUTE, substring.beginIndex());
                    } else {
                        this.moveCursor(CursorMovement.RELATIVE, 1);
                    }

                    return true;
                case 263:
                    if (Screen.hasControlDown()) {
                        Substring substring = this.getPreviousWordAtCursor();
                        this.moveCursor(CursorMovement.ABSOLUTE, substring.beginIndex());
                    } else {
                        this.moveCursor(CursorMovement.RELATIVE, -1);
                    }

                    return true;
                case 264:
                    if (!Screen.hasControlDown()) {
                        this.moveCursorLine(1);
                    }

                    return true;
                case 265:
                    if (!Screen.hasControlDown()) {
                        this.moveCursorLine(-1);
                    }

                    return true;
                case 266:
                    this.moveCursor(CursorMovement.ABSOLUTE, 0);
                    return true;
                case 267:
                    this.moveCursor(CursorMovement.END, 0);
                    return true;
                case 268:
                    if (Screen.hasControlDown()) {
                        this.moveCursor(CursorMovement.ABSOLUTE, 0);
                    } else {
                        this.moveCursor(CursorMovement.ABSOLUTE, this.getCurrentLine().beginIndex());
                    }

                    return true;
                case 269:
                    if (Screen.hasControlDown()) {
                        this.moveCursor(CursorMovement.END, 0);
                    } else {
                        this.moveCursor(CursorMovement.ABSOLUTE, this.getCurrentLine().endIndex());
                    }

                    return true;
                default:
                    return false;
            }
        }
    }

    public Iterable<Substring> getLines() {
        return this.lines;
    }

    public boolean hasSelection() {
        return this.selectionEnd != this.cursor;
    }

    public String getSelectedText() {
        Substring substring = this.getSelection();
        return this.text.substring(substring.beginIndex(), substring.endIndex());
    }

    private Substring getCurrentLine() {
        return this.getOffsetLine(0);
    }

    private Substring getOffsetLine(int offsetFromCurrent) {
        int i = this.getCurrentLineIndex();
        if (i < 0) {
            return new Substring(0, 0);
        } else {
            return this.lines.get(Math.max(0, Math.min(this.lines.size() - 1, i + offsetFromCurrent)));
        }
    }

    public Substring getPreviousWordAtCursor() {
        if (this.text.isEmpty()) {
            return Substring.EMPTY;
        } else {
            int i;
            for(i = Math.max(0, Math.min(this.text.length() - 1, this.cursor)); i > 0 && Character.isWhitespace(this.text.charAt(i - 1)); --i) {
            }

            while(i > 0 && !Character.isWhitespace(this.text.charAt(i - 1))) {
                --i;
            }

            return new Substring(i, this.getWordEndIndex(i));
        }
    }

    public Substring getNextWordAtCursor() {
        if (this.text.isEmpty()) {
            return Substring.EMPTY;
        } else {
            int i;
            for(i = Math.max(0, Math.min(this.text.length() - 1, this.cursor)); i < this.text.length() && !Character.isWhitespace(this.text.charAt(i)); ++i) {
            }

            while(i < this.text.length() && Character.isWhitespace(this.text.charAt(i))) {
                ++i;
            }

            return new Substring(i, this.getWordEndIndex(i));
        }
    }

    private int getWordEndIndex(int startIndex) {
        int i;
        for(i = startIndex; i < this.text.length() && !Character.isWhitespace(this.text.charAt(i)); ++i) {
        }

        return i;
    }

    public int getLineWidth(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= this.lines.size()) {
            return 0;
        }
        Substring substring = this.lines.get(lineIndex);
        return this.textRenderer.getWidth(this.text.substring(substring.beginIndex(), substring.endIndex()));
    }

    public int getMaxLineWidth() {
        int maxWidth = 0;
        for (int i = 0; i < this.lines.size(); i++) {
            maxWidth = Math.max(maxWidth, getLineWidth(i));
        }
        return maxWidth;
    }

    private void onChange() {
        this.rewrap();
        this.changeListener.accept(this.text);
        this.cursorChangeListener.run();
    }

    private void rewrap() {
        this.lines.clear();
        String[] split = this.text.split("\n", -1);
        int start = 0;
        for (String line : split) {
            int end = start + line.length();
            this.lines.add(new Substring(start, end));
            start = end + 1;
        }
    }

    private String truncateForReplacement(String value) {
        return this.hasMaxLength() ? StringHelper.truncate(value, this.maxLength, false) : value;
    }

    private String truncate(String value) {
        if (this.hasMaxLength()) {
            int i = this.maxLength - this.text.length();
            return StringHelper.truncate(value, i, false);
        } else {
            return value;
        }
    }

    public static class Substring {
        public static final Substring EMPTY = new Substring(0, 0);
        private final int beginIndex;
        private final int endIndex;

        public Substring(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }

        public int beginIndex() { return beginIndex; }
        public int endIndex() { return endIndex; }
    }
}
