## Motivation

### 1. Economic Coupling in Decentralized Systems

Decentralized financial systems exist along a spectrum of economic coupling with the physical economy. At one extreme, purely speculative systems circulate value among participants without generating external economic utility. At the other extreme, deeply coupled systems facilitate direct exchange of goods, services, and productive resources.

Most contemporary decentralized finance operates with low economic coupling: trading fees, interest, leverage, and token incentives redistribute value among participants, with limited connection to productive economic activity outside the system.

Increasing economic coupling does not require replacing speculation with commerce. It requires infrastructure that lets decentralized systems connect to real economic activity when participants choose to do so.

This project focuses on the payment vector as one practical path.

---

### 2. Sovereignty as a Local Property

Sovereignty is not binary. It varies by step in the transaction lifecycle.

We use **transactional sovereignty** as a practical definition: reducing censorship risk and single points of failure in the critical path of a transaction.

In this framing, authorization matters most: if signing requires third-party permission, the system is permissioned regardless of what happens later on-chain.

---

### 3. Authorization as Critical Path

The user authorization moment is the most sovereignty-sensitive point.

Common compromises:

- Custodial wallets delegate authorization
- Always-online UX couples authorization to connectivity
- Central processors can censor or fail at authorization time

This project prioritizes local, self-custodied signing so authorization remains available without mandatory online intermediaries.

---

### 4. Threat Model (Realistic Scope)

Resisted threats:

- Network disruption during authorization
- Mandatory centralized processor censorship
- Interface-layer metadata concentration

Not fully resisted:

- Chain-level censorship
- Advanced chain-analysis correlation
- App-store distribution censorship
- Bundler/relayer collusion in delegated execution

This is a bounded, explicit sovereignty claim.

---

### 5. Connectivity Asymmetry by Design

The design intentionally uses connectivity asymmetry:

- Payer side: high-sovereignty local authorization
- Merchant side: online execution and settlement

This aligns infrastructure burden with incentives: merchants benefit from reliable acceptance and can operate connectivity and risk controls.

---

### 6. Privacy as Correlation Surface Reduction

Privacy is treated as a reduction in correlation difficulty, not absolute anonymity.

Three additive layers reduce correlation surface:

- Local NFC transport reduces remote observers
- Delegated execution patterns can reduce direct wallet linkage
- Stealth reception patterns reduce recipient linkability

Limits are explicit: no amount privacy, no timing privacy, no full anonymity.

---

### 7. Human Factors

Payment UX must work under time pressure and low cognitive bandwidth.

The product prioritizes familiar "tap, confirm, done" flows while preserving cryptographic guarantees under the hood. This is a deliberate, practical tradeoff.

---

## Related Technical Document

For protocol mechanics, components, and execution infrastructure, see [ARCHITECTURE.md](ARCHITECTURE.md).
