// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
/**
 * Пет-система (1.14.6+): PetDef-реестр (wolf/demon/shadowfiend), pure-масштабирование
 * PetMath (hp = base + attr×2, dmg = base + power×0.3, × pet_pct-узлы),
 * жизненный цикл PetService: follow 12 блоков, ttl, баффы, consume, гейты canHit,
 * ретаргет на цель владельца, скан осиротевших петов.
 *
 * Контракты: 1 пет на владельца; китовые бонусы при призыве (+10 Скверны, +10 Света)
 * остаются в китах — PetService про них не знает; дезспавн на смерть и выход владельца.
 */
package dev.raskol.classes.pet;
