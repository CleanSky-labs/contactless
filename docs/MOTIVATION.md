## Motivation

### 1. Degrees of Economic Coupling in Decentralized Systems

Decentralized financial systems exist along a spectrum of economic coupling with the physical economy. At one extreme, purely speculative systems circulate value among participants without generating external economic utility. At the other extreme, deeply coupled systems facilitate direct exchange of goods, services, and productive resources.

Most contemporary decentralized finance operates with low economic coupling: trading fees, interest, leverage, and token incentives redistribute value among participants, with limited connection to productive economic activity outside the system. This is not inherently problematic—traditional financial markets also operate with varying degrees of coupling to physical production—but it does constrain the long-term sustainability and utility of decentralized infrastructure.

Increasing economic coupling does not require replacing speculation with commerce. Rather, it requires building infrastructure that enables decentralized systems to interface with physical economic activity when participants choose to do so. Payment protocols represent one vector for increasing coupling, alongside tokenized services, decentralized infrastructure (compute, storage, bandwidth), and machine-to-machine economic coordination.

This protocol addresses the payment vector specifically, without claiming that retail payments are the only or primary path to economic coupling.

---

### 2. Sovereignty as a Local Property

Financial sovereignty is often discussed as a binary property: either a system is decentralized and sovereign, or it is centralized and permissioned. This framing obscures the reality that sovereignty exists in degrees and varies across different moments in a transaction lifecycle.

We propose a more precise framing: **transactional sovereignty** is the minimization of failure points and censorship vectors in the critical path of a specific transaction, acknowledging that secondary dependencies may exist outside the critical path.

Consider a typical crypto payment today:

- **Authorization**: User signs a transaction (sovereign if self-custodied)
- **Broadcast**: Transaction submitted to mempool (depends on RPC providers)
- **Execution**: Transaction included in block (depends on validators/miners)
- **Settlement**: Finality achieved (protocol-level guarantee)
- **Conversion**: Assets converted to goods or fiat (often centralized)

Each step has different sovereignty characteristics. A protocol that maximizes sovereignty at the authorization layer provides meaningful guarantees even if other layers involve dependencies.

**Definition (Transactional Sovereignty).** A transaction exhibits transactional sovereignty to the degree that its authorization can be performed without real-time dependency on external infrastructure, and its execution path minimizes single points of censorship or failure.

This definition is intentionally modest. It does not claim that the entire economic stack becomes sovereign, only that specific chokepoints can be reduced or eliminated.

---

### 3. The Authorization Moment as Critical Path

In payment systems, the moment of authorization—when a user commits to a specific payment—is the most sovereignty-sensitive point in the transaction lifecycle. If authorization requires permission from a third party, the system is permissioned regardless of how decentralized subsequent layers may be.

Existing crypto payment interfaces often compromise sovereignty at the authorization moment:

- **Custodial wallets**: Authorization is delegated to a third party
- **Always-online requirements**: Authorization fails without connectivity
- **Centralized payment processors**: Authorization routed through intermediaries

A protocol that enables offline, self-custodied authorization preserves sovereignty at the critical path, even if broadcast and execution involve infrastructure dependencies.

This is analogous to signing a check versus swiping a debit card. The check represents a self-sovereign authorization that can be executed later by various parties. The debit card requires real-time permission from the issuing bank. Both result in payments, but they have fundamentally different sovereignty properties at the authorization moment.

---

### 4. Threat Model and Adversarial Scope

A coherent sovereignty claim requires an explicit threat model. This protocol is designed to resist specific adversaries while acknowledging limitations against others.

**Adversaries the protocol resists:**

- **Network disruption at authorization time**: Payer can sign payment intents offline
- **Centralized payment processor censorship**: No mandatory intermediary for authorization
- **Metadata collection at interface layer**: NFC communication is local and peer-to-peer
- **Single-point RPC censorship**: Merchant can use multiple broadcast paths
- **Recipient correlation (partial)**: Stealth addresses provide heuristic unlinkability

**Adversaries the protocol does not fully resist:**

- **Chain-level censorship**: If validators refuse to include transactions, settlement fails
- **Sophisticated chain analysis**: Amount and timing patterns remain observable
- **App store censorship**: Wallet distribution can be restricted
- **Bundler/relayer collusion**: Account abstraction execution depends on bundler availability
- **Nation-state adversaries**: Determined adversaries can correlate metadata across layers

This threat model is not a limitation of the protocol design but a realistic assessment of what any protocol can achieve without additional infrastructure (mixers, zkSNARKs, decentralized sequencers).

---

### 5. Connectivity Asymmetry as Design Choice

The protocol assumes intentional connectivity asymmetry: payers operate in high-sovereignty mode (offline, local signing), while merchants operate in high-liquidity mode (online, immediate settlement).

