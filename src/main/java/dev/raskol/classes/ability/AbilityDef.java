package dev.raskol.classes.ability;

import dev.raskol.classes.model.RClass;
import dev.raskol.classes.resource.ClassResource;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable-спека активной способности класса.
 * Создаётся только через {@link Builder} и регистрируется в {@link AbilityRegistry}.
 */
public final class AbilityDef {

    /** Контекст применения способности. */
    public record Context(
            @NotNull Player caster,
            int classLevel,
            long currentResource
    ) {}

    /** Исполнитель способности. Возвращает true, если каст удался. */
    @FunctionalInterface
    public interface Executor {
        boolean cast(@NotNull AbilityDef def, @NotNull Context ctx);
    }

    private final String id;
    private final RClass rClass;
    private final Component displayName;
    private final Component description;
    private final int requiredLevel;
    private final ClassResource resource;
    private final int resourceCost;
    private final Duration cooldown;
    private final boolean usableInLobby;
    @Nullable private final Executor executor;

    private AbilityDef(@NotNull Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.rClass = Objects.requireNonNull(b.rClass, "rClass");
        this.displayName = Objects.requireNonNull(b.displayName, "displayName");
        this.description = b.description == null ? Component.empty() : b.description;
        this.requiredLevel = b.requiredLevel;
        this.resource = Objects.requireNonNull(b.resource, "resource");
        this.resourceCost = Math.max(0, b.resourceCost);
        this.cooldown = b.cooldown == null ? Duration.ZERO : b.cooldown;
        this.usableInLobby = b.usableInLobby;
        this.executor = b.executor;
        if (this.requiredLevel < 1) {
            throw new IllegalArgumentException("requiredLevel must be >= 1: " + this.id);
        }
    }

    public @NotNull String id() { return id; }
    public @NotNull RClass rClass() { return rClass; }
    public @NotNull Component displayName() { return displayName; }
    public @NotNull Component description() { return description; }
    public int requiredLevel() { return requiredLevel; }
    public @NotNull ClassResource resource() { return resource; }
    public int resourceCost() { return resourceCost; }
    public @NotNull Duration cooldown() { return cooldown; }
    public boolean usableInLobby() { return usableInLobby; }
    public boolean hasExecutor() { return executor != null; }

    /**
     * Пытается применить способность. Возвращает false, если исполнителя нет.
     */
    public boolean cast(@NotNull Context ctx) {
        return executor != null && executor.cast(this, ctx);
    }

    /** Разблокирована ли способность на данном уровне класса. */
    public boolean unlockedAt(int level) {
        return level >= requiredLevel;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private RClass rClass;
        private Component displayName;
        private Component description;
        private int requiredLevel = 1;
        private ClassResource resource;
        private int resourceCost;
        private Duration cooldown;
        private boolean usableInLobby = false;
        private Executor executor;

        private Builder() {}

        public @NotNull Builder id(@NotNull String id) { this.id = id; return this; }
        public @NotNull Builder rClass(@NotNull RClass rClass) { this.rClass = rClass; return this; }
        public @NotNull Builder displayName(@NotNull Component displayName) { this.displayName = displayName; return this; }
        public @NotNull Builder description(@NotNull Component description) { this.description = description; return this; }
        public @NotNull Builder requiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; return this; }
        public @NotNull Builder resource(@NotNull ClassResource resource) { this.resource = resource; return this; }
        public @NotNull Builder resourceCost(int resourceCost) { this.resourceCost = resourceCost; return this; }
        public @NotNull Builder cooldown(@NotNull Duration cooldown) { this.cooldown = cooldown; return this; }
        public @NotNull Builder usableInLobby(boolean usableInLobby) { this.usableInLobby = usableInLobby; return this; }
        public @NotNull Builder executor(@Nullable Executor executor) { this.executor = executor; return this; }

        public @NotNull AbilityDef build() {
            return new AbilityDef(this);
        }
    }
}
