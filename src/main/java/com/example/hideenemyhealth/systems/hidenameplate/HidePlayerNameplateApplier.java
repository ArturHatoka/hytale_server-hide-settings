package com.example.hideenemyhealth.systems.hidenameplate;

import com.example.hideenemyhealth.config.HideEnemyHealthConfig;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies the "Players: Hide nameplates" setting by removing/restoring {@link Nameplate} on player entities.
 */
public final class HidePlayerNameplateApplier {

    private HidePlayerNameplateApplier() {
    }

    /**
     * Apply current runtime config to a ref.
     *
     * @param forceRestore if true, restore baseline even if config says to hide (used on shutdown)
     * @return true if a component write/remove was attempted
     */
    public static boolean applyForRefAndReport(@Nonnull final Ref<EntityStore> ref,
                                               @Nonnull final Store<EntityStore> store,
                                               @Nullable final CommandBuffer<EntityStore> buffer,
                                               final boolean forceRestore) {

        if (!ref.isValid()) return false;

        // Only players.
        try {
            if (store.getComponent(ref, Player.getComponentType()) == null) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }

        final HideEnemyHealthConfig cfg = HideNameplateConfigRegistry.getConfig();
        final boolean wantHide = !forceRestore && cfg.enabled && cfg.getPlayers() != null && cfg.getPlayers().hideNameplate;

        final Object type = Nameplate.getComponentType();
        final long key = NameplateBaselineCache.entityKey(ref);
        final NameplateBaselineCache.Entry entry = NameplateBaselineCache.getOrCreate(key);

        final Nameplate current;
        try {
            current = store.getComponent(ref, Nameplate.getComponentType());
        } catch (Throwable ignored) {
            return false;
        }

        // Keep baseline updated while visible (helps if the server or other plugins rewrite nameplates).
        if (!entry.hidden && current != null) {
            entry.baseline = NameplateCopySupport.copy(current);
        }

        if (wantHide) {
            // Capture baseline before removing.
            if (current != null) {
                entry.baseline = NameplateCopySupport.copy(current);
            }

            // Already hidden.
            if (entry.hidden && current == null) {
                return false;
            }

            // Prefer an actual remove so the client doesn't render anything.
            boolean removed = false;
            if (buffer != null) {
                removed = ComponentRemoveSupport.tryRemoveFromBuffer(buffer, ref, type);
            } else {
                removed = ComponentRemoveSupport.tryRemoveFromStore(store, ref, type);
            }

            // Fallback: some builds may not expose removeComponent for direct store writes.
            if (!removed) {
                try {
                    final Nameplate empty = new Nameplate("");
                    if (buffer != null) {
                        buffer.putComponent(ref, Nameplate.getComponentType(), empty);
                    } else {
                        store.putComponent(ref, Nameplate.getComponentType(), empty);
                    }
                } catch (Throwable ignored) {
                    // If even fallback fails, we still mark hidden to avoid thrashing.
                }
            }

            entry.hidden = true;
            return true;
        }

        // Not hiding: restore if we previously hid.
        if (entry.hidden) {
            final Nameplate baseline = entry.baseline;
            if (baseline != null) {
                final Nameplate restored = NameplateCopySupport.copy(baseline);
                try {
                    if (buffer != null) {
                        buffer.putComponent(ref, Nameplate.getComponentType(), restored);
                    } else {
                        store.putComponent(ref, Nameplate.getComponentType(), restored);
                    }
                    entry.hidden = false;
                    return true;
                } catch (Throwable ignored) {
                    // Leave hidden flag as-is if we couldn't restore.
                    return false;
                }
            }

            // No baseline available: if the component is already present, we can consider it visible.
            if (current != null) {
                entry.hidden = false;
            }
        }

        return false;
    }
}
