## Architecture

### 1. Authorization Primitive: Signed Intents

A payment intent is a structured, signed message containing amount, asset, recipient, chain, expiry, and nonce.

Properties:

- Offline authorization
- Execution delegation (merchant/relayer/bundler)
- Replay protection (domain + nonce)
- Client interoperability

Signing uses EIP-712 typed data.

---

### 2. Transport Layer: NFC

NFC is used as local transport for payment messages.

Properties:

- Local proximity exchange
- No internet requirement for message handoff
- Low-latency tap UX

Cryptographic security does not depend on NFC; it depends on signed intents.

---

### 3. Dependency Mapping

Sovereign components (local):

- Intent signing
- Local message exchange
- Local verification and derivation

Delegated components (replaceable providers):

- RPC broadcast
- Bundler execution (AA)
- Paymaster sponsorship

Centralized pressure points:

- App distribution channels
- External service operators if chosen

Design objective: keep authorization local; keep execution pluggable.

---

### 4. Execution Infrastructure

Supported execution modes:

1. Direct execution
2. Relayer execution
3. Account abstraction (ERC-4337)

Tradeoff: delegated execution improves UX and gas abstraction, but introduces service dependencies.

---

### 5. Merchant Identity Layers

Identity is optional and layered:

- Self-signed metadata
- DNS-linked verification
- ENS-linked verification
- Raw address-only mode

This avoids mandatory centralized merchant registries.

---

### 6. Stealth Reception Model

Merchants can enable stealth reception to use one-time destination addresses derived from published stealth metadata.

Flow summary:

1. Merchant publishes stealth metadata
2. Payer derives one-time destination
3. Funds arrive at unique address
4. Merchant scans and derives spend access

This improves recipient unlinkability heuristically; it is not full anonymity.

---

### 7. Platform and Operational Constraints

- Android supports full NFC merchant flows
- iOS imposes stronger NFC role restrictions
- Mainnet delegated paths depend on provider availability and policy

Architecture keeps a direct mode path available to reduce hard dependency risk.
