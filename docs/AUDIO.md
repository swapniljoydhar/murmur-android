# Murmur Audio

## Playback baseline

Murmur uses Media3/ExoPlayer for local audio playback. Actual codec support depends on the Android device and the selected source. The app does not currently promise gapless playback, bit-perfect output, or a particular hardware route.

## Processing status

ReplayGain, equalizer, crossfeed, limiter, balance, and dither are not exposed as user controls and are not attached to the active ExoPlayer renderer chain. Their processor prototypes must not be treated as working features. The old Sound screen was removed because its status labels reported configured values as active and inferred output bit depth/latency that Android did not provide.

No ReplayGain metadata is currently applied. Until processing is implemented and verified, Murmur makes no claims about clipping prevention, output-profile matching, bit-perfect playback, or measured audio quality.

## Validation boundary

Source inspection and build tests do not establish behavior across audio devices. Format support, focus changes, headset controls, Bluetooth reconnects, route switching, and gapless transitions require the deferred device validation matrix.
