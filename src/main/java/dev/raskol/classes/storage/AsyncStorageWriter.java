// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.storage;

import java.io.File;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Последовательный исполнитель дисковых операций хранилищ плагина.
 *
 * YAML и другие изменяемые Bukkit-объекты должны быть преобразованы в
 * неизменяемый снимок до передачи сюда. Фоновый поток получает только файл
 * и готовую строку, поэтому не обращается к Bukkit API.
 */
public final class AsyncStorageWriter implements AutoCloseable {

    private final Object lifecycleLock = new Object();
    private final ThreadPoolExecutor executor;
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private final Logger logger;
    private final AtomicFileWriter fileWriter;
    private boolean accepting = true;

    public AsyncStorageWriter(Logger logger) {
        this(logger, SafeStorage::saveAtomic, 256);
    }

    AsyncStorageWriter(Logger logger, AtomicFileWriter fileWriter) {
        this(logger, fileWriter, 256);
    }

    AsyncStorageWriter(Logger logger, AtomicFileWriter fileWriter, int queueCapacity) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fileWriter = Objects.requireNonNull(fileWriter, "fileWriter");
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity должен быть положительным");
        }
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                task -> {
                    Thread thread = new Thread(task, "RaskolClasses-Storage");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Ставит неизменяемый снимок в FIFO-очередь фоновой записи. */
    public CompletableFuture<Boolean> submit(File file, String snapshot) {
        if (file == null || snapshot == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!accepting) {
                logger.warning("AsyncStorageWriter: запись отклонена — исполнитель закрывается");
                return CompletableFuture.completedFuture(false);
            }

            PendingWrite task = new PendingWrite(file, snapshot, result);
            pendingWrites.incrementAndGet();
            try {
                executor.execute(task);
            } catch (RuntimeException exception) {
                task.finish(false);
                logger.log(Level.SEVERE, "AsyncStorageWriter: не удалось поставить запись в очередь", exception);
            }
        }
        return result;
    }

    /** Количество выполняющихся и ожидающих операций. */
    public int pendingWrites() {
        return pendingWrites.get();
    }

    /**
     * Прекращает приём новых операций и ожидает завершения очереди.
     *
     * @return true, если все поставленные операции завершились в срок
     */
    public boolean closeAndAwait(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout не может быть отрицательным");
        }

        synchronized (lifecycleLock) {
            accepting = false;
            executor.shutdown();
        }

        try {
            if (executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
            logger.severe("AsyncStorageWriter: истёк тайм-аут ожидания очереди; ожидающие операции отменяются");
            cancelQueuedWrites();
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "AsyncStorageWriter: ожидание очереди прервано", exception);
            cancelQueuedWrites();
            return false;
        }
    }

    private void cancelQueuedWrites() {
        for (Runnable task : executor.shutdownNow()) {
            if (task instanceof PendingWrite pendingWrite) {
                pendingWrite.finish(false);
            }
        }
    }

    private final class PendingWrite implements Runnable {

        private final File file;
        private final String snapshot;
        private final CompletableFuture<Boolean> result;
        private final AtomicBoolean finished = new AtomicBoolean();

        private PendingWrite(File file, String snapshot, CompletableFuture<Boolean> result) {
            this.file = file;
            this.snapshot = snapshot;
            this.result = result;
        }

        @Override
        public void run() {
            boolean successful;
            try {
                successful = fileWriter.write(snapshot, file, logger);
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "AsyncStorageWriter: ошибка записи " + file.getName(), exception);
                successful = false;
            }
            finish(successful);
        }

        private void finish(boolean successful) {
            if (finished.compareAndSet(false, true)) {
                pendingWrites.decrementAndGet();
                result.complete(successful);
            }
        }
    }

    @Override
    public void close() {
        closeAndAwait(Duration.ofSeconds(10));
    }

    @FunctionalInterface
    interface AtomicFileWriter {
        boolean write(String snapshot, File file, Logger logger);
    }
}
