# Mandir TV: program feed to every TV, with no PC behind the TVs

One PC captures the program out and sends it over the fiber network. Each
Hisense TV with **Fire TV built in** runs a small app that plays it full screen.
The TVs need no extra PCs or decoder boxes.

```
 Switcher ──HDMI/SDI──► Program PC (OBS + MediaMTX relay)
                              │  one H.265 stream, ~25 Mbps at 4K30
                              ▼
                     10G fiber network / switches
             ┌────────────┬────────────┬────────────┐
             ▼            ▼            ▼            ▼
        Hisense TV   Hisense TV   Hisense TV   PC + TV (optional)
        "Mandir TV"  "Mandir TV"  "Mandir TV"  mpv player
          app          app          app
```

**Why not NDI straight to the TVs?** Full NDI is about 250 Mbps at 4K and is
decoded on the CPU. The TV's built-in Ethernet is usually 100 Mbps and its CPU
is too slow, so the picture would stutter. Instead, the program PC compresses
the feed with **H.265 (HEVC)** on its graphics card. The TV's own video chip
decodes H.265 in hardware, the same way it plays Netflix in 4K. You can keep
using NDI everywhere else.

| | 4K 30 | 1080p 30 |
|---|---|---|
| Bitrate per TV | 20–25 Mbps | 6–8 Mbps |
| 10 TVs total | ~250 Mbps | ~80 Mbps |
| Expected delay | ~0.5–1 s | ~0.5–1 s |

Start with 4K30. If any TV struggles, switch to 1080p30 (one setting in OBS).

---

## What's in this repo

| Folder | What it is |
|---|---|
| `firetv/` | The **Mandir TV** Android app for Fire TV (source code) |
| `sender/` | Scripts for the program PC (relay, firewall) |
| `windows/` | `install-on-firetv.bat` installs the app on a TV from a PC; `play-on-windows-tv.bat` is a player for a TV driven by a Windows PC |

---

## Step 1: Set up the program PC (once)

The program PC needs a graphics card or CPU with a hardware H.265 encoder.
Almost any NVIDIA GTX 10-series or newer card, Intel CPU from 7th gen onwards
(Quick Sync) or recent AMD card works.

1. **Give the PC a fixed IP address** (for example `192.168.1.50`), either in
   Windows network settings or as a DHCP reservation on your router. The TVs
   connect to this address.
2. **Install OBS Studio** (free): https://obsproject.com
3. **Install the relay (MediaMTX, free):**
   1. Download the latest `mediamtx_vX.Y.Z_windows_amd64.zip` from
      https://github.com/bluenviron/mediamtx/releases
   2. Copy this repo's `sender` folder to the PC (for example `C:\MandirTV\sender`).
   3. Unzip MediaMTX into a folder named `mediamtx` inside it, so you have
      `C:\MandirTV\sender\mediamtx\mediamtx.exe`.
   4. Right-click `allow-firewall.bat` and choose **Run as administrator** (once).
   5. Double-click `start-relay.bat`. Leave the window open; it restarts
      the relay automatically if it ever stops.
   6. To start the relay automatically with Windows: press `Win+R`, type
      `shell:startup`, and put a shortcut to `start-relay.bat` in that folder.
4. **Bring the program feed into OBS:**
   - From a capture card: *Sources → + → Video Capture Device* and pick the card.
   - If the program is already NDI on the network: install the free
     **DistroAV** (NDI) plugin for OBS, then *Sources → + → NDI Source*.
5. **OBS → Settings → Video**
   - Base and Output resolution: `3840x2160` (or `1920x1080` for 1080p)
   - FPS: `30`
6. **OBS → Settings → Output** (set *Output Mode* to **Advanced**), **Streaming** tab:
   - Video Encoder: **NVIDIA NVENC HEVC** (or *QuickSync HEVC* / *AMD HW H.265 (HEVC)*)
   - Rate control: **CBR**
   - Bitrate: **20000** Kbps for 4K30 (**7000** for 1080p30)
   - Keyframe interval: **1 s**
   - Preset: P4 (or "Quality"); Tuning: **Low latency**; Multipass: off
   - Profile: **main**; Look-ahead: off; Max B-frames: **0** (if shown)
   - Audio tab: AAC, 160–192 Kbps
