---
id: PAR-DECO-29
title: "Rack extras: shelf placed items, lectern book, bookshelf power and signal, better carriers, waterlogging (follow-up of PAR-DECO-05)"
area: DECO
status: open
wave: 47
deps: [PAR-DECO-05]
size: M
aliases: ["split from PAR-DECO-05: the tool rack, shelf, bookshelf and lectern blocks, the tool rack and bookshelf clicks, the placement and the support rule exist; what needs the placed item framework, a vanilla lectern book, or other items' files is here"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/ShelfBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/PlacedItemBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCLecternBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/BookshelfBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rack/**"
  - "core/src/test/java/dev/tfp/core/rack/**"
  - "paper/src/main/java/dev/tfp/paper/rack/**"
  - "paper/src/test/java/dev/tfp/paper/rack/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/rack/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/rack/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:wood/shelf/oak; place on a wall; right-click the lower half with an item (it shows on the shelf), the top with an item (a placed item above); screenshot"
  - "ce item give Bot tfp:wood/lectern/oak; place; right-click with a written book; turn pages; screenshot"
plan:
  - "Shelf: TFC's ShelfBlock is a PlacedItemBlock (four quadrant items, a placed item above it when the top is clicked); give the shelf block the placed item device kind logic of paper/placed (PlacedItems, PlacedItemListener hit quadrants) with the shelf's facing and its own 1 px / 3 px top outline."
  - "Lectern: vanilla LecternBlock state (has_book, powered, page) and block entity: a written or writable book is put on and taken off, pages turn from the screen (vanilla LecternMenu), the redstone signal follows the page; the book is drawn as one more item display of the vanilla lectern book model."
  - "Bookshelf: TFC's getEnchantPower (half a point per book) for the enchanting table (the bookshelf is a barrier carrier, so vanilla's power tag does not see it: count it in the enchant listener), the comparator signal of vanilla's chiseled bookshelf (last interacted slot), books dropped on break are done."
  - "Carriers: the tool rack's board is 2 px (the open trapdoor's is 3 px) and the shelf's plate 1 px (a closed top trapdoor's is 3 px); pick free vanilla states with the exact shapes through the carrier selector (TfcShapes already has the tool_rack and shelf rows) and a lectern collision as TFC's noCollission asks."
  - "Waterlogging: tool rack and shelf are IFluidLoggable / PlacedItemBlock waterloggable in TFC; add a waterlogged property with fluid_state water (DeviceBlockSpec variants carry no fluid)."
  - "Displays after a chunk unload or a restart: the tool rack items are persistent item displays; add a chunk-load check that recreates a missing display from the record."
  - "Sounds: TFC's tfc:block.tool_rack.place is not in the pack yet (the rack uses block.wood.place)."
tests:
  - "paper: shelf put and take, lectern book and pages, enchant power, display recreate; datagen: waterlogged variants, exact carriers"
done_criteria:
  - "Items stand on a shelf, a book lies on a lectern and turns pages, a bookshelf powers an enchanting table, the tool rack and shelf keep their exact outline"
---

# PAR-DECO-29: Rack extras (follow-up of PAR-DECO-05)

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`. PAR-DECO-05 shipped the blocks (`dev.tfp.datagen.rack.RackBlocks`, `configuration/rack/<wood>.yml`), the device kinds `tfp:wood/tool_rack` and `tfp:wood/bookshelf`, the clicks (`RackInteractions`), the placement (`RackPlacement`) and the support rule (`RackSupport`) in `paper/rack` and `core/rack`.
