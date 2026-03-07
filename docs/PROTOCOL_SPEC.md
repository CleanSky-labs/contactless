# CleanSky Research — Contactless Payment Protocol v0.5

> A reference protocol for sovereign offline-capable payments using signed intents and NFC as a local transport layer.

## Abstract

This document specifies the CleanSky Contactless Payment Protocol, a peer-to-peer payment system enabling NFC-based cryptocurrency transactions without intermediaries. The protocol prioritizes user sovereignty, offline capability until broadcast, mutual authentication, and decentralized settlement.

---

## 1) Motivation

### 1.1 Why Current Payment Systems Fail

**Visa/Mastercard/Apple Pay:**
- Custodial by design — funds held by intermediaries
- Censorable — transactions can be blocked arbitrarily
- Surveillance — complete transaction history visible to multiple parties
- Chargebacks create uncertainty for merchants

**Crypto QR Payments:**
- Poor UX — requires camera, good lighting, steady hands
- No mutual authentication — payer doesn't verify merchant identity
- Single-direction communication — no confirmation feedback

**Lightning Network:**
- Channel management complexity
- Requires online connectivity for settlement
- Liquidity constraints

### 1.2 Why NFC + Signed Intents

NFC enables:
- **Instant communication** — sub-second data exchange
- **Proximity verification** — physical presence required
- **Bidirectional flow** — request → sign → confirm
- **Offline signing** — no internet needed until broadcast

Signed intents enable:
- **Sovereign payments** — user signs, never custody
- **Censorship resistance** — multiple broadcast paths
- **Offline queuing** — merchant can broadcast later

### 1.3 Why Offline Matters

In censorship scenarios:
- Internet shutdowns don't prevent local commerce
- Payments can queue until connectivity restored
- No dependency on centralized infrastructure

---

## 2) Protocol Overview

### 2.1 Payment Flow

```
1. Merchant creates PaymentRequest (signed with merchant key)
2. NFC tap transmits request to payer device
3. Payer verifies merchant signature and reviews details
4. Payer signs authorization with their key
5. NFC tap returns SignedTransaction to merchant
6. Merchant broadcasts to blockchain (immediate or queued)
```

### 2.2 Key Innovation: Mutual Authentication

Both parties authenticate:
- **Merchant → Payer**: PaymentRequest signed by merchant (mpk)
- **Payer → Merchant**: SignedTransaction signed by payer

This prevents:
- Merchant spoofing (fake identity with legitimate address)
- Request tampering (MITM modification)

### 2.3 Roles

| Role | Description | Custodial? |
|------|-------------|------------|
| **Payer** | Signs payment authorization | No |
| **Merchant** | Signs payment request, broadcasts tx | No |
| **Relayer** | Executes tx on-chain (optional) | No* |
| **Bundler** | Aggregates UserOperations (ERC-4337) | No |

*Relayer never has custody; only executes pre-signed transactions.

---

## 3) State Machine

```
┌─────────────────┐
│ REQUEST_CREATED │ Merchant creates and signs request
└────────┬────────┘
         │ NFC tap (merchant → payer)
         ▼
┌─────────────────┐
│  REQUEST_SENT   │ Payer verifies merchant signature
└────────┬────────┘
         │ Payer reviews and signs
         ▼
┌─────────────────┐
│ AUTH_RECEIVED   │ Merchant receives payer signature
└────────┬────────┘
         │ Transaction broadcast
         ▼
┌─────────────────┐
│  TX_BROADCAST   │ Transaction submitted to network
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌──────────┐
│TX_CONF│ │TX_FAILED │
│IRMED  │ │          │
└───────┘ └──────────┘
```

### 3.1 State Timeouts

| State | Timeout | Action on timeout |
|-------|---------|-------------------|
| `REQUEST_SENT` | `expiry` timestamp | Reject request |
| `AUTH_RECEIVED` | 30 seconds | Broadcast or fail |
| `TX_BROADCAST` | 5 minutes | Mark as failed |

---

## 4) Message Format

### 4.1 Encoding

