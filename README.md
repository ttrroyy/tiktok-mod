# TikTok — Функции мода

Version 0.1.1 uses a compact menu: Region, Downloads, Feed, Fonts, Extra features,
Mod diagnostics and Check for updates. Search, quick setup cards, project status,
pause, donation and promotional pages are absent. Controls use neutral colors with
green switches. The native entry uses TikTok's own filled settings icon.

SIM overriding is enabled by default and once when upgrading from 0.1.0. The chosen
country stays saved. Optional **Принудительный регион** filters For You by the country
in TikTok's video data, rejects unknown countries, and can leave the feed empty.

Customization of [Hushfeed](https://github.com/SysAdminDoc/HushFeed), targeting the
original TikTok 47.5.3 APK (`com.zhiliaoapp.musically`, version code `2024705030`).

The first item in TikTok settings opens **Функции мода**. The update button checks this
repository's latest release, shows its changes, downloads a compatible APK with a progress
bar, verifies its size, SHA-256, package and signing certificate, then opens Android's installer.
There is no embedded GitHub credential, automatic installation, forced update dialog,
advertising redirect or remote update policy. Anonymous updating requires a public repository;
while it is private, the button reports that no public release is available.

Priority checks are region/SIM overrides and downloads without watermarks. Region changes
inside the client do not change your IP or the server's account-region decisions.

This is a work in progress. A build or successful bytecode patch does not establish that
every runtime feature works on a physical phone. See PORT_STATUS.md for the current checks
and known gaps. No APK is committed to this source repository.

The source is based on upstream Hushfeed 0.78.0. Existing copyright, author and license
notices are retained. See LICENSE, NOTICE and [upstream documentation](UPSTREAM.md).
Build instructions and dependency requirements are in UPSTREAM.md under Building from source.

If TikTok closes while saves are still going, the next time you open it a banner names the ones that didn't finish, or that Hushfeed can't confirm, and how many of their files are missing. Files that already reached your gallery count as saved and are left alone. Nothing picks a save up again by itself, so save it again if you still want it. The note Hushfeed keeps for this stays inside TikTok's own storage and holds no links or tokens. On Android 9 and older, a file that's still being written is noted by its own path, which is the name it's saved under and can include the creator and the video ID, until it's confirmed. Each save's entry goes when the save ends or once the banner has named it.
