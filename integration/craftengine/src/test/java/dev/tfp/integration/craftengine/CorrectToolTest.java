/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.setting.BlockSettings;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.setting.ItemSettings;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.LazyReference;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-22: the adapter's harvest check is CraftEngine's own ({@code BlockStateUtils.isCorrectTool}) over the block's
 * settings, so a block delegated to the Paper loot runtime is gated like a block with a CE {@code loot:}. TFC: a
 * {@code requiresCorrectToolForDrops} block (raw rock, Rock.java:130) drops nothing by hand and its table with a pickaxe.
 */
class CorrectToolTest {
    private static final Key BRONZE_PICKAXE = Key.of("tfp", "metal/pickaxe/bronze");
    private static final Key STICK = Key.of("minecraft", "stick");

    @Test
    void anEmptyHandIsRefusedOnABlockThatRequiresATool() {
        ImmutableBlockState rock = state(rockSettings());
        assertThat(CraftEngineIntegration.isCorrectTool(rock, null)).isFalse();
        assertThat(CraftEngineIntegration.isCorrectTool(rock, item(BRONZE_PICKAXE, true, -1))).isFalse();
    }

    @Test
    void aCorrectPickaxeIsAccepted() {
        assertThat(CraftEngineIntegration.isCorrectTool(state(rockSettings()), item(BRONZE_PICKAXE, false, -1))).isTrue();
    }

    @Test
    void aToolOutsideCorrectToolsIsRefused() {
        assertThat(CraftEngineIntegration.isCorrectTool(state(rockSettings()), item(STICK, false, -1))).isFalse();
    }

    @Test
    void aBlockWithoutRequirementAcceptsAnyToolAndTheEmptyHand() {
        ImmutableBlockState dirt = state(BlockSettings.of());
        assertThat(CraftEngineIntegration.isCorrectTool(dirt, null)).isTrue();
        assertThat(CraftEngineIntegration.isCorrectTool(dirt, item(STICK, false, -1))).isTrue();
    }

    /**
     * A non-empty {@code correct_tools} is a requirement by itself ({@code BlockSettings.requireCorrectTool()}), so the
     * rock template's {@code correct_tools: ['#minecraft:pickaxes']} needs no {@code require_correct_tools}.
     */
    @Test
    void correctToolsAloneRequiresTheTool() {
        BlockSettings settings = BlockSettings.of().correctTools(LazyReference.oneTime(() -> Set.of(BRONZE_PICKAXE)));
        assertThat(CraftEngineIntegration.isCorrectTool(state(settings), null)).isFalse();
        assertThat(CraftEngineIntegration.isCorrectTool(state(settings), item(STICK, false, -1))).isFalse();
        assertThat(CraftEngineIntegration.isCorrectTool(state(settings), item(BRONZE_PICKAXE, false, -1))).isTrue();
    }

    @Test
    void requiredBreakPowerComparesTheItemsBreakPower() {
        BlockSettings settings = rockSettings().requiredBreakPower(3);
        assertThat(CraftEngineIntegration.isCorrectTool(state(settings), item(BRONZE_PICKAXE, false, 3))).isTrue();
        assertThat(CraftEngineIntegration.isCorrectTool(state(settings), item(BRONZE_PICKAXE, false, 2))).isFalse();
    }

    /** A rock-like block: {@code require_correct_tools: true} and the pickaxe as its correct tool. */
    private static BlockSettings rockSettings() {
        return BlockSettings.of()
                .requireCorrectTool(true)
                .respectToolComponent(false)
                .correctTools(LazyReference.oneTime(() -> Set.of(BRONZE_PICKAXE)));
    }

    /** A bare state carrying {@code settings}: the check reads nothing else of it. */
    private static ImmutableBlockState state(BlockSettings settings) {
        try {
            Constructor<?> ctor = ImmutableBlockState.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            ImmutableBlockState state = (ImmutableBlockState) ctor.newInstance(null, null, new Reference2ObjectArrayMap<>());
            state.setSettings(settings);
            return state;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** A CE item view: {@code breakPower} -1 means no definition (the vanilla break power table, unused here). */
    private static Item item(Key id, boolean empty, int breakPower) {
        ItemDefinition definition = breakPower < 0 ? null
                : implement(ItemDefinition.class, DefinitionBase.class).init(itemSettings().breakPower(breakPower));
        return implement(Item.class, ItemBase.class).init(id, empty, definition);
    }

    /** {@code ItemSettings.of()} reads CraftEngine's loaded config; the check only reads {@code breakPower}. */
    private static ItemSettings itemSettings() {
        try {
            Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            return (ItemSettings) unsafe.getClass().getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, ItemSettings.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final Map<String, Class<?>> GENERATED = new ConcurrentHashMap<>();

    /**
     * {@link Item} and {@link ItemDefinition} reach CraftEngine's relocated adventure classes, which only its runtime
     * library loader provides, so neither a {@link java.lang.reflect.Proxy} nor a Java class implementing them can load
     * here. A class generated with only {@code extends base implements iface} (the base holds the few methods the check
     * calls) loads without resolving the other method signatures.
     */
    @SuppressWarnings("unchecked")
    private static <B> B implement(Class<?> iface, Class<B> base) {
        String name = CorrectToolTest.class.getPackageName().replace('.', '/') + "/Generated" + base.getSimpleName();
        ClassDesc superDesc = base.describeConstable().orElseThrow();
        Class<?> type = GENERATED.computeIfAbsent(name, n -> define(ClassFile.of().build(ClassDesc.ofInternalName(n),
                cb -> cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
                        .withSuperclass(superDesc)
                        .withInterfaceSymbols(iface.describeConstable().orElseThrow())
                        .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                                .aload(0)
                                .invokespecial(superDesc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                .return_()))));
        try {
            return (B) type.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<?> define(byte[] bytes) {
        try {
            return MethodHandles.lookup().defineClass(bytes);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** The {@link Item} methods {@code BlockStateUtils.isCorrectTool} and {@code ItemUtils.breakPower} call. */
    public abstract static class ItemBase {
        private Key id;
        private boolean empty;
        private ItemDefinition definition;

        protected ItemBase() {}

        final Item init(Key id, boolean empty, ItemDefinition definition) {
            this.id = id;
            this.empty = empty;
            this.definition = definition;
            return (Item) this;
        }

        public boolean isEmpty() {
            return empty;
        }

        public Key id() {
            return id;
        }

        public Key vanillaId() {
            return id;
        }

        public Optional<ItemDefinition> getDefinition() {
            return Optional.ofNullable(definition);
        }
    }

    /** The {@link ItemDefinition} method {@code ItemUtils.breakPower} calls. */
    public abstract static class DefinitionBase {
        private ItemSettings settings;

        protected DefinitionBase() {}

        final ItemDefinition init(ItemSettings settings) {
            this.settings = settings;
            return (ItemDefinition) this;
        }

        public ItemSettings settings() {
            return settings;
        }
    }
}