All protocol messages use **CBOR (RFC 8949)** for deterministic encoding.

CBOR provides:
- Deterministic byte representation
- Smaller payload size (~30% vs JSON)
- Unambiguous signature verification
- Binary-safe transport over NFC

### 4.2 PaymentRequest Structure

```
PaymentRequest {
  // Header
  v: 5,                                // Protocol version (MUST be 5)

  // Required fields
  mid: bytes32,                        // Merchant ID
  inv: bytes32,                        // Invoice ID (unique)
  amt: string,                         // Amount in smallest unit
  ast: address,                        // Token address (0x0 for native)
  cid: uint64,                         // Chain ID
  esc: address,                        // Recipient/escrow address
  exp: uint64,                         // Expiry timestamp (UNIX)
  non: bytes32,                        // Nonce

  // Merchant identity (SHOULD)
  mdn: string?,                        // Display name
  mdo: string?,                        // Domain or ENS
  mpk: bytes?,                         // Merchant public key

  // Privacy (v0.4 - EIP-5564)
  sma: string?,                        // Stealth meta-address (if privacy mode enabled)

  // Authentication (MUST for v0.3+)
  msig: bytes                          // Merchant signature over request
}
```

### 4.3 Merchant Signature (Critical for v0.3)

```
msig = Sign(merchantPrivateKey, keccak256(
  v || mid || inv || amt || ast || cid || esc || exp || non || mdn || mdo || mpk
))
```

Payer MUST verify:
1. `msig` is valid signature
2. `recover(msig) == mpk` (signer matches claimed merchant key)
3. If `mdo` is ENS: resolve and verify `mpk` matches

### 4.4 SignedTransaction Structure

```
SignedTransaction {
  v: 4,
  mid: bytes32,
  inv: bytes32,
  amt: string,
  ast: address,
  cid: uint64,
  esc: address,
  exp: uint64,
  non: bytes32,

  // Payer fields
  pay: address,                        // Payer address
  psig: bytes,                         // Payer signature (EIP-712)

  // Merchant identity (copied)
  mdn: string?,
  mdo: string?,
  mpk: bytes?,
  msig: bytes,

  // Stealth payment fields (v0.4 - filled by payer when sma is present)
  sta: address?,                       // One-time stealth address
  epk: bytes?,                         // Ephemeral public key (R)
  vtg: uint8?                          // View tag (first byte of shared secret)
}
```

---

## 5) Merchant Identity Trust Model

### 5.1 Trust Levels

| Level | Verification | Trust Source |
|-------|--------------|--------------|
| **Level 0** | None | Address only (legacy) |
| **Level 1** | Self-signed | mpk signs (mid + mdo) |
| **Level 2** | DNS verified | DNS TXT record contains mpk |
| **Level 3** | ENS verified | ENS text record contains mpk |
| **Level 4** | Registry | On-chain merchant registry |

### 5.2 Self-Signed Identity (Level 1)

Merchant generates keypair and self-certifies:
```
identityCert = Sign(mpk, keccak256(mid || mdo))
```

Payer sees: "Coffee Shop (self-certified)"

### 5.3 DNS Verification (Level 2)

Merchant adds DNS TXT record:
```
_cleansky.coffeeshop.com TXT "mpk=0x..."
```

Payer verifies: `DNS_TXT(_cleansky.{mdo}) == mpk`
Payer sees: "Coffee Shop ✓ (coffeeshop.com)"

### 5.4 ENS Verification (Level 3)

Merchant sets ENS text record:
```
coffeeshop.eth text.cleansky.mpk = "0x..."
```

Payer verifies: `ENS_TEXT({mdo}, "cleansky.mpk") == mpk`
Payer sees: "Coffee Shop ✓ (coffeeshop.eth)"

### 5.5 Future: DID Integration

Protocol is extensible to support:
- did:ethr for Ethereum-native DIDs
- did:web for domain-based DIDs
- Verifiable Credentials for rich attestations

---

## 6) Offline Operation Semantics

### 6.1 What Works Offline