This asymmetry is a design choice, not a limitation. It reflects economic reality:

- **Merchants** have strong incentives to maintain infrastructure (connectivity, point-of-sale systems, settlement verification) because their business depends on reliable payment acceptance
- **Consumers** prioritize convenience and privacy, preferring minimal infrastructure requirements at the moment of payment
- **Double-spend risk** is a fundamental constraint in offline systems; placing the online requirement on the merchant side concentrates risk management where economic incentives align with the required investment

An alternative design requiring both parties to be offline would necessitate either trusted hardware, probabilistic acceptance, or deferred settlement—each introducing different tradeoffs. The current design chooses immediate settlement certainty at the cost of merchant connectivity requirements.

---

### 6. Correlation Surface Reduction

Privacy in payment systems is often framed as a binary property: either transactions are anonymous or they are not. This framing is misleading. Privacy exists along a spectrum of correlation difficulty, and practical privacy improvements can be achieved without full anonymity.

The protocol implements **correlation surface reduction** across three independent layers:

**Transport Layer (NFC)**
Local peer-to-peer communication avoids centralized servers that could collect metadata, logs, IP addresses, or timing information. An observer must be physically proximate to intercept communication, dramatically reducing the set of potential surveillors compared to server-mediated payment protocols.

**Execution Layer (Ephemeral Accounts)**
Using ERC-4337 Account Abstraction, payments can be authorized from the payer's main wallet but executed from ephemeral smart accounts. On-chain, only the ephemeral-to-merchant link is visible. The main wallet's involvement is limited to an off-chain signature, reducing direct linkage in chain analysis.

**Reception Layer (Stealth Addresses)**
EIP-5564 stealth addresses allow merchants to receive payments to one-time addresses derived from a published stealth meta-address. Each payment goes to a unique address, providing heuristic unlinkability for recipients without requiring interactive key exchange.

These layers provide **additive** correlation reduction. Each layer independently reduces the set of observers or the ease of correlation, but they do not compose into cryptographic anonymity. Sophisticated adversaries with access to multiple data sources may still correlate transactions through amount patterns, timing analysis, or auxiliary information.

The protocol does not provide:

- Amount privacy (transaction values are publicly visible)
- Timing privacy (transaction timestamps are on-chain)
- Full anonymity (determined adversaries can correlate patterns)
- Sender-receiver unlinkability against chain analysis

These limitations are fundamental to operating on transparent blockchains without zero-knowledge infrastructure. The protocol's privacy properties should be understood as raising the cost and complexity of surveillance, not eliminating it.

---

### 7. Signed Intents as Authorization Primitive

A payment intent is a structured, cryptographically signed message specifying the terms of a transaction: amount, asset, recipient, chain, expiry, and replay protection nonce. Signed intents decouple authorization from execution, enabling several useful properties:

**Offline Authorization**
Users can sign intents without network connectivity. The signed intent is a self-contained authorization that can be transmitted locally (via NFC) and broadcast later by any party with network access.

**Execution Delegation**
The signer authorizes a payment but does not need to broadcast it. Merchants, relayers, or bundlers can handle broadcast and gas payment, reducing user-side complexity.

**Formal Replay Protection**
Intents include chain ID, contract address, and nonce, preventing cross-chain replay and same-chain replay attacks.

**Client Agnosticism**
Any wallet or application that can produce conformant signed intents can participate in the protocol, enabling interoperability without requiring specific implementations.

The intent structure uses EIP-712 typed data signing, providing human-readable signing prompts and domain separation.

---

### 8. NFC as Local Transport

Near Field Communication provides a peer-to-peer transport layer with properties well-suited to sovereign payment authorization:

- **Local communication**: Data exchange occurs within approximately 4 centimeters, requiring physical proximity
- **No network dependency**: Communication does not traverse internet infrastructure
- **Low latency**: Typical exchange completes in under 500 milliseconds
- **Familiar UX**: Users understand "tap to pay" from existing contactless systems
- **Hardware availability**: NFC is present in most contemporary smartphones

NFC serves as a transport layer only. The protocol does not depend on NFC-specific security properties; cryptographic guarantees come from the signed intent structure. NFC provides physical locality and user experience benefits.

**Platform Limitations**
iOS restricts NFC to tag reading and Apple Pay; full peer-to-peer NFC (Host Card Emulation) is not available. This means iOS devices can initiate payments (by reading merchant NFC tags) but cannot operate as merchants receiving tap payments. Android devices support full HCE and can operate in either role. This platform asymmetry affects deployment options but does not change the protocol's security properties.

---

### 9. Dependency Mapping

Honest sovereignty claims require explicit enumeration of dependencies. The protocol involves the following components with varying sovereignty characteristics:

**Sovereign Components (no external dependency for operation):**

