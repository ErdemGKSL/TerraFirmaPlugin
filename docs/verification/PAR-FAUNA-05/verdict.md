# PAR-FAUNA-05 verification
2026-11-20, HEAD fce1191, Paper 1.21.11, headless client (steps typed in chat, `tfp time add days` over RCON).
- Feeding: female cow familiarity 0.00 -> 0.06, action bar "You feed the animal."; a second right-click at once: "It is not hungry." PASS.
- Six daily feeds: 0.12, 0.18, 0.24, 0.30, 0.35 (the species cap). PASS.
- Mating: the ready pair mated after the fifth feed (female fertilized=1, pregnantDay set). PASS.
- Birth: after 60 days the female was no longer fertilized and census showed tfc:cow 3: two calves (age -24000, both genders, familiarity 0.175 = half of 0.35), see calf.png (small calf with the TFC cow model). PASS.
- Not verified: the walk of a pair towards each other (PAR-FAUNA-08 goals) and rotten-food refusal (divergence recorded).
