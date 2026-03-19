# 🦴 Bony

> A bare-bones, Google-free Nostr client for Android. Fast, lean, and extensible via plugins.

**Name is a placeholder** — Bony captures the bare-bones philosophy for now.

---

## 🧘 Philosophy

Do the minimum well. No analytics, no tracking, no Google Services. Authentication is always delegated to an external signer — the app never touches your private key. Features you don't want don't ship with the app; they're plugins you install by choice.

---

## ⚡ Core Features

- **Multi-account** — add, switch, and remove accounts from within the app
- **External signers** — [Amber](https://github.com/greenart7c3/Amber) (NIP-55) and nsecBunker (NIP-46); Android Keystore as a local fallback
- **Home feed** — follow-graph events with live relay streaming, pull-to-refresh, and atomic load (feed appears all at once, not one note at a time)
- **Reposts and quote-notes** — kind-6 reposts rendered as embedded cards; quote-notes (NIP-18 `q` tag and inline `nostr:note1…` refs) resolved and embedded
- **Inline media** — images render inline (tap to expand); video links open the system player
- **Thread view** — root note → gap indicator → direct parent → focused note → live replies
- **Boost notes** — one-tap repost (kind-6) via your active signer
- **Share notes** — Android share sheet with note text + `nostr:note1…` URI
- **Relay management** — live connectivity indicator in the feed top bar; add/remove relays with status dots (green/yellow/red); changes persisted to your account
- **nostr: URI handling** — `nostr:npub1…` @mentions abbreviated inline; `nostr:note1…`/`nostr:nevent1…` refs rendered as embedded quote cards
- **Log export** — Settings → Share logs for bug reports
- **Push notifications** via [UnifiedPush](https://unifiedpush.org/) (no FCM)
- **Distribution** via F-Droid and GitHub Releases

---

## 🛠️ Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| WebSocket | OkHttp |
| Database | Room (SQLite) — events persisted across restarts |
| Crypto | Bouncy Castle / tink (secp256k1) |
| DI | Hilt |
| Preferences | DataStore |
| Min SDK | API 26 (Android 8.0) |

No Firebase. No Google Play Services.

---

## 🗂️ Event Kind Support

| Kind | Name | Handling |
|------|------|----------|
| 0 | Metadata | Parsed into `ProfileContent`; cached in `ProfileRepository`; drives avatar + display name in feed, threads, and top-bar account header |
| 1 | Text Note | Stored in Room DB; displayed in feed and thread view; inline images and video; `nostr:` URI handling |
| 3 | Follow List | Parsed on login to expand home feed subscription to followed pubkeys |
| 6 | Repost | Rendered as embedded card with original author and content; publishing (boost) supported |
| 10002 | Relay List | Read relays extracted and connected on login; list persisted to account |
| 24133 | Nostr Connect | Used internally by `NsecBunkerSigner` for NIP-46 request/response |

---

## 📡 NIP Compatibility

Legend: ✅ Supported &nbsp;|&nbsp; 🚧 Partial &nbsp;|&nbsp; 🔌 Plugin &nbsp;|&nbsp; planned &nbsp;|&nbsp; — N/A

### Built into the app

| NIP | Name | Status | Notes |
|---|---|---|---|
| [NIP-01](https://github.com/nostr-protocol/nips/blob/master/01.md) | Basic protocol & event model | ✅ | Event, Filter, relay WebSocket pool, kind-0 profile metadata |
| [NIP-02](https://github.com/nostr-protocol/nips/blob/master/02.md) | Contact lists | ✅ | Follow list drives the home feed |
| [NIP-10](https://github.com/nostr-protocol/nips/blob/master/10.md) | Text note references & replies | ✅ | Reply indicator; thread view shows root → parent → focused note → live replies |
| [NIP-18](https://github.com/nostr-protocol/nips/blob/master/18.md) | Reposts | ✅ | Render kind-6 reposts and `q`-tag quote-notes as embedded cards; boost (publish kind-6) |
| [NIP-19](https://github.com/nostr-protocol/nips/blob/master/19.md) | bech32-encoded entities | ✅ | npub, note, nevent encode/decode; nostr: URI parsing in note content |
| [NIP-27](https://github.com/nostr-protocol/nips/blob/master/27.md) | Text note references | 🚧 | `nostr:note1`/`nevent1` refs rendered as embedded quote cards; `nostr:npub1` abbreviated as @mention; display-name resolution for @mentions not yet implemented |
| [NIP-44](https://github.com/nostr-protocol/nips/blob/master/44.md) | Versioned encryption | ✅ | ChaCha20 + HMAC-SHA256, HKDF |
| [NIP-46](https://github.com/nostr-protocol/nips/blob/master/46.md) | Nostr Connect (nsecBunker) | 🚧 | Signer and onboarding UI implemented; end-to-end testing against real bunkers pending |
| [NIP-55](https://github.com/nostr-protocol/nips/blob/master/55.md) | Android signer (Amber) | ✅ | Sign, encrypt, decrypt via intent |
| [NIP-65](https://github.com/nostr-protocol/nips/blob/master/65.md) | Relay list metadata | ✅ | Read relays fetched on login; persisted to account; relay management UI with live status |

### NIPs with plugins available

*No plugins exist yet. This section will list community-built plugins as they are published.*

### Common NIPs

| NIP | Name | Status | Notes |
|---|---|---|---|
| [NIP-04](https://github.com/nostr-protocol/nips/blob/master/04.md) | Encrypted direct messages (legacy) | 🔌 | Deprecated; for DMs consider [Pokey](https://github.com/KoalaSat/pokey) (notifications) or [0xchat](https://0xchat.com) (full client) |
| [NIP-05](https://github.com/nostr-protocol/nips/blob/master/05.md) | DNS-based identifiers | planned | Verification badge on profiles |
| [NIP-09](https://github.com/nostr-protocol/nips/blob/master/09.md) | Event deletion | planned | |
| [NIP-11](https://github.com/nostr-protocol/nips/blob/master/11.md) | Relay information document | planned | Relay metadata / limits |
| [NIP-17](https://github.com/nostr-protocol/nips/blob/master/17.md) | Private direct messages | 🔌 | For DMs, [0xchat](https://0xchat.com) is the recommended companion app |
| [NIP-21](https://github.com/nostr-protocol/nips/blob/master/21.md) | `nostr:` URI scheme | planned | Deep links from other apps |
| [NIP-25](https://github.com/nostr-protocol/nips/blob/master/25.md) | Reactions | 🔌 | Likes, emoji reactions |
| [NIP-36](https://github.com/nostr-protocol/nips/blob/master/36.md) | Sensitive content | planned | Content warnings |
| [NIP-42](https://github.com/nostr-protocol/nips/blob/master/42.md) | Relay authentication | planned | Required by nos.lol and other quality relays; on the immediate roadmap |
| [NIP-51](https://github.com/nostr-protocol/nips/blob/master/51.md) | Lists | 🔌 | Mute lists, pin lists, bookmarks |
| [NIP-57](https://github.com/nostr-protocol/nips/blob/master/57.md) | Lightning zaps | 🔌 | |

### Uncommon NIPs

| NIP | Name | Status | Notes |
|---|---|---|---|
| [NIP-13](https://github.com/nostr-protocol/nips/blob/master/13.md) | Proof of work | — | Anti-spam; relay-dependent |
| [NIP-23](https://github.com/nostr-protocol/nips/blob/master/23.md) | Long-form content | 🔌 | Articles / blogs |
| [NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md) | Expiration timestamp | planned | |
| [NIP-47](https://github.com/nostr-protocol/nips/blob/master/47.md) | Wallet Connect | 🔌 | NWC; pay invoices in-app |
| [NIP-50](https://github.com/nostr-protocol/nips/blob/master/50.md) | Search | 🔌 | Relay-dependent full-text search |
| [NIP-52](https://github.com/nostr-protocol/nips/blob/master/52.md) | Calendar events | 🔌 | |
| [NIP-53](https://github.com/nostr-protocol/nips/blob/master/53.md) | Live activities | 🔌 | Streams, live audio |
| [NIP-58](https://github.com/nostr-protocol/nips/blob/master/58.md) | Badges | 🔌 | |
| [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md) | Gift wrap | planned | Used by NIP-17 DMs |
| [NIP-72](https://github.com/nostr-protocol/nips/blob/master/72.md) | Moderated communities | 🔌 | Reddit-style communities |
| [NIP-84](https://github.com/nostr-protocol/nips/blob/master/84.md) | Highlights | 🔌 | |
| [NIP-89](https://github.com/nostr-protocol/nips/blob/master/89.md) | Recommended app handlers | 🔌 | |
| [NIP-90](https://github.com/nostr-protocol/nips/blob/master/90.md) | Data vending machines | 🔌 | AI/compute marketplace |
| [NIP-94](https://github.com/nostr-protocol/nips/blob/master/94.md) | File metadata | 🔌 | |
| [NIP-96](https://github.com/nostr-protocol/nips/blob/master/96.md) | HTTP file storage | 🔌 | Image/file uploads |
| [NIP-99](https://github.com/nostr-protocol/nips/blob/master/99.md) | Classifieds | 🔌 | |

---

## 🧩 Plugin System

Bony is intentionally minimal. Extended functionality is delivered via plugins — separate APKs that implement a defined AIDL interface. The host app binds to plugin services; Android enforces process isolation.

Some features that are candidates for plugins rather than core:
- Reactions (NIP-25), zaps (NIP-57)
- DMs — NIP-04 legacy and NIP-17 private DMs
- Long-form content (NIP-23)
- Feed lists (global, hashtag, curated)
- Tor transport
- Image/video upload (NIP-96)

### 🔐 Plugin Permissions

Plugins declare only what they need:

| Permission | Description |
|---|---|
| `READ_EVENTS` | Read cached events from the feed |
| `READ_PROFILE` | Access contact/profile metadata |
| `INJECT_UI` | Render UI into defined host slots |
| `PUBLISH_EVENT` | Request to publish an event (proxied through the app's signer — plugin never sees keys) |
| `READ_DMs` | Access decrypted DMs (high-trust, explicit user grant required) |

### 📦 Plugin Registry

Plugins are discovered through an in-app registry. Two approaches are under consideration:

- **Option A (GitHub JSON):** A curated `registry.json` in a public repo. PRs = vetting. Simple to bootstrap.
- **Option B (Nostr-native):** A specific event kind for plugin listings, published by vetted curator pubkeys. Stacks are playlist-style events grouping related plugins.

Option B is philosophically consistent with Nostr. Option A is simpler to start with. Likely: start with A, migrate to B.

### 🥞 Stacks

Stacks are curated plugin bundles for common use cases, e.g.:

- **Newb Stack** — DMs, zaps, image display
- **Power User Stack** — relay management, advanced filters, NIP-72 communities

Stacks lower the barrier for new users while keeping the core app lean.

### 🛡️ Security Model

- Plugins **never** receive private keys or signed events
- `PUBLISH_EVENT` submits an unsigned template → app signer signs → app broadcasts
- Plugin identity is verified by package name + signing certificate
- Unverified (sideloaded) plugins trigger a prominent warning
- UI-injecting (WebView) plugins run with strict Content-Security-Policy, no shared storage

---

## 🚀 Getting Started

```bash
# Clone and open in Android Studio — the Gradle wrapper will be generated automatically.
# Min SDK: API 26 (Android 8.0). No Google Play Services required.
```

## 📁 Repository Layout

```
bony/
├── app/
│   └── src/main/
│       ├── kotlin/social/bony/
│       │   ├── BonyApp.kt          # Hilt application entry point
│       │   ├── MainActivity.kt
│       │   ├── nostr/
│       │   │   ├── Event.kt        # NIP-01 event model + UnsignedEvent
│       │   │   ├── EventKind.kt    # Known event kind constants
│       │   │   ├── Tag.kt          # Tag wrapper + NIP-10/18 helpers
│       │   │   ├── Filter.kt       # Subscription filters
│       │   │   ├── Nip19.kt        # bech32 encode/decode (npub, note, nevent, TLV)
│       │   │   └── Crypto.kt       # BIP-340 Schnorr verification
│       │   ├── account/
│       │   │   ├── signer/         # NostrSigner, AmberSigner, LocalKeySigner, NsecBunkerSigner
│       │   │   └── AccountRepository.kt
│       │   ├── db/                 # Room DB: events, profiles
│       │   ├── profile/            # ProfileRepository, ProfileContent
│       │   └── ui/
│       │       ├── BonyNavHost.kt
│       │       ├── feed/           # FeedScreen, FeedViewModel, NoteCard, NoteContent
│       │       ├── thread/         # ThreadScreen, ThreadViewModel
│       │       ├── compose/        # ComposeScreen, ComposeViewModel
│       │       ├── profile/        # ProfileScreen, ProfileViewModel
│       │       ├── settings/       # SettingsScreen, AccountManagement, RelayManagement
│       │       ├── onboarding/     # OnboardingScreen, OnboardingViewModel
│       │       ├── components/     # AccountSwitcher
│       │       └── theme/
│       └── res/
├── plugin-api/       # (planned) AIDL interfaces for plugin developers
├── plugins/
│   └── example/     # (planned) Reference plugin implementation
└── docs/
    └── plugin-dev-guide.md  # (planned)
```

---

## 🐛 Reporting Issues

Bony includes built-in log export to make bug reports useful:

1. Reproduce the issue
2. Open **Settings** (gear icon in the feed top bar)
3. Tap **Share logs** and send the log file with your issue report

Logs are written to the app's private storage (`filesDir/logs/bony.log`), rotate at 2 MB, and never leave the device unless you explicitly share them.

---

## 🗺️ Roadmap

### Immediate (next session)

- **NIP-42 relay AUTH** — required by nos.lol and other quality relays; without it those relays return nothing
- **Reply** — compose a kind-1 reply with NIP-10 `e`/`p` tags; show reply context above the compose field
- **Quote** — compose a quote-note with `q` tag and inline `nostr:note1…` reference
- **@mention display-name resolution** — look up `nostr:npub1…` refs in note text against the profile cache and replace with display names

### Near-term

- Follow / unfollow profiles
- Share profile (npub as `nostr:npub1…` URI + display name)
- Feed lists: global feed, hashtag feeds — some may be plugins
- Tor transport — route relay connections over Tor for privacy; plugin candidate

### Deferred / companion apps

- **Notifications** — [Pokey](https://github.com/KoalaSat/pokey) handles UnifiedPush-based Nostr notifications and integrates well
- **DMs** — [0xchat](https://0xchat.com) supports NIP-04 and NIP-17 private DMs; Bony may eventually offer a basic DM plugin

### Known limitations

- NIP-42 relay AUTH is not yet implemented. Relays that require authentication (e.g. nos.lol, paid relays) will not serve events.
- nsecBunker onboarding has not been tested end-to-end against a real bunker.
- `nostr:npub1…` @mentions in note text are abbreviated but not resolved to display names.
- Feed scroll position may not reset correctly on account switch in all cases.

---

## 🤝 Contributing

Plugins extend Bony. Core PRs should keep the app lean — if a feature can be a plugin, it should be.

---

## 📄 License

MIT
