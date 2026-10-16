# Usage: python3 -I export_fullcube_states.py <research/fc dir> <mcmeta dir> <out.csv>
# Per-state export of the vanilla full-cube supply for the datagen visual allocator (M1-DGN-04).
# Inputs: fc_<ver>.json (fullcube.py), blockprops_26.3.json (blockprops.py), rendertypes_<ver>.txt (rendertypes.py)
# and misode/mcmeta blocks_<ver>.json, as described in docs/research/block-budget-workarounds.md section 7.
# The classification below is the same as the one that produced docs/research/vanilla-fullcube-supply.csv
# (classify.py); a datagen unit test checks that the per-block counts of both files agree.
import json, sys, csv, re, collections, os
fcdir, metadir, outcsv = sys.argv[1], sys.argv[2], sys.argv[3]
VERS = ['1.21.4', '1.21.11', '26.1.2', '26.3']
fc = {v: json.load(open(f'{fcdir}/fc_{v}.json')) for v in VERS}
meta = {v: json.load(open(f'{metadir}/blocks_{v}.json')) for v in VERS}
bp = json.load(open(f'{fcdir}/blockprops_26.3.json'))

def layers(v):
    res = {}; cur = None
    p = f'{fcdir}/rendertypes_{v}.txt'
    if not os.path.exists(p): return res
    for l in open(p):
        if l.startswith('##'):
            name = l.split()[1]
            cur = {'beaconBeam/cutout/entitySolidZOffsetForward/lambda$static$5': 'cutout',
                   'cutoutMipped/entitySolid/entityTranslucentEmissive/lambda$static$6': 'cutout_mipped',
                   'entityCutout/translucent/weather': 'translucent', 'outline/tripwire': 'tripwire'}.get(name, name.lower())
            continue
        if l.startswith('#'): continue
        for b in l.split(): res[b] = cur
    return res
LAYER = {v: layers(v) for v in ['1.21.4', '1.21.11']}

COLORS = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
NETHER_END = set('''netherrack nether_bricks cracked_nether_bricks chiseled_nether_bricks red_nether_bricks nether_wart_block warped_wart_block
crimson_nylium warped_nylium crimson_stem warped_stem crimson_hyphae warped_hyphae stripped_crimson_stem stripped_warped_stem
stripped_crimson_hyphae stripped_warped_hyphae crimson_planks warped_planks soul_sand soul_soil basalt polished_basalt blackstone
polished_blackstone polished_blackstone_bricks cracked_polished_blackstone_bricks chiseled_polished_blackstone gilded_blackstone
nether_gold_ore nether_quartz_ore ancient_debris netherite_block glowstone shroomlight magma_block crying_obsidian respawn_anchor lodestone
quartz_block quartz_bricks quartz_pillar chiseled_quartz_block smooth_quartz end_stone end_stone_bricks purpur_block purpur_pillar
nether_brick_slab red_nether_brick_slab blackstone_slab polished_blackstone_slab polished_blackstone_brick_slab quartz_slab smooth_quartz_slab
purpur_slab end_stone_brick_slab crimson_slab warped_slab'''.split())
# client-side light emitters (constant) -- minecraft-data emitLight + Blocks.lightLevel in 26.3
LIGHT = set('glowstone sea_lantern shroomlight ochre_froglight verdant_froglight pearlescent_froglight jack_o_lantern magma_block crying_obsidian beacon sculk_catalyst vault trial_spawner'.split())
LIT_STATE = set('redstone_lamp furnace smoker blast_furnace redstone_ore deepslate_redstone_ore'.split())  # light only when lit=true
MOVEMENT = {'ice': 'friction 0.98', 'packed_ice': 'friction 0.98', 'blue_ice': 'friction 0.989', 'frosted_ice': 'friction 0.98, melts',
            'slime_block': 'bounce + friction 0.8', 'honey_block': 'speed/jump 0.4/0.5, slide', 'soul_sand': 'speed 0.4, 14px collision',
            'mud': '14px collision', 'powder_snow': 'no collision, freezing'}
BE_RENDER = {'spawner': 'spinning mob + flame particles', 'trial_spawner': 'spinning mob, light', 'vault': 'item renderer, light 6',
             'beacon': 'beam renderer, light 15', 'potent_sulfur': 'geyser/gas client tickers (26.3)', 'creaking_heart': 'client ticker + idle sound (animateTick)',
             'structure_block': 'bounding-box renderer (ops)', 'test_instance_block': 'BE renderer (ops)'}
