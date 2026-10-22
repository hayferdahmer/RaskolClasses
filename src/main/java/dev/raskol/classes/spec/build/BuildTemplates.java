// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import dev.raskol.classes.spec.service.Spec2Service;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 1.14.15: загрузчик и валидатор кураторских шаблонов raskolspec+ из builds.yml.
 *
 * Жизненный цикл:
 *  - RaskolClasses.onEnable извлекает builds.yml из jar, если файла нет;
 *  - BuildTemplates.load(...) читает YAML и валидирует каждый шаблон;
 *  - невалидные шаблоны помечаются как disabled (warning в лог), остальные работают;
 *  - /rc reload перечитывает файл через reload().
 *
 * Валидация шаблона (нарушение = шаблон disabled, warning, не краш):
 *  1. spec существует в Spec2Registry;
 *  2. archetype ∈ {PVP, PVE};
 *  3. каждый id в ranks существует в дереве спеки;
 *  4. ранг ∈ [1, maxRank] узла;
 *  5. сумма рангов ≤ Spec2Points.snapshot().maxPoints().
 *
 * 1.14.16-fix (AUTO-HEAL): вместо жёсткого отказа при расхождении с reconcile
 *  загрузчик прогоняет ranks через Spec2Service.simulateApply() и СОХРАНЯЕТ
 *  выживший набор как шаблон. Вырезаются: узлы из стоп-листа disabled-nodes,
 *  узлы с невыполненными prereq-цепочками, узлы ниже ряд-гейтов. Куратор получает
 *  INFO-строку со списком вырезанных узлов (прозрачность), игрок — всегда применимый
 *  шаблон. Если после auto-heal не осталось ни одного узла — шаблон отключается.
 *
 * 1.14.16-fix2 (EXPORT): exportNormalized() (/rc plus export) перезаписывает
 *  builds.yml фактическим нормализованным содержимым — ручная синхронизация
 *  «YAML ↔ reconcile» больше не нужна. Запись атомарная (tmp + move).
 *
 * 1.14.15-fix: поиск узла в дереве — Spec2Tree.find(id) (каноническое имя метода).
 */
public final class BuildTemplates {

    private static final Logger LOG = Logger.getLogger("RaskolClasses");

    private final RaskolClasses plugin;
    private final File file;

    private volatile Map<String, Map<Archetype, BuildTemplate>> bySpec;
    private volatile Set<String> staleSpecs; // спеки, у которых хотя бы один архетип stale
    private volatile Map<String, String> loadProblems; // spec → причина отказа

