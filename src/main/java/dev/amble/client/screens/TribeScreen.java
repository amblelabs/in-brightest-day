package dev.amble.client.screens;

import dev.amble.core.networking.payloads.c2s.GatherC2SPayload;
import dev.amble.core.networking.payloads.s2c.TribeRosterS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;

public class TribeScreen extends Screen {
    private static final int WIDTH = 236;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 22;
    private static final int COLUMN_GAP = 6;
    private static final int COLUMNS = 2;
    private static final int FACE = 16;
    private static final int TITLE_SPACE = 22;
    private static final int WHOLE_HEIGHT = 20;
    private static final int MAX_ROWS = 8;

    private final List<TribeRosterS2CPayload.Member> members;
    private final List<ResolvableProfile> profiles = new ArrayList<>();
    private final List<Button> memberButtons = new ArrayList<>();
    private int left;
    private int top;
    private int panelHeight;
    private int scroll;

    public TribeScreen(List<TribeRosterS2CPayload.Member> members) {
        super(Component.translatable("gui.brightestday.tribe.title"));
        this.members = members;
        for (TribeRosterS2CPayload.Member member : members) this.profiles.add(ResolvableProfile.createUnresolved(member.id()));
    }

    private int rows() {
        return Math.min(MAX_ROWS, Mth.positiveCeilDiv(this.members.size(), COLUMNS));
    }

    @Override
    protected void init() {
        this.panelHeight = PADDING * 2 + TITLE_SPACE + WHOLE_HEIGHT + 6 + this.rows() * ROW_HEIGHT;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.tribe.whole"), button -> this.choose(GatherC2SPayload.whole()))
                .bounds(this.left + PADDING, this.top + PADDING + TITLE_SPACE, WIDTH - PADDING * 2, WHOLE_HEIGHT).build());

        this.memberButtons.clear();
        int columnWidth = (WIDTH - PADDING * 2 - COLUMN_GAP) / COLUMNS;
        int first = this.scroll * COLUMNS;
        int last = Math.min(this.members.size(), first + this.rows() * COLUMNS);
        for (int index = first; index < last; index++) {
            TribeRosterS2CPayload.Member member = this.members.get(index);
            int slot = index - first;
            int x = this.left + PADDING + (slot % COLUMNS) * (columnWidth + COLUMN_GAP);
            int y = this.top + PADDING + TITLE_SPACE + WHOLE_HEIGHT + 6 + (slot / COLUMNS) * ROW_HEIGHT;
            Button button = Button.builder(Component.literal(member.name()), pressed -> this.choose(GatherC2SPayload.member(member.id())))
                    .bounds(x + FACE + 4, y, columnWidth - FACE - 4, ROW_HEIGHT - 2).build();
            this.memberButtons.add(this.addRenderableWidget(button));
        }
    }

    private void choose(GatherC2SPayload choice) {
        ClientPlayNetworking.send(choice);
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int total = Mth.positiveCeilDiv(this.members.size(), COLUMNS);
        int next = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, total - this.rows()));
        if (next != this.scroll) {
            this.scroll = next;
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int accent = ARGB.opaque(LanternCorps.INDIGO.color());
        LanternWidgets.panel(graphics, this.left, this.top, WIDTH, this.panelHeight, accent);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, this.top + PADDING + 2, LanternWidgets.TEXT);

        int first = this.scroll * COLUMNS;
        for (int i = 0; i < this.memberButtons.size(); i++) {
            Button button = this.memberButtons.get(i);
            int x = button.getX() - FACE - 4;
            int y = button.getY() + (button.getHeight() - FACE) / 2;
            graphics.fill(x - 1, y - 1, x + FACE + 1, y + FACE + 1, accent);
            PlayerFaceExtractor.extractRenderState(graphics, this.profiles.get(first + i), x, y, FACE);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
