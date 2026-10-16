/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.momirealms.craftengine.libraries.nbt.ByteArrayTag;
import net.momirealms.craftengine.libraries.nbt.ByteTag;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.DoubleTag;
import net.momirealms.craftengine.libraries.nbt.FloatTag;
import net.momirealms.craftengine.libraries.nbt.IntTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.LongTag;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import net.momirealms.craftengine.libraries.nbt.Tag;

/**
 * {@link DataMap} ↔ CraftEngine NBT ({@link CompoundTag}), so our {@code VersionedCodec}s store block-entity
 * data as readable NBT in CraftEngine's chunk records. Type mapping: int → Int, long → Long, float → Float,
 * double → Double, boolean → Byte (read back as boolean), String → String, byte[] → ByteArray, map →
 * Compound, list of maps → List of Compounds. Any other tag type fails with {@link PersistException}
 * (we never write one). Pure; any thread.
 */
public final class NbtDataMaps {
    private NbtDataMaps() {
    }

    public static CompoundTag toNbt(DataMap map) {
        CompoundTag tag = new CompoundTag();
        for (String key : map.keys()) {
            tag.put(key, toTag(key, map.raw(key)));
        }
        return tag;
    }

    private static Tag toTag(String key, Object v) {
        return switch (v) {
            case Integer i -> IntTag.valueOf(i);
            case Long l -> LongTag.valueOf(l);
            case Float f -> new FloatTag(f);
            case Double d -> new DoubleTag(d);
            case Boolean b -> ByteTag.valueOf(b);
            case String s -> new StringTag(s);
            case byte[] b -> new ByteArrayTag(b);
            case DataMap m -> toNbt(m);
            case List<?> l -> {
                ListTag list = new ListTag();
                for (Object o : l) {
                    list.add(list.size(), toNbt((DataMap) o));
                }
                yield list;
            }
            default -> throw new PersistException(key + ": unsupported value type " + v.getClass().getName());
        };
    }

    public static DataMap fromNbt(CompoundTag tag) {
        DataMap map = new DataMap();
        for (Map.Entry<String, Tag> e : tag.entrySet()) {
            String key = e.getKey();
            switch (e.getValue()) {
                case IntTag t -> map.putInt(key, t.getAsInt());
                case LongTag t -> map.putLong(key, t.getAsLong());
                case FloatTag t -> map.putFloat(key, t.getAsFloat());
                case DoubleTag t -> map.putDouble(key, t.getAsDouble());
                case ByteTag t -> map.putBoolean(key, t.getAsByte() != 0);
                case StringTag t -> map.putString(key, t.getAsString());
                case ByteArrayTag t -> map.putBytes(key, t.getAsByteArray());
                case CompoundTag t -> map.putMap(key, fromNbt(t));
                case ListTag t -> {
                    List<DataMap> list = new ArrayList<>(t.size());
                    for (int i = 0; i < t.size(); i++) {
                        if (!(t.get(i) instanceof CompoundTag c)) {
                            throw new PersistException(key + "[" + i + "]: expected a compound, found "
                                    + t.get(i).getClass().getSimpleName());
                        }
                        list.add(fromNbt(c));
                    }
                    map.putList(key, list);
                }
                default -> throw new PersistException(key + ": unsupported NBT tag " + e.getValue().getClass().getSimpleName());
            }
        }
        return map;
    }
}
