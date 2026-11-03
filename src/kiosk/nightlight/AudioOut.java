// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Streams the lullaby to the media volume on its own thread while the night light shows. It reads the settings and
 * the session itself every 50 ms, so the music follows the timeline without any timers.
 */
final class AudioOut implements Runnable {
    interface Source {
        Config config();
        long elapsed();
        void log(String message);
    }

    private static final int RATE = 22050;
    private static final long STOP_FADE_NS = 1_500_000_000L;

    private final Source source;
    private volatile boolean running = true;
    private volatile long stopRequested;
    private final Thread thread;

    AudioOut(Source source) {
        this.source = source;
        thread = new Thread(this, "night-light-audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
    }

    void start() {
        thread.start();
    }

    /** Fades out over about a second, then releases the speaker. */
    void stop() {
        if (!running) return;
        stopRequested = System.nanoTime();
        running = false;
    }

    /** Stops right away, for plugin shutdown. */
    void stopNow() {
        stop();
        thread.interrupt();
    }

    boolean alive() {
        return running && thread.isAlive();
    }

    @Override public void run() {
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bytes = Math.max(min, RATE / 4 * 2);
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            loop(track);
        } catch (InterruptedException ignored) {
            // Plugin shutdown.
        } catch (Throwable error) {
            source.log("Night light audio stopped: " + error);
        } finally {
            if (track != null) {
                try { track.pause(); track.flush(); } catch (Throwable ignored) { }
                track.release();
            }
        }
    }

    private void loop(AudioTrack track) throws InterruptedException {
        LullabySynth synth = new LullabySynth(RATE, System.nanoTime());
        Scene.Mix mix = new Scene.Mix();
        short[] chunk = new short[RATE / 20];
        boolean playing = false;
        while (!Thread.currentThread().isInterrupted()) {
            Config c = source.config();
            Scene.mix(c, source.elapsed(), mix);
            boolean stopping = !running;
            double master = stopping ? 0 : Math.pow(c.volume, 1.5);
            boolean silent = master * Math.max(mix.music, mix.waves) < 1e-4;
            synth.setDreamy(c.sound == Config.Sound.DREAMY);
            synth.setLevels(master, mix.music, mix.waves, mix.tempo);

            if (stopping && (synth.currentMaster() < 0.002 || System.nanoTime() - stopRequested > STOP_FADE_NS)) return;
            if (silent && synth.currentMaster() * Math.max(mix.music, mix.waves) < 1e-4 && !stopping) {
                // Nothing to hear, for example the silent sleep light: rest the speaker and check again shortly.
                if (playing) { track.pause(); track.flush(); playing = false; }
                Thread.sleep(1000);
                continue;
            }
            if (!playing) { track.play(); playing = true; }
            synth.render(chunk, 0, chunk.length);
            int offset = 0;
            while (offset < chunk.length) {
                int wrote = track.write(chunk, offset, chunk.length - offset);
                if (wrote < 0) throw new IllegalStateException("AudioTrack write failed: " + wrote);
                offset += wrote;
            }
        }
    }
}