7. **OBS → Settings → Stream**
   - Service: **Custom…**
   - Server: `srt://127.0.0.1:8890?streamid=publish:program&pkt_size=1316`
   - Stream key: leave empty
8. Click **Start Streaming**. The relay window should show a line like
   `[SRT] ... is publishing to path 'program'`.

The TVs now read the feed at `rtsp://192.168.1.50:8554/program` (replace with
your PC's IP). To check it from any PC on the network, open that address in VLC
(*Media → Open Network Stream*).

> Using vMix instead of OBS? Add an SRT output (caller mode) to the same address
> `srt://<relay-ip>:8890?streamid=publish:program`, with HEVC or H.264 encoding.

---

## Step 2: Install the app on each Hisense Fire TV (no PC needed)

Everything is done on the TV with the remote.

1. **Plug in a network cable** (wired is much more reliable than Wi-Fi).
2. **Install "Downloader":** on the Fire TV home screen, open **Find / Search**,
   type `Downloader`, and install the orange **Downloader** app (by AFTVnews).
   It's free.
3. **Allow Downloader to install apps:**
   1. Go to **Settings → My Fire TV** (on some TVs: *Device & Software*) **→ About**.
   2. Press **OK** on the TV / device name **7 times** until it says you are a developer.
   3. Go back to **My Fire TV → Developer options → Install unknown apps**
      (or *Apps from Unknown Sources*) and set **Downloader** to **ON**.
4. **Open Downloader.** In the address box, type:
   ```
   github.com/dhruhit29/NDI/releases/latest/download/MandirTV.apk
   ```
   and press **Go**. It downloads the app and shows an **Install** button.
   Press **Install**, then **Open**. (Afterwards Downloader asks to delete the
   file; choose **Delete**, as it is no longer needed.)
5. **In Mandir TV**, type the program PC's IP address (for example
   `192.168.1.50`) and choose **Save and play**.
6. If the settings screen shows an **Allow auto-open** button, press it and
   switch **Mandir TV** to **ON**. The app can then open itself when the TV is
   switched on. If the TV says it has no screen for this, see
   *Auto-open on newer TVs* below.
7. Recommended for a display TV:
   - **Settings → Display & Sounds → Screensaver → Start time: Never**
   - **Settings → Display & Sounds → Power → Sleep / Auto standby: Off / Never**
     (the names differ a little between Hisense models)

Repeat for each TV. **To update** the app later, do step 4 again; your
settings are kept.

### Auto-open on newer TVs
TVs on older Fire TV software (Fire OS 7) open the app by themselves after
switch-on with no extra steps. Some newer ones (Fire OS 8) hide the permission
for this. On those TVs the app still works, but you open it from the home
screen after switching the TV on. Alternatively, grant the permission once from any Windows PC:

<details>
<summary>Grant auto-open from a Windows PC (optional)</summary>

1. On the TV: **My Fire TV → Developer options → ADB debugging → ON**, and
   note the TV's IP (**My Fire TV → About → Network**).
