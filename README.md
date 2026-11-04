# Night Light

A bedtime night light for a child's room, built as a [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) screensaver.

Night Light has two modes:

- **Night light**: a dim, warm light that stays on all night, with optional quiet sound. The screensaver always opens on it.
- **Soother**: gentle sleepy music or sounds while the screen drifts slowly through soft colours. Press the **Soother** button in the corner to start it. Press **End soother**, or let it finish (30 minutes by default), and it fades back to the night light.

What it offers:

- **Sound made on the device.** A music box lullaby, dreamy pads or ocean waves, composed as it plays so it never repeats exactly. Or Brahms' Lullaby and Mozart's Twinkle, Twinkle on the music box. It slows down a little as the soother goes on. There are no audio files and nothing to download.
- **Soft colours.** The soother is a slow glow drifting through Rainbow, Ocean, Sunset, Aurora or Candy colours. Or pick the **Liquid** style: a few soft blobs of colour that slowly drift and blend into each other, then gather into the night light at the end. It dims gradually, so the night light is not a jump.
- **A warm night light.** Amber by default, with adjustable colour and brightness. Or turn on **Colour waves** and it drifts very slowly through the soother's colours instead, just as dim.
- **Sounds for each mode.** The soother and the night light choose from the same sounds: a music box lullaby, Brahms & Mozart lullabies, dreamy pads, ocean waves, white, pink or brown noise, or no sound. Each has its own choice and volume, so the soother can play a lullaby and the night light brown noise all night. White noise is a steady hiss, pink noise is softer and brown noise is a deep rumble.
- **Bedtime countdown.** Optionally shows a row of stars on the night light, about one per hour between bed time and up time (at most 10). They go out one by one through the night, so a child can see how long until it is time to get up.
- **Wakes gently.** If the screen is tapped mid-soother and the screensaver comes back within an hour, the soother carries on where it was.

## Using it

Either way works:

1. **As the screensaver.** Choose **Night Light (Night Light)** under **Screensaver > Screensaver mode**, or in a screensaver schedule entry, for example every evening from 7pm to 7am.
2. **On demand.** Run the **Show night light** or **Start soother** action from a gesture, the kiosk drawer or a Home Assistant button. It shows the screensaver right away and gives your usual one back once it is dismissed in the morning.

| Action | What it does |
| --- | --- |
| **Show night light** | Shows the night light, fading out the soother if it is playing |
| **Start soother** | Starts the soother, showing the screensaver first if needed |
| **End soother** | Fades the soother back to the night light over about 20 seconds |
| **Start or end soother** | Starts the soother, or ends it if it is playing. Handy for a single clap gesture |
| **Close Night Light** | Dismisses the screensaver and restores your usual one |

While the screensaver shows, a dim button in the bottom right corner switches between **Soother** and **End soother**. A single tap anywhere else does nothing, so little hands cannot close it by accident. **Double tap** to close the screensaver. A hint saying so shows for a few seconds after each touch.

The button needs a Kiosk Satellite release that passes taps to plugin screensavers. On older releases there is no button and any tap closes the screensaver, so use a gesture such as a clap with the **Start or end soother** action instead.

Each action can also be exposed to Home Assistant from the plugin page. You can then start the soother from a bedtime automation.

### Recommended Kiosk Satellite settings

Kiosk Satellite still controls when the screensaver starts and stops, so check these screensaver settings:

- **Turn off any screen-off timer** for the screensaver, or the night light goes dark when it fires.
- **Screensaver brightness**: set a lowish panel brightness. The night light's own brightness setting dims it further.
- **Motion wake**: turn it off, or someone moving in the room closes the screensaver.
- **Media volume**: sound plays on the device's media volume. The plugin's **Soother volume** and **Night light volume** settings sit on top of that.

### Night mode

Like the Clock screensaver's Night mode, this uses the device's light sensor. When the room is at or below the **Light level** (5 lx by default), the Soother button, the hint and the stars turn the **Night color**, a muted red by default. With **Hide button and stars** on they disappear instead, leaving only the night light. Night mode ends a quarter above the light level, so the colours never flicker. The night light itself keeps its own colour and brightness.

Kiosk Satellite's own corner widgets and At a Glance row only follow the Clock screensaver's Night mode, so they keep their usual colours over the night light.

## Settings

| Group | Setting | Default |
| --- | --- | --- |
| Soother | Soother length | 30 min |
| Soother | Soother sound | Music box lullaby |
| Soother | Soother volume | 40% |
| Soother | Soother colours | Rainbow |
| Soother | Colour style | Glow |
| Soother | Colour speed | Slow |
| Soother | Colour brightness | 70% |
| Soother | Fade to night light | 3 min |
| Night light | Colour waves | Off |
| Night light | Night light colour | #FF9440 (warm amber) |
| Night light | Night light brightness | 25% |
| Night light | Night light sound | No sound |
| Night light | Night light volume | 25% |
| Countdown | Bedtime countdown | Off |
| Countdown | Bed time | 19:00 |
| Countdown | Up time | 07:00 |
| Night mode | Night mode | Off |
| Night mode | Light level | 5 lx |
| Night mode | Night color | #A63A32 (muted red) |
| Night mode | Hide button and stars | Off |

Settings save automatically and apply straight away, even while the night light is showing.

## Permissions

The plugin declares `screensaver` to draw the night light, plus `screensaver.mode` and `host.control` so the actions can switch to it and start or dismiss it, and `host.read` to read the room's light level for **Night mode**. It reads nothing else from the kiosk and makes no network requests.

## Building

Requires Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools.

```sh
python3 tools/test.py
python3 tools/build.py
```

The ZIP lands in `dist/`. Install it for testing with **Plugin Manager > Developer Tools > Install from ZIP**.

To publish, push to `main`. The included workflow tests and builds the plugin, then releases it under the date: `v2026.1104.1` for the first release on 4 November 2026, `v2026.1104.2` for the second that day. Pushes that only change Markdown files or the licence do not release. You can still create a release by hand, and the workflow builds and attaches the plugin to it. Anyone can add the plugin with **Plugin Manager > Add plugin** and the repository URL.

## License

Apache-2.0. The plugin SDK in `sdk/` and the build tools in `tools/` come from the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins), also Apache-2.0.
