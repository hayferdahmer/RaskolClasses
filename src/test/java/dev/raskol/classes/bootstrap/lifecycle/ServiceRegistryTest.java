// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

    @Test
    void resolvesRegisteredServiceOnlyAfterRegistryIsSealed() {
        ServiceRegistry registry = new ServiceRegistry();
        ExampleService service = new ExampleServiceImpl();

        registry.register(ExampleService.class, service);

        assertThrows(IllegalStateException.class, () -> registry.require(ExampleService.class));
        assertThrows(IllegalStateException.class, () -> registry.find(ExampleService.class));

        registry.seal();

        assertSame(service, registry.require(ExampleService.class));
        assertEquals(Optional.of(service), registry.find(ExampleService.class));
    }

    @Test
    void rejectsDuplicateRegistration() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(ExampleService.class, new ExampleServiceImpl());

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(ExampleService.class, new ExampleServiceImpl()));
    }

    @Test
    void rejectsRegistrationAfterSeal() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.seal();

        assertThrows(
                IllegalStateException.class,
                () -> registry.register(ExampleService.class, new ExampleServiceImpl()));
    }

    @Test
    void distinguishesMissingRequiredAndOptionalServices() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.seal();

        assertThrows(IllegalStateException.class, () -> registry.require(ExampleService.class));
        assertEquals(Optional.empty(), registry.find(ExampleService.class));
        assertEquals(0, registry.size());
        assertTrue(registry.isSealed());
    }

    @Test
    void sealIsIdempotent() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.seal();
        registry.seal();

        assertTrue(registry.isSealed());
        assertFalse(registry.find(ExampleService.class).isPresent());
    }

    private interface ExampleService {}

    private static final class ExampleServiceImpl implements ExampleService {}
}