2. On the PC, download Google's **SDK Platform-Tools for Windows**
   (https://developer.android.com/tools/releases/platform-tools) and also
   `MandirTV.apk` (same link as in step 4, opened in a browser).
3. Put `install-on-firetv.bat` (from this repo's `windows` folder),
   `MandirTV.apk` and the `platform-tools` folder together, then run:
   ```
   install-on-firetv.bat <TV-IP> <PROGRAM-PC-IP>
   ```
4. When the TV asks **"Allow USB debugging?"**, tick *Always allow* and press **OK**.
</details>

---

## Using the app

- It opens full screen and plays the program feed. There are no menus on screen.
- **OK** or **☰ Menu** on the remote opens settings: program PC address and
  auto-start options. Typing only the IP (e.g. `192.168.1.50`) is enough.
- **Back twice** exits.
- The app first tries RTSP (port 8554, under 1 s delay). If that fails twice on a
  TV, it switches by itself to HLS (port 8888, about 1–2 s delay) and remembers
  that for next time. Both come from the same relay; nothing to set up.
- If the feed stops (OBS stopped, PC restarted, cable unplugged), the screen says
  *Waiting for the program feed* and reconnects by itself as soon as the feed is back.
- If the picture freezes, or falls more than 3 seconds behind live after a
  network hiccup, the app reconnects to get back to live.
- When the TV is switched on (from standby or after a power cut), the app opens
  by itself after a few seconds.

---

## TVs driven by a Windows PC (optional)

If a TV already has a PC attached, use `windows/play-on-windows-tv.bat`:

1. Install **mpv** (free player): https://mpv.io/installation/, or put
   `mpv.exe` in a folder named `mpv` next to the script.
2. Run `play-on-windows-tv.bat 192.168.1.50`. It plays full screen and keeps
   reconnecting if the feed stops.
3. To start it with Windows, put a shortcut to it (with the IP after the
   filename) in `shell:startup`.

---

## Troubleshooting

| What you see | What to check |
|---|---|
| "Waiting for the program feed (...)" forever | Is OBS streaming and is `start-relay.bat` running? Can a PC play `rtsp://<pc-ip>:8554/program` in VLC? Did you run `allow-firewall.bat` (it opens TCP 8554 and 8888)? Is the address in the app's settings correct? |
| Works in VLC but not on the TV | Make sure the TV is on the network (Settings shows its IP), and that a firewall or VLAN rule between the TV and the program PC allows TCP 8554 and 8888. |
| Picture stutters at 4K | Switch OBS to 1080p30 at 7000 Kbps, or lower the 4K bitrate to 15000. Use a cable, not Wi-Fi. |
| No picture but sound | Your TV may not support H.265. In OBS, pick the H.264 encoder (NVENC H.264 / QuickSync H.264) instead. |
| App doesn't open when the TV is switched on | Open the app's settings and check "Auto-open permission". If it says *NOT granted*, press **Allow auto-open**, or use the PC method under *Auto-open on newer TVs*. |
| Downloader says "404" / can't download | The GitHub repository must be **public** for the link to work (GitHub → Settings → General → Danger Zone → Change visibility). Check the address for typos. |
| Delay too big | Check OBS: keyframe interval 1 s, B-frames 0, Low-latency tuning. |

---

## For developers

- App source: `firetv/app/src/main/java/org/mandir/tvreceiver/`
  - `PlayerActivity.kt`: player, reconnects, freeze and delay watchdog
  - `SettingsActivity.kt`: settings screen
  - `KeepAliveService.kt`, `BootReceiver.kt`: re-open on wake / boot
- Built with Android Gradle Plugin 8.11, Kotlin 2.2 and AndroidX Media3 1.11
  (ExoPlayer). Media3 1.11 or later is needed: earlier versions cannot play the
  H.265 RTSP packets that MediaMTX sends.
- Build locally: `cd firetv && ./gradlew assembleRelease`. This needs the
  Android SDK (API 36) and JDK 17.
- Besides `rtsp://`, the app also accepts HLS (`http://.../index.m3u8`) and
  MPEG-TS over UDP (`udp://239.x.x.x:port`) addresses.
- Set the address from a PC:
  `adb shell am start -n org.mandir.tvreceiver/.PlayerActivity -e url rtsp://IP:8554/program`
- GitHub Actions builds the APK on every change and publishes it as the latest
  release, which is what the Downloader link points to.
- The APK is signed with `firetv/app/mandirtv.keystore`, which is kept in the
  repo so updates install over older versions. It is only meant for sideloading
  onto the mandir's own TVs.
