// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/** Colour sets for the soother, chosen to be soft rather than neon. Values are 0xRRGGBB. */
final class Palettes {
    static final String[] NAMES = {"Rainbow", "Ocean", "Sunset", "Aurora", "Candy"};

    static final int[] RAINBOW = {0xFF6B6B, 0xFFB86B, 0xFFE66B, 0x7BE07B, 0x6BC8FF, 0x8F7BFF, 0xE27BFF};
    static final int[] OCEAN = {0x1E6FD9, 0x18B5C9, 0x2AD4A8, 0x3A7BD5, 0x5B5BD6, 0x1F9ED1};
    static final int[] SUNSET = {0xFF7E5F, 0xFEB47B, 0xFF6F91, 0xC86DD7, 0xFF9A76, 0xF6C26B};
    static final int[] AURORA = {0x2AF598, 0x08AEEA, 0x7F5AF0, 0x2CB67D, 0x3D8BFD, 0xB15EFF};
    static final int[] CANDY = {0xFF9AC8, 0xB99CFF, 0x8FD3FF, 0x9CFFD9, 0xFFF29C, 0xFFC09C};

    static int[] named(String name) {
        switch (name == null ? "" : name) {
            case "Ocean": return OCEAN;
            case "Sunset": return SUNSET;
            case "Aurora": return AURORA;
            case "Candy": return CANDY;
            default: return RAINBOW;
        }
    }

    private Palettes() {}
}
