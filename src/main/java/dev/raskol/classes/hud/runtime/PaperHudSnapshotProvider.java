// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hud.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.ClassProvider;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.effect.ActiveEffectManager;
import dev.raskol.classes.hud.snapshot.HudSnapshotProvider;
import dev.raskol.classes.hud.snapshot.HudValue;
import dev.raskol.classes.hud.snapshot.PlayerHudSnapshot;
import dev.raskol.classes.resource.ResourceService;
import dev.raskol.classes.resource.ResourceService.ResourceSnapshot;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRole;
import dev.raskol.classes.spec.service.Spec2Service;
import dev.raskol.classes.targeting.domain.TargetReadResult;
import dev.raskol.classes.targeting.domain.TargetUnavailableReason;

/**
 * Paper read adapter, который копирует актуальные игровые данные в immutable HUD snapshot.
 * Все обращения к игровым сервисам и Bukkit API выполняются на основном серверном потоке.
 */
public final class PaperHudSnapshotProvider implements HudSnapshotProvider {

    private final Server server;
    private final ClassProvider classProvider;
    private final AttributeService attributes;
    private final ResourceService resources;
    private final ActiveEffectManager effects;
    private final DotService dots;
    private final Spec2Service specializations;
    private final LongSupplier clock;

    public PaperHudSnapshotProvider(
            Server server,
            ClassProvider classProvider,
            AttributeService attributes,
            ResourceService resources,
            ActiveEffectManager effects,
            DotService dots,
            Spec2Service specializations,
            LongSupplier clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.classProvider = Objects.requireNonNull(classProvider, "classProvider");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.dots = Objects.requireNonNull(dots, "dots");
        this.specializations = Objects.requireNonNull(specializations, "specializations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlayerHudSnapshot capture(UUID playerId, long revision) {
        Objects.requireNonNull(playerId, "playerId");
        if (!server.isPrimaryThread()) {
            throw new IllegalStateException("HUD snapshot capture должен выполняться на Paper main thread");
        }
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Нельзя собрать HUD snapshot для offline player: " + playerId);
        }

        long capturedAtMillis = clock.getAsLong();
        if (capturedAtMillis < 0L) {
            throw new IllegalStateException("HUD snapshot clock вернул отрицательное время");
        }

        PlayerClass playerClass = classProvider.getClassOf(player);
        PlayerHudSnapshot.CharacterView character = readCharacter(playerId, playerClass);
        PlayerHudSnapshot.HealthView health = readHealth(player, playerClass);
        Optional<ResourceSnapshot> resourceSnapshot =
                playerClass == null ? Optional.empty() : resources.readSnapshot(playerId, playerClass);
        PlayerHudSnapshot.ResourceView resource = readResource(playerClass, resourceSnapshot);
        PlayerHudSnapshot.AttributeView attributeView = readAttributes(playerId, playerClass);
        List<PlayerHudSnapshot.EffectView> effectViews = readEffects(playerId);
        List<PlayerHudSnapshot.DotView> dotViews = readDots(playerId, capturedAtMillis);

        HudValue<Boolean> inCombat = resourceSnapshot
                .map(snapshot -> HudValue.available(snapshot.inCombat()))
                .orElseGet(HudValue::unavailable);

        return new PlayerHudSnapshot(
                playerId,
                revision,
                capturedAtMillis,
                character,
                health,
                resource,
                attributeView,
                TargetReadResult.unavailable(TargetUnavailableReason.POLICY_PROVIDER_UNAVAILABLE),
                effectViews,
                dotViews,
                new PlayerHudSnapshot.CombatView(inCombat, HudValue.unavailable()),
                new PlayerHudSnapshot.HudProfileView(
                        playerClass == null
                                ? HudValue.unavailable()
                                : HudValue.available(playerClass.name().toLowerCase(Locale.ROOT)),
                        Set.of()));
    }

    private PlayerHudSnapshot.CharacterView readCharacter(UUID playerId, PlayerClass playerClass) {
        if (playerClass == null) {
            return new PlayerHudSnapshot.CharacterView(
                    HudValue.unavailable(),
                    HudValue.unavailable(),
                    HudValue.unavailable(),
                    HudValue.unavailable(),
                    HudValue.unavailable(),
                    HudValue.unavailable(),
                    HudValue.unavailable());
        }

        String specializationId = specializations.mainSpec(playerId);
        HudValue<String> specialization;
        HudValue<String> role;
        if (specializationId == null || specializationId.isBlank()) {
            specialization = HudValue.notApplicable();
            role = HudValue.notApplicable();
        } else {
            Spec spec = Spec.fromId(specializationId);
            SpecRole specRole = specializations.roleOfOwner(playerId);
            if (spec == null || spec.playerClass() != playerClass || specRole == null) {
                specialization = HudValue.unavailable();
                role = HudValue.unavailable();
            } else {
                specialization = HudValue.available(spec.id());
                role = HudValue.available(specRole.id());
            }
        }

        // CharacterLevelService пока зависит от AuraSkills и не является целевым RPG Core read source.
        return new PlayerHudSnapshot.CharacterView(
                HudValue.available(playerClass.name().toLowerCase(Locale.ROOT)),
                HudValue.available(playerClass.getDisplayName()),
                specialization,
                role,
                HudValue.unavailable(),
                HudValue.unavailable(),
                HudValue.unavailable());
    }