GLASSLIKE = {'glass', 'tinted_glass'} | {f'{c}_stained_glass' for c in COLORS}
INTERACT = {'crafting_table', 'cartography_table', 'smithing_table', 'loom', 'furnace', 'smoker', 'blast_furnace', 'barrel', 'dispenser',
            'dropper', 'crafter', 'chiseled_bookshelf'}
OPS = {'command_block', 'chain_command_block', 'repeating_command_block', 'structure_block', 'jigsaw', 'test_block', 'test_instance_block'}
FALLING = {'sand', 'red_sand', 'gravel', 'suspicious_sand', 'suspicious_gravel'} | {f'{c}_concrete_powder' for c in COLORS}
DESERT_SOUND = {'sand', 'red_sand', 'terracotta'} | {f'{c}_terracotta' for c in COLORS}
AXE = lambda b: (b.endswith('_log') or b.endswith('_wood') or b.endswith('_stem') or b.endswith('_hyphae') or b == 'bamboo_block') and not b.startswith('stripped_') and b != 'mushroom_stem'
COPPER = lambda b: ('copper' in b and 'ore' not in b and 'raw_' not in b)
SNOWY = {'grass_block', 'podzol', 'mycelium'}
PARTICLE = {'mycelium': 'spore particles (animateTick)', 'wet_sponge': 'drip particles (animateTick)', 'crying_obsidian': 'drip particles',
            'respawn_anchor': 'portal particles when charged'}

def is_leaves(b): return b.endswith('_leaves')

def behaviour(b, st):
    """returns (class, reason) for one state string; class in clean|caveat|avoid"""
    props = dict(kv.split('=') for kv in st.split('[', 1)[1].rstrip(']').split(',')) if '[' in st else {}
    if b in LIGHT: return 'avoid', 'client light emission'
    if b in LIT_STATE and props.get('lit') == 'true': return 'avoid', 'client light emission (lit=true)'
    if 'copper_bulb' in b and props.get('lit') == 'true': return 'avoid', 'client light emission (lit=true)'
    if b == 'respawn_anchor' and props.get('charges') != '0': return 'avoid', 'light + particles when charged'
    if b in MOVEMENT: return 'avoid', 'client movement: ' + MOVEMENT[b]
    if b == 'snow': return 'avoid', 'snow[layers=8] has 14px collision'
    if b in BE_RENDER: return 'avoid', BE_RENDER[b]
    if b in GLASSLIKE: return 'avoid', 'noOcclusion + translucent layer; light passes (client light mismatch)'
    if b in ('redstone_ore', 'deepslate_redstone_ore'): return 'avoid', 'client predicts lit=true on touch (stepOn/attack/use)'
    reasons = []
    if props.get('waterlogged') == 'true': reasons.append('waterlogged: client fluid (drip particles below unless #impermeable)')
    if is_leaves(b): reasons.append('leaves: noOcclusion, light dampening 1, rain-drip/leaf particles; cutout layer <=1.21.11')
    if b in ('mangrove_roots',) or 'copper_grate' in b: reasons.append('noOcclusion, cutout layer')
    if b in INTERACT: reasons.append('client-interactable (no placement prediction; CE resends sounds)')
    if b == 'jukebox' and props.get('has_record') == 'true': reasons.append('client-interactable (no placement prediction) when has_record=true')
    if b in ('beehive', 'bee_nest') and props.get('honey_level') == '5': reasons.append('honey drip particles + shears/bottle interaction')
    if b in OPS: reasons.append('op/creative-only GUI on right-click')
    if b in FALLING: reasons.append('falling-dust particles when unsupported')
    if b in DESERT_SOUND: reasons.append('ambient desert sounds 1.21.5+ (tag-controlled)')
    if AXE(b): reasons.append('axe strip predicted client-side (AxeItem)')
    if COPPER(b) and 'grate' not in b: reasons.append('axe scrape / honeycomb wax predicted client-side')
    if b in SNOWY: reasons.append('snowy=true predicted by client neighbour update')
    if b in PARTICLE: reasons.append(PARTICLE[b])
    if b in ('tnt',): reasons.append('flint&steel interaction')
    if b in ('pumpkin',): reasons.append('shears interaction')
    if b in ('amethyst_block', 'budding_amethyst'): reasons.append('crystal step chime (tag-controlled)')
    if b in ('redstone_block', 'observer', 'target'): reasons.append('vanilla redstone dust connects to it client-side')
    if b.endswith('concrete_powder'): reasons.append('turns to concrete client-side next to water')
    if reasons:
        MAJOR = ('waterlogged', 'leaves:', 'noOcclusion', 'client-interactable (no', 'falling-dust', 'spore particles', 'drip particles (animateTick)')
        major = any(r.startswith(MAJOR) or any(m in r for m in MAJOR) for r in reasons)
        return ('caveat_major' if major else 'caveat_minor'), '; '.join(reasons)
    return 'clean', ''

