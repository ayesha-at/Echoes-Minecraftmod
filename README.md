<div align="center">

# 👻 EchoesLab

### *Record yourself. Summon your ghost. Watch it work forever.*

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric-Mod-DBB69B?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
![Tests](https://img.shields.io/badge/tests-46%20passing-brightgreen?style=for-the-badge)
![Status](https://img.shields.io/badge/status-V1.1-purple?style=for-the-badge)

**Press `R`. Do something. Press `R` again. Press `Z`. Congratulations, you now have a ghost.**

</div>

---

## 🕹️ 30 seconds, no reading required

```
 R  →  start/stop recording yourself   (max 60s)
 Z  →  summon an Echo of your last recording
 H  →  banish every Echo currently haunting your world
```

That's it. That's the whole mod. Everything below is just you finding out how deep the rabbit hole goes.

<div align="center">

```
        YOU                          YOUR ECHO
         │                                │
    ┌────▼────┐                     ┌─────▼─────┐
    │  walk    │                     │  walks     │
    │  mine    │  ──── record ────►  │  mines     │
    │  place   │                     │  places    │
    └──────────┘                     └─────┬──────┘
                                            │
                                    loops forever,
                                  striding forward
                                   a little more
                                    each time 🔁
```

</div>

---

## 📜 Table of Contents

- [What actually happens when you press Z](#-what-actually-happens-when-you-press-z)
- [Installation](#-installation)
- [Full controls](#-full-controls)
- [The automation trick](#-the-automation-trick-this-is-the-whole-point)
- [Rules an Echo lives by](#-rules-an-echo-lives-by)
- [Building it yourself](#-building-it-yourself)
- [Testing](#-testing)
- [FAQ / "wait, what if I—"](#-faq--wait-what-if-i)
- [Roadmap](#-roadmap)
- [Credits & prior art](#-credits--prior-art)

---

## 🌀 What actually happens when you press Z

An Echo isn't a recording of a *video* — it's a recording of **you**, played back by a ghost that occupies real space in your world. It has your skin. It swings a pickaxe. It falls off ledges you fell off. And critically:

> **It loops.** And each time it loops, it doesn't reset to where you stood — it picks up exactly where the last loop left off.

Dig a 5-block tunnel while recording, summon an Echo, and it will keep digging *forward* forever — loop 1 digs blocks 1–5, loop 2 digs blocks 6–10, and so on — instead of re-digging the same 5 blocks until the heat death of the universe.

<details>
<summary><b>🔍 Okay but how does it know how far to move each loop?</b> (click to nerd out)</summary>

<br>

Every recording has a **stride** — the distance the whole loop shifts by when it wraps around. EchoesLab figures this out with a simple rule of thumb:

| You recorded... | Stride comes from... |
|---|---|
| Walking in a direction | Your own net displacement, rounded to the nearest whole block |
| Standing still and mining/placing | The spread of your actions along whichever axis they cover the most ground on |
| A round trip back to where you started | Nothing — it just loops in place, and it'll tell you so |

The rounding matters more than it sounds like it should. Real walking is never perfectly straight — a little strafe drift here, a little mouse wobble there — and if you don't round that away, it *accumulates* loop after loop until one day your perfectly straight bridge takes a surprise 90° turn into a lake. Whole numbers in, whole numbers out, no surprises after loop #10,000.

</details>

---

## 📦 Installation

<table>
<tr><td>

**1.** Grab [Fabric Loader](https://fabricmc.net/use/) for Minecraft `26.2`

**2.** Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`

**3.** Build (or download) `echoes-0.1.0.jar` and drop that in too

**4.** Launch. You should see nothing different — until you press `R`.

</td></tr>
</table>

```bash
# building it yourself instead of downloading a jar:
./gradlew build
# -> build/libs/echoes-0.1.0.jar
```

> **Singleplayer only for now.** Multiplayer Echoes are a "someday" problem, not a "this version" problem.

---

## 🎮 Full controls

| Key | Action | Notes |
|:---:|---|---|
| `R` | Start / stop recording | Up to 60 seconds. Rebindable in *Options → Controls → EchoesLab*. |
| `Z` | Summon an Echo | Spawns at whatever block your crosshair is on. Max **3 active at once**. |
| `H` | Clear all Echoes | The nuclear option. No confirmation, no mercy. |
| `F3+B` | *(vanilla)* | Shows hitboxes, if you want to watch your ghost's exact collision box like a weirdo. |

---

## ⚙️ The automation trick (this is the whole point)

<div align="center">

| Scenario | What your Echo does |
|---|---|
| 🚶 Walk forward while mining a tunnel | Keeps extending the tunnel, loop after loop |
| ⛏️ Stand still, mine straight down | Keeps digging deeper, one shaft-length per loop |
| 🧱 Place blocks while walking off a ledge | Keeps bridging, one span at a time |
| 🕺 Record a dance with no net movement | Loops in place forever — automation-free, purely decorative |

</div>

Block breaking is deliberately **creative-style**: no tool tiers, no hardness checks, instant break. It's a wow-factor choice, not an oversight — this mod is about the *mechanic*, not survival balance.

---

## ☠️ Rules an Echo lives by

An Echo isn't invincible, it's just very hard to kill by accident. It ends its haunting (with a little chat message telling you why) when:

- 🧱 it hits a block nothing can break (hello, bedrock)
- 🌍 it wanders past the world border or out of the build-height limits
- 🎒 you (its owner) run out of the item it needs to place
- 👤 you log off entirely
- 🔁 it hits a *very* generous max-loop safety cap, in case none of the above ever trip

Everything else — a missing block, a changed world, an already-placed block — it just shrugs off and keeps going. Echoes are forgiving. The world moved on; so does the ghost.

---

## 🔨 Building it yourself

<details>
<summary>Full dev setup</summary>

<br>

```bash
git clone <this-repo>
cd Echoes
./gradlew build          # compiles + runs tests + produces the jar
./gradlew runClient       # launches a dev Minecraft instance with the mod loaded
```

**Stack:** Fabric Loom · Minecraft 26.2 (Mojang mappings, no Yarn) · Java 25

**Package layout:**

```
com.ayesha.echoes/
├── recording/    ← what you did (pure data — zero Minecraft imports)
├── playback/     ← the looping/striding math (also pure data)
├── echo/         ← the ghost entity + the manager that tracks it
├── rendering/    ← makes the ghost look like you
├── input/        ← keybinds
└── hud/          ← the little REC / Echoes: x/3 overlay
```

The `recording/` and `playback/` packages are deliberately Minecraft-free. That's not an aesthetic choice — it's what makes the next section possible.

</details>

---

## 🧪 Testing

```bash
./gradlew test
```

<div align="center">

**46 tests. Milliseconds. No game boot required.**

</div>

Because the looping math has zero dependency on Minecraft itself, it's covered by ordinary JUnit tests instead of "load the game and squint at a tunnel for ten minutes." That's how the sinking-tunnel bug and the sideways-jog bug got caught and stayed caught — they're now permanent regression tests, not tribal knowledge.

CI runs the fast test suite on every push, *then* the full mod build — so a bad loop calculation gets flagged in seconds, before anything spends time compiling the whole thing.

---

## ❓ FAQ / "wait, what if I—"

<details>
<summary><b>What if I record 3 seconds and it's basically nothing?</b></summary>
<br>
Stop and start in the same tick and the previous recording is kept rather than replaced with something unusable — you won't accidentally nuke a good recording with a misclick.
</details>

<details>
<summary><b>What if two of my Echoes are digging toward each other?</b></summary>
<br>
They don't know about each other. Enjoy the collision. This is a feature, not a bug — chaos is part of the fun.
</details>

<details>
<summary><b>What if I summon an Echo, then immediately log off?</b></summary>
<br>
It doesn't get saved to disk on purpose — Echoes are session-scoped, so nothing haunted comes back when you reload the world.
</details>

<details>
<summary><b>Can Echoes fight monsters / take damage / die to lava?</b></summary>
<br>
No — they're invulnerable by design. They're automation tools, not combat pets. If you want a bodyguard, this isn't it (yet).
</details>

<details>
<summary><b>Why does my Echo just stand there looping in place?</b></summary>
<br>
Your recording ended exactly where it started — zero net displacement, so there's nowhere for the loop to advance to. The summon message tells you this. Walk somewhere while recording next time.
</details>

---

## 🗺️ Roadmap

- [x] Record player movement + rotation
- [x] Ghost rendering with your actual skin
- [x] World interaction (break / place)
- [x] Forward-striding loops (automation!)
- [x] Full test coverage on the core loop math
- [x] CI/CD
- [ ] Multiplayer support
- [ ] Persistence across sessions
- [ ] Echoes that survive world reload
- [ ] More action types (item use, entity interaction)

---

## 🙏 Credits & prior art

Inspired by the time-echo mechanic from [**Echo Labs**](#), a JS/Canvas puzzle platformer built earlier in this same spirit — EchoesLab is that idea's "what if this were *useful*" sequel.

[`mc-mocap-mod`](https://github.com/mt1006/mc-mocap-mod) was consulted as conceptual reference during early rendering work (LGPL-3.0 — no code was copied; this project is MIT-licensed).

---

<div align="center">

**Built by [Ayesha Tanvir](#)** · MIT Licensed · *go forth and haunt responsibly* 👻

</div>
