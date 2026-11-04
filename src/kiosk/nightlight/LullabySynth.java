// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.Random;

/**
 * A small generative lullaby: a music box (or soft bell) melody in 3/4 over warm pads and a soft bass, plus an
 * ocean-waves layer. Every phrase is new but follows a repeating motif, so it sounds composed rather than random.
 * It can also play classic lullabies on the music box instead, and a white, pink or brown noise, rain or
 * thunderstorm layer. Everything is synthesised here, so there are no audio
 * files and nothing to license: the melodies are long in the public domain.
 *
 * Pure Java with no Android classes, so tests can render it on the desktop. Not thread-safe: call it from the audio
 * thread only.
 */
final class LullabySynth {
    private static final int TABLE = 4096;
    private static final float[] SIN = new float[TABLE];
    static {
        for (int i = 0; i < TABLE; i++) SIN[i] = (float) Math.sin(2 * Math.PI * i / TABLE);
    }

    /** Key of F major, the melody's major pentatonic over two octaves. */
    private static final int KEY = 65;
    private static final int[] PENTA = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24};
    /** Chord roots relative to the key and whether each is minor: I vi IV V I iii IV V, two bars each. */
    private static final int[] ROOTS = {0, 9, 5, 7, 0, 4, 5, 7};
    private static final boolean[] MINOR = {false, true, false, false, false, true, false, false};
    private static final int EIGHTHS_PER_BAR = 6;

    /** A written-out tune in C: notes and chords as pairs of (semitones from C, length in eighths). -1 rests. */
    private static final class Song {
        final int[] notes;
        final int[] chords;
        final int bar;
        final int length;

        Song(int bar, int[] notes, int[] chords) {
            this.bar = bar;
            this.notes = notes;
            this.chords = chords;
            int sum = 0;
            for (int i = 1; i < notes.length; i += 2) sum += notes[i];
            length = sum;
        }
    }

    private static final int C = 0, D = 2, E = 4, F = 5, G = 7, A = 9, B = 11, HC = 12, REST = -1;
    private static final Song[] SONGS = {
        // Brahms, Wiegenlied (Lullaby), Op. 49 No. 4.
        new Song(6, new int[] {
            REST, 4, E, 1, E, 1,
            G, 3, E, 1, E, 2,
            G, 4, E, 1, G, 1,
            HC, 2, B, 3, A, 1,
            A, 2, G, 2, D, 1, E, 1,
            F, 2, D, 2, D, 1, E, 1,
            F, 4, D, 1, F, 1,
            B, 1, A, 1, G, 2, B, 2,
            HC, 4, C, 1, C, 1,
            HC, 4, A, 1, F, 1,
            G, 4, E, 1, C, 1,
            F, 2, G, 2, A, 2,
            G, 4, C, 1, C, 1,
            HC, 4, A, 1, F, 1,
            G, 4, E, 1, C, 1,
            F, 2, E, 2, D, 2,
            C, 6,
        }, new int[] {
            C, 6, C, 6, C, 6, G, 6, G, 6, G, 6, G, 6, G, 6, C, 6,
            F, 6, C, 6, G, 6, C, 6, F, 6, C, 6, G, 6, C, 6,
        }),
        // Mozart's theme for Ah vous dirai-je, maman (Twinkle, Twinkle, Little Star), K. 265.
        new Song(8, new int[] {
            C, 2, C, 2, G, 2, G, 2, A, 2, A, 2, G, 4,
            F, 2, F, 2, E, 2, E, 2, D, 2, D, 2, C, 4,
            G, 2, G, 2, F, 2, F, 2, E, 2, E, 2, D, 4,
            G, 2, G, 2, F, 2, F, 2, E, 2, E, 2, D, 4,
            C, 2, C, 2, G, 2, G, 2, A, 2, A, 2, G, 4,
            F, 2, F, 2, E, 2, E, 2, D, 2, D, 2, C, 4,
        }, new int[] {
            C, 8, F, 4, C, 4, F, 4, C, 4, G, 4, C, 4,
            C, 4, F, 4, C, 4, G, 4, C, 4, F, 4, C, 4, G, 4,
            C, 8, F, 4, C, 4, F, 4, C, 4, G, 4, C, 4,
        }),
    };
    /** Quiet eighths between one song and the next. */
    private static final int SONG_GAP = 12;

    /**
     * Noise colours: white is hiss, pink is softer, brown is a low rumble like a distant waterfall. Rain is a soft
     * hiss with random drops; a storm adds distant thunder now and then.
     */
    enum Noise { WHITE, PINK, BROWN, RAIN, STORM }

    private static final double DROPS_PER_SECOND = 120;

    private final int rate;
    private final Random rnd;

    // Levels, smoothed per sample so changes never click.
    private double masterTarget, musicTarget, wavesTarget, noiseTarget;
    private double master, music, waves, noise;
    private final double smoothK;
    private double tempo = 1;
    private boolean dreamy;
    private boolean songs;
    private Noise noiseColour = Noise.BROWN;

    // Song position.
    private long step = -1;
    private double untilNextStep;
    private final boolean[] motifRhythm = new boolean[12];
    private final int[] motifSteps = new int[12];
    private int melodyIndex = 5;
    private int motifStart = 5;
    private int song;
    private int songAt;
    private int noteI;
    private int noteAt;
    private int chordI;
    private int chordAt;

    // Voices.
    private final Bell[] bells = new Bell[24];
    private final Pad[] pads = new Pad[12];
    private double lfoPhase;

    // Effects.
    private final float[] delay;
    private int delayPos;
    private double delayDamp;
    private double bellLow;

    // Waves.
    private double wavePhaseA, wavePhaseB = 0.37, brown, waveLow, waveCoef;
    private int waveCounter;

    // Noise.
    private double pink0, pink1, pink2, brownNoise;

    // Rain and thunder.
    private final double dropDecay;
    private final double thunderK;
    private double rainLow, rainDrops, dropEnv, gustPhase;
    private double thunderA, thunderB, rollPhase, rollHz, thunderRise, thunderLoud;
    private long noiseClock, thunderAt = -1, thunderStart = -1, thunderLength;

    LullabySynth(int sampleRate, long seed) {
        rate = sampleRate;
        rnd = new Random(seed);
        smoothK = 1 - Math.exp(-1.0 / (0.3 * rate));
        delay = new float[(int) (0.42 * rate)];
        dropDecay = Math.exp(-1 / (0.006 * rate));
        thunderK = 1 - Math.exp(-2 * Math.PI * 300 / rate);
        for (int i = 0; i < bells.length; i++) bells[i] = new Bell();
        for (int i = 0; i < pads.length; i++) pads[i] = new Pad();
        newMotif();
    }

    /** Dreamy pads: slower, lower and softer than the music box. */
    void setDreamy(boolean dreamy) {
        this.dreamy = dreamy;
    }

    /** Classic lullabies on the music box, instead of making the tune up. */
    void setSongs(boolean songs) {
        if (songs == this.songs) return;
        this.songs = songs;
        song = 0;
        startSong();
    }

    void setNoise(Noise colour) {
        if (colour != null) noiseColour = colour;
    }

    /** master, music and waves from 0 to 1; tempo multiplies the speed, 0.5 to 1.5. */
    void setLevels(double master, double music, double waves, double tempo) {
        setLevels(master, music, waves, 0, tempo);
    }

    /** master, music, waves and noise from 0 to 1; tempo multiplies the speed, 0.5 to 1.5. */
    void setLevels(double master, double music, double waves, double noise, double tempo) {
        masterTarget = clamp(master);
        musicTarget = clamp(music);
        wavesTarget = clamp(waves);
        noiseTarget = clamp(noise);
        this.tempo = Math.max(0.5, Math.min(1.5, tempo));
    }

    /** Jump the smoothed levels straight to their targets, for the first buffer. */
    void settle() {
        master = masterTarget;
        music = musicTarget;
        waves = wavesTarget;
        noise = noiseTarget;
    }

    double currentMaster() {
        return master;
    }

    void render(short[] out, int offset, int count) {
        double eighth = rate * 60.0 / (bpm() * tempo) / 2;
        for (int n = 0; n < count; n++) {
            master += (masterTarget - master) * smoothK;
            music += (musicTarget - music) * smoothK;
            waves += (wavesTarget - waves) * smoothK;
            noise += (noiseTarget - noise) * smoothK;

            if (--untilNextStep <= 0) {
                step++;
                untilNextStep += eighth * (1 + (rnd.nextDouble() - 0.5) * 0.03);
                if (music > 1e-4 || musicTarget > 1e-4) onStep();
            }

            double mixed = 0;
            if (music > 1e-4) mixed += music * renderMusic();
            if (waves > 1e-4) mixed += waves * renderWaves();
            if (noise > 1e-4) mixed += noise * renderNoise();
            double x = mixed * master * 1.6;
            // Gentle soft clip, then leave headroom.
            if (x > 3) x = 3; else if (x < -3) x = -3;
            x = x * (27 + x * x) / (27 + 9 * x * x);
            int s = (int) Math.round(x * 0.85 * 32767);
            out[offset + n] = (short) Math.max(-32767, Math.min(32767, s));
        }
    }

    private double bpm() {
        return dreamy ? 50 : 64;
    }

    // ---- Composition ------------------------------------------------------------------------------------------

    private void onStep() {
        if (songs) {
            playSong();
            return;
        }
        int bar = (int) (step / EIGHTHS_PER_BAR);
        int slot = (int) (step % EIGHTHS_PER_BAR);
        int chord = (bar / 2) % ROOTS.length;
        int section = bar % 8;
        if (slot == 0) {
            if (bar % 8 == 0) newMotif();
            if (bar % 2 == 0) changePads(chord);
            bass(chord);
        }
        int motifSlot = (section % 2) * EIGHTHS_PER_BAR + slot;
        boolean strong = slot % 2 == 0;
        if (section < 4) {
            // Bars 0-3: the motif, then the motif again over the next chord.
            if (motifSlot == 0) melodyIndex = snap(section < 2 ? motifStart : motifStart + 1, chord);
            if (!motifRhythm[motifSlot]) return;
            if (motifSlot > 0) melodyIndex = range(melodyIndex + motifSteps[motifSlot]);
            if (strong) melodyIndex = snap(melodyIndex, chord);
            bell(melodyIndex, slot == 0 ? 0.75 : 0.55);
        } else if (section < 6) {
            // Bars 4-5: a freer answer.
            double chance = slot == 0 ? 1 : strong ? 0.55 : 0.15;
            if (dreamy) chance *= 0.7;
            if (rnd.nextDouble() > chance) return;
            melodyIndex = range(melodyIndex + walk());
            if (strong) melodyIndex = snap(melodyIndex, chord);
            bell(melodyIndex, slot == 0 ? 0.7 : 0.5);
        } else {
            // Bars 6-7: settle on a long chord tone and rest.
            if (section == 6 && (slot == 0 || slot == 2 || (slot == 4 && rnd.nextBoolean()))) {
                melodyIndex = range(melodyIndex + (melodyIndex > 5 ? -1 : 1));
                if (strong) melodyIndex = snap(melodyIndex, chord);
                bell(melodyIndex, 0.55);
            } else if (section == 7 && slot == 0) {
                melodyIndex = snap(range(melodyIndex - 1), chord);
                bell(melodyIndex, 0.6);
            }
        }
    }

    private void startSong() {
        songAt = noteI = noteAt = chordI = chordAt = 0;
    }

    private void playSong() {
        Song s = SONGS[song];
        if (songAt >= s.length + SONG_GAP) {
            song = (song + 1) % SONGS.length;
            startSong();
            s = SONGS[song];
        }
        if (chordI < s.chords.length && chordAt == songAt) {
            int root = s.chords[chordI];
            changePads(root, false);
            bassNote(root);
            chordAt += s.chords[chordI + 1];
            chordI += 2;
        }
        if (noteI < s.notes.length && noteAt == songAt) {
            int note = s.notes[noteI];
            if (note != REST) bellNote(KEY + note, songAt % s.bar == 0 ? 0.75 : 0.55);
            noteAt += s.notes[noteI + 1];
            noteI += 2;
        }
        // Let the last chord fade away during the gap.
        if (songAt == s.length) for (Pad p : pads) p.target = 0;
        songAt++;
    }

    private void newMotif() {
        for (int i = 0; i < motifRhythm.length; i++) {
            int slot = i % EIGHTHS_PER_BAR;
            double chance = slot == 0 ? 1 : slot == 2 ? 0.6 : slot == 4 ? 0.7 : 0.18;
            if (dreamy && slot != 0) chance *= 0.6;
            motifRhythm[i] = rnd.nextDouble() < chance;
            motifSteps[i] = walk();
        }
        motifStart = 3 + rnd.nextInt(4);
    }

    private int walk() {
        int[] choices = {-2, -1, -1, 0, 1, 1, 2};
        int d = choices[rnd.nextInt(choices.length)];
        // Drift back towards the middle of the range.
        if (melodyIndex > 7 && d > 0) d = -d;
        if (melodyIndex < 3 && d < 0) d = -d;
        return d;
    }

    private static int range(int index) {
        return Math.max(1, Math.min(PENTA.length - 2, index));
    }

    /** The nearest pentatonic index whose note belongs to the chord. */
    private static int snap(int index, int chord) {
        index = range(index);
        for (int d = 0; d < PENTA.length; d++) {
            if (index - d >= 0 && inChord(PENTA[index - d], chord)) return range(index - d);
            if (index + d < PENTA.length && inChord(PENTA[index + d], chord)) return range(index + d);
        }
        return index;
    }

    private static boolean inChord(int semis, int chord) {
        int pc = ((semis - ROOTS[chord]) % 12 + 12) % 12;
        return pc == 0 || pc == 7 || pc == (MINOR[chord] ? 3 : 4);
    }

    private int melodyMidi(int index) {
        return KEY + PENTA[index] - (dreamy ? 12 : 0);
    }

    // ---- Voices -----------------------------------------------------------------------------------------------

    private void bell(int index, double velocity) {
        bellNote(melodyMidi(index), velocity);
    }

    private void bellNote(int midi, double velocity) {
        Bell b = free();
        b.start(midiHz(midi), velocity * (0.85 + 0.3 * rnd.nextDouble()) * (dreamy ? 0.32 : 0.30),
            dreamy ? 2.6 : 1.5, dreamy ? 0.04 : 0.004, dreamy, false);
    }

    private void bass(int chord) {
        bassNote(ROOTS[chord]);
    }

    private void bassNote(int root) {
        int midi = KEY - 24 + root;
        if (midi > 52) midi -= 12;
        free().start(midiHz(midi), dreamy ? 0.09 : 0.11, 2.4, 0.02, true, true);
    }

    private Bell free() {
        Bell quietest = bells[0];
        for (Bell b : bells) {
            if (!b.active) return b;
            if (b.level() < quietest.level()) quietest = b;
        }
        return quietest;
    }

    private void changePads(int chord) {
        changePads(ROOTS[chord], MINOR[chord]);
    }

    private void changePads(int root, boolean minor) {
        for (Pad p : pads) p.target = 0;
        int third = minor ? 3 : 4;
        int[] tones = {0, third, 7};
        double level = dreamy ? 0.075 : 0.045;
        for (int tone : tones) {
            int midi = KEY - 12 + root + tone;
            while (midi >= 64) midi -= 12;
            while (midi < 52) midi += 12;
            Pad p = freePad();
            p.start(midiHz(midi), level);
        }
    }

    private Pad freePad() {
        Pad quietest = pads[0];
        for (Pad p : pads) {
            if (p.amp < 1e-4 && p.target == 0) return p;
            if (p.amp < quietest.amp) quietest = p;
        }
        return quietest;
    }

    private double renderMusic() {
        lfoPhase += 0.18 / rate;
        if (lfoPhase >= 1) lfoPhase -= 1;
        double tremolo = 1 - 0.12 * (0.5 + 0.5 * sin(lfoPhase));

        double bellSum = 0;
        double bassSum = 0;
        for (Bell b : bells) {
            if (!b.active) continue;
            if (b.bass) bassSum += b.next();
            else bellSum += b.next();
        }
        double padSum = 0;
        for (Pad p : pads) padSum += p.next();

        // Soften the bells a touch, then send them to the echo.
        bellLow += (bellSum - bellLow) * 0.55;
        double echo = delay[delayPos];
        delayDamp += (echo - delayDamp) * 0.35;
        delay[delayPos] = (float) (bellLow + delayDamp * 0.38);
        if (++delayPos >= delay.length) delayPos = 0;

        return bellLow + echo * 0.3 + bassSum + padSum * tremolo;
    }

    private double renderWaves() {
        // Two slow swells that drift in and out of step, like waves on a beach.
        wavePhaseA += 1.0 / (9.3 * rate);
        wavePhaseB += 1.0 / (13.7 * rate);
        if (wavePhaseA >= 1) wavePhaseA -= 1;
        if (wavePhaseB >= 1) wavePhaseB -= 1;
        double a = 0.5 - 0.5 * cos(wavePhaseA);
        double b = 0.5 - 0.5 * cos(wavePhaseB);
        double env = 0.08 + 0.55 * a * a + 0.37 * b * b;
        if (--waveCounter <= 0) {
            waveCounter = 64;
            double cutoff = 250 + 1600 * env;
            waveCoef = 1 - Math.exp(-2 * Math.PI * cutoff / rate);
        }
        double white = rnd.nextDouble() * 2 - 1;
        brown = brown * 0.996 + white * 0.04;
        waveLow += (white - waveLow) * waveCoef;
        return (waveLow * 0.9 + brown * 0.8) * env * 0.55;
    }

    /** Noise at about the same loudness in every colour. */
    private double renderNoise() {
        double white = rnd.nextDouble() * 2 - 1;
        switch (noiseColour) {
            case WHITE:
                return white * 0.3;
            case PINK:
                // Paul Kellet's economy pink filter.
                pink0 = 0.99765 * pink0 + white * 0.0990460;
                pink1 = 0.96300 * pink1 + white * 0.2965164;
                pink2 = 0.57000 * pink2 + white * 1.0526913;
                return (pink0 + pink1 + pink2 + white * 0.1848) * 0.1;
            case RAIN:
                return rain(white);
            case STORM:
                return rain(white) + thunder();
            default:
                brownNoise = (brownNoise + 0.02 * white) / 1.02;
                return brownNoise * 3.0;
        }
    }

    /** Steady fine rain that swells a little, like gusts, with random drops landing on top. */
    private double rain(double white) {
        gustPhase += 1.0 / (7.3 * rate);
        if (gustPhase >= 1) gustPhase -= 1;
        rainLow += (white - rainLow) * 0.35;
        double bed = rainLow * (0.8 + 0.2 * sin(gustPhase));
        if (rnd.nextDouble() < DROPS_PER_SECOND / rate) {
            // Mostly small drops, the odd big one.
            double size = rnd.nextDouble();
            dropEnv = Math.max(dropEnv, size * size);
        }
        double drop = (rnd.nextDouble() * 2 - 1) * dropEnv;
        dropEnv *= dropDecay;
        rainDrops += (drop - rainDrops) * 0.5;
        return bed * 0.6 + rainDrops * 1.2;
    }

    /**
     * Distant thunder: a low rumble every minute or two that swells in over about a second, rolls, and dies away.
     * Never a sudden crack, so it does not wake anyone.
     */
    private double thunder() {
        noiseClock++;
        if (thunderAt < 0) thunderAt = noiseClock + (long) ((20 + 40 * rnd.nextDouble()) * rate);
        if (thunderStart < 0) {
            if (noiseClock < thunderAt) return 0;
            thunderStart = noiseClock;
            thunderLength = (long) ((6 + 6 * rnd.nextDouble()) * rate);
            thunderRise = 0.5 + 1.0 * rnd.nextDouble();
            thunderLoud = 0.5 + 0.5 * rnd.nextDouble();
            rollHz = 0.3 + 0.5 * rnd.nextDouble();
        }
        double t = (noiseClock - thunderStart) / (double) rate;
        double length = thunderLength / (double) rate;
        if (t >= length) {
            thunderStart = -1;
            thunderAt = noiseClock + (long) ((45 + 105 * rnd.nextDouble()) * rate);
            return 0;
        }
        rollPhase += rollHz / rate;
        if (rollPhase >= 1) rollPhase -= 1;
        double env = Math.min(1, t / thunderRise) * Math.exp(-2 * t / length) * Math.min(1, length - t)
            * (0.55 + 0.45 * (0.5 + 0.5 * sin(rollPhase)));
        double white = rnd.nextDouble() * 2 - 1;
        thunderA += (white - thunderA) * thunderK;
        thunderB += (thunderA - thunderB) * thunderK;
        return thunderB * 8.0 * thunderLoud * env;
    }

    private static double midiHz(int midi) {
        return 440 * Math.pow(2, (midi - 69) / 12.0);
    }

    private static double sin(double cycles) {
        return SIN[(int) (cycles * TABLE) & (TABLE - 1)];
    }

    private static double cos(double cycles) {
        return sin(cycles + 0.25);
    }

    private static double clamp(double x) {
        return Double.isNaN(x) ? 0 : x < 0 ? 0 : x > 1 ? 1 : x;
    }

    /** A plucked note: music-box tine, soft bell or bass. */
    private final class Bell {
        boolean active;
        boolean bass;
        double f, amp, attackStep, attack;
        double p1, p2, p3, p4;
        double e1, e2, e3, e4, m1, m2, m3, m4;
        double w2, w3, w4;

        void start(double hz, double velocity, double decaySeconds, double attackSeconds, boolean soft, boolean isBass) {
            active = true;
            bass = isBass;
            f = hz / rate;
            amp = velocity;
            attack = 0;
            attackStep = 1.0 / Math.max(1, attackSeconds * rate);
            p1 = p2 = p3 = p4 = 0;
            e1 = e2 = e3 = e4 = 1;
            m1 = Math.exp(-1 / (decaySeconds * rate));
            m2 = Math.exp(-1 / (decaySeconds * 0.45 * rate));
            m3 = Math.exp(-1 / (decaySeconds * 0.22 * rate));
            m4 = Math.exp(-1 / (0.03 * rate));
            if (isBass) { w2 = 0.12; w3 = 0; w4 = 0; }
            else if (soft) { w2 = 0.12; w3 = 0.03; w4 = 0; }
            else { w2 = 0.24; w3 = 0.07; w4 = 0.035; }
        }

        double level() {
            return amp * e1;
        }

        double next() {
            if (attack < 1) attack = Math.min(1, attack + attackStep);
            p1 += f; p2 += f * 2; p3 += f * 4.03; p4 += f * 6.3;
            if (p1 >= 1) p1 -= 1;
            if (p2 >= 1) p2 -= 1;
            if (p3 >= 1) p3 -= Math.floor(p3);
            if (p4 >= 1) p4 -= Math.floor(p4);
            double v = sin(p1) * e1 + w2 * sin(p2) * e2 + w3 * sin(p3) * e3 + w4 * sin(p4) * e4;
            e1 *= m1; e2 *= m2; e3 *= m3; e4 *= m4;
            if (e1 < 1e-4) active = false;
            return v * amp * attack;
        }
    }

    /** A sustained chord tone with a slow swell and a slight chorus. */
    private final class Pad {
        double f, amp, target, pA, pB, pC;

        void start(double hz, double level) {
            f = hz / rate;
            target = level;
        }

        double next() {
            // About 2.5 s to swell in and 3 s to fade out.
            double k = target > amp ? 1.0 / (2.5 * rate) : 1.0 / (3.0 * rate);
            amp += (target - amp) * k * 4;
            if (amp < 1e-5 && target == 0) { amp = 0; return 0; }
            pA += f * 0.9985; pB += f * 1.0015; pC += f * 2;
            if (pA >= 1) pA -= 1;
            if (pB >= 1) pB -= 1;
            if (pC >= 1) pC -= 1;
            return amp * (0.5 * sin(pA) + 0.5 * sin(pB) + 0.1 * sin(pC));
        }
    }
}
