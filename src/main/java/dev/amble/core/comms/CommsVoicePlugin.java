package dev.amble.core.comms;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import dev.amble.BrightestDay;
import dev.amble.core.oath.OathCharge;
import dev.amble.core.oath.VoiceAnswers;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommsVoicePlugin implements VoicechatPlugin {
    public static final String CATEGORY = "ring_comms";
    public static final String MEGAPHONE_CATEGORY = "megaphone";
    private static final float[] BAND_LIMITS = {0.25F, 0.55F};
    private static final float[] BAND_GAIN = {2.4F, 1.5F, 1.1F};
    private static final float[] BAND_DRIVE = {1.6F, 3.0F, 4.5F};
    private static final float FALLOFF_STRETCH = 1.4F;

    private static final class Stream {
        final VoiceFilter filter;
        final OpusEncoder encoder;

        Stream(VoiceFilter filter, OpusEncoder encoder) {
            this.filter = filter;
            this.encoder = encoder;
        }

        byte[] encode(short[] samples) {
            return this.encoder.encode(this.filter.process(samples));
        }

        void reset() {
            this.filter.reset();
            this.encoder.resetState();
        }

        void close() {
            this.encoder.close();
        }
    }

    private static final class Voice {
        final OpusDecoder decoder;
        @Nullable Stream radio;
        final Stream[] bands = new Stream[BAND_GAIN.length];

        Voice(OpusDecoder decoder) {
            this.decoder = decoder;
        }

        Stream radio(VoicechatServerApi api) {
            if (this.radio == null) this.radio = new Stream(VoiceFilter.radio(), api.createEncoder());
            return this.radio;
        }

        Stream band(VoicechatServerApi api, int band) {
            if (this.bands[band] == null) this.bands[band] = new Stream(VoiceFilter.megaphone(BAND_GAIN[band], BAND_DRIVE[band]), api.createEncoder());
            return this.bands[band];
        }

        void reset() {
            this.decoder.resetState();
            if (this.radio != null) this.radio.reset();
            for (Stream stream : this.bands) {
                if (stream != null) stream.reset();
            }
        }

        void close() {
            this.decoder.close();
            if (this.radio != null) this.radio.close();
            for (Stream stream : this.bands) {
                if (stream != null) stream.close();
            }
        }
    }

    private static final Map<UUID, Voice> VOICES = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return BrightestDay.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        api.registerVolumeCategory(api.volumeCategoryBuilder()
                .setId(CATEGORY)
                .setName("Ring Comms")
                .setDescription("Teammates talking to you through your power ring")
                .build());
        api.registerVolumeCategory(api.volumeCategoryBuilder()
                .setId(MEGAPHONE_CATEGORY)
                .setName("Megaphones")
                .setDescription("Lanterns amplifying their voice with a hard-light megaphone")
                .build());
        Comms.setVoiceReady(id -> {
            VoicechatConnection connection = api.getConnectionOf(id);
            return connection != null && connection.isConnected() && !connection.isDisabled();
        });
        BrightestDay.LOGGER.info("Ring comms registered with Simple Voice Chat");
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;
        VoicechatServerApi api = event.getVoicechat();
        UUID id = sender.getPlayer().getUuid();

        boolean oath = OathCharge.listening(id);
        boolean answering = VoiceAnswers.listening(id);
        boolean megaphone = Megaphone.isActive(id);
        VoicechatConnection radio = radioReceiver(api, sender, id);
        if (!oath && !answering && !megaphone && radio == null) {
            Voice idle = VOICES.remove(id);
            if (idle != null) idle.close();
            return;
        }

        MicrophonePacket packet = event.getPacket();
        Voice voice = VOICES.computeIfAbsent(id, key -> new Voice(api.createDecoder()));
        byte[] opus = packet.getOpusEncodedData();
        short[] samples = null;
        if (opus.length == 0) {
            voice.reset();
        } else {
            samples = voice.decoder.decode(opus);
            if (oath) OathCharge.hear(id, samples);
            if (answering) VoiceAnswers.hear(id, samples);
        }

        if (radio != null) {
            byte[] data = samples == null ? opus : voice.radio(api).encode(samples);
            api.sendStaticSoundPacketTo(radio, packet.staticSoundPacketBuilder().opusEncodedData(data).category(CATEGORY).build());
        }
        if (megaphone) amplify(event, api, sender, voice, samples, opus);
    }

    private static @Nullable VoicechatConnection radioReceiver(VoicechatServerApi api, VoicechatConnection sender, UUID id) {
        UUID target = Comms.receiver(id);
        if (target == null) return null;
        VoicechatConnection receiver = api.getConnectionOf(target);
        if (receiver == null || !receiver.isConnected()) return null;
        if (receiver.getPlayer().getPlayer() instanceof ServerPlayer listener && sender.getPlayer().getPlayer() instanceof ServerPlayer speaker
                && listener.level() == speaker.level() && listener.distanceTo(speaker) <= api.getVoiceChatDistance()) return null;
        return receiver;
    }

    private static void amplify(MicrophonePacketEvent event, VoicechatServerApi api, VoicechatConnection sender, Voice voice, short @Nullable [] samples, byte[] opus) {
        event.cancel();
        de.maxhenkel.voicechat.api.ServerPlayer speaker = sender.getPlayer();
        UUID id = speaker.getUuid();
        float range = (float) (api.getVoiceChatDistance() * Megaphone.RANGE_MULTIPLIER);

        List<List<VoicechatConnection>> bands = new ArrayList<>(BAND_GAIN.length);
        for (int i = 0; i < BAND_GAIN.length; i++) bands.add(new ArrayList<>());
        for (de.maxhenkel.voicechat.api.ServerPlayer listener : api.getPlayersInRange(speaker.getServerLevel(), speaker.getPosition(), range)) {
            if (listener.getUuid().equals(id)) continue;
            VoicechatConnection connection = api.getConnectionOf(listener);
            if (connection == null || !connection.isConnected()) continue;
            bands.get(band(distance(speaker, listener) / range)).add(connection);
        }

        for (int band = 0; band < bands.size(); band++) {
            List<VoicechatConnection> listeners = bands.get(band);
            if (listeners.isEmpty()) continue;
            byte[] data = samples == null ? opus : voice.band(api, band).encode(samples);
            for (VoicechatConnection connection : listeners) {
                api.sendEntitySoundPacketTo(connection, event.getPacket().entitySoundPacketBuilder()
                        .entityUuid(id)
                        .distance(range * FALLOFF_STRETCH)
                        .opusEncodedData(data)
                        .category(MEGAPHONE_CATEGORY)
                        .build());
            }
        }
    }

    private static int band(float fraction) {
        for (int i = 0; i < BAND_LIMITS.length; i++) {
            if (fraction < BAND_LIMITS[i]) return i;
        }
        return BAND_LIMITS.length;
    }

    private static float distance(de.maxhenkel.voicechat.api.ServerPlayer a, de.maxhenkel.voicechat.api.ServerPlayer b) {
        double dx = a.getPosition().getX() - b.getPosition().getX();
        double dy = a.getPosition().getY() - b.getPosition().getY();
        double dz = a.getPosition().getZ() - b.getPosition().getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
