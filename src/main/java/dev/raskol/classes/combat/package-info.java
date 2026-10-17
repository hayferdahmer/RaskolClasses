// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
/**
 * Боевое ядро: путь A (ванильные события, VanillaDamageListener) и путь B
 * (способности, CombatService.dealDamage); капы и burst-окно (DamageCaps),
 * школы урона и митигация (combat.school), DoT (combat.dot), проки (ProcService),
 * таргетинг и фракционные гейты (Targeting).
 *
 * Границы: формулы урона меняются ТОЛЬКО под unit-тестами (CombatMathTest,
 * DotMathTest, selftest-чеки 16/52/62/66–68); legacy-мосты
 * SchoolProfile.fromLegacy/toLegacy живут до 1.15.0 (план 3-04);
 *_hot-путь_ не читает конфиг с 1.14.12 (снапшот настроек, план 5-04).
 */
package dev.raskol.classes.combat;
