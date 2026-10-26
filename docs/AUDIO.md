# Murmur Audio

## Supported Formats

- MP3
- M4A / MP4 audio
- AAC (where supported)
- FLAC
- OGG/Vorbis
- Opus
- WAV

Codec compatibility is based on the actual playback stack (Media3/ExoPlayer), not universal claims.

## Audio Processing

Supported processors:
- ReplayGain (track/album gain with preamp)
- Equalizer (10-band parametric EQ with presets)
- Crossfeed (headphone simulation)
- Headroom Limiter (lookahead limiter with configurable attack/release)
- Balance (L/R control)
- Dither (TPDF dithering for bit-depth reduction)

## ReplayGain Honesty

If valid ReplayGain information exists, it is used. If it does not exist, normalization is not pretended to be active. "Automatic" only uses actually available trustworthy gain information.

## Gapless Honesty

Gapless playback is implemented where the underlying decoder/container/path supports it. Not promised for media where the platform cannot reliably provide it.

## Headroom / Clipping

EQ/gain changes do not silently cause clipping. Sensible headroom is used. Processing state is visible in the Sound screen.

## Bit-Perfect Claims

"Bit-perfect confirmed" is only displayed when the implementation can genuinely establish the relevant audio path. Otherwise "Not confirmed", "System audio path", or "Unknown" is shown.

Bit-perfect is NOT inferred from:
- Source being FLAC
- USB DAC existing
- Source/output sample rates matching
- Device advertising Hi-Res Audio

## Output Profiles

Optional per-output profiles for reliably identifiable devices:
- Wired IEM
- Bluetooth headphones
- USB DAC
- Speaker

Settings: EQ, gain, headroom, crossfeed, balance. Applied automatically only when output identity is reliable.

## Audio Route Testing

Tested:
- Speaker
- Wired headset
- Bluetooth
- USB DAC
- Disconnect/reconnect
- Route switching

Output changes handled safely without volume spikes. Position is preserved.

## Audio Focus

Handles transient loss, permanent loss, ducking, and regain. Does not fight other audio apps.

## No Fake Audio Features

No AI sound enhancement, fake remastering, quality scores, audiophile scores, golden-ear scoring, fake scientific meters, arbitrary quality colors, magical stereo enhancement, or fake analog superiority claims.

Every audio feature solves a real problem.