TFC_PRESENT = {'bedrock', 'snow_block', 'ice', 'packed_ice', 'blue_ice', 'powder_snow', 'snow'}  # referenced by TFC worldgen (src/tfc .../world)
FORMED = {'obsidian': 'formed by lava+water', 'cobblestone': 'formed by lava+water', 'stone': 'formed by lava+water',
          'basalt': 'formed by lava over soul soil next to blue ice', 'snow_block': '', }
def source_family(b):
    if b in TFC_PRESENT: return 'tfc_present'
    if b in NETHER_END: return 'nether_end'
    return 'absent'


# ---- per-state export (new) ----------------------------------------------------------------------------------
FAMILIES = {'1.21': ['1.21.4', '1.21.11'], '26': ['26.1.2', '26.3']}
per_ver = {}
for v in VERS:
    data = fc[v]
    sig_owner = {}
    for b, r in data.items():
        if not r.get('full_n'): continue
        if b.startswith('waxed_') or b.startswith('infested_') or b.endswith('_slab'): continue
        for s in r['statelist']:
            if s['full'] and not s['ce']: sig_owner.setdefault(s['sig'], s['state'])
    out = {}
    for b, r in sorted(data.items()):
        if not r.get('full_n'): continue
        default = meta[v][b][1]
        dkey = b + ('[' + ','.join(f'{k}={default[k]}' for k in sorted(default)) + ']' if default else '')
        states = [dict(s) for s in r['statelist'] if s['full']]
        if is_leaves(b) or b == 'mangrove_roots':
            for s in states:
                if 'waterlogged=true' in s['state']: s['sig'] += '|water'
        states.sort(key=lambda s: (s['state'] != dkey, s['state']))
        seen = {}
        for s in states:
            if s['ce']:
                out[s['state']] = ('ce_used', '-', '', b); continue
            cross = None
            if b.startswith('waxed_') or b.startswith('infested_') or b.endswith('_slab'):
                cross = sig_owner.get(s['sig'])
            if s['sig'] in seen:
                src, twin = 'identity_dup', seen[s['sig']]
            elif cross:
                src, twin = 'identity_dup_cross', cross; seen[s['sig']] = s['state']
            else:
                src, twin = source_family(b), ''; seen[s['sig']] = s['state']
            beh, why = behaviour(b, s['state'])
            out[s['state']] = (src, beh, twin, b)
    per_ver[v] = out

rows = []
for fam, vers in FAMILIES.items():
    common = set.intersection(*(set(per_ver[v]) for v in vers))
    for st in sorted(common):
        recs = [per_ver[v][st] for v in vers]
        if any(r[0] == 'ce_used' for r in recs):
            continue
        if len({r[:3] for r in recs}) != 1:
            print('MISMATCH', fam, st, recs, file=sys.stderr)
            continue
        src, beh, twin, b = recs[0]
        rows.append((fam, 'minecraft:' + st, src, beh, ('minecraft:' + twin) if twin else ''))
with open(outcsv, 'w', newline='') as f:
    f.write('# Opaque full-cube vanilla block states that CraftEngine\'s default mappings.yml does not use, per MC version family.\n')
    f.write('# A state is listed for a family only if it exists, is a full cube and is not CE-used in every version of the family\n')
    f.write('# (1.21 = 1.21.4 + 1.21.11, 26 = 26.1.2 + 26.3) with the same classification in each.\n')
    f.write('# source: identity_dup (same model as twin, same block) | identity_dup_cross (same model as twin, other block) | absent | nether_end | tfc_present\n')
    f.write('# behaviour: clean | caveat_minor | caveat_major | avoid (docs/research/block-budget-workarounds.md section 2.5)\n')
    f.write('# Generated by tools/datagen/scripts/export_fullcube_states.py from the research inputs; do not edit by hand.\n')
    w = csv.writer(f, lineterminator='\n')
    w.writerow(['family', 'state', 'source', 'behaviour', 'twin'])
    w.writerows(rows)
import collections as C
for fam in FAMILIES:
    c = C.Counter((r[2], r[3]) for r in rows if r[0] == fam)
    tg = sum(n for (s, b), n in c.items() if b in ('clean', 'caveat_minor') and s != 'tfc_present')
    print(fam, 'terrain-grade', tg, dict(sorted(c.items())))
