# Flyradar for Android

Kart over fly og båtar rundt Stord, i sanntid. Ingen konto, ingen nøkkel og ingen reklame.

- **Fly** frå opne ADS-B-nettverk ([adsb.lol](https://adsb.lol), med [adsb.fi](https://adsb.fi) som reserve), oppdatert kvart 5. sekund
- **Båtar** frå Kystverket sin opne AIS-straum, oppdatert fortløpande
- **Kart** frå OpenStreetMap

## Funksjonar (versjon 0.1)

- Fly farga etter høgd og rotert etter kurs
- Båtar farga etter type (ferje, last, tank, fiske, service, fritid). Båtar som ligg i ro blir viste som prikkar
- Slå fly og båtar av og på kvar for seg
- Vel radius: 25, 50, 100 eller 200 km
- Trykk på eit fly eller ein båt for detaljar: høgd, fart, kurs, type, destinasjon, MMSI/ICAO o.l.
- Forklaring på fargane under ⓘ-knappen
- Hentar data berre medan appen er open
- Grøn tittellinje
- Krev Android 8.0 eller nyare

## Installere

1. Last ned `Flyradar-x.y.apk` frå [Releases](../../releases).
2. Opne fila på telefonen og tillat installasjon frå denne kjelda når telefonen spør.

### Automatiske oppdateringar med Obtainium

Legg til `https://github.com/Frodolini/Flyradar-Android` i [Obtainium](https://github.com/ImranR98/Obtainium).

## Byggje sjølv

Opne mappa i **Android Studio** og trykk ▶ Run. Eller frå kommandolinja:

```bash
./gradlew testDebugUnitTest   # køyr testane (og lag skjermbilete i app/build/skjermbilete/)
./gradlew assembleDebug       # byggjer app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # signert versjon (krev keystore.properties, sjå under)
```

Signert release-bygg krev fila `keystore.properties` i rotmappa (ligg **ikkje** i repoet):

```properties
storeFile=/sti/til/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Ved ny versjon: auk `versionCode` (og `versionName`) i `app/build.gradle.kts`.

## Korleis prosjektet heng saman

| Fil | Kva han gjer |
|---|---|
| `app/build.gradle.kts` | Byggjeoppsett: namn, versjon, Android-versjonar, bibliotek, signering |
| `gradle/libs.versions.toml` | Alle bibliotek og versjonar samla på éin stad |
| `AndroidManifest.xml` | «Visittkortet» til appen, med løyve til internett |
| `MainActivity.kt` | Inngangen til appen; startar og stoppar datahentinga |
| `ui/RadarViewModel.kt` | Held på tilstanden: fly, båtar, val og brytarar. Hentar data i bakgrunnen |
| `ui/RadarScreen.kt` | Skjermbiletet: tittellinje, brytarar, status, infokort og forklaring |
| `ui/RadarMap.kt` | Kartet (osmdroid) og markørane på det |
| `ui/MapIcons.kt` | Teiknar fly- og båtikon i rett farge og retning |
| `ui/theme/Theme.kt` | Fargar, lyst/mørkt tema og den grøne tittellinja |
| `data/Aircraft.kt` | Les flydata (JSON) frå adsb.lol / adsb.fi |
| `data/AircraftClient.kt` | Hentar flydata over HTTPS |
| `data/Vessel.kt` | Båtar, båttypar og `VesselStore`, som fører saman posisjon og namn |
| `data/Geo.kt` | Midtpunktet (Stord) og avstandsrekning |
| `ais/AisDecoder.kt` | Dekodar AIS-meldingar (six-bit-format) til posisjon, namn, type osv. |
| `ais/AisStream.kt` | TCP-kopling til Kystverket, med automatisk gjenoppkopling |
| `src/test/...` | Testar: AIS-dekoding, flydata, avstand, båtlager og skjermbilete (Robolectric) |

## Datakjelder

- **ADS-B:** Fly sender posisjon, høgd og fart på 1090 MHz. Frivillige mottakarar over heile verda deler dette med adsb.lol og adsb.fi, som har opne API.
- **AIS:** Båtar sender posisjon og skipsdata på VHF. Kystverket samlar dette frå landstasjonar langs kysten og deler ein open straum (`153.44.253.27:5631`) under Norsk lisens for offentlege data (NLOD). Nokre mindre fartøy er filtrerte bort.

Appen er skriven i **Kotlin** med **Jetpack Compose**.
