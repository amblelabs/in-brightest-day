package dev.amble.client.lore;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.client.screens.LanternWidgets;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LoreBookScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 214;
    private static final int MARGIN = 14;
    private static final int GUTTER = 16;
    private static final int TITLE_SPACE = 22;
    private static final int FOOTER_SPACE = 22;
    private static final int LINE_HEIGHT = 10;
    private static final int HEADER_SPACE = 16;
    private static final int TOC_ROW = 14;
    private static final int ARROW_WIDTH = 20;
    private static final int ARROW_HEIGHT = 16;
    private static int remembered;

    private record Page(int chapter, List<FormattedCharSequence> lines, boolean opens) {}

    private final List<Page> pages = new ArrayList<>();
    private final List<Integer> chapterStarts = new ArrayList<>();
    private int spread;
    private int left;
    private int top;
    private @Nullable Button previous;
    private @Nullable Button next;

    public LoreBookScreen() {
        super(Component.literal(LoreLibrary.book().title()));
    }

    private int pageWidth() {
        return (WIDTH - MARGIN * 2 - GUTTER) / 2;
    }

    private int bodyLines(boolean opens) {
        return (HEIGHT - TITLE_SPACE - FOOTER_SPACE - MARGIN - (opens ? HEADER_SPACE : 0)) / LINE_HEIGHT;
    }

    private void paginate() {
        this.pages.clear();
        this.chapterStarts.clear();
        this.pages.add(new Page(-1, List.of(), false));
        List<LoreLibrary.Chapter> chapters = LoreLibrary.book().chapters();
        for (int c = 0; c < chapters.size(); c++) {
            this.chapterStarts.add(this.pages.size());
            boolean opens = true;
            for (String text : chapters.get(c).pages()) {
                List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.pageWidth());
                int index = 0;
                do {
                    int room = this.bodyLines(opens);
                    int end = Math.min(lines.size(), index + room);
                    this.pages.add(new Page(c, List.copyOf(lines.subList(index, end)), opens));
                    index = end;
                    opens = false;
                } while (index < lines.size());
            }
        }
    }

    private int spreads() {
        return Math.max(1, Mth.positiveCeilDiv(this.pages.size(), 2));
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.paginate();
        this.spread = Mth.clamp(remembered, 0, this.spreads() - 1);
        int y = this.top + HEIGHT - MARGIN - ARROW_HEIGHT + 4;
        this.previous = this.addRenderableWidget(Button.builder(Component.literal("◀"), button -> this.turn(-1))
                .bounds(this.left + MARGIN, y, ARROW_WIDTH, ARROW_HEIGHT).build());
        this.next = this.addRenderableWidget(Button.builder(Component.literal("▶"), button -> this.turn(1))
                .bounds(this.left + WIDTH - MARGIN - ARROW_WIDTH, y, ARROW_WIDTH, ARROW_HEIGHT).build());
        this.refreshArrows();
    }

    private void turn(int direction) {
        this.jump(this.spread + direction);
    }

    private void jump(int target) {
        int clamped = Mth.clamp(target, 0, this.spreads() - 1);
        if (clamped == this.spread) return;
        this.spread = clamped;
        remembered = clamped;
        this.refreshArrows();
        if (this.minecraft.player != null) this.minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }

    private void refreshArrows() {
        if (this.previous != null) this.previous.active = this.spread > 0;
        if (this.next != null) this.next.active = this.spread < this.spreads() - 1;
    }

    private int accent(int chapter) {
        if (chapter >= 0 && chapter < LoreLibrary.book().chapters().size()) {
            LanternCorps corps = LoreLibrary.book().chapters().get(chapter).corps();
            if (corps != null) return ARGB.opaque(corps.color());
        }
        return LanternWidgets.accent();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int spine = this.left + WIDTH / 2;
        int accent = this.accent(this.pageAt(this.spread * 2 + 1) != null ? this.pageAt(this.spread * 2 + 1).chapter() : -1);
        LanternWidgets.panel(graphics, this.left, this.top, WIDTH, HEIGHT, accent);
        graphics.fill(spine, this.top + 8, spine + 1, this.top + HEIGHT - 8, ARGB.color(0x70, accent));
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.centeredText(this.font, this.title, spine, this.top + 9, LanternWidgets.TEXT);
        int bodyTop = this.top + TITLE_SPACE + 4;
        int leftX = this.left + MARGIN;
        int rightX = spine + GUTTER / 2;
        this.page(graphics, this.spread * 2, leftX, bodyTop, mouseX, mouseY);
        this.page(graphics, this.spread * 2 + 1, rightX, bodyTop, mouseX, mouseY);

        Component number = Component.literal((this.spread + 1) + " / " + this.spreads());
        graphics.centeredText(this.font, number, spine, this.top + HEIGHT - MARGIN - 4, LanternWidgets.TEXT_DIM);
    }

    private @Nullable Page pageAt(int index) {
        return index >= 0 && index < this.pages.size() ? this.pages.get(index) : null;
    }

    private void page(GuiGraphicsExtractor graphics, int index, int x, int y, int mouseX, int mouseY) {
        Page page = this.pageAt(index);
        if (page == null) return;
        if (page.chapter() < 0) {
            this.contents(graphics, x, y, mouseX, mouseY);
            return;
        }
        int accent = this.accent(page.chapter());
        if (page.opens()) {
            graphics.text(this.font, Component.literal(LoreLibrary.book().chapters().get(page.chapter()).title()), x, y, accent, true);
            LanternWidgets.divider(graphics, x, x + this.pageWidth(), y + 11, accent);
            y += HEADER_SPACE;
        }
        for (FormattedCharSequence line : page.lines()) {
            graphics.text(this.font, line, x, y, LanternWidgets.TEXT, false);
            y += LINE_HEIGHT;
        }
    }

    private void contents(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        graphics.text(this.font, Component.translatable("gui.brightestday.lore.contents"), x, y, LanternWidgets.TEXT, true);
        LanternWidgets.divider(graphics, x, x + this.pageWidth(), y + 11, LanternWidgets.accent());
        y += HEADER_SPACE;
        List<LoreLibrary.Chapter> chapters = LoreLibrary.book().chapters();
        if (chapters.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.brightestday.lore.empty"), x, y, LanternWidgets.TEXT_DIM, false);
            return;
        }
        for (int c = 0; c < chapters.size(); c++) {
            int rowY = y + c * TOC_ROW;
            boolean hovered = this.contentsRow(mouseX, mouseY) == c;
            int accent = this.accent(c);
            graphics.fill(x, rowY + 1, x + 2, rowY + 9, accent);
            Component label = Component.literal(chapters.get(c).title());
            graphics.text(this.font, label, x + 6, rowY, hovered ? ARGB.srgbLerp(0.5F, accent, 0xFFFFFFFF) : LanternWidgets.TEXT, false);
            String number = String.valueOf(this.chapterStarts.get(c) / 2 + 1);
            graphics.text(this.font, number, x + this.pageWidth() - this.font.width(number), rowY, LanternWidgets.TEXT_DIM, false);
        }
    }

    private int contentsRow(double mouseX, double mouseY) {
        if (this.spread != 0) return -1;
        int x = this.left + MARGIN;
        int y = this.top + TITLE_SPACE + 4 + HEADER_SPACE;
        if (mouseX < x || mouseX > x + this.pageWidth() || mouseY < y) return -1;
        int row = (int) ((mouseY - y) / TOC_ROW);
        return row < LoreLibrary.book().chapters().size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = this.contentsRow(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && row >= 0) {
            this.jump(this.chapterStarts.get(row) / 2);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) this.turn(scrollY > 0.0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_A) {
            this.turn(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT || event.key() == InputConstants.KEY_D) {
            this.turn(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
