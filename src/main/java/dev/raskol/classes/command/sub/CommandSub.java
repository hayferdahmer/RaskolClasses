// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 1.11.4 (P4d): контракт подкоманды /rc. Одна подкоманда = один файл.
 * execute получает хвост аргументов БЕЗ имени подкоманды.
 * complete получает ПОЛНЫЙ массив аргументов (args[0] = имя подкоманды).
 * permission() = null означает «доступно всем».
 */
public interface CommandSub {

    String name();

    /** Требуемая нода или null. */
    String permission();

    boolean execute(CommandSender sender, String[] tailArgs);

    default List<String> complete(CommandSender sender, String[] fullArgs) {
        return List.of();
    }
}
