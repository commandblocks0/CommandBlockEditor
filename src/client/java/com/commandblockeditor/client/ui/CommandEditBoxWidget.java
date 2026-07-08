package com.commandblockeditor.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class CommandEditBoxWidget extends ScrollableWidget {
    private static final int PADDING = 4;
    private static final int GUTTER_PADDING = 4;
    private static final int SCROLLBAR_SIZE = 6;
    private final TextRenderer textRenderer;
    private final CommandEditBox editBox;
    private long lastSwitchFocusTime = Util.getMeasuringTimeMs();
    private double scrollX;
    private boolean scrollingX;
    private boolean scrollingYManual;
    
    private final boolean SHIFT = GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

    public CommandEditBoxWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text placeholder, Text message) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
        this.editBox = new CommandEditBox(textRenderer, width - PADDING*2);
        this.editBox.setCursorChangeListener(this::onCursorChange);
    }

    private int getGutterWidth() {
        int lineCount = this.editBox.getLineCount();
        int digits = Math.max(2, String.valueOf(lineCount).length());
        return this.textRenderer.getWidth("0".repeat(digits)) + GUTTER_PADDING * 2;
    }

    public double getScrollX() {
        return this.scrollX;
    }

    public void setScrollX(double scrollX) {
        this.scrollX = Math.max(0.0, Math.min(scrollX, (double)this.getMaxScrollX()));
    }

    public int getMaxScrollX() {
        int gutterWidth = getGutterWidth();
        int visibleWidth = this.width - PADDING*2 - gutterWidth - SCROLLBAR_SIZE - 4;
        return Math.max(0, this.editBox.getMaxLineWidth() - visibleWidth + 4);
    }

    @Override
    public int getMaxScrollY() {
        int contentHeight = this.getContentsHeight();
        int textAreaHeight = this.height - PADDING*2;
        if (this.getMaxScrollX() > 0) {
            textAreaHeight -= (SCROLLBAR_SIZE + 2);
        }
        return Math.max(0, contentHeight - textAreaHeight);
    }

    public void setMaxLength(int maxLength) {
        this.editBox.setMaxLength(maxLength);
    }

    public void setChangeListener(Consumer<String> changeListener) {
        this.editBox.setChangeListener(changeListener);
    }

    public void setText(String text) {
        this.editBox.setText(text);
    }

    public String getText() {
        return this.editBox.getText();
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, Text.translatable("gui.narrate.editBox", new Object[]{this.getMessage(), this.getText()}));
    }

    private boolean isWithinVerticalScrollbar(double mouseX, double mouseY) {
        return mouseX >= (double)(this.getX() + this.width - SCROLLBAR_SIZE - 2) && mouseX <= (double)(this.getX() + this.width);
    }

    private boolean isWithinHorizontalScrollbar(double mouseX, double mouseY) {
        int yStart = this.getY() + this.height - SCROLLBAR_SIZE - 2;
        int startX = this.getX() + getGutterWidth() + PADDING;
        return this.getMaxScrollX() > 0 && mouseX >= (double)startX && mouseX <= (double)(this.getX() + this.width - SCROLLBAR_SIZE - 2) && mouseY >= (double)yStart && mouseY <= (double)(this.getY() + this.height);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!this.visible || !this.isFocused()) return false;
        
        if (this.isWithinHorizontalScrollbar(click.x(), click.y()) && click.button() == 0) {
            this.scrollingX = true;
            return true;
        }

        if (this.isWithinVerticalScrollbar(click.x(), click.y()) && click.button() == 0) {
            this.scrollingYManual = true;
            return true;
        }

        if (this.isMouseOver(click.x(), click.y()) && click.button() == 0) {
            this.editBox.setSelecting(SHIFT);
            this.moveCursor(click.x(), click.y());
            return true;
        }

        return false;
    }

    @Override
    public void onRelease(Click click) {
        if (click.button() == 0) {
            this.scrollingX = false;
            this.scrollingYManual = false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (SHIFT) {
            this.setScrollX(this.getScrollX() - verticalAmount * 15.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.scrollingX) {
            int maxScrollX = this.getMaxScrollX();
            int startX = this.getX() + getGutterWidth() + PADDING;
            int trackWidth = this.width - (startX - this.getX()) - SCROLLBAR_SIZE - 4;
            int thumbWidth = this.getHorizontalScrollbarThumbWidth();
            double d = (double)Math.max(1, maxScrollX) / (double)Math.max(1, trackWidth - thumbWidth);
            this.setScrollX(this.getScrollX() + offsetX * d);
            return true;
        }
        if (this.scrollingYManual) {
            int maxScrollY = this.getMaxScrollY();
            int scrollbarHeight = this.height - (this.getMaxScrollX() > 0 ? SCROLLBAR_SIZE + 2 : 0);
            int thumbHeight = Math.max(8, (int)((float)(scrollbarHeight * scrollbarHeight) / (float)Math.max(1, this.getContentsHeight())));
            double d = (double)Math.max(1, maxScrollY) / (double)Math.max(1, scrollbarHeight - thumbHeight);
            this.setScrollY(this.getScrollY() + offsetY * d);
            return true;
        }
        if (this.isMouseOver(click.x(), click.y()) && click.button() == 0) {
            this.editBox.setSelecting(true);
            this.moveCursor(click.x(), click.y());
            this.editBox.setSelecting(SHIFT);
            return true;
        }
        return false;
    }

    private int getHorizontalScrollbarThumbWidth() {
        int startX = this.getX() + getGutterWidth() + PADDING;
        int trackWidth = this.width - (startX - this.getX()) - SCROLLBAR_SIZE - 4;
        int contentsWidth = Math.max(1, this.editBox.getMaxLineWidth() + 4);
        return Math.max(20, Math.min(trackWidth, (int)((float)(trackWidth * trackWidth) / (float)contentsWidth)));
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.editBox.handleSpecialKey(keyCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        if (this.visible && this.isFocused() && StringHelper.isValidChar(chr)) {
            this.editBox.replaceSelection(Character.toString(chr));
            return true;
        }
        return false;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.visible) {
            int gutterWidth = getGutterWidth();
            int padding = PADDING;
            boolean hasHorizontalScroll = this.getMaxScrollX() > 0;
            int textAreaHeight = this.height - (hasHorizontalScroll ? SCROLLBAR_SIZE + 2 : 0);
            int textAreaWidth = this.width - SCROLLBAR_SIZE - 2;

            context.fill(this.getX(), this.getY(), this.getX() + textAreaWidth, this.getY() + textAreaHeight, 0xFF000000);

            int gutterBackgroundWidth = gutterWidth + padding;
            context.fill(this.getX(), this.getY(), this.getX() + gutterBackgroundWidth, this.getY() + textAreaHeight, 0xFF202020);

            context.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + textAreaHeight);

            this.renderContents(context, mouseX, mouseY, delta, gutterBackgroundWidth, textAreaWidth, textAreaHeight);

            context.disableScissor();

            if (this.editBox.hasMaxLength()) {
                int i = this.editBox.getMaxLength();
                Text text = Text.translatable("gui.multiLineEditBox.character_limit", new Object[]{this.editBox.getText().length(), i});
                context.drawTextWithShadow(this.textRenderer, text, this.getX() + this.width - this.textRenderer.getWidth(text), this.getY() + this.height + 4, 10526880);
            }

            this.drawVerticalScrollbar(context, textAreaHeight);
            if (hasHorizontalScroll) {
                this.drawHorizontalScrollbar(context);
            }
        }
    }

    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta, int gutterBackgroundWidth, int textAreaWidth, int textAreaHeight) {
        String string = this.editBox.getText();
        int contentX = this.getX() + gutterBackgroundWidth;
        int contentY = this.getY() + PADDING;
        int scrollY = (int)this.getScrollY();
        
        int currentLineIdx = this.editBox.getCurrentLineIndex();
        int cursorIdx = this.editBox.getCursor();
        boolean isCursorVisible = this.isFocused() && (Util.getMeasuringTimeMs() - this.lastSwitchFocusTime) / 300L % 2L == 0L;
        
        int lineIdx = 0;
        for (CommandEditBox.Substring substring : this.editBox.getLines()) {
            int l = contentY + (lineIdx * 9) - scrollY;
            
            if (l + 9 > this.getY() && l < this.getY() + textAreaHeight) {
                String lineNum = String.valueOf(lineIdx + 1);
                int lineNumX = this.getX() + gutterBackgroundWidth - this.textRenderer.getWidth(lineNum) - 2;
                context.drawTextWithShadow(this.textRenderer, lineNum, lineNumX, l, 0x606060);
                
                int lineX = contentX - (int)this.scrollX;
                String lineText = string.substring(substring.beginIndex(), substring.endIndex());
                
                context.enableScissor(contentX, this.getY(), this.getX() + textAreaWidth, this.getY() + textAreaHeight);
                context.drawTextWithShadow(this.textRenderer, lineText, lineX, l, -2039584);
                
                if (isCursorVisible && lineIdx == currentLineIdx) {
                    int cursorOffsetInLine = cursorIdx - substring.beginIndex();
                    int cursorX = lineX + this.textRenderer.getWidth(lineText.substring(0, Math.min(cursorOffsetInLine, lineText.length())));
                    
                    if (cursorIdx == string.length() && cursorOffsetInLine == lineText.length() && lineIdx == this.editBox.getLineCount() - 1) {
                        context.drawTextWithShadow(this.textRenderer, "_", cursorX, l, -3092272);
                    } else {
                        context.fill(cursorX, l - 1, cursorX + 1, l + 9, -3092272);
                    }
                }
                context.disableScissor();
            }
            lineIdx++;
        }

        if (this.editBox.hasSelection()) {
            CommandEditBox.Substring selection = this.editBox.getSelection();
            lineIdx = 0;
            for (CommandEditBox.Substring line : this.editBox.getLines()) {
                int l = contentY + (lineIdx * 9) - scrollY;
                if (l + 9 > this.getY() && l < this.getY() + textAreaHeight) {
                    int selStartInLine = Math.max(selection.beginIndex(), line.beginIndex());
                    int selEndInLine = Math.min(selection.endIndex(), line.endIndex());
                    if (selStartInLine < selEndInLine) {
                        int startX = contentX - (int)this.scrollX + this.textRenderer.getWidth(string.substring(line.beginIndex(), selStartInLine));
                        int endX = contentX - (int)this.scrollX + this.textRenderer.getWidth(string.substring(line.beginIndex(), selEndInLine));
                        
                        context.enableScissor(contentX, this.getY(), this.getX() + textAreaWidth, this.getY() + textAreaHeight);
                        context.fill(startX, l, endX, l + 9, -16776961);
                        context.disableScissor();
                    }
                }
                lineIdx++;
            }
        }
    }

    private void drawVerticalScrollbar(DrawContext context, int textAreaHeight) {
        if (this.overflows()) {
            int maxScrollY = this.getMaxScrollY();
            int x = this.getX() + this.width - SCROLLBAR_SIZE;
            int yStart = this.getY();
            int scrollbarHeight = textAreaHeight;
            int thumbHeight = Math.max(8, (int)((float)(scrollbarHeight * scrollbarHeight) / (float)Math.max(1, this.getContentsHeight())));
            int thumbY = yStart + (int)(this.getScrollY() * (double)(scrollbarHeight - thumbHeight) / (double)maxScrollY);

            context.fill(x, thumbY, x + SCROLLBAR_SIZE, thumbY + thumbHeight, 0xFF808080);
            context.fill(x, thumbY, x + SCROLLBAR_SIZE - 1, thumbY + thumbHeight - 1, 0xFFC0C0C0);
        }
    }

    private void drawHorizontalScrollbar(DrawContext context) {
        int maxScrollX = this.getMaxScrollX();
        int startX = this.getX() + getGutterWidth() + PADDING;
        int trackWidth = this.width - (startX - this.getX()) - SCROLLBAR_SIZE - 4;
        int y = this.getY() + this.height - SCROLLBAR_SIZE;
        
        int thumbWidth = this.getHorizontalScrollbarThumbWidth();
        int thumbX = startX + (int)(this.scrollX * (double)(trackWidth - thumbWidth) / (double)maxScrollX);
        
        context.fill(thumbX, y, thumbX + thumbWidth, y + SCROLLBAR_SIZE, 0xFF808080);
        context.fill(thumbX, y, thumbX + thumbWidth - 1, y + SCROLLBAR_SIZE - 1, 0xFFC0C0C0);
    }

    public int getContentsHeight() {
        return 9 * this.editBox.getLineCount();
    }

    @Override
    protected boolean overflows() {
        return (double)this.editBox.getLineCount() > (double)(this.height - PADDING*2) / 9.0;
    }

    @Override
    protected int getContentsHeightWithPadding() {
        return 0;
    }

    @Override
    protected double getDeltaYPerScroll() {
        return 4.5;
    }

    private void onCursorChange() {
        double d = this.getScrollY();
        CommandEditBox.Substring substring = this.editBox.getLine((int)(d / 9.0));
        if (this.editBox.getCursor() <= substring.beginIndex()) {
            int var5 = this.editBox.getCurrentLineIndex();
            d = (double)(var5 * 9);
        } else {
            double var10001 = d + (double)this.height;
            CommandEditBox.Substring substring2 = this.editBox.getLine((int)(var10001 / 9.0) - 1);
            if (this.editBox.getCursor() > substring2.endIndex()) {
                int var7 = this.editBox.getCurrentLineIndex();
                var7 = var7 * 9 - this.height;
                d = (double)(var7 + 9 + PADDING*2);
            }
        }
        this.setScrollY(d);

        int gutterWidth = getGutterWidth();
        int visibleWidth = this.width - PADDING*2 - gutterWidth - SCROLLBAR_SIZE - 4;
        int currentLineIdx = this.editBox.getCurrentLineIndex();
        CommandEditBox.Substring currentLine = this.editBox.getLine(currentLineIdx);
        int cursorOffsetInLine = this.editBox.getCursor() - currentLine.beginIndex();
        String lineText = this.editBox.getText().substring(currentLine.beginIndex(), currentLine.endIndex());
        int cursorXInLine = this.textRenderer.getWidth(lineText.substring(0, Math.min(cursorOffsetInLine, lineText.length())));
        
        if (cursorXInLine < this.scrollX) {
            this.setScrollX(cursorXInLine);
        } else if (cursorXInLine > this.scrollX + visibleWidth) {
            this.setScrollX(cursorXInLine - visibleWidth);
        }
    }

    private void moveCursor(double mouseX, double mouseY) {
        double d = mouseX - (double)this.getX() - (double)PADDING - getGutterWidth() + this.scrollX;
        double e = mouseY - (double)this.getY() - (double)PADDING + this.getScrollY();
        this.editBox.moveCursor(d, e);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.lastSwitchFocusTime = Util.getMeasuringTimeMs();
        }
    }
}