| Operation | Offline? | Notes |
|-----------|----------|-------|
| Merchant creates request | ✅ | No chain access needed |
| Payer receives request | ✅ | NFC is local |
| Payer verifies merchant sig | ✅ | Crypto is local |
| Payer signs authorization | ✅ | No chain access needed |
| Merchant receives signature | ✅ | NFC is local |
| Balance verification | ❌ | Requires chain state |
| Transaction broadcast | ❌ | Requires internet |

### 6.2 Offline Window

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max request TTL | 5 minutes | Limits replay risk |
| Max offline queue | 24 hours | Nonce storage retention |
| Recommended broadcast | < 1 hour | Reduce chain state drift |

### 6.3 Nonce Independence

Nonces are:
- Generated randomly (not derived from chain state)
- Tracked locally by merchant
- Valid until expiry + 24h retention

This enables offline operation without chain queries.

### 6.4 Risk Disclaimers

**Offline acceptance risks:**
- Payer balance may have changed
- Token may have been blacklisted
- Network fees may have increased
- Reorgs may affect confirmation

Merchants accepting offline payments SHOULD:
- Limit offline acceptance to low-value transactions
- Implement velocity limits
- Monitor for patterns of abuse

---

## 7) Security Properties

### 7.1 Formal Properties

| Property | Definition | Mechanism |
|----------|------------|-----------|
| **Authenticity** | Merchant identity is verifiable | msig + mpk verification |
| **Non-repudiation** | Payer cannot deny signing | EIP-712 signature on-chain |
| **Replay resistance** | Request cannot be reused | nonce + expiry + invoiceId |
| **Transaction integrity** | Request cannot be modified | Signature covers all fields |
| **No custody** | No party holds user funds | Direct transfer to escrow |
| **Censorship resistance** | Multiple broadcast paths | Bundlers are interchangeable |

### 7.2 What The Protocol Does NOT Provide

- **Balance verification offline** — Payer may have insufficient funds
- **Finality before broadcast** — Optimistic accept has risks
- **Cryptographic anonymity** — Privacy features provide heuristic unlinkability, not ZK-level anonymity
- **Merchant reputation** — Out of scope (future work)

### 7.3 Stealth Addresses (v0.4 - EIP-5564)

Merchants MAY enable privacy mode to receive payments to one-time stealth addresses. This provides **unlinkability**: observers cannot link multiple payments to the same merchant.

#### 7.3.1 Flow

```
1. Merchant generates stealth keys (spending key k_s, viewing key k_v)
2. Merchant derives stealth meta-address: sma = (K_s || K_v)
3. PaymentRequest includes sma field
4. Payer generates ephemeral keypair (r, R)
5. Payer computes shared secret: S = r * K_v (ECDH)
6. Payer derives one-time address: sta = K_s + hash(S) * G
7. SignedTransaction includes sta, epk (R), vtg (view tag)
8. Merchant scans: computes S' = k_v * R, derives key p = k_s + hash(S')
9. Merchant claims funds from sta using derived key p
```

#### 7.3.2 View Tags

The `vtg` field contains the first byte of the shared secret. This enables fast scanning: merchants can check view tags (256 possibilities) before doing full ECDH, providing ~256x speedup for scanning.

#### 7.3.3 Stealth Meta-Address Format

```
sma = "st:eth:" || hex(K_s) || hex(K_v)
```

Where:
- `K_s` = uncompressed spending public key (65 bytes)
- `K_v` = uncompressed viewing public key (65 bytes)

#### 7.3.4 Privacy Properties

| Property | Achieved | Notes |
|----------|----------|-------|
| **Merchant unlinkability** | ✅ | Each payment uses unique address |
| **Payer privacy** | ❌ | Payer address still visible |
| **Amount privacy** | ❌ | Amount visible on-chain |
| **Asset privacy** | ❌ | Token contract visible |

#### 7.3.5 Claiming Stealth Payments

Merchants MUST:
1. Store pending stealth payments with (invoiceId, sta, epk, vtg)
2. Periodically scan for received funds at sta addresses
3. Derive spending keys and transfer to main wallet
4. Mark payments as claimed with txHash

