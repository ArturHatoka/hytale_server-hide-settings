package com.example.hideenemyhealth.systems.hideentityui;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;

public final class HideEntityUiWorldRefresher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HideEntityUiWorldRefresher() {
    }

    public static void refreshLoadedEntities() {
        final HideEnemyHealthConfig cfg = HideEntityUiConfigRegistry.getConfig();
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

    private static void refreshWorld(@Nonnull final World world, @Nonnull final HideEnemyHealthConfig cfg) {
        try {
            for (PlayerRef pref : world.getPlayerRefs()) {
                if (pref == null) continue;
                Store<EntityStore> store = pref.getReference().getStore();
                if (store == null) continue;
                HideEntityUiApplier.applyForRef(pref.getReference(), store, null, Boolean.FALSE);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[HideEnemyHealth] Failed to refresh players in world: %s", safeWorldName(world));
        }
        try {
            final Method m = world.getClass().getMethod("getNpcRefs");
            final Object npcRefsObj = m.invoke(world);

            if (npcRefsObj instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o == null) continue;

                    Ref<EntityStore> er = null;

                    if (o instanceof Ref<?> refObj) {
                        @SuppressWarnings("unchecked")
                        final Ref<EntityStore> cast = (Ref<EntityStore>) refObj;
                        er = cast;
                    } else {
                        try {
                            final Method getReference = o.getClass().getMethod("getReference");
                            final Object refObj = getReference.invoke(o);
                            if (refObj instanceof Ref<?> r2) {
                                @SuppressWarnings("unchecked")
                                final Ref<EntityStore> cast2 = (Ref<EntityStore>) r2;
                                er = cast2;
                            }
                        } catch (Throwable ignored) {
                        }
                    }

                    if (er == null) continue;

                    final Store<EntityStore> store = er.getStore();
                    if (store == null) continue;

                    HideEntityUiApplier.applyForRef(er, store, null, Boolean.TRUE);
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t).log("[HideEnemyHealth] NPC refresh skipped due to API differences");
        }
    }

    private static void runOnWorldThread(@Nonnull final World world, @Nonnull final Runnable task) {
        try {
            Method m = world.getClass().getMethod("execute", Runnable.class);
            m.invoke(world, task);
        } catch (Throwable ignored) {
            task.run();
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
