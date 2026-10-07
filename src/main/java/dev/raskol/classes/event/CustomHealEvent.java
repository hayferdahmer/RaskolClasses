// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.14.1 (Волна 1): кастомное лечение (не через ванильные зелья/реген).
 * Позволяет отслеживать целителя для роли HEALER и отменять хил.
 * Вызывается из HpBarService.heal(target, amount, healer).
 */
public final class CustomHealEvent extends Event implements Cancellable {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    @Nullable private final Player healer;
    private final LivingEntity target;
    private double amount;
    private boolean cancelled;
    
    /**
     * @param healer Целитель (null если системный хил/реген)
     * @param target Цель лечения
     * @param amount Количество лечения (формульные единицы HP)
     */
    public CustomHealEvent(@Nullable Player healer, @NotNull LivingEntity target, double amount) {
        this.healer = healer;
        this.target = target;
        this.amount = amount;
        this.cancelled = false;
    }
    
    /** Целитель (null если системный хил без атрибуции). */
    @Nullable
    public Player getHealer() {
        return healer;
    }
    
    /** Цель лечения. */
    @NotNull
    public LivingEntity getTarget() {
        return target;
    }
    
    /** Количество лечения (формульные единицы HP). */
    public double getAmount() {
        return amount;
    }
    
    /** Установить количество лечения (для модификации в listener'ах). */
    public void setAmount(double amount) {
        this.amount = Math.max(0.0, amount);
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
