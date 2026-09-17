# Echo looping / automation fix

## What was wrong

The per-loop advance was derived from `lastAction - firstAction` — the
*spread of block actions inside one loop*, which is not the same quantity as
*how far the loop should move between iterations*. Two symptoms came out of
that one mistake:

**The Echo sank a block per loop ("drowning").** Digging a 2-high tunnel
breaks the head block first and the foot block last, so the Y component of
that span is `-1`. Every loop dropped the Echo one block, it tunnelled into
the ground, and after a few dozen loops it hit bedrock and died on the
"unbreakable block" rule — which is why it looked like it stopped repeating.

**It re-dug ground it had already cleared.** For a 5-column tunnel the span
gives 4, so loop *n+1* started on top of loop *n*'s last column.

**Short recordings didn't advance at all.** With fewer than two actions it
fell back to a raw double position delta, which is ~0 for a short recording —
so it looped in place, looking like it reset to the summon point.

## What it does now

The stride is the player's own net displacement **rounded to whole blocks**.
Integers multiplied by a loop count are exact, so this also removes the
sideways-jog class of bug at the source (that was accumulated strafe drift
crossing a block boundary; rounding was the right tool, action offsets
weren't). A flat walk gives `y = 0`, so the sink is gone by construction.

For recordings made standing still — reach-mining, or digging straight down —
the stride falls back to the **bounding span of the actions along their single
dominant axis**, sized `span + 1` so consecutive loops sit flush. One axis
only, so no off-axis sink or jog can creep back in. Horizontal axes win ties,
so a 2-high tunnel advances forward rather than downward, but a genuine
mineshaft still strides down.

Zero stride is still valid — a recording that ends where it started loops in
place, and the summon message now says so instead of leaving you guessing.

## Other fixes

- **Tick order.** Playback now reads then advances. It advanced first, so
  snapshot 0 and any action on it were skipped on the *first* loop only, and
  replayed on every later one.
- **One-snapshot recordings** froze forever (`if (count < 2) return`). They
  now loop like any other length.
- **Action de-duplication** uses an absolute tick counter instead of a
  per-loop index, which wraps.
- **Lag spikes** wrap cleanly instead of stranding progress past the end.
- **Orphaned Echoes.** Entities weren't excluded from world saves, so an Echo
  present at save time came back with no playback — invulnerable, immobile,
  untracked, unremovable. Now excluded, plus a first-tick cleanup backstop.
- **Threading.** Spawn/despawn hop onto the server thread instead of mutating
  server-owned state from the client tick. The tracking list is copy-on-write.
- **Bounds.** Added a world-border check; only build height was checked.
- **Chunk safety.** Block interactions skip unloaded chunks rather than
  forcing terrain generation from an entity tick.
- **Placement misattribution.** A watched position is now confirmed against
  the specific `Block` the held item places, not merely "something solid
  appeared" — so an Echo bridging through that spot isn't recorded as yours.
  Echo placements are suppressed from capture too, not just breaks.
- **Velocity.** Residual `deltaMovement` is cleared each tick; it was
  accumulating against the authoritative `setPos`.
- **Empty recordings** are rejected with a clear message instead of an index
  error, and start/stop in the same tick no longer overwrites a good
  recording with an unusable empty one.
- **Death reasons** are now reported to the owner in chat.
- **HUD** no longer races between `isRecording()` and `getCurrentRecording()`.
- **Rejoin** clears in-flight state but keeps your last completed recording
  (snapshots are offsets, so a recording is portable between worlds).

## Testing

`recording/` and `playback/` are now free of Minecraft types, so the whole
loop/stride/translation core is covered by plain JUnit — 46 tests, no game
bootstrap, milliseconds to run. CI runs them on every push before it spends
time on the full Loom build.
