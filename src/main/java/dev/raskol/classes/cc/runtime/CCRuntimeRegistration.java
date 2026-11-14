// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.cc.runtime;

/** Граница регистрации listener CCService в Paper runtime. */
public interface CCRuntimeRegistration {
    void register();
    void unregister();
}
