package com.example.hideenemyhealth.systems;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.EntityUIType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Hides floating HP bars & damage numbers by manipulating entity UIComponentList on the server.
 *
 * Why this works:
 *  - Client renders overhead UI based on UIComponentList componentIds (EntityUI components).
 *  - We remove (or add back) IDs corresponding to:
 *      * EntityStat(Health)  -> HP bar
 *      * CombatText          -> damage/heal numbers
 *
 * No client mod required.
 */
public final class HideEntityUiSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final AtomicReference<HideEnemyHealthConfig> CONFIG_REF =
            new AtomicReference<>(new HideEnemyHealthConfig());

    // Reflection for UIComponentList.componentIds
    private static final Field COMPONENT_IDS_FIELD;

    // Cached global IDs for CombatText and Health EntityStat components
    private static volatile Set<Integer> COMBAT_TEXT_IDS = Set.of();
    private static volatile Set<Integer> HEALTH_STAT_IDS = Set.of();
    private static volatile boolean CACHE_READY = false;

    // Baseline UI componentIds per entity (supports live toggling ON/OFF without reconstructing global IDs)
    private static final ConcurrentHashMap<Long, int[]> BASELINE_COMPONENT_IDS = new ConcurrentHashMap<>();

    private static long entityKey(@Nonnull final Ref<EntityStore> ref) {
        // Prefer stable numeric entity id if available; fall back to identity hash.
        try {
            final String[] candidates = new String[]{"getId", "getEntityId", "getIndex", "getNetworkId"};
            for (String mName : candidates) {
                try {
                    final Method m = ref.getClass().getMethod(mName);
                    final Object v = m.invoke(ref);
                    if (v instanceof Number) {
                        return ((Number) v).longValue();
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
        } catch (Throwable ignored) {
        }
        return System.identityHashCode(ref);
    }


    private final Query<EntityStore> query;

    static {
        Field f = null;
        try {
            f = UIComponentList.class.getDeclaredField("componentIds");
            f.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Failed to access UIComponentList.componentIds via reflection. Plugin will be limited.");
        }
        COMPONENT_IDS_FIELD = f;
    }

    public HideEntityUiSystem() {
        this.query = Query.and(UIComponentList.getComponentType());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    /** Hot-swap config for all system instances. */
    public static void setConfig(@Nonnull final HideEnemyHealthConfig cfg) {
        cfg.normalize();
        CONFIG_REF.set(cfg);
    }

    @Nonnull
    public static HideEnemyHealthConfig getConfig() {
        return CONFIG_REF.get();
    }

    /** Force-update already loaded entities (players + NPCs if API provides refs). */
    public static void refreshLoadedEntities() {
        final HideEnemyHealthConfig cfg = getConfig();
        try {
            final Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds == null) return;

            for (World w : worlds.values()) {
                if (w == null) continue;
                runOnWorldThread(w, () -> refreshWorld(w, cfg));
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] refreshLoadedEntities failed");
        }
    }

    @Override
    public void onEntityAdded(@Nonnull final Ref<EntityStore> entityRef,
                              @Nonnull final AddReason addReason,
                              @Nonnull final Store<EntityStore> store,
                              @Nonnull final CommandBuffer<EntityStore> buffer) {
        applyForRef(entityRef, store, buffer, null);
    }

    @Override
    public void onEntityRemove(@Nonnull final Ref<EntityStore> ref,
                               @Nonnull final RemoveReason reason,
                               @Nonnull final Store<EntityStore> store,
                               @Nonnull final CommandBuffer<EntityStore> buffer) {
        // Prevent memory leaks in baseline cache.
        BASELINE_COMPONENT_IDS.remove(entityKey(ref));
    }


    private static void refreshWorld(@Nonnull final World world, @Nonnull final HideEnemyHealthConfig cfg) {
        // Players
        try {
            for (PlayerRef pref : world.getPlayerRefs()) {
                if (pref == null) continue;
                Store<EntityStore> store = pref.getReference().getStore();
                if (store == null) continue;
                applyForRef(pref.getReference(), store, null, Boolean.FALSE);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Failed to refresh players in world: %s", safeWorldName(world));
        }
        // NPCs (optional / API can change). Done via reflection to avoid hard dependency.
        try {
            final Method m = world.getClass().getMethod("getNpcRefs");
            final Object npcRefsObj = m.invoke(world);

            if (npcRefsObj instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o == null) continue;

                    Ref<EntityStore> er = null;

                    // Case 1: API returns Ref<EntityStore> directly
                    if (o instanceof Ref<?> refObj) {
                        @SuppressWarnings("unchecked")
                        final Ref<EntityStore> cast = (Ref<EntityStore>) refObj;
                        er = cast;
                    } else {
                        // Case 2: API returns wrapper (e.g. NpcRef) that exposes getReference()
                        try {
                            final Method getReference = o.getClass().getMethod("getReference");
                            final Object refObj = getReference.invoke(o);
                            if (refObj instanceof Ref<?> r2) {
                                @SuppressWarnings("unchecked")
                                final Ref<EntityStore> cast2 = (Ref<EntityStore>) r2;
                                er = cast2;
                            }
                        } catch (Throwable ignored) {
                            // wrapper not supported
                        }
                    }

                    if (er == null) continue;

                    final Store<EntityStore> store = er.getStore();
                    if (store == null) continue;

                    applyForRef(er, store, null, Boolean.TRUE);
                }
            }
        } catch (NoSuchMethodException ignored) {
            // OK: build doesn't expose NPC refs.
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t).log("[HideEnemyHealth] NPC refresh skipped due to API differences");
        }
    }

    private static void runOnWorldThread(@Nonnull final World world, @Nonnull final Runnable task) {
        try {
            Method m = world.getClass().getMethod("execute", Runnable.class);
            m.invoke(world, task);
        } catch (Throwable ignored) {
            // Fallback: run immediately
            task.run();
        }
    }

    /**
     * Apply current config to entity.
     *
     * @param buffer If non-null, uses CommandBuffer; else uses Store directly.
     * @param forceNpc If non-null, forces target selection: TRUE=npc, FALSE=player.
     */
    private static void applyForRef(@Nonnull final Ref<EntityStore> entityRef,
                                    @Nonnull final Store<EntityStore> store,
                                    @Nullable final CommandBuffer<EntityStore> buffer,
                                    @Nullable final Boolean forceNpc) {

        final HideEnemyHealthConfig cfg = getConfig();

        // We need UIComponentList either way (to restore baseline when disabled).
        final UIComponentList list = store.getComponent(entityRef, UIComponentList.getComponentType());
        if (list == null) return;

        final int[] currentIds = getComponentIds(list);
        if (currentIds == null) return;

        final long key = entityKey(entityRef);

        // If plugin disabled: restore baseline (if we ever touched this entity) and exit.
        if (!cfg.enabled) {
            final int[] baseline = BASELINE_COMPONENT_IDS.get(key);
            if (baseline != null && !Arrays.equals(currentIds, baseline)) {
                setComponentIds(list, baseline.clone());
                if (buffer != null) {
                    buffer.putComponent(entityRef, UIComponentList.getComponentType(), list);
                } else {
                    store.putComponent(entityRef, UIComponentList.getComponentType(), list);
                }
            }
            return;
        }

        if (!ensureCache()) return;

        final boolean isPlayer;
        final boolean isNpc;

        try {
            if (forceNpc != null) {
                isNpc = forceNpc;
                isPlayer = !forceNpc;
            } else {
                isPlayer = store.getComponent(entityRef, Player.getComponentType()) != null;
                isNpc = store.getComponent(entityRef, NPCEntity.getComponentType()) != null;
            }
        } catch (Throwable t) {
            // API moved - fail-safe
            return;
        }

        final HideEnemyHealthConfig.TargetSettings settings =
                isPlayer ? cfg.getPlayers() :
                        (isNpc ? cfg.getNpcs() : null);

        if (settings == null) return;

        // Capture entity baseline once so we can safely toggle settings ON/OFF later.
        final int[] baseline = BASELINE_COMPONENT_IDS.computeIfAbsent(key, k -> currentIds.clone());

        final int[] desired = computeDesiredIds(baseline, settings);
        if (Arrays.equals(currentIds, desired)) return;

        setComponentIds(list, desired);

        // Put back to trigger replication.
        if (buffer != null) {
            buffer.putComponent(entityRef, UIComponentList.getComponentType(), list);
        } else {
            store.putComponent(entityRef, UIComponentList.getComponentType(), list);
        }
    }

    private static int[] computeDesiredIds(@Nonnull final int[] baselineIds,
                                          @Nonnull final HideEnemyHealthConfig.TargetSettings settings) {

        final LinkedHashSet<Integer> out = new LinkedHashSet<>(baselineIds.length + 8);

        for (int id : baselineIds) {
            if (settings.hideDamageNumbers && COMBAT_TEXT_IDS.contains(id)) continue;
            if (settings.hideHealthBar && HEALTH_STAT_IDS.contains(id)) continue;
            out.add(id);
        }

        final int[] arr = new int[out.size()];
        int i = 0;
        for (Integer id : out) arr[i++] = id;
        return arr;
    }

    @Nullable
    private static int[] getComponentIds(@Nonnull final UIComponentList list) {
        try {
            if (COMPONENT_IDS_FIELD == null) return null;
            return (int[]) COMPONENT_IDS_FIELD.get(list);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setComponentIds(@Nonnull final UIComponentList list, @Nonnull final int[] ids) {
        try {
            if (COMPONENT_IDS_FIELD == null) return;
            COMPONENT_IDS_FIELD.set(list, ids);
        } catch (Throwable ignored) {
        }
    }

    private static boolean ensureCache() {
        if (CACHE_READY) return true;

        try {
            DefaultEntityStatTypes.update();
            final int healthIndex = DefaultEntityStatTypes.getHealth();

            final IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap = EntityUIComponent.getAssetMap();
            if (assetMap == null) return false;

            final int nextIndex = assetMap.getNextIndex();
            final HashSet<Integer> combat = new HashSet<>();
            final HashSet<Integer> health = new HashSet<>();

            for (int i = 0; i < nextIndex; i++) {
                // getAsset(i) возвращает JsonAssetWithMap -> кастим в EntityUIComponent
                final Object raw = assetMap.getAsset(i);
                if (!(raw instanceof EntityUIComponent uiAsset)) continue;

                final com.hypixel.hytale.protocol.EntityUIComponent packet = uiAsset.toPacket();
                if (packet == null) continue;

                final EntityUIType type = packet.type; // актуально для твоей версии
                if (type == null) continue;

                if (type == EntityUIType.CombatText) {
                    combat.add(i);
                } else if (type == EntityUIType.EntityStat && packet.entityStatIndex == healthIndex) {
                    health.add(i);
                }
            }

            COMBAT_TEXT_IDS = Collections.unmodifiableSet(combat);
            HEALTH_STAT_IDS = Collections.unmodifiableSet(health);
            CACHE_READY = true;

            LOGGER.at(Level.INFO).log(
                    "[HideEnemyHealth] UI component cache ready (health=%d, combat=%d)",
                    health.size(), combat.size()
            );
            return true;

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[HideEnemyHealth] Failed to build UI component cache");
            return false;
        }
    }

    private static String safeWorldName(@Nonnull final World world) {
        try {
            return world.getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
