# CleanSky Contactless - Project Guide (English)

## 1) What this project is

CleanSky Contactless is an Android app for peer-to-peer crypto payments over NFC.
It is designed around self-custody and local signing:

- Payer signs on device
- Merchant receives signed payload via NFC
- Merchant broadcasts on-chain

Core principle: private keys never leave the device.

## 2) Core capabilities

- NFC tap-to-pay flow (request -> sign -> return signed tx)
- Local key security with Android Keystore (AES-GCM, hardware-backed when available)
- Optional biometric authentication
- Multiple execution paths:
  - direct on-chain
  - relayer-based
  - account abstraction (ERC-4337)
- Stealth/privacy-oriented payment modes
- Transaction history and refund flows

## 3) Tech stack

- Kotlin + Jetpack Compose
- Android SDK 34 / minSdk 26
- Gradle (AGP 8.4.x)
- Web3j + Gson + Jackson CBOR
- DataStore for local persistence

## 3.1) Network support snapshot

Currently configured networks:

- Ethereum (`1`)
- Base (`8453`)
- Base Sepolia (`84532`)
- Polygon (`137`)
- Arbitrum One (`42161`)
- Optimism (`10`)
- zkSync Era (`324`)
- Linea (`59144`)

Execution support:

- Core networks (`Ethereum`, `Base`, `Polygon`, `Arbitrum`, `Optimism`) are enabled for Direct + Relayer + AA.
- `zkSync Era` and `Linea` are available in Direct mode by default; Relayer/AA require provider-specific setup.

## 4) Repository structure

- `app/src/main/java/io/cleansky/contactless/`
  - `crypto/` key management, signing, wallet primitives
  - `service/` execution, feedback, payment orchestration
  - `nfc/` NFC transport
  - `data/` repositories / persistence
  - `model/` protocol and domain models
  - `ui/` Compose UI components/screens
- `app/src/test/java/...` unit tests
- `docs/` protocol, motivation, market and language reports

## 5) Build and run

### Local

```bash
./gradlew testDebugUnitTest --no-daemon --console=plain
```

### Docker (ephemeral)

```bash
docker build -t cleansky-contactless-test .
docker run --rm cleansky-contactless-test ./gradlew testDebugUnitTest --no-daemon --console=plain
```

### Docker (fast inner loop with cached container)

```bash
docker run -d --name cleansky-dev-cache \
  --entrypoint sleep \
  -v "$PWD:/workspace" \
  -w /workspace \
  cleansky-contactless-test infinity

docker exec cleansky-dev-cache ./gradlew testDebugUnitTest --no-daemon --console=plain
```

## 6) Quality gates used in this repo

- Unit tests: `testDebugUnitTest`
- Android lint: `:app:lintDebug`

Current quality baseline in this branch:

- Lint errors: `0`
- Lint warnings: `0` (with `GradleDependency` lint check intentionally disabled until AGP/compileSdk migration)
- Unit tests: passing

## 7) Key documentation

- Protocol spec: `docs/PROTOCOL_SPEC.md`
- Motivation and threat model: `docs/MOTIVATION.md`
- Market analysis (EN): `docs/MARKET_ANALYSIS_EN.md`
- Language coverage (EN): `docs/LANGUAGES_REPORT_EN.md`

## 8) Notes

- This project currently targets AGP/SDK constraints compatible with `compileSdk 34`.
- Dependency upgrades beyond current pins may require a coordinated AGP + compileSdk migration.
