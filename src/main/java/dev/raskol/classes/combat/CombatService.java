    /**
     * 1.10.0: анти-хил «Раскола Души» блокирует ванильные события лечения.
     * 1.11.2 (S5): блокирует ВСЕ причины RegainHealth, включая SATIATED
     *              (еда-реген, в т.ч. после золотых яблок) — это закрывает
     *              основной обход анти-хила через еду. Absorption-сердца
     *              снимаются в WarlockAbilities.soulRift при наложении
     *              (опциональный гейт antiheal-strip-absorption).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity le)) {
            return;
        }
        if (!WarlockAbilities.isAntihealed(le.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            String reason = event.getRegainReason() != null ? event.getRegainReason().name() : "UNKNOWN";
            Component msg = Component.text(
                    "[antiheal] " + le.getName() + " блокировано лечение ("
                            + reason + ", amount=" + event.getAmount() + ")",
                    NamedTextColor.DARK_PURPLE);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("raskolclasses.debug")) {
                    online.sendMessage(msg);
                }
            }
        }
    }
