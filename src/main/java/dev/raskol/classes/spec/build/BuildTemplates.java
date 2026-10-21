// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
 *  5. сумма рангов ≤ Spec2Points.snapshot().maxPoints();
 *  6. prereq-замкнутость: для (nodeId, rank) с rank≥1 все prereqs имеют rank≥required;
 *  7. узлы не входят в spec2.disabled-nodes (иначе шаблон помечается stale).
 *
 * Гейты рядов в шаблонах raskolspec+ не проверяем при загрузке:
 *  - куратор (ты) контролирует их при составлении;
 *  - игрок применяет шаблон через /rc plus apply, где Spec2Service.reconcile
 *    сам применит/отклонит узлы через существующий гейт-конвейер;
 *  - валидация гейтов усложнила бы загрузчик без пользы для безопасности.
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
                    List<String> v = validate(t, tree, disabledNodes);
                    if (!v.isEmpty()) {
                        specProblems.add(arch.yamlKey() + ": " + String.join("; ", v));
                    } else {
                        archetypeMap.put(arch, t);
                        if (hasStaleNode(t, disabledNodes)) {
                            stale.add(specId);
                        }
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

    private List<String> validate(BuildTemplate t, Spec2Tree tree, Set<String> disabledNodes) {
        List<String> errs = new ArrayList<>();

        // 1–2 уже проверены на этапе парсинга (spec валиден, архетип ∈ {PVP,PVE})

        int totalPoints = 0;
        Spec2Points.Snapshot snap = Spec2Points.snapshot();
        int maxPoints = snap.maxPoints();

        // 3, 4: узлы и ранги
        for (Map.Entry<String, Integer> e : t.ranks().entrySet()) {
            String id = e.getKey();
            int rank = e.getValue();
            Spec2Node node = tree.nodeById(id);
            if (node == null) {
                errs.add("узел " + id + " отсутствует в дереве " + t.spec());
                continue;
            }
            if (rank < 1 || rank > node.maxRank()) {
                errs.add("узел " + id + " ранг " + rank + " вне [1.." + node.maxRank() + "]");
            }
            totalPoints += rank;
        }

        // 5: сумма очков
        if (totalPoints > maxPoints) {
            errs.add("сумма очков " + totalPoints + " превышает maxPoints=" + maxPoints);
        }

        // 6: prereq-замкнутость
        for (Map.Entry<String, Integer> e : t.ranks().entrySet()) {
            String id = e.getKey();
            int rank = e.getValue();
            if (rank < 1) continue;
            Spec2Node node = tree.nodeById(id);
            if (node == null) continue;
            for (Map.Entry<String, Integer> pre : node.prereqs().entrySet()) {
                String preId = pre.getKey();
                int required = pre.getValue();
                int actual = t.ranks().getOrDefault(preId, 0);
                if (actual < required) {
                    errs.add("узел " + id + " требует " + preId + "≥" + required
                            + ", в шаблоне " + actual);
                }
            }
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