- Intent signing (local cryptographic operation)
- NFC communication (local peer-to-peer)
- Stealth address derivation (local computation)
- Payment verification (local signature checking)

**Delegated Components (external dependency, multiple provider options):**

- Transaction broadcast (RPC providers, can use multiple)
- Bundler execution for AA (bundler network, can use multiple)
- Block inclusion (validator/miner set, protocol-level decentralization)
- Gas sponsorship (paymaster providers, optional)

**Centralized Dependencies (single points of potential failure):**

- Wallet software distribution (app stores)
- Smart contract deployment (one-time, then immutable)
- Price feeds for fiat display (oracle dependency if displaying fiat equivalents)

The protocol minimizes dependencies in the authorization path while accepting dependencies in execution and settlement paths. This tradeoff prioritizes sovereignty at the moment of user decision while leveraging existing infrastructure for execution.

---

### 10. Execution Infrastructure

#### 10.1 ERC-4337 Bundler Network

Account Abstraction requires bundlers to aggregate and execute UserOperations. The protocol supports tiered bundler selection:

1. **User-configured bundler**: Maximum control, requires technical sophistication
2. **API-key bundler**: Commercial providers (Pimlico, Stackup, Alchemy) with rate limits
3. **Public bundler**: Testnet availability, rate-limited, no API key required

Public bundlers on testnets (Base Sepolia, Sepolia, Arbitrum Sepolia, OP Sepolia) enable experimentation without API key management. Mainnet deployments typically require commercial bundler relationships or self-operated bundler infrastructure.

Bundler dependency is a sovereignty tradeoff: users gain gas abstraction and simplified UX at the cost of execution-layer dependency. The protocol permits direct transaction broadcast as an alternative, preserving a fully sovereign execution path for users willing to manage gas.

#### 10.2 Paymaster Sponsorship