### 7.4 Payer Privacy via Ephemeral Accounts (v0.5 - ERC-4337)

Payers MAY enable privacy mode to send payments from ephemeral smart accounts. This provides **payer unlinkability**: observers cannot link multiple payments to the same payer.

#### 7.4.1 Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│ Main Wallet │ ──► │ Ephemeral AA │ ──► │ Recipient/      │
│   (hidden)  │     │   Account    │     │ Stealth Address │
└─────────────┘     └──────────────┘     └─────────────────┘
      │                    │
      │ funds + UserOp     │ payment
      └────────────────────┘
```

The main wallet:
1. Derives deterministic ephemeral account for each payment
2. Funds ephemeral account with exact payment amount
3. Signs UserOperation from ephemeral account
4. Bundler executes UserOp with paymaster sponsoring gas

#### 7.4.2 Ephemeral Account Derivation

```
// Deterministic key derivation
seed = keccak256(mainPrivateKey || "cleansky-ephemeral" || paymentIndex)
ephemeralKey = secp256k1_derive(seed)

// SimpleAccount address (ERC-4337)
salt = keccak256(paymentIndex)
initCode = factory.createAccount(ephemeralOwner, salt)
address = CREATE2(factory, salt, initCode)
```

Where:
- `paymentIndex` is a monotonically increasing counter per wallet
- `factory` is the SimpleAccountFactory (0x9406Cc6185a346906296840746125a0E44976454)
- Ephemeral accounts are deployed on first use

#### 7.4.3 UserOperation Structure

```
UserOperation {
  sender: ephemeralAccountAddress,
  nonce: 0,                              // First tx from this account
  initCode: factoryAddress + createAccountCalldata,  // Deploy if needed
  callData: executePayment(recipient, amount, token),
  callGasLimit: 100000,
  verificationGasLimit: 400000,
  preVerificationGas: 50000,
  maxFeePerGas: chainGasPrice,
  maxPriorityFeePerGas: chainPriorityFee,
  paymasterAndData: paymasterAddress + sponsorshipData,
  signature: ephemeralOwnerSignature
}
```

#### 7.4.4 Payment Flow

```
1. Payer receives PaymentRequest via NFC
2. If privacy mode enabled:
   a. Derive ephemeral account for next paymentIndex
   b. Fund ephemeral account with payment amount (from main wallet)
   c. Wait for funding confirmation
   d. Create UserOperation: ephemeral → recipient
   e. Sign UserOp with ephemeral owner key
   f. Submit to bundler with paymaster
3. Return SignedTransaction to merchant (optional for privacy mode)
4. Bundler executes UserOp on-chain
```

#### 7.4.5 Privacy Properties

| Property | Achieved | Notes |
|----------|----------|-------|
| **Payer unlinkability** | ✅ | Each payment from unique address |
| **Funding traceability** | ⚠️ | Main wallet funds ephemeral (heuristic link) |
| **Combined with stealth** | ✅ | Both payer and merchant hidden |
| **Gas sponsorship** | ✅ | Paymaster covers ephemeral account gas |

#### 7.4.6 Limitations

1. **Heuristic, not cryptographic**: Funding transaction links main wallet to ephemeral
2. **Requires bundler API**: Cannot operate fully offline
3. **Higher latency**: Two transactions (fund + execute)
4. **Higher cost**: Paymaster fees + account deployment

#### 7.4.7 Dual Privacy Mode

When both payer privacy (§7.4) and merchant privacy (§7.3) are enabled:

```
Main Wallet → Ephemeral Account → Stealth Address
   (hidden)      (one-time)         (one-time)
