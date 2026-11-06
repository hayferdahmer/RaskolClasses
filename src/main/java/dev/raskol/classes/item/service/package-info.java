// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
/**
 * 1.15.1 (Итерация A): бизнес-логика предметного слоя — иммутабельный реестр
 * определений, агрегация статов (base + roll + сеты), рантайм-фасад ItemSystem
 * (reload с сохранением прежнего реестра при структурной ошибке, start/stop,
 * снапшоты экипировки), сет-бонусы с порогами 2..6.
 */
package dev.raskol.classes.item.service;
