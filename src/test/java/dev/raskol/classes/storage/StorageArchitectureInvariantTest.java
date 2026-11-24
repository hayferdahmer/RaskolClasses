// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.raskol.classes.character.profile.CharacterProfileStorage;
import dev.raskol.classes.spec.storage.Spec2Storage;

/**
 * Архитектурные инварианты единого хранилища персонажа и безопасной записи.
 */
@DisplayName("Archivist: архитектурные инварианты хранилища")
class StorageArchitectureInvariantTest {

    @Test
    @DisplayName("Spec2Storage остаётся фасадом и не владеет отдельным persistence-контуром")
    void specializationStorageIsOnlyAProfileFacade() {
        Field[] fields = Spec2Storage.class.getDeclaredFields();

        assertEquals(1, fields.length, "Фасад не должен заводить собственное состояние хранения");
        assertEquals("profiles", fields[0].getName());
        assertEquals(CharacterProfileStorage.class, fields[0].getType());
        assertFalse(Arrays.stream(fields).anyMatch(field -> File.class.isAssignableFrom(field.getType())));
        assertFalse(Arrays.stream(fields).anyMatch(field -> Path.class.isAssignableFrom(field.getType())));
        assertFalse(Arrays.stream(fields).anyMatch(field -> AsyncStorageWriter.class.isAssignableFrom(field.getType())));
    }

    @Test
    @DisplayName("профиль персонажа — единственный активный файл прогрессии и специализации")
    void characterProfileOwnsUnifiedStorageFile() {
        assertEquals("characters.yml", CharacterProfileStorage.FILE_NAME);
    }

    @Test
    @DisplayName("SafeStorage явно различает новое, восстановленное, повреждённое и недоступное хранилище")
    void safeStorageExposesExplicitLoadStates() {
        Set<String> actual = Arrays.stream(SafeStorage.LoadStatus.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("NEW", "LOADED_MAIN", "RECOVERED_TMP", "RECOVERED_BACKUP", "CORRUPT", "IO_ERROR"),
                actual);
    }

    @Test
    @DisplayName("фоновый writer принимает только файл и неизменяемый строковый снимок")
    void asyncWriterDoesNotAcceptMutableBukkitConfiguration() throws Exception {
        Method submit = AsyncStorageWriter.class.getMethod("submit", File.class, String.class);

        assertEquals(File.class, submit.getParameterTypes()[0]);
        assertEquals(String.class, submit.getParameterTypes()[1]);
        assertEquals(java.util.concurrent.CompletableFuture.class, submit.getReturnType());

        boolean acceptsBukkitConfiguration = Arrays.stream(AsyncStorageWriter.class.getMethods())
                .filter(method -> method.getDeclaringClass() == AsyncStorageWriter.class)
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(YamlConfiguration.class::isAssignableFrom);
        assertFalse(acceptsBukkitConfiguration, "Bukkit YAML не должен передаваться в storage worker");
    }

    @Test
    @DisplayName("миграция имеет одну публичную точку входа и получает единое профильное хранилище")
    void migrationWritesThroughCharacterProfileStorage() throws Exception {
        Method migrate = dev.raskol.classes.spec.storage.Spec2StorageMigration.class.getMethod(
                "migrate", File.class, CharacterProfileStorage.class, Logger.class);

        assertTrue(java.lang.reflect.Modifier.isStatic(migrate.getModifiers()));
        assertEquals(void.class, migrate.getReturnType());
        assertArrayEquals(
                new Class<?>[] {File.class, CharacterProfileStorage.class, Logger.class},
                migrate.getParameterTypes());
    }
}