    public BuildTemplates(RaskolClasses plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "builds.yml");
        this.bySpec = Map.of();
        this.staleSpecs = Set.of();
        this.loadProblems = Map.of();
    }

    /**
     * 1.14.15: извлечь builds.yml из jar, если файла ещё нет.
     * Вызывается в RaskolClasses.onEnable до load(...).
     */
    public void extractIfMissing() {
        if (file.exists()) {
            return;
        }
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            LOG.warning("raskolspec+: не удалось создать директорию " + file.getParent());
            return;
        }
        try (InputStream in = plugin.getResource("builds.yml")) {
            if (in == null) {
                LOG.warning("raskolspec+: ресурс builds.yml отсутствует в jar");
                return;
            }
            Files.copy(in, file.toPath());
            LOG.info("raskolspec+: извлечён builds.yml из jar в " + file.getPath());
        } catch (IOException ex) {
            LOG.warning("raskolspec+: не удалось извлечь builds.yml: " + ex.getMessage());
        }
    }

    /**
     * Загрузить и валидировать все шаблоны из builds.yml.
     * Idempotent: можно вызывать повторно на /rc reload.
     */
    public void load() {
        if (!file.exists()) {
            LOG.info("raskolspec+: builds.yml отсутствует, шаблоны не загружены");
            bySpec = Map.of();
            staleSpecs = Set.of();
            loadProblems = Map.of();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection templatesSec = yaml.getConfigurationSection("templates");
        if (templatesSec == null) {
            LOG.warning("raskolspec+: в builds.yml отсутствует секция templates");
            bySpec = Map.of();
            staleSpecs = Set.of();
            loadProblems = Map.of();
            return;
        }

        Map<String, Map<Archetype, BuildTemplate>> loaded = new LinkedHashMap<>();
        Map<String, String> problems = new LinkedHashMap<>();
        Set<String> stale = new HashSet<>();

        Set<String> disabledNodes = disabledNodesSnapshot();
        Spec2Service svc = plugin.getSpec2Service();

        for (String specId : templatesSec.getKeys(false)) {
            ConfigurationSection specSec = templatesSec.getConfigurationSection(specId);
            if (specSec == null) {
                problems.put(specId, "не конфигурационная секция");
                continue;
            }
            Spec specEnum = Spec.fromId(specId);
            if (specEnum == null) {
                problems.put(specId, "неизвестная спека");
                continue;
            }
            Spec2Tree tree = Spec2Registry.treeOf(specId);
            if (tree == null) {
                problems.put(specId, "дерево не зарегистрировано в Spec2Registry");
                continue;
            }

            Map<Archetype, BuildTemplate> archetypeMap = new EnumMap<>(Archetype.class);
            List<String> specProblems = new ArrayList<>();

            for (Archetype arch : Archetype.values()) {
                ConfigurationSection archSec = specSec.getConfigurationSection(arch.yamlKey());
                if (archSec == null) {
                    specProblems.add(arch.yamlKey() + ": отсутствует секция");
                    continue;
                }
                try {
                    BuildTemplate t = parseTemplate(specId, arch, archSec);
                    List<String> v = validate(t, tree);
                    if (!v.isEmpty()) {
                        specProblems.add(arch.yamlKey() + ": " + String.join("; ", v));
                        continue;
                    }
                    // 1.14.16-fix (AUTO-HEAL): приводим шаблон к множеству, которое
                    // гарантированно переживает reconcile (стоп-лист, prereq, ряд-гейты).
                    if (svc != null) {
                        Map<String, Integer> kept = svc.simulateApply(tree, t.ranks());
                        if (!kept.equals(t.ranks())) {
                            if (kept.isEmpty()) {
                                specProblems.add(arch.yamlKey()
                                        + ": после auto-heal не осталось узлов (весь шаблон в стоп-листе/без prereq)");
                                continue;
                            }
                            List<String> removed = new ArrayList<>(t.ranks().keySet());
                            removed.removeAll(kept.keySet());
                            // 1.14.16-fix2: нормализация по стоп-листу/гейтам — ожидание,
                            // а не дефект; INFO сохраняет прозрачность без замусоривания WARN.
                            // Постоянно убрать строки можно командой /rc plus export.
                            LOG.info("raskolspec+: [" + specId + "] " + arch.yamlKey()
                                    + ": нормализовано по стоп-листу/гейтам, вырезано узлов "
                                    + removed.size() + ": " + removed
                                    + " — /rc plus export перезапишет builds.yml");
                            t = BuildTemplate.of(t.spec(), t.archetype(), t.name(),
                                    t.tagline(), kept, t.updated());
                        }
                    }
                    archetypeMap.put(arch, t);
                    if (hasStaleNode(t, disabledNodes)) {
                        stale.add(specId);
                    }
                } catch (Exception ex) {
                    specProblems.add(arch.yamlKey() + ": парсинг — " + ex.getMessage());
                }
            }

            if (!archetypeMap.isEmpty()) {
                loaded.put(specId, archetypeMap);
            }
            if (!specProblems.isEmpty()) {
                problems.put(specId, String.join(" | ", specProblems));
                for (String p : specProblems) {
                    LOG.warning("raskolspec+: [" + specId + "] " + p);
                }
            }
        }

        this.bySpec = Collections.unmodifiableMap(loaded);
        this.staleSpecs = Set.copyOf(stale);
        this.loadProblems = Collections.unmodifiableMap(problems);

        LOG.info("raskolspec+: загружено шаблонов " + countLoaded()
                + ", проблемных сек " + problems.size()
                + ", stale " + stale.size());
    }

    /** Перечитать builds.yml (вызов из /rc reload). */
    public void reload() {
        load();
    }

    /* ------------------------------ 1.14.16-fix2: экспорт нормализованного файла ------------------------------ */

    /**
     * Перезаписать builds.yml фактическим нормализованным содержимым
     (ранги = множество, пережившее reconcile). Устраняет расхождение «YAML ↔ боевые
     * правила» без ручной правки 36 шаблонов. Вызывается командой /rc plus export
     * (право raskolclasses.admin.plus).
     *
     * Семантика:
     *  - порядок спек = порядок загрузки файла; порядок узлов = порядок дерева
     *    (читается куратором как «ряды сверху вниз»);
     *  - subscription-блок и current-patch берутся из живого config.yml (plus.*),
     *    чтобы экспорт не расходился с runtime-значениями;
     *  - запись атомарная: builds.yml.tmp → move (REPLACE_EXISTING, ATOMIC_MOVE
     *    с фолбэком), сервер не может прочитать полуп файл;
     *  - in-memory состояние не меняется (оно уже нормализовано), reload не нужен;
     *    следующий reload/рестарт просто не даст INFO-строк нормализации.
     *
     * @return true если файл успешно перезаписан
     */
    public boolean exportNormalized() {
        if (bySpec.isEmpty()) {
            LOG.warning("raskolspec+: export отклонён — нет загруженных шаблонов");
            return false;
        }
        StringBuilder sb = new StringBuilder(8192);
        sb.append("# ============================================================\n");
        sb.append("#  RaskolClasses · builds.yml · АВТОСБОРКА /rc plus export\n");
        sb.append("#  Ранги приведены к множеству, переживающему reconcile\n");
        sb.append("#  (стоп-лист disabled-nodes, prereq-цепочки, ряд-гейты).\n");
        sb.append("#  Ручные правки вноси ПОСЛЕ экспорта: повторный экспорт их затрёт.\n");
        sb.append("# ============================================================\n");
        sb.append("schema: 1\n\n");
        sb.append("subscription:\n");
        sb.append("  enabled: ").append(plugin.getConfig().getBoolean("plus.enabled", true)).append('\n');
        sb.append("  price-per-period: ").append(plugin.getConfig().getInt("plus.price-per-period", 100)).append('\n');
        sb.append("  period-days: ").append(plugin.getConfig().getInt("plus.period-days", 30)).append('\n');
        sb.append("  max-gift-periods: ").append(plugin.getConfig().getInt("plus.max-gift-periods", 12)).append('\n');
        sb.append("  cooldown-gift-ms: ").append(plugin.getConfig().getLong("plus.cooldown-gift-ms", 5000L)).append('\n');
        sb.append("\ncurrent-patch: \"").append(plugin.getPluginMeta().getVersion()).append("\"\n\n");
        sb.append("templates:\n");

        int totalNodes = 0;
        for (Map.Entry<String, Map<Archetype, BuildTemplate>> e : bySpec.entrySet()) {
            String specId = e.getKey();
            Spec2Tree tree = Spec2Registry.treeOf(specId);
            sb.append("  ").append(specId).append(":\n");
            for (Map.Entry<Archetype, BuildTemplate> ae : e.getValue().entrySet()) {
                BuildTemplate t = ae.getValue();
                sb.append("    ").append(ae.getKey().yamlKey()).append(":\n");
                sb.append("      name: ").append(yq(t.name())).append('\n');
                sb.append("      tagline: ").append(yq(t.tagline())).append('\n');
                sb.append("      updated: ").append(yq(t.updated())).append('\n');
                sb.append("      nodes:\n");
                if (tree == null) {
                    for (Map.Entry<String, Integer> r : t.ranks().entrySet()) {
                        sb.append("        ").append(r.getKey()).append(": ").append(r.getValue()).append('\n');
                        totalNodes++;
                    }
                } else {
                    for (Spec2Node n : tree.nodes()) {
                        Integer rank = t.ranks().get(n.id());
                        if (rank != null && rank > 0) {
                            sb.append("        ").append(n.id()).append(": ").append(rank).append('\n');
                            totalNodes++;
                        }
                    }
                }
            }
        }

        if (!writeAtomic(sb.toString())) {
            return false;
        }
        LOG.info("raskolspec+: builds.yml перезаписан нормализованными шаблонами ("
                + countLoaded() + " шаблонов, " + totalNodes + " узлов)");
        return true;
    }

    /** Двойные кавычки с экранированием для YAML-скаляров name/tagline/updated. */
    private static String yq(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Атомарная запись: tmp-файл в той же директории → move с REPLACE_EXISTING. */
    private boolean writeAtomic(String content) {
        Path target = file.toPath();
        Path tmp = file.getAbsoluteFile().getParentFile().toPath().resolve("builds.yml.tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            LOG.warning("raskolspec+: не удалось записать builds.yml: " + ex.getMessage());
            return false;
        }
    }

    /* ------------------------------ парсинг ------------------------------ */

    private BuildTemplate parseTemplate(String spec, Archetype archetype,
                                         ConfigurationSection sec) {
        String name = sec.getString("name", "");
        String tagline = sec.getString("tagline", "");
        String updated = sec.getString("updated", "");

        ConfigurationSection nodesSec = sec.getConfigurationSection("nodes");
        Map<String, Integer> ranks = new HashMap<>();
        if (nodesSec != null) {
            for (String nodeId : nodesSec.getKeys(false)) {
                int rank = nodesSec.getInt(nodeId, 0);
                if (rank > 0) {
                    ranks.put(nodeId, rank);
                }
            }
        }
        return BuildTemplate.of(spec, archetype, name, tagline, ranks, updated);
    }

    /* ------------------------------ валидация ------------------------------ */

    /**
     * Структурная валидация (жёсткие ошибки): существование узлов, границы рангов,
     * бюджет очков. Prereq/ряд-гейты/стоп-лист НЕ являются ошибкой с 1.14.16-fix —
     * их нормализует auto-heal через simulateApply (шаблон остаётся применимым).
     */
    private List<String> validate(BuildTemplate t, Spec2Tree tree) {
        List<String> errs = new ArrayList<>();

        int totalPoints = 0;
        Spec2Points.Snapshot snap = Spec2Points.snapshot();
        int maxPoints = snap.maxPoints();

        for (Map.Entry<String, Integer> e : t.ranks().entrySet()) {
            String id = e.getKey();
            int rank = e.getValue();
            Spec2Node node = tree.find(id);
            if (node == null) {
                errs.add("узел " + id + " отсутствует в дереве " + t.spec());
                continue;
            }
            if (rank < 1 || rank > node.maxRank()) {
                errs.add("узел " + id + " ранг " + rank + " вне [1.." + node.maxRank() + "]");
            }
            totalPoints += rank;
        }

        if (totalPoints > maxPoints) {
            errs.add("сумма очков " + totalPoints + " превышает maxPoints=" + maxPoints);
        }

        return errs;
    }

    private boolean hasStaleNode(BuildTemplate t, Set<String> disabledNodes) {
        for (String id : t.ranks().keySet()) {
            if (disabledNodes.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Актуальный стоп-лист из Spec2Service (disabled-nodes). */
    private Set<String> disabledNodesSnapshot() {
        if (plugin.getSpec2Service() == null) {
            return Set.of();
        }
        return plugin.getSpec2Service().disabledNodesSnapshot();
    }

    /* ------------------------------ API ------------------------------ */

    /** Шаблоны спеки по архетипам; пустая карта, если спеки нет. */
    public Map<Archetype, BuildTemplate> forSpec(String spec) {
        Map<Archetype, BuildTemplate> m = bySpec.get(spec);
        return m == null ? Map.of() : m;
    }

    /** Конкретный шаблон или empty. */
    public Optional<BuildTemplate> get(String spec, Archetype archetype) {
        Map<Archetype, BuildTemplate> m = bySpec.get(spec);
        return m == null ? Optional.empty() : Optional.ofNullable(m.get(archetype));
    }

    /** Шаблон stale (содержит узел из disabled-nodes)? */
    public boolean isStale(BuildTemplate t) {
        return staleSpecs.contains(t.spec());
    }

    /** Все валидные спеки. */
    public Set<String> availableSpecs() {
        return bySpec.keySet();
    }

    /** Число валидных шаблонов (для selftest и логов). */
    public int countLoaded() {
        int n = 0;
        for (Map<Archetype, BuildTemplate> m : bySpec.values()) {
            n += m.size();
        }
        return n;
    }

    /** Число stale-шаблонов (disabled-nodes-маркер). */
    public int countStale() {
        int n = 0;
        for (Map<Archetype, BuildTemplate> m : bySpec.values()) {
            for (BuildTemplate t : m.values()) {
                if (isStale(t)) n++;
            }
        }
        return n;
    }

    /** Число проблемных секций (для selftest). */
    public int countProblems() {
        return loadProblems.size();
    }

    /** Причины отказа по спекам — для selftest-чека 123. */
    public Map<String, String> loadProblems() {
        return loadProblems;
    }
}
