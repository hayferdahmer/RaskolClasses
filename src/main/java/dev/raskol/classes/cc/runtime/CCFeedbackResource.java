// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

/** Ресурс визуальной обратной связи, очистка которого принадлежит CC feedback runtime. */
@FunctionalInterface
public interface CCFeedbackResource {
    void remove();
}
