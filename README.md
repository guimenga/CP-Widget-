# CP Widget

Android home-screen widget (Android 12+) that shows the departure board of a
[CP – Comboios de Portugal](https://www.cp.pt) station: time, destination and
status (on time ✓, delayed with the new estimated time, or *Suprimido*).

- Scrollable list of all upcoming departures (rolls into tomorrow late at night).
- One station per widget; add several widgets for several stations.
- Refreshes **only when you tap ↻** (no background battery use). The header shows when it was last updated.
- Tap the station name to change station. Tap a train to open the station page on cp.pt.
- Interface in Portuguese.

## Install

1. Open the repository's **Releases** → `latest` on your phone and download `cp-widget.apk`.
2. Open it. Android will ask you to allow installing apps from your browser/files app — allow it once.
3. Long-press the home screen → **Widgets** → **Comboios CP** → drag *Partidas CP* onto the screen.
4. On cp.pt open your station (*Pesquisa de estações*), copy the page address, e.g.
   `https://www.cp.pt/pt/pesquisa-estacao-detalhe/94-60087`, paste it and tap **Guardar**.
   (If the link is already in the clipboard it is filled in automatically.)

Every push builds a new APK (GitHub Actions) and replaces the `latest` release.
Builds are signed with the same key (`app/sideload.keystore`, not a secret), so a new
APK installs as an update over the old one.

## How it gets the data

This uses CP's **unofficial** API — the same one www.cp.pt calls in your browser:

- `https://www.cp.pt/fe-config.json` → public API keys (read at every refresh, so key rotation is handled)
- `https://api-gateway.cp.pt/cp/services/travel-api/stations/{code}/timetable/{date}?view=DEPARTURES`

CP may change or block this at any time, and using it outside cp.pt may not be
permitted by CP's terms. If the widget starts showing *Erro ao atualizar*, this is the likely cause.

## Develop

```
./gradlew assembleRelease                       # APK in app/build/outputs/apk/release/
./gradlew testReleaseUnitTest                   # offline unit tests
./gradlew testReleaseUnitTest -Dcp.live=true    # also hits the live CP API
```
