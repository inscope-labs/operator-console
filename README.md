# operator-xconsole

**Operator Console — native Android (Kotlin) client for the build engine.**

Scaffold the build engine app.

> **This repository is a scaffold.** Every source file is a *placeholder*: the packages,
> types, module wiring and test harness exist and compile, but the behaviour is not
> implemented. Nothing here is production code, and nothing here talks to a real gateway.

---

## What this is

A thin, credential-minimal operator console. The design follows the **Operator Console —
Android App and Cloudflare Gateway: Design and Security Review** (30 October 2026), and
the package structure follows `directory-structure.md` in this repository.

The decomposition is the one the build engine already uses for agents:

| Component | Role | Where it lives |
|---|---|---|
| **Console** (this repo) | The sole seat of operator authority. Represents human intent. Untrusted for enforcement. | `app/` |
| **Gateway** (Cloudflare Worker) | The sole enforcement point. Verifies capabilities, runs the guard set, holds the GitHub credential. | **Separate project.** Not in this tree. |

### The one property that shapes everything

> **No operator gets full repository access — only the minted branch.**

Concretely, and this is the part that differs from the original proposal:

- **This app holds no GitHub credential of any kind.** No PAT, no OAuth token, no GitHub
  App credential. There is nothing repository-scoped on the device to steal.
- It holds a **non-exportable device keypair** (Keystore, StrongBox where available) that
  signs capabilities, and a **gateway-scoped operator credential** that authorises the
  gateway's operator surface and grants no repository access.
- It **mints a scoped, expiring capability per action** — `ctxop-v1`, naming the
  operation, the branch, the exact head SHA and a single-use nonce — *after* the operator
  confirms with a biometric prompt. No capability exists before the operator decides.
- The gateway keeps its own narrow GitHub credential. Neither side needs the other's.

Rationale and the full argument: `docs/adr/0002-no-repository-credentials-on-device.md`.

---

## Layout

```
operator-xconsole/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml              # version catalog
├── gradlew / gradlew.bat                   # wrapper
├── directory-structure.md                  # the structural spec this tree follows
├── docs/
│   ├── architecture.md                     # package → design-review § mapping
│   ├── threat-model.md                     # §15 residual risks R1–R8
│   ├── capability-format.md                # ctxop-v1 wire format (§5)
│   ├── device-enrollment.md                # Model B enrolment & rotation (D-a)
│   └── adr/
│       ├── 0001-capability-signing-direction.md
│       ├── 0002-no-repository-credentials-on-device.md
│       └── 0003-mtls-to-gateway.md
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/inscopelabs/buildengine/
        │   ├── core/          # crypto, security, network, time, result
        │   ├── capability/    # the ctxop-v1 capability: model, codec, signing, minter
        │   ├── data/          # remote gateway client, local stores, repository impls
        │   ├── domain/        # models, repository interfaces, use cases
        │   ├── ui/            # Compose screens, navigation, components
        │   ├── di/            # Hilt modules
        │   └── util/          # redaction-aware logging
        ├── main/res/
        ├── test/              # JVM unit tests
        └── androidTest/       # instrumented tests (Keystore, biometrics, pinning)
```

`app/src/main/kotlin/com/inscopelabs/buildengine/` is organised so that the package
boundaries translate 1:1 into Gradle modules later without a re-architecture — see
`directory-structure.md` §6.

---

## Build

Requires **JDK 17+** (the build is pinned to JVM target 17) and the **Android SDK** with
platform 36 and build-tools 36.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug     # debug APK
./gradlew :app:testDebugUnitTest # JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
```

Toolchain pinned in `gradle/libs.versions.toml`:

| | |
|---|---|
| Gradle | 8.14.6 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.20 |
| KSP | 2.3.12 |
| Hilt | 2.58 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 28 |

`minSdk 28` is the Keystore API surface floor. **StrongBox requires Android 12+ *and* a
device with a Secure Element** — many devices are software-backed, and the console must
report what is actually true rather than claim hardware backing it does not have.

---

## Deliberately absent from this tree

Their absence is a security property, not an oversight. A PR that adds any of them needs
a very good reason.

- ❌ **Any GitHub token, PAT, OAuth token or GitHub App credential** — on-device or in
  preferences.
- ❌ **A `verify` screen or `verify.argv` execution path.** Plan-supplied verification
  commands stay on a real machine; the gateway has no subprocess and the console must not
  become a way to reach that surface.
- ❌ **`bootstrap` / `init`.** These create repositories and branches. Terminal-only.
- ❌ **A local git worktree, a shell bridge, or `Runtime.exec`.**
- ❌ **Durable audit storage on-device.** The audit trail lives in the private consumer
  repository.
- ❌ **Session tokens persisted across launches.** The session is in-memory only; the
  five-minute capability TTL is the real bound.

---

## Phasing

The console is built in phases so that the read-only half is useful long before there is
a write path worth attacking:

| Phase | Deliverable | Gate |
|---|---|---|
| 0 | Decisions D-a…D-g, GitHub rulesets, V1–V4 verification | Branch scope enforced by GitHub |
| 1 | **Read-only console** (status, next, check, render --check, plans) | Plan state visible from the phone with zero write capability |
| 2 | Single-action approval — `publish` for one minted branch | One branch publishable under a scoped, expiring capability, every action audited |
| 3 | Plan store port — compare-and-swap persistence | Lost updates impossible |
| 4 | Full surface — upload, reconcile, halt/resume, sync, doctor | Guard parity proven by executed tests |
| 5 | Retirement of the Termux workflow | The manual path is no longer the only way to publish |

This scaffold is the **phase 1–2 shape**: the read use cases, the capability machinery and
the publish confirmation screen all exist as placeholders.

---

## Related repositories

| Repository | What it is |
|---|---|
| `inscope-labs/build-engine` | The engine, the plan of record, the write gateway, the guard set |
| `inscope-labs/castiel` | The AMK framework specification (`amk-console` / `amk-controller`) |
| The operator gateway | **Separate project** — the Cloudflare Worker that enforces |

The gateway is deliberately a separate repository with its own `wrangler.toml` and deploy
gate. **Nothing in this tree implements enforcement.** The console is untrusted for
enforcement; every action it takes is an envelope the gateway alone validates.
