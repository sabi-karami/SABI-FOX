# SABI-FOX Windows -- PROXY-ONLY DEVELOPMENT PREVIEW

This is an experimental unsigned build, not an official production release.
The browser sign-in is experimental and has not been tested with a real account.
Sign in only on the official Mozilla page. Paste the resulting complete redirect
address only into the app, never into chat or another site. The address contains
a sensitive short-lived authorization code; do not share or screenshot it.
Do not rely on this preview for privacy or security. No account switching or
cross-device transfer exists.

ONLY the local SOCKS5/HTTP proxy mode is available. The program does not route
all Windows traffic, install a tunnel or configure the system proxy. IPv6 and
DNS leak protection and network behavior have not been validated. No automatic
update is enabled. Windows security warnings are expected because the app is
not digitally signed. Close the app to stop the proxy. Do not install a separate
unverified tunnel engine into this preview.

Source: based on https://github.com/M-RTZ1/FoxyVPN commit
cc2e169207d8866e92be20216dfed8069068d4de under the MIT license; original
LICENSE and third-party notices are included. SABI-FOX is not affiliated with
Mozilla. Original Firefox/FoxyVPN names in notices identify their owners.

Extract the entire zip, then run SABI-FOX.exe. The build has not been tested on
a physical Windows machine.


## UI redesign preview (0.2.0)

The Windows UI was restyled to match the Android "Nocturne Aurora" design
(theme, home orb, guided browser sign-in, settings cards, new app icon).
Functionality is unchanged: still local SOCKS5 proxy only (127.0.0.1:21080),
no system-wide tunnel, no kill switch, unsigned and experimental. Sign-in has
had only limited manual testing; the new UI has not been tested on a physical
Windows device.
