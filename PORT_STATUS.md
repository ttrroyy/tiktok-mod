# Status of this port

Base: original TikTok 47.5.3, versionCode 2024705030, package
`com.zhiliaoapp.musically`. Foundation: Hushfeed 0.78.0, upstream commit
`c3e0c1845aa98e0cee7aa97d596f0cb9ba1807e8`, GPL-3.0. This is an independent
customization; no MN native library or MN source is included.

## Main features

- Region and SIM overrides: country ISO, MCC/MNC, operator name, network/SIM country
  getters and supported cell identity getters. Region presets and separate region settings
  are available. These affect the application's view of the device; they do not hide the
  IP address or override TikTok's server-side account restrictions. Enable the overrides
  in the Region page and restart TikTok. Sign-in requests retain their special handling.
- Download video and images without a TikTok watermark where the original media URL
  is available. Extra download controls cover audio, stickers, paths and naming. A missing
  clean source is a failure to report, not a reason to promise a watermark-free copy.
- Feed advertising and content filters, keywords and repetition filtering, navigation
  controls, playback/background audio and loop settings, sharing-link cleanup, privacy
  switches, comment tools, fonts and diagnostic options supplied by the selected patches.
- First native settings entry: **Функции мода**. Black/gray or light surfaces and TikTok's
  pink accent replace the reference mod's blue glass style.
- Voluntary update button: GitHub release metadata, compatible ABI, progress and cancel,
  size/SHA-256 checks, matching package and installed certificate, Android installer.
  GitHub tokens and signing private keys are never bundled. No startup update prompt.

The exact selected patch names are in `release-profile.json`. The full upstream patch
catalog is larger; the release does not apply every catalog entry.

## Limits and acceptance

This first release is a testing build. Compilation, unit checks, APK patch reports and
signature verification are separate from device acceptance. No personal account has
been used to test sign-in, uploads, SIM/region behavior or downloads on a physical phone.
Google login has the upstream fix selected, but every login provider cannot be promised:
re-signing can affect providers that depend on the original signing certificate.

There is no verified equivalent here for bundled iOS emoji, recovery of deleted messages,
or suppression of message read receipts. Offline list settings follow the upstream limit
of 10,000. A list size of 50,000 from the reference screenshots is not implemented.

Only ARM64, ARMv7 and universal ARM APKs can be built from the supplied original APK.
It contains no x86/x86_64 native libraries. Root is not required. Installation over the
official app or a different mod is prevented by Android's different-certificate rule;
removing such an app can remove its local drafts and other application data.

The repository is private at the owner's request. Releases can be downloaded by its
authorized users. Anonymous in-app updating becomes available after the owner makes
the repository public. Later releases must increase `UpdateSpec.CURRENT_BUILD`, publish
matching `update.json`, retain the package name and use the same private signing key.
A newer TikTok base needs patch compatibility work and another build; it is not automatic.

## Building

Follow the upstream Java/Android SDK and GitHub Packages dependency instructions in
`UPSTREAM.md`. Run `:extensions:tiktok:testDebugUnitTest`, `:patches:test`,
`:patches:generatePatchesList` and `:patches:buildAndroid`. The release bundle is under
`patches/build/release/`, not an intermediate JAR. Pass `release-profile.json` selections
to the desktop patcher, then zipalign and sign the APK. Keep the vendor APK, SDK, caches,
credentials, signing key and keystore passwords outside version control.

The updater manifest has `build`, `version`, `changes`, and `assets`. Asset keys are
`arm64-v8a`, `armeabi-v7a`, and `universal`; each has a GitHub release `url`, lowercase
64-character `sha256`, and exact `size` in bytes. Publication is explicit and installation
always goes through Android's confirmation.
