# CleanSky Contactless

An open-source Android app for contactless cryptocurrency payments.

## Overview

CleanSky Contactless enables peer-to-peer crypto payments over NFC. The payer signs on their own device, and the merchant submits and executes the transaction on-chain (covering gas fees).

**Key principle:** Your keys never leave your device. All cryptographic operations happen locally with hardware-backed security.

## Features

- **NFC Contactless Payments** - Tap-to-pay UX similar to card payments
- **Self-Custody Wallet** - Private keys encrypted with Android Keystore (AES-256-GCM)
- **Hardware Security** - StrongBox (HSM) support when available
- **Biometric Authentication** - Optional fingerprint/face authentication for transactions
- **Multi-chain Support** - Ethereum, Base, Base Sepolia, Polygon, Arbitrum, Optimism, zkSync Era, Linea
- **Transaction History** - Track payments with partial/full refund support
- **Multiple Execution Modes:**
  - Direct (merchant pays gas)
  - Relayer (Gelato/Biconomy)
  - Account Abstraction (ERC-4337 with Paymaster)

## How It Works

```
┌─────────────┐         NFC          ┌─────────────┐
│   PAYER     │◄────────────────────►│  MERCHANT   │
│             │                       │             │
│ 1. Receive  │  Payment Request     │ 1. Create   │
│    request  │ ─────────────────►   │    request  │
│             │                       │             │
│ 2. Review & │                       │             │
│    sign     │                       │             │
│             │  Signed Transaction   │             │
│ 3. Send     │ ◄─────────────────   │ 2. Execute  │
│    signed   │                       │    on-chain │
└─────────────┘                       └─────────────┘
```

## Security

| Feature | Implementation |
|---------|----------------|
| Key Storage | Android Keystore (hardware-backed) |
| Encryption | AES-256-GCM |
| HSM Support | StrongBox when available |
| Authentication | Optional biometrics |
| Transaction Signing | EIP-712 typed data |

**Why open source?** For a crypto payment app, transparency is essential. Users can verify:
- Private keys never leave the device
- No backdoors or hidden data exfiltration
- Cryptographic implementations are correct

## Build

### Requirements
- Android SDK 35
- JDK 17
- Gradle 8.x

### Android Studio
1. Clone the repo
2. Open in Android Studio
3. Sync Gradle
4. Run on device with NFC

### Docker
```bash
docker build -t cleansky-contactless .
docker run --rm -v "$PWD/out:/out" cleansky-contactless
# APK will be in out/app-debug.apk
```

## Project Structure

```
io.cleansky.contactless/
├── MainActivity.kt              # Entry point
├── AppScaffold.kt               # Navigation & state
├── PayScreen.kt                 # Payer UI
├── CollectScreen.kt             # Merchant UI
├── SettingsScreen.kt            # Configuration
├── HistoryScreen.kt             # Transaction history
├── RefundScreen.kt              # Process refunds
├── crypto/
│   ├── SecureKeyStore.kt        # Android Keystore wrapper
│   ├── BiometricAuth.kt         # Biometric authentication
│   ├── SecureWalletManager.kt   # Wallet operations
│   └── TransactionSigner.kt     # EIP-712 signing
├── data/
│   └── TransactionRepository.kt # Persistence
├── model/
│   ├── ChainConfig.kt           # Network configs
│   ├── PaymentRequest.kt        # Payment request model
│   ├── RelayerConfig.kt         # Relayer/AA configs
│   └── TransactionHistory.kt    # Transaction model
├── nfc/
│   └── NfcManager.kt            # NFC communication
├── service/
│   ├── PaymentFeedback.kt       # Haptic/sound feedback
│   ├── TransactionExecutor.kt   # Execute transactions
│   └── RefundService.kt         # Process refunds
└── ui/
    ├── Theme.kt                 # Colors & theme
    ├── SuccessAnimation.kt      # Payment animations
    └── SecuritySettings.kt      # Security config UI
```

## Configuration

### Supported Networks

| Network | Chain ID | Status |
|---------|----------|--------|
| Ethereum | 1 | ✅ |
| Base | 8453 | ✅ |
| Base Sepolia | 84532 | ✅ |
| Polygon | 137 | ✅ |
| Arbitrum One | 42161 | ✅ |
| Optimism | 10 | ✅ |
| zkSync Era | 324 | ✅ |
| Linea | 59144 | ✅ |

### Execution Modes

1. **Direct** - Merchant pays gas directly from their wallet
2. **Relayer** - Meta-transactions via Gelato or Biconomy (requires API key)
3. **Account Abstraction** - ERC-4337 with Paymaster support (Pimlico)

Note: Relayer/AA are enabled by default on core networks (Ethereum, Base, Polygon, Arbitrum, Optimism). Newly added networks are available in Direct mode immediately and may require provider-specific relayer/bundler setup.

### Network Capability Matrix

| Network | Direct | Relayer | AA (ERC-4337) |
|---|---|---|---|
| Ethereum | ✅ | ✅ | ✅ |
| Base | ✅ | ✅ | ✅ |
| Base Sepolia | ✅ | ✅ | ✅ |
| Polygon | ✅ | ✅ | ✅ |
| Arbitrum One | ✅ | ✅ | ✅ |
| Optimism | ✅ | ✅ | ✅ |
| zkSync Era | ✅ | ⚠️ Provider-dependent | ⚠️ Provider-dependent |
| Linea | ✅ | ⚠️ Provider-dependent | ⚠️ Provider-dependent |

## Documentation

- [Project Guide (English)](docs/PROJECT_GUIDE_EN.md)
- [Protocol Spec](docs/PROTOCOL_SPEC.md)
- [Motivation & Threat Model](docs/MOTIVATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Market Analysis (English)](docs/MARKET_ANALYSIS_EN.md)
- [Languages Report (English)](docs/LANGUAGES_REPORT_EN.md)
- [Market Analysis (Original)](docs/MARKET_ANALYSIS.md)
- [Languages Report (Original)](docs/LANGUAGES_REPORT.md)

## Contributing

Contributions are welcome! Please:
1. Fork the repo
2. Create a feature branch
3. Submit a PR with clear description

Security issues should be reported privately.

## License

MIT License - see [LICENSE](LICENSE)

## Disclaimer

This software is provided as-is. Always verify transactions before signing. The authors are not responsible for any loss of funds.
