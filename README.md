# Night Light

A bedtime night light for a child's room, built as a [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) screensaver.

When the screensaver starts it shows a dim, warm night light. Press the **Go to sleep** button in the corner and gentle sleepy music plays while the screen drifts slowly through soft colours. Press **End**, or wait for the show to finish (30 minutes by default), and the colours and music fade over a few minutes back to the warm night light.

- **Music made on the device.** A music box lullaby, dreamy pads or ocean waves, composed as it plays so it never repeats exactly. Or Brahms' Lullaby and Mozart's Twinkle, Twinkle on the music box. It slows down a little as bedtime goes on. There are no audio files and nothing to download.
- **Soft colours.** A slow glow drifting through Rainbow, Ocean, Sunset, Aurora or Candy colours. Or the **Liquid** style: a few soft blobs of colour that slowly drift and blend into each other, then gather into the warm light at the end. It dims gradually as the show goes on, so the sleep light is not a jump.
- **Warm sleep light.** Amber by default, with adjustable colour and brightness. Or turn on **Colour waves** and it drifts very slowly through the show's colours instead, just as dim. The sound can fade to silence, keep playing quietly or turn into ocean waves.
- **Bedtime countdown.** Optionally shows a row of stars on the night light, about one per hour between bed time and up time (at most 10). They go out one by one through the night, so a child can see how long until it is time to get up.
- **Wakes gently.** If the screen is tapped mid-show and the night light comes back within an hour, the show carries on where it was.

## Using it

Either way works:

1. **As the screensaver.** Choose **Night Light (Night Light)** under **Screensaver > Screensaver mode**, or in a screensaver schedule entry, for example every evening from 7pm to 7am.
2. **On demand.** Run the **Start night light** or **Go to sleep** action from a gesture, the kiosk drawer or a Home Assistant button. It shows the night light right away and gives your usual screensaver back once it is dismissed in the morning.

| Action | What it does |
| --- | --- |
| **Start night light** | Switches to the warm night light, fading out the show if it is playing |
| **Go to sleep** | Starts the colours and music, switching to the night light first if needed |
| **End go to sleep** | Fades the show back to the warm night light over about 20 seconds |
| **Go to sleep or end** | Starts the show, or ends it if it is playing. Handy for a single clap gesture |
| **Stop night light** | Dismisses it and restores your usual screensaver |

While the night light shows, a dim button in the bottom right corner switches between **Go to sleep** and **End**. A single tap anywhere else does nothing, so little hands cannot close it by accident. **Double tap** to close the screensaver. A hint saying so shows for a few seconds after each touch.

The button needs a Kiosk Satellite release that passes taps to plugin screensavers. On older releases there is no button and any tap closes the screensaver, so use a gesture such as a clap with the **Go to sleep or end** action instead.

Each action can also be exposed to Home Assistant from the plugin page. You can then start the night light from a bedtime automation.

### Recommended Kiosk Satellite settings

Kiosk Satellite still controls when the screensaver starts and stops, so check these screensaver settings:

- **Turn off any screen-off timer** for the screensaver, or the night light goes dark when it fires.
- **Screensaver brightness**: set a lowish panel brightness. The sleep light's own brightness setting dims it further.
- **Dismissal**: if little hands keep tapping it off, adjust how the screensaver is dismissed.
- **Media volume**: the music plays on the device's media volume. The plugin's **Volume** setting sits on top of that.

### Night mode

Like the Clock screensaver's Night mode, this uses the device's light sensor. When the room is at or below the **Light level** (5 lx by default), the Go to sleep button, the hint and the stars turn the **Night color**, a muted red by default. With **Hide button and stars** on they disappear instead, leaving only the night light. Night mode ends a quarter above the light level, so the colours never flicker. The night light itself keeps its own colour and brightness.

Kiosk Satellite's own corner widgets and At a Glance row only follow the Clock screensaver's Night mode, so they keep their usual colours over the night light.

## Settings

| Group | Setting | Default |
| --- | --- | --- |
| Colours | Colour show length | 30 min |
| Colours | Colour style | Glow |
| Colours | Colours | Rainbow |
| Colours | Colour speed | Slow |
| Colours | Colour brightness | 70% |
| Sleep light | Fade to sleep light | 3 min |
| Sleep light | Sleep light colour | #FF9440 (warm amber) |
| Sleep light | Sleep light brightness | 25% |
| Sound | Music | Music box lullaby |
| Sound | Volume | 40% |
| Sound | During the sleep light | Fade to silence |
| Countdown | Bedtime countdown | Off |
| Countdown | Bed time | 19:00 |
| Countdown | Up time | 07:00 |
| Night mode | Night mode | Off |
| Night mode | Light level | 5 lx |
| Night mode | Night color | #A63A32 (muted red) |
| Night mode | Hide button and stars | Off |

Settings save automatically and apply straight away, even while the night light is showing.

## Permissions

The plugin declares `screensaver` to draw the night light, plus `screensaver.mode` and `host.control` so the actions can switch to it and start or dismiss it, and `host.read` to read the room's light level for **Night mode**. It reads nothing from the kiosk and makes no network requests.

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
