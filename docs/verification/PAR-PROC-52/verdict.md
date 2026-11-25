# PAR-PROC-52 verdict

Date 2026-11-25, HEAD 0b20422 plus this change, Paper 1.21.11 build 132 + vanilla client 1.21.11 (headless, GUI scale 2, 1280x720), CraftEngine 26.9.2, packet pack applied.
Bot in survival on a stone platform; items from `ce item give`, heat from `tfp debug heat Bot 1200`, off-hand tools copied with `item replace entity Bot weapon.offhand`.

| Step | Result | Evidence |
|---|---|---|
| The 12 new CE items show TFC's icons with TFC's names (blowpipe, silica glass batch, jacks, paddle, wool cloth, gem saw) | PASS | items-in-inventory.png |
| Batch on the cursor, right click on the blowpipe in the inventory: it becomes `tfp:blowpipe_with_glass` (one batch used, 3 left) | PASS | batch-loaded-blowpipe-with-glass.png |
| Heated to 1200 C: yellow heat glow on the icon; empty off hand + use: action bar "Glass operations (1/24): Blow" | PASS | heated-blow-actionbar-glow.png |
| Off-hand jacks: "Blow, Pinch" | PASS | pinch-offhand-jacks.png |
| Item tooltip lists the steps ("Glass operations: - Stretch ...", batch name, heat) | PASS | tooltip-steps.png |
| Cold blowpipe (about 1 in-game day later): "Not hot enough" in red | PASS | cold-not-hot-enough.png |
| Lamp glass: blow, pinch (jacks), flatten (paddle), blow, saw (gem saw): `tfp:lamp_glass` in the inventory, the blowpipe is empty again | PASS | lamp-glass-result-blowpipe-empty.png |
| Right click on a powder bowl holding the loaded blowpipe: a soda ash step is added | FAIL, two causes. (1) Our own listener cancelled the click before the bowl's behaviour ran: fixed here (a device block now keeps the click; MockBukkit test). (2) After that fix the bowl takes the click but the soda ash step is lost: `BowlDevices` hands `BowlGlass.apply` a clone of the held stack and never writes it back to the hand (paper/bowl is not owned by this item). Follow-up PAR-PROC-55 | powder-bowl-click-no-step.png |
| Sound of the blow | NOT CHECKED (headless client has no sound) | |

Verdict: the done criterion (a lamp glass made in game, steps on the action bar) passes. The bowl powder step stays open in PAR-PROC-55.
