# TFC particles on vanilla clients (PAR-DATA-09)

Played through `dev.tfp.paper.particle.TfcParticle#play` and only for explicit actions (policy NP-PART-01).

| Facade entry | Vanilla particle | Count | Spread | Speed | TFC particle and action |
|---|---|---|---|---|---|
| `BUBBLE` | `BUBBLE_POP` | 3 | 0.2 | 0.0 | {@code tfc:bubble}: a bubbling fluid in a device (pot, barrel brewing). |
| `STEAM` | `CLOUD` | 6 | 0.15 | 0.02 | {@code tfc:steam}: water on hot metal or a hot device, quenching. |
| `SPARK` | `CRIT` | 8 | 0.2 | 0.15 | {@code tfc:spark}: hammering, forge welding, flint strikes. |
| `NITROGEN` | `HAPPY_VILLAGER` | 5 | 0.3 | 0.0 | {@code tfc:nitrogen}: fertiliser applied (bonemeal-style). |
| `PHOSPHORUS` | `HAPPY_VILLAGER` | 5 | 0.3 | 0.0 | {@code tfc:phosphorus}. |
| `POTASSIUM` | `HAPPY_VILLAGER` | 5 | 0.3 | 0.0 | {@code tfc:potassium}. |
| `COMPOST_READY` | `HAPPY_VILLAGER` | 6 | 0.3 | 0.0 | {@code tfc:compost_ready}: a compost heap that became ready, on interaction. |
| `COMPOST_ROTTEN` | `SMOKE` | 6 | 0.3 | 0.01 | {@code tfc:compost_rotten}: a rotten compost heap, on interaction. |
| `FLUID_POUR` | `FALLING_WATER` | 8 | 0.1 | 0.0 | {@code tfc:fluid_drip}/{@code fluid_fall}/{@code fluid_land}: pouring a fluid. |
| `BARREL_SPILL` | `FALLING_WATER` | 12 | 0.25 | 0.0 | {@code tfc:barrel_spill}/{@code barrel_drip}: a barrel emptied or spilling. |
| `WATER_FLOW` | `SPLASH` | 6 | 0.2 | 0.0 | {@code tfc:water_flow}/{@code bubble_column_*}: water directed by a device. |

## Dropped (ambient, divergence NP-PART-01)

`butterfly_*`, `moth_*`, `wind`, `leaf`, `falling_leaf`, `snowflake`, `flying_snowflake`, `feather`, `sleep`, `smoke_0..4`: no vanilla client form and a constant packet stream; they appear nowhere. `smoke_N` (chimney-style smoke) may be replaced by vanilla campfire smoke by the feature that needs it, on its own action.