Paymasters can sponsor gas costs for UserOperations, enabling ephemeral accounts to execute transactions without holding ETH. This improves UX (users don't need to manage gas across multiple accounts) but introduces paymaster dependency.

Without paymaster sponsorship, the ephemeral account must be funded with gas before executing. The protocol supports both modes.

---

### 11. Merchant Identity and Trust

The protocol does not mandate a centralized merchant registry. Merchant identity operates through layered, optional mechanisms:

**Self-Signed Metadata**
Merchants include signed metadata (business name, logo, description) in NFC payloads. This provides no third-party attestation but enables consistent display across transactions.

**DNS Verification**
Merchants can publish TXT records at a domain they control, linking their payment address to a domain. Wallets can verify DNS records to display domain-verified merchant identity.

**ENS Records**
Merchants can set ENS text records linking their ENS name to payment addresses and metadata. ENS provides decentralized name resolution with on-chain attestation.

**No Verification**
Payments can proceed without any identity verification, displaying only the recipient address. This is appropriate for pseudonymous or privacy-focused merchants.

These mechanisms provide defense in depth without requiring centralized certificate authorities or merchant registries. Trust is contextual: a coffee shop payment may require only physical presence and displayed pricing, while a large online payment may warrant ENS or DNS verification.

---

### 12. Human Factors and Practical Constraints

Protocol specifications often emphasize cryptographic properties while underweighting human factors. Real-world payment systems operate under practical constraints:

- **Cognitive load**: Users cannot evaluate cryptographic proofs at point of sale
- **Time pressure**: Retail transactions complete in seconds
- **Environmental factors**: Varying lighting, noise, device orientations
- **Trust models**: Physical proximity and merchant reputation substitute for cryptographic verification

The protocol is designed for "tap, confirm, done" interaction patterns that match existing contactless payment mental models. Cryptographic verification happens automatically; users see familiar confirmation flows.

This is a deliberate tradeoff. Maximum security might require users to verify recipient addresses character-by-character, but this would make the protocol unusable. The design accepts that users will rely on contextual trust (physical location, displayed merchant info) for most transactions.

---

### 13. Stealth Address Implementation

Merchants can enable stealth addresses to receive payments to one-time addresses, providing heuristic unlinkability for their receiving activity.

**Setup Process:**

1. App derives stealth keys deterministically from merchant wallet seed
2. Generates spending key (k_s) and viewing key (k_v)
3. Computes stealth meta-address for publication
4. Stores keys in encrypted local storage

**Payment Flow:**

1. Payer retrieves merchant's stealth meta-address
2. Payer generates ephemeral keypair
3. Payer computes one-time address using ECDH
4. Payment sent to one-time address
5. Merchant scans for payments using viewing key
6. Merchant derives spending key to access funds

No additional key backup is required beyond the main wallet seed, as stealth keys are deterministically derived.

---

### 14. Contact Exchange Without Payment

The protocol extends NFC communication beyond payment authorization to enable **peer-to-peer contact exchange**—sharing wallet addresses without initiating a transaction.

**Use Case:**
Two parties meet in person and want to establish a payment relationship for future transactions. Rather than dictating addresses verbally, scanning QR codes, or using centralized contact-sharing services, they can tap devices to exchange cryptographic identities directly.

**Implementation:**
Contact exchange uses the same NFC transport layer as payments but with a distinct message type (CBOR-encoded contact records). The exchange is mutual: both devices can share and receive addresses simultaneously.

**Privacy Considerations:**
Contact exchange inherits the transport-layer privacy of NFC (local, no server involvement). However, sharing a wallet address establishes a persistent identifier. Users who prioritize privacy may prefer to share stealth meta-addresses rather than direct wallet addresses, enabling future payments without creating a static correlation point.

**Relationship to Payments:**
Saved contacts enable "remote send" functionality—initiating payments to known addresses without physical proximity. This complements tap-to-pay for scenarios where the recipient is not present (remittances, scheduled payments, supplier payments).

---

### 15. Interface-Layer Scam Protection

Decentralized systems shift responsibility for transaction validation from institutions to users. This creates new attack surfaces that the protocol addresses through interface-layer protections.

**Threat: Dust Attacks**
Attackers send minimal-value transactions to establish on-chain links between addresses, enabling wallet clustering and deanonymization. The protocol implements dust detection: payment requests below configurable thresholds (default: 0.0001 ETH equivalent) trigger explicit warnings explaining the risk pattern.

**Threat: Social Engineering via Large Amounts**
Attackers may manipulate users into authorizing unexpectedly large payments. The protocol implements tiered confirmation for large amounts:
- Amounts ≥ 0.5 ETH: Warning screen with explicit confirmation
- Amounts ≥ 5.0 ETH: Strong warning with prominent visual indicators

**Threat: Address Confusion**
Users may send to incorrect addresses due to typos or clipboard hijacking. The protocol encourages use of the contact book for repeat payments, reducing manual address entry.

**Design Philosophy:**
These protections operate at the interface layer, not the protocol layer. They do not prevent users from completing transactions—sovereignty includes the right to make suboptimal decisions—but they ensure users make informed choices. Warnings are educational, explaining the specific risk pattern rather than generic "are you sure?" prompts.

**Limitations:**
Interface-layer protections cannot defend against:
- Compromised devices that modify displayed information
- Social engineering that convinces users to dismiss warnings
- Novel attack patterns not yet recognized by heuristics

Scam protection is probabilistic risk reduction, not cryptographic guarantee.

---

### 16. Limitations and Non-Goals

The protocol explicitly does not address:

- **Amount privacy**: Transaction values are visible on-chain
- **Full anonymity**: Determined adversaries can correlate patterns
- **Offline merchant operation**: Merchants require connectivity for settlement
- **Cross-chain atomic settlement**: Single-chain settlement only
- **Fiat conversion**: Off-ramping to fiat currencies
- **Dispute resolution**: No protocol-level chargeback or arbitration mechanism
- **Regulatory compliance**: KYC/AML requirements are out of scope

These are not failures but scope boundaries. A protocol that attempted to solve all problems would solve none well. The current scope—sovereign authorization with practical settlement—is tractable and useful.

---

### 17. Future Directions

The protocol specification opens several research directions:

**Zero-Knowledge Amount Privacy**
Client-side ZK proofs could hide transaction amounts while preserving verifiability. Mobile ZK proving is computationally intensive but improving.

**Decentralized Bundler Networks**
Reducing bundler centralization through reputation systems, economic incentives, or threshold execution.

**Cross-Chain Intent Settlement**
Extending intent structures to specify cross-chain settlement paths with appropriate atomicity guarantees.

**Hardware Security Integration**
Leveraging secure enclaves for key storage and signing, improving security against device compromise.

**Formal Verification**
Machine-checked proofs of protocol properties, particularly replay protection and intent validity.

---

### 18. Conclusion

The CleanSky Contactless Payment Protocol provides a framework for sovereign payment authorization with practical settlement. It does not claim to solve all problems in decentralized commerce but addresses a specific, tractable problem: enabling users to authorize payments without real-time dependency on centralized infrastructure.

The protocol achieves this through signed intents (decoupling authorization from execution), NFC transport (local peer-to-peer communication), and layered privacy mechanisms (reducing correlation surfaces without guaranteeing anonymity).

Sovereignty claims are scoped precisely: the protocol maximizes sovereignty at the authorization moment while accepting infrastructure dependencies for execution and settlement. This tradeoff reflects practical constraints and economic realities rather than ideological compromise.

The specification is proposed as an open standard for interoperable, sovereign payment authorization, bridging cryptographic guarantees with usable interfaces for everyday commerce.