```

This provides maximum unlinkability:
- Observer cannot identify payer (ephemeral account)
- Observer cannot identify merchant (stealth address)
- Observer only sees: random address A → random address B

---

## 8) Security Considerations

### 8.1 Critical Risks

**1. Merchant Spoofing**
- Attack: Fake merchant identity with legitimate escrow address
- Mitigation: Verify msig against mpk; verify mpk via DNS/ENS
- Residual: Level 1 (self-signed) provides no external trust

**2. Replay Attacks**
- Attack: Rebroadcast old signed transaction
- Mitigation: Nonce tracking, expiry timestamps, invoice uniqueness
- Residual: Merchant must maintain nonce storage

**3. Key Compromise**
- Attack: Stolen merchant or payer private key
- Mitigation: Hardware security (StrongBox), biometric auth
- Residual: No revocation mechanism (self-custody tradeoff)

### 8.2 Token-Specific Risks

**Centralized Stablecoins (USDC, USDT):**
- Issuer MAY blacklist addresses without notice
- Issuer MAY freeze funds globally
- Users seeking sovereignty SHOULD prefer native ETH or decentralized tokens

### 8.3 Relayer/Bundler Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Front-running | MEV extraction | Private mempools |
| Censorship | Tx not included | Multiple bundlers |
| Delay | Poor UX | Timeout + fallback |

---

## 9) Replay Protection

### 9.1 Triple Protection

1. **Nonce**: Random 32-byte value, unique per request
2. **Expiry**: Timestamp-based validity window
3. **Invoice ID**: Globally unique transaction identifier

### 9.2 Merchant Requirements

Merchants MUST:
- Store (nonce, invoiceId) pairs for 24 hours post-expiry
- Reject requests with previously-seen pairs
- Atomically mark nonce as used before broadcast
- Release nonce if broadcast fails

### 9.3 Timing

| Parameter | Value |
|-----------|-------|
| Max TTL | 5 minutes |
| Default TTL | 3 minutes |
| Min TTL | 30 seconds |
| Clock skew tolerance | ±30 seconds |

---

## 10) Signatures

### 10.1 Merchant Signature

Purpose: Authenticate PaymentRequest origin

```
domain = keccak256("CleanSky Contactless Request")
message = abi.encodePacked(v, mid, inv, amt, ast, cid, esc, exp, non, mdn, mdo, mpk)
msig = eth_sign(merchantKey, keccak256(domain || message))
```

### 10.2 Payer Signature (EIP-712)

Purpose: Authorize payment transfer

```solidity
struct PaymentAuthorization {
    bytes32 merchantId;
    bytes32 invoiceId;
    uint256 amount;
    address asset;
    uint256 chainId;
    address escrow;
    uint256 expiry;
    uint256 nonce;
}

