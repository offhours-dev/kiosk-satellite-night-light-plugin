# Night Light

A bedtime night light for a child's room, built as a [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) screensaver.

When the night light starts, gentle sleepy music plays while the screen drifts slowly through soft colours. After the colour show (30 minutes by default) the colours and music fade over a few minutes into a dim, warm light that stays on for the rest of the night.

- **Music made on the device.** A music box lullaby, dreamy pads or ocean waves, composed as it plays so it never repeats exactly. It slows down a little as bedtime goes on. There are no audio files and nothing to download.
- **Soft colours.** A slow glow drifting through Rainbow, Ocean, Sunset, Aurora or Candy colours. It dims gradually as the show goes on, so the sleep light is not a jump.
- **Warm sleep light.** Amber by default, with adjustable colour and brightness. The sound can fade to silence, keep playing quietly or turn into ocean waves.
- **Wakes gently.** If the screen is tapped in the night and the night light comes back within an hour, it carries on where it was, so a child gets the sleep light back, not the colour show from the start.

## Using it

Either way works:

1. **As the screensaver.** Choose **Night Light (Night Light)** under **Screensaver > Screensaver mode**, or in a screensaver schedule entry, for example every evening from 7pm to 7am.
2. **On demand.** Run the **Start night light** action from a gesture, the kiosk drawer or a Home Assistant button. It shows the night light right away and gives your usual screensaver back once it is dismissed in the morning.

| Action | What it does |
| --- | --- |
| **Start night light** | Switches to the night light and starts it from the colour show |
| **Skip to sleep light** | Begins the fade to the warm sleep light now |
| **Stop night light** | Dismisses it and restores your usual screensaver |

Each action can also be exposed to Home Assistant from the plugin page. You can then start the night light from a bedtime automation.

### Recommended Kiosk Satellite settings

Kiosk Satellite still controls when the screensaver starts and stops, so check these screensaver settings:

- **Turn off any screen-off timer** for the screensaver, or the night light goes dark when it fires.
- **Screensaver brightness**: set a lowish panel brightness. The sleep light's own brightness setting dims it further.
- **Dismissal**: if little hands keep tapping it off, adjust how the screensaver is dismissed.
- **Media volume**: the music plays on the device's media volume. The plugin's **Volume** setting sits on top of that.

## Settings

| Group | Setting | Default |
| --- | --- | --- |
| Colours | Colour show length | 30 min |
| Colours | Colours | Rainbow |
| Colours | Colour speed | Slow |
| Colours | Colour brightness | 70% |
| Sleep light | Fade to sleep light | 3 min |
| Sleep light | Sleep light colour | #FF9440 (warm amber) |
| Sleep light | Sleep light brightness | 25% |
| Sound | Music | Music box lullaby |
| Sound | Volume | 40% |
| Sound | During the sleep light | Fade to silence |

Settings save automatically and apply straight away, even while the night light is showing.

## Permissions

The plugin declares `screensaver` to draw the night light, plus `screensaver.mode` and `host.control` so the **Start** and **Stop** actions can switch to it and start or dismiss it. It reads nothing from the kiosk and makes no network requests.

## Building

Requires Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools.

```sh
python3 tools/test.py
python3 tools/build.py
```

The ZIP lands in `dist/`. Install it for testing with **Plugin Manager > Developer Tools > Install from ZIP**.

To publish, push this repository to GitHub and create a release, for example tagged `v1.0.0`. The included workflow builds and attaches the plugin. Anyone can then add it with **Plugin Manager > Add plugin** and the repository URL.

## License

Apache-2.0. The plugin SDK in `sdk/` and the build tools in `tools/` come from the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins), also Apache-2.0.
