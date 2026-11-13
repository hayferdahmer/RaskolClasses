// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook.runtime;

import java.util.Objects;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.FlavorPlaceholder;
import dev.raskol.classes.hook.PlaceholderSnapshotService;
import dev.raskol.classes.hook.RaskolPlaceholder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/** Paper/PlaceholderAPI-адаптер двух публичных expansions RASKOL. */
public final class PaperPlaceholderRuntimeRegistration implements PlaceholderRuntimeRegistration {

    private final RaskolClasses plugin;
    private final PlaceholderSnapshotService snapshots;
    private PlaceholderExpansion raskolExpansion;
    private PlaceholderExpansion crownExpansion;
    private boolean raskolRegistrationAttempted;
    private boolean crownRegistrationAttempted;

    public PaperPlaceholderRuntimeRegistration(RaskolClasses plugin, PlaceholderSnapshotService snapshots) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public synchronized void register() {
        if (raskolRegistrationAttempted || crownRegistrationAttempted) {
            return;
        }

        raskolExpansion = new RaskolPlaceholder(plugin, snapshots);
        crownExpansion = new FlavorPlaceholder(plugin, snapshots);

        try {
            raskolRegistrationAttempted = true;
            if (!raskolExpansion.register()) {
                throw new IllegalStateException("PlaceholderAPI отклонил expansion raskolclasses");
            }

            crownRegistrationAttempted = true;
            if (!crownExpansion.register()) {
                throw new IllegalStateException("PlaceholderAPI отклонил expansion raskolcrown");
            }
        } catch (RuntimeException registrationFailure) {
            try {
                unregister();
            } catch (RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }
    }

    @Override
    public synchronized void unregister() {
        RuntimeException failure = null;

        if (crownRegistrationAttempted && crownExpansion != null) {
            try {
                crownExpansion.unregister();
                crownRegistrationAttempted = false;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }

        if (raskolRegistrationAttempted && raskolExpansion != null) {
            try {
                raskolExpansion.unregister();
                raskolRegistrationAttempted = false;
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            }
        }

        if (!crownRegistrationAttempted && !raskolRegistrationAttempted) {
            crownExpansion = null;
            raskolExpansion = null;
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            current = new IllegalStateException("Не удалось снять все PlaceholderAPI expansions");
        }
        current.addSuppressed(next);
        return current;
    }
}