EIP712Domain {
    name: "CleanSky Contactless",
    version: "3",
    chainId: <chainId>,
    verifyingContract: <escrow_address>
}
```

### 10.3 Smart Account Support

The `psig` field MAY contain:
- ECDSA signature (EOA)
- ERC-1271 signature (Smart Account)

Verifiers MUST support both.

---

## 11) Settlement

### 11.1 Execution Modes

| Mode | Gas Payer | Latency | Decentralization |
|------|-----------|---------|------------------|
| Direct | Merchant | ~2-12s | Maximum |
| Relayer | Sponsor | ~3-15s | Medium |
| ERC-4337 | Paymaster | ~5-20s | High |

### 11.2 Merchant Risk Modes

**Instant Accept** (optimistic):
- Accept after valid signatures
- Risk: reorgs, insufficient balance
- Use case: Low-value, trusted

**Confirmed** (conservative):
- Wait for block confirmation
- Lower risk, higher latency
- Use case: High-value

---

## 12) Comparison

| Feature | CleanSky | Apple Pay | Visa | QR Crypto | Lightning |
|---------|----------|-----------|------|-----------|-----------|
| Self-custody | ✅ | ❌ | ❌ | ✅ | ✅ |
| No intermediary | ✅ | ❌ | ❌ | ✅ | ⚠️ |
| Mutual auth | ✅ | ❌ | ❌ | ❌ | ❌ |
| Offline signing | ✅ | ❌ | ❌ | ✅ | ⚠️* |
| NFC native | ✅ | ✅ | ✅ | ❌ | ✅ |
| Multi-chain | ✅ | N/A | N/A | ⚠️ | ❌ |

*Lightning: Offline invoice generation only, not settlement

---

## 13) Performance

### 13.1 Latency Targets

| Phase | Target | P95 |
|-------|--------|-----|
| NFC transfer | 200ms | 500ms |
| Signature verification | 50ms | 100ms |
| User signing | 2s | 3s |
| Broadcast | 1s | 2s |
| L2 confirmation | 5s | 12s |
| **End-to-end** | **8s** | **18s** |

*These targets are aspirational, based on empirical mobile NFC tests and L2 benchmarks. Actual performance varies by device and network conditions.*

### 13.2 Transaction Costs

| Chain | Cost (USD) |
|-------|------------|
| Ethereum L1 | $1-10 |
| Base | $0.001-0.01 |
| Arbitrum | $0.01-0.05 |
| Polygon | $0.001-0.01 |

---

## 14) SDK Architecture

```
cleansky-protocol/
├── spec/
│   ├── protocol.md
│   └── test-vectors/
├── sdk-kotlin/
│   ├── core/
│   └── android/
├── sdk-ts/
└── sdk-rust/
```

---

## 15) Regulatory Considerations

This specification defines a technical protocol. Regulatory status is:
- **Jurisdiction-dependent**
- **Outside scope of this specification**

Implementers are responsible for compliance with local laws.

| Component | Custody | Notes |
|-----------|---------|-------|
| Wallet app | No | Self-custody tool |
| Relayer | No | No fund access |
| Bundler | No | No fund access |

---

## 16) Roadmap

### v0.5 (Current)
- [x] Payer privacy via ephemeral accounts (ERC-4337)
- [x] Deterministic ephemeral key derivation
- [x] Paymaster-sponsored gas for ephemeral accounts
- [x] Dual privacy mode (payer + merchant)

### v0.4
- [x] Stealth addresses for merchant privacy (EIP-5564)
- [x] View tags for fast scanning
- [x] Stealth meta-address in PaymentRequest
- [x] Stealth payment data in SignedTransaction

### v0.3
- [x] Merchant signature (mutual authentication)
- [x] Trust model for merchant identity
- [x] Offline semantics
- [x] Security properties
- [x] CBOR encoding

### v1.0 (Planned)
- [ ] DNS/ENS verification implementation
- [ ] Formal test vector suite
- [ ] Multi-language SDK release
- [ ] Security audit
- [ ] Stealth payment claiming UI

### v2.0 (Research)
- [ ] DID integration
- [ ] Cross-chain intents

---

## 17) References

1. EIP-712: Typed Structured Data Hashing and Signing
2. ERC-4337: Account Abstraction
3. ERC-1271: Signature Validation for Contracts
4. EIP-5564: Stealth Addresses
5. RFC 2119: Key words for RFCs
6. RFC 8949: CBOR

---

## Appendix A: Normative Language

Per RFC 2119:
- **MUST**: Absolute requirement
- **SHOULD**: Recommended
- **MAY**: Optional

---

## Appendix B: Changelog

### v0.5
- Added payer privacy via ephemeral accounts (ERC-4337)
- Added section 7.4 documenting ephemeral account architecture
- Added dual privacy mode combining payer and merchant privacy
- Updated section 7.2 to clarify heuristic vs cryptographic privacy
- Updated roadmap (payer privacy moved from research to implemented)

### v0.4
- Added stealth addresses for merchant privacy (EIP-5564)
- Added stealth meta-address (sma) field to PaymentRequest
- Added stealth payment fields (sta, epk, vtg) to SignedTransaction
- Added section 7.3 documenting stealth address flow
- Added view tags for fast scanning optimization
- Updated roadmap

### v0.3
- Added merchant signature (msig) for mutual authentication
- Added Trust Model section for merchant identity verification
- Added Offline Operation Semantics section
- Added formal Security Properties
- Added Motivation section
- Added Security Considerations (RFC style)
- Updated comparison table (Lightning clarification)
- Added performance metrics disclaimer

### v0.2
- Added CBOR encoding
- Added merchant identity fields
- Added formal state machine
- Added nonce storage requirements

### v0.1
- Initial specification

---

*CleanSky Research — Verify, don't trust.*
