package dev.amble.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.amble.BrightestDay;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.ringpowers.EyePaint;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class SuitTextures {
    private static final int SIZE = 64;
    private static final int HEAD_HEIGHT = 16;
    private static final int HEAD_SIDE_TOP = 8;
    private static final int STEPS = 128;
    private static final float EDGE = 0.2F;
    private static final float EDGE_TINT = 0.7F;
    private static final float GLOW_WHITEN = 0.35F;
    private static final float ORDER_WEIGHT = 0.65F;
    private static final float NOISE_WEIGHT = 0.35F;
    private static final int[] STRETCH = {0, 1, 1, 2};
    private static final int FACE_TOP = 8;
    private static final int FACE_LEFT = 8;
    private static final int FACE_RIGHT = 16;
    private static final int HAT_LEFT = 40;
    private static final float EYE_GLOW_WHITEN = 0.2F;
    private static final float EYE_FLARE_WHITEN = 0.45F;

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<String, Optional<int[]>> SUITS = new HashMap<>();
    private static final Map<Identifier, Optional<Skin>> SKINS = new HashMap<>();
    private static final Map<Integer, float[]> THRESHOLDS = new HashMap<>();
    private static float @Nullable [] maskThresholds;

    static @Nullable Entry update(int id, PlayerSkin playerSkin, LanternCorps corps, HumanoidArm ringArm, int maskOffset, float maskProgress, float progress, int color,
                                  EyePaint eyes, float eyeProgress, int eyeColor) {
        Identifier skinPath = playerSkin.body().texturePath();
        Skin skin = skin(skinPath);
        if (skin == null) return null;

        boolean slim = playerSkin.model() == PlayerModelType.SLIM;
        int[] suit = progress > 0.0F ? suit(corps, ringArm, slim, maskOffset) : null;
        if (suit == null) progress = 0.0F;
        if (progress <= 0.0F && (eyeProgress <= 0.0F || eyes.isEmpty())) return null;

        Key key = new Key(skinPath, corps, ringArm, slim, maskOffset, Math.round(maskProgress * STEPS), Math.round(progress * STEPS), color,
                eyes, Math.round(eyeProgress * STEPS), eyeColor);
        Entry entry = ENTRIES.get(id);
        if (entry != null && entry.size != skin.size) {
            entry.close();
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(id, skin.size);
            ENTRIES.put(id, entry);
        }
        if (!key.equals(entry.key)) {
            entry.key = key;
            build(entry, skin, suit, thresholds(slim, ringArm), key.step / (float) STEPS, key.maskStep / (float) STEPS, color,
                    eyes, key.eyeStep / (float) STEPS, eyeColor);
        }
        return entry;
    }

    static void release(int id) {
        Entry entry = ENTRIES.remove(id);
        if (entry != null) entry.close();
        SKINS.keySet().removeIf(path -> ENTRIES.values().stream().noneMatch(remaining -> remaining.key != null && remaining.key.skin().equals(path)));
    }

    static void clear() {
        ENTRIES.values().forEach(Entry::close);
        ENTRIES.clear();
        SUITS.clear();
        SKINS.clear();
    }

    private static void build(Entry entry, Skin skin, int @Nullable [] suit, float[] thresholds, float progress, float maskProgress, int color,
                              EyePaint eyes, float eyeProgress, int eyeColor) {
        float[] maskThresholds = maskThresholds();
        NativeImage body = entry.body.getPixels();
        NativeImage glow = entry.glow.getPixels();
        NativeImage flare = entry.flare.getPixels();
        float eyeGlow = eyeProgress * eyes.glow();
        int glowColor = VoxelRenderer.toWhite(color, GLOW_WHITEN);
        int scale = skin.size / SIZE;

        for (int y = 0; y < skin.size; y++) {
            for (int x = 0; x < skin.size; x++) {
                int sx = x / scale;
                int sy = y / scale;
                int own = sy * SIZE + sx;
                int inner = innerIndex(sx, sy);
                int base = skin.pixels[y * skin.size + x];

                int source = inner >= 0 ? inner : own;
                float reveal = reveal(progress, thresholds[source]);
                if (sy < HEAD_HEIGHT) reveal = Math.min(reveal, reveal(maskProgress, maskThresholds[source]));
                float edge = reveal > 0.0F && reveal < 1.0F ? Mth.sin(reveal * Mth.PI) : 0.0F;

                int out = base;
                int lit = 0;
                int flared = 0;
                if (suit != null && ARGB.alpha(suit[own]) > 0) {
                    out = mix(base, suit[own], reveal);
                    out = ARGB.color(ARGB.alpha(out), ARGB.srgbLerp(edge * EDGE_TINT, out, glowColor));
                    lit = ARGB.color(edge, glowColor);
                } else if (suit != null && inner >= 0 && ARGB.alpha(suit[inner]) > 0) {
                    out = ARGB.multiplyAlpha(base, 1.0F - reveal);
                }

                int eye = eyeProgress > 0.0F ? eyePixel(eyes, sx, sy, eyeColor) : 0;
                if (eye != 0 && sx < FACE_RIGHT) {
                    out = ARGB.opaque(ARGB.srgbLerp(eyeProgress, ARGB.opaque(out), eye));
                    lit = ARGB.color(eyeGlow, VoxelRenderer.toWhite(eye, EYE_GLOW_WHITEN));
                    flared = ARGB.color(eyeGlow, VoxelRenderer.toWhite(eye, EYE_FLARE_WHITEN));
                } else if (eye != 0) {
                    out = ARGB.multiplyAlpha(out, 1.0F - eyeProgress);
                    lit = 0;
                }

                body.setPixel(x, y, out);
                glow.setPixel(x, y, lit);
                flare.setPixel(x, y, flared);
            }
        }

        entry.body.upload();
        entry.glow.upload();
        entry.flare.upload();
    }

    private static int eyePixel(EyePaint eyes, int sx, int sy, int eyeColor) {
        if (sy < FACE_TOP || sy >= FACE_TOP + EyePaint.SIZE) return 0;
        int column;
        if (sx >= FACE_LEFT && sx < FACE_RIGHT) column = sx - FACE_LEFT;
        else if (sx >= HAT_LEFT && sx < HAT_LEFT + EyePaint.SIZE) column = sx - HAT_LEFT;
        else return 0;

        int kind = eyes.get(column, sy - FACE_TOP);
        if (kind == EyePaint.NONE) return 0;
        return kind == EyePaint.WHITE ? 0xFFFFFFFF : ARGB.opaque(eyeColor);
    }

    private static float reveal(float progress, float threshold) {
        return Mth.clamp((progress * (1.0F + EDGE) - threshold) / EDGE, 0.0F, 1.0F);
    }

    private static int mix(int from, int to, float amount) {
        if (ARGB.alpha(from) == 0) return ARGB.multiplyAlpha(to, amount);
        return ARGB.srgbLerp(amount, from, to);
    }

    private static int innerIndex(int x, int y) {
        if (y < 16 && x >= 32) return y * SIZE + x - 32;
        if (y >= 32 && y < 48 && x < 56) return (y - 16) * SIZE + x;
        if (y >= 48 && x < 16) return y * SIZE + x + 16;
        if (y >= 48 && x >= 48) return y * SIZE + x - 16;
        return -1;
    }

    private static float[] thresholds(boolean slim, HumanoidArm ringArm) {
        return THRESHOLDS.computeIfAbsent((slim ? 2 : 0) + ringArm.ordinal(), unused -> {
            float[] thresholds = new float[SIZE * SIZE];
            for (int i = 0; i < thresholds.length; i++) thresholds[i] = noise(i % SIZE, i / SIZE);

            int arm = slim ? 3 : 4;
            boolean rightRing = ringArm == HumanoidArm.RIGHT;
            part(thresholds, 0, 0, 8, 8, 8, row -> 0.55F + 0.35F * (1.0F - row));
            part(thresholds, 16, 16, 8, 12, 4, row -> 0.3F + 0.2F * row);
            part(thresholds, 40, 16, arm, 12, 4, rightRing ? SuitTextures::ringArm : SuitTextures::freeArm);
            part(thresholds, 32, 48, arm, 12, 4, rightRing ? SuitTextures::freeArm : SuitTextures::ringArm);
            part(thresholds, 0, 16, 4, 12, 4, row -> 0.5F + 0.5F * row);
            part(thresholds, 16, 48, 4, 12, 4, row -> 0.5F + 0.5F * row);
            return thresholds;
        });
    }

    private static float[] maskThresholds() {
        if (maskThresholds == null) {
            maskThresholds = new float[SIZE * SIZE];
            part(maskThresholds, 0, 0, 8, 8, 8, row -> 1.0F - row);
        }
        return maskThresholds;
    }

    private static float ringArm(float row) {
        return 0.3F * (1.0F - row);
    }

    private static float freeArm(float row) {
        return 0.5F + 0.4F * row;
    }

    private static void part(float[] thresholds, int u, int v, int width, int height, int depth, Order order) {
        for (int y = 0; y < depth + height; y++) {
            for (int x = 0; x < 2 * (depth + width); x++) {
                float row = y < depth
                        ? (x < depth + width ? 0.0F : 1.0F)
                        : (y - depth) / (float) (height - 1);
                int index = (v + y) * SIZE + u + x;
                thresholds[index] = ORDER_WEIGHT * order.at(row) + NOISE_WEIGHT * noise(u + x, v + y);
            }
        }
    }

    private static float noise(int x, int y) {
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        return ((hash ^ (hash >>> 16)) & 0xFFFF) / 65536.0F;
    }

    private static int @Nullable [] suit(LanternCorps corps, HumanoidArm ringArm, boolean slim, int maskOffset) {
        String key = corps.getSerializedName() + "/" + ringArm.ordinal() + "/" + slim + "/" + maskOffset;
        return SUITS.computeIfAbsent(key, unused -> {
            int[] pixels = loadSuit(corps, ringArm);
            if (pixels == null) pixels = loadSuit(corps, ringArm.getOpposite());
            if (pixels == null) return Optional.empty();
            if (!slim) pixels = widen(pixels);
            return Optional.of(shiftHead(pixels, maskOffset));
        }).orElse(null);
    }

    private static int @Nullable [] loadSuit(LanternCorps corps, HumanoidArm side) {
        Identifier path = BrightestDay.id("textures/lanterns/suits/" + corps.getSerializedName() + "_lantern_" + side.name().toLowerCase(Locale.ROOT) + ".png");
        int[] pixels = read(path);
        return pixels != null && pixels.length == SIZE * SIZE ? pixels : null;
    }

    private static int[] shiftHead(int[] pixels, int offset) {
        if (offset == 0) return pixels;
        int[] shifted = pixels.clone();
        for (int y = HEAD_SIDE_TOP; y < HEAD_HEIGHT; y++) {
            int source = y + offset;
            boolean inside = source >= HEAD_SIDE_TOP && source < HEAD_HEIGHT;
            for (int x = 0; x < SIZE; x++) shifted[y * SIZE + x] = inside ? pixels[source * SIZE + x] : 0;
        }
        return shifted;
    }

    private static int[] widen(int[] slim) {
        int[] wide = slim.clone();
        widenArm(slim, wide, 40, 16);
        widenArm(slim, wide, 32, 48);
        widenArm(slim, wide, 40, 32);
        widenArm(slim, wide, 48, 48);
        return wide;
    }

    private static void widenArm(int[] slim, int[] wide, int u, int v) {
        for (int y = 0; y < 16; y++) {
            int row = (v + y) * SIZE + u;
            for (int x = 0; x < 16; x++) wide[row + x] = 0;
            for (int i = 0; i < 4; i++) {
                if (y < 4) {
                    wide[row + 4 + i] = slim[row + 4 + STRETCH[i]];
                    wide[row + 8 + i] = slim[row + 7 + STRETCH[i]];
                } else {
                    wide[row + i] = slim[row + i];
                    wide[row + 4 + i] = slim[row + 4 + STRETCH[i]];
                    wide[row + 8 + i] = slim[row + 7 + i];
                    wide[row + 12 + i] = slim[row + 11 + STRETCH[i]];
                }
            }
        }
    }

    private static @Nullable Skin skin(Identifier path) {
        return SKINS.computeIfAbsent(path, unused -> {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(path);
            int[] pixels;
            int width;
            int height;
            if (texture instanceof DynamicTexture dynamic) {
                NativeImage image = dynamic.getPixels();
                width = image.getWidth();
                height = image.getHeight();
                pixels = image.getPixels();
            } else {
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(path);
                if (resource.isEmpty()) return Optional.empty();
                try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                    width = image.getWidth();
                    height = image.getHeight();
                    pixels = image.getPixels();
                } catch (IOException e) {
                    BrightestDay.LOGGER.warn("Failed to read skin {} for lantern suit", path, e);
                    return Optional.empty();
                }
            }
            if (width != height || width < SIZE || width % SIZE != 0) return Optional.empty();
            return Optional.of(new Skin(pixels, width));
        }).orElse(null);
    }

    private static int @Nullable [] read(Identifier path) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(path);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            return image.getWidth() == SIZE && image.getHeight() == SIZE ? image.getPixels() : null;
        } catch (IOException e) {
            BrightestDay.LOGGER.warn("Failed to read lantern suit {}", path, e);
            return null;
        }
    }

    @FunctionalInterface
    private interface Order {
        float at(float row);
    }

    private record Skin(int[] pixels, int size) {}

    private record Key(Identifier skin, LanternCorps corps, HumanoidArm ringArm, boolean slim, int maskOffset, int maskStep, int step, int color,
                       EyePaint eyes, int eyeStep, int eyeColor) {}

    static final class Entry {
        final Identifier bodyId;
        final Identifier glowId;
        final Identifier flareId;
        final int size;
        final DynamicTexture body;
        final DynamicTexture glow;
        final DynamicTexture flare;
        @Nullable Key key;

        private Entry(int id, int size) {
            this.bodyId = BrightestDay.id("suit/" + id);
            this.glowId = BrightestDay.id("suit_glow/" + id);
            this.flareId = BrightestDay.id("eye_flare/" + id);
            this.size = size;
            this.body = new DynamicTexture(this.bodyId::toString, size, size, true);
            this.glow = new DynamicTexture(this.glowId::toString, size, size, true);
            this.flare = new DynamicTexture(this.flareId::toString, size, size, true);
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            textures.register(this.bodyId, this.body);
            textures.register(this.glowId, this.glow);
            textures.register(this.flareId, this.flare);
        }

        private void close() {
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            textures.release(this.bodyId);
            textures.release(this.glowId);
            textures.release(this.flareId);
        }
    }

    private SuitTextures() {}
}
