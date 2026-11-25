# PAR-PROC-55 verdict

Date 2026-11-25, HEAD 25475f18 plus the fix, Paper 1.21.11 + vanilla client 1.21.11, headless.
Blowpipe with a silica batch, heated to 1200 C, right click on a ceramic bowl holding soda ash: the tooltip lists "Glass Operations: - Soda Ash" (blowpipe-tooltip-soda-ash-step.png), the bowl keeps its remaining powder (bowl-with-powder-after-click.png). PASS. Cause and fix: BowlDevices wrote the step into the clone of the held stack; it now writes the stack back to the hand (MockBukkit test: the held stack carries the change).
