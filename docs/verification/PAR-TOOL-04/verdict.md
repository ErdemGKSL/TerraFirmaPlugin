# PAR-TOOL-04 verdict (2026-11-19, HEAD 88d19ab, Paper 1.21.11 + vanilla client 1.21.11, headless)
- tfp:metal/sword/steel: attack_damage 5.75, attack_speed -2.4, max_damage 3300 (item components read over RCON).
- One hit on an unarmoured NoAI cow (10.0 health): 3.25 left = 6.75 damage = 1 base + 5.75 (TFC's productAttributes). PASS (TFC value 6.75, +-0.05).
- tfp:obsidian_macuahuitl: attack_damage 2.5, attack_speed -2.4, max_damage 60. tfp:metal/hammer/copper: 3.25 / -3.0, hammer tag rule speed 5.25.
- Screenshot: inventory_hover.png (hotbar, stone axe tooltip +3 attack damage, -3.2 attack speed).
Note: earlier ledger formula "damage - 1" was wrong against TFC's code; amount = factor x tier damage (see ledger).
