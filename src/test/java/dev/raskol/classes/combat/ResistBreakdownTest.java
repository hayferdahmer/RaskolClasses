// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResistBreakdownTest {
    @Test
    void activeModifiersAreCopiedOnInputAndOutput() {
        List<ResistService.Modifier> source = new ArrayList<>();
        source.add(new ResistService.Modifier("test", 5.0, 2.0, Long.MAX_VALUE));
        ResistService.Breakdown breakdown = new ResistService.Breakdown(10.0, 20.0, source, 15.0, 22.0);

        source.clear();
        assertEquals(1, breakdown.active().size());
        assertThrows(UnsupportedOperationException.class, () -> breakdown.active().clear());
    }
}