    private PlayerHudSnapshot.HealthView readHealth(Player player, PlayerClass playerClass) {
        if (playerClass == null) {
            return unavailableHealth();
        }
        double current = attributes.currentFormulaHp(player);
        double maximum = attributes.maxHp(player.getUniqueId());
        if (!isFiniteNonNegative(current) || !Double.isFinite(maximum) || maximum <= 0.0) {
            return unavailableHealth();
        }
        return new PlayerHudSnapshot.HealthView(HudValue.available(current), HudValue.available(maximum));
    }

    private PlayerHudSnapshot.ResourceView readResource(
            PlayerClass playerClass, Optional<ResourceSnapshot> snapshot) {
        if (playerClass == null || snapshot.isEmpty()) {
            return unavailableResource();
        }
        return new PlayerHudSnapshot.ResourceView(
                HudValue.available(playerClass.name().toLowerCase(Locale.ROOT) + "_resource"),
                HudValue.available(playerClass.getResourceName()),
                HudValue.available(playerClass.getResourceSymbol()),
                HudValue.available(snapshot.orElseThrow().current()),
                HudValue.available(snapshot.orElseThrow().effectiveMax()));
    }

    private PlayerHudSnapshot.AttributeView readAttributes(UUID playerId, PlayerClass playerClass) {
        if (playerClass == null) {
            return unavailableAttributes();
        }
        return new PlayerHudSnapshot.AttributeView(
                finiteValue(attributes.value(playerId, AttributeType.STR)),
                finiteValue(attributes.value(playerId, AttributeType.AGI)),
                finiteValue(attributes.value(playerId, AttributeType.INT)));
    }

    private List<PlayerHudSnapshot.EffectView> readEffects(UUID playerId) {
        return effects.getActiveEffects(playerId).entrySet().stream()
                .filter(entry -> entry.getKey().displayName() != null)
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> {
                    long expiresAt = entry.getValue();
                    return new PlayerHudSnapshot.EffectView(
                            entry.getKey().name().toLowerCase(Locale.ROOT),
                            entry.getKey().displayName(),
                            Optional.empty(),
                            expiresAt,
                            expiresAt == Long.MAX_VALUE,
                            1);
                })
                .toList();
    }

    private List<PlayerHudSnapshot.DotView> readDots(UUID playerId, long capturedAtMillis) {
        return dots.activeDotsOf(playerId).stream()
                .filter(instance -> instance.expiresAt() > capturedAtMillis)
                .map(this::copyDot)
                .sorted(Comparator.comparing(PlayerHudSnapshot.DotView::id))
                .toList();
    }

    private PlayerHudSnapshot.DotView copyDot(DotInstance instance) {
        UUID ownerId = instance.ownerUuid();
        String stableId = instance.def().id() + ":" + (ownerId == null ? "unknown" : ownerId);
        return new PlayerHudSnapshot.DotView(
                stableId,
                instance.def().school().name().toLowerCase(Locale.ROOT),
                instance.stacks(),
                instance.expiresAt(),
                Optional.ofNullable(ownerId));
    }

    private static HudValue<Double> finiteValue(double value) {
        return isFiniteNonNegative(value) ? HudValue.available(value) : HudValue.unavailable();
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static PlayerHudSnapshot.HealthView unavailableHealth() {
        return new PlayerHudSnapshot.HealthView(HudValue.unavailable(), HudValue.unavailable());
    }

    private static PlayerHudSnapshot.ResourceView unavailableResource() {
        return new PlayerHudSnapshot.ResourceView(
                HudValue.unavailable(),
                HudValue.unavailable(),
                HudValue.unavailable(),
                HudValue.unavailable(),
                HudValue.unavailable());
    }

    private static PlayerHudSnapshot.AttributeView unavailableAttributes() {
        return new PlayerHudSnapshot.AttributeView(
                HudValue.unavailable(), HudValue.unavailable(), HudValue.unavailable());
    }
}
