export type Crown = "gold" | "scarlet";

export interface ClassAbility {
  name: string;
  cd: number; // сек
  cost: number; // ресурс
  icon: "sword" | "shield" | "orb" | "mask" | "herb";
}

export interface RaskolClass {
  id: number;
  slot: string;
  name: string;
  crown: Crown;
  role: string;
  resource: string;
  desc: string;
  abilities: ClassAbility[];
  passive: string;
  stats: { label: string; value: number }[];
}

export const CROWN_LABEL: Record<Crown, string> = {
  gold: "Золотая корона",
  scarlet: "Алая корона",
};

export const CLASSES: RaskolClass[] = [
  {
    id: 1,
    slot: "I",
    name: "Страж",
    crown: "gold",
    role: "Тяжёлая линия фронта",
    resource: "Стойкость",
    desc: "Держит проход и принимает урон короны на себя. Чем ниже здоровье — тем злее щит.",
    abilities: [
      { name: "Стена щитов", cd: 18, cost: 30, icon: "shield" },
      { name: "Вызов", cd: 12, cost: 20, icon: "shield" },
      { name: "Несокрушимость", cd: 30, cost: 45, icon: "shield" },
    ],
    passive: "Латная кожа — часть урона гасится бронёй без зачёта",
    stats: [
      { label: "Выживаемость", value: 92 },
      { label: "Урон", value: 48 },
      { label: "Мобильность", value: 34 },
    ],
  },
  {
    id: 2,
    slot: "II",
    name: "Клинок",
    crown: "scarlet",
    role: "Ближний взрывной урон",
    resource: "Ярость",
    desc: "Копит ярость в размене и отдаёт её одним росчерком. Ошибается один раз — и кулдаун решает всё.",
    abilities: [
      { name: "Росчерк", cd: 8, cost: 25, icon: "sword" },
      { name: "Вихрь стали", cd: 16, cost: 40, icon: "sword" },
      { name: "Казнь", cd: 28, cost: 55, icon: "sword" },
    ],
    passive: "Кровоточащая кромка — криты вешают кровоток",
    stats: [
      { label: "Выживаемость", value: 44 },
      { label: "Урон", value: 94 },
      { label: "Мобильность", value: 62 },
    ],
  },
  {
    id: 3,
    slot: "III",
    name: "Ведун",
    crown: "gold",
    role: "Дальний контроль и разлом",
    resource: "Концентрация",
    desc: "Читает поле боя и ломает построения. Концентрация сбивается уроном — держи дистанцию.",
    abilities: [
      { name: "Разлом", cd: 10, cost: 30, icon: "orb" },
      { name: "Цепи света", cd: 14, cost: 35, icon: "orb" },
      { name: "Корона звёзд", cd: 32, cost: 60, icon: "orb" },
    ],
    passive: "Ясный ум — концентрация восстанавливается быстрее вне боя",
    stats: [
      { label: "Выживаемость", value: 36 },
      { label: "Урон", value: 78 },
      { label: "Мобильность", value: 40 },
    ],
  },
  {
    id: 4,
    slot: "IV",
    name: "Тень",
    crown: "scarlet",
    role: "Скрытность и заказ",
    resource: "Тень",
    desc: "Заходит в спину и уходит до того, как поймут, кто это был. Ресурс копится в темноте.",
    abilities: [
      { name: "Шаг сквозь", cd: 9, cost: 20, icon: "mask" },
      { name: "Веер клинков", cd: 13, cost: 35, icon: "mask" },
      { name: "Приговор", cd: 26, cost: 50, icon: "mask" },
    ],
    passive: "Бесшумный шаг — первый удар из невидимости усилен",
    stats: [
      { label: "Выживаемость", value: 40 },
      { label: "Урон", value: 86 },
      { label: "Мобильность", value: 95 },
    ],
  },
  {
    id: 5,
    slot: "V",
    name: "Знахарь",
    crown: "gold",
    role: "Поддержка и восстановление",
    resource: "Благодать",
    desc: "Держит отряд в строю. Благодать тратится на слова и обереги, копится — на спокойствии.",
    abilities: [
      { name: "Слово жизни", cd: 10, cost: 30, icon: "herb" },
      { name: "Оберег", cd: 15, cost: 40, icon: "herb" },
      { name: "Две короны", cd: 36, cost: 65, icon: "herb" },
    ],
    passive: "Тёплый очаг — союзники рядом медленно лечатся",
    stats: [
      { label: "Выживаемость", value: 58 },
      { label: "Урон", value: 30 },
      { label: "Мобильность", value: 52 },
    ],
  },
];

export interface PackageInfo {
  name: string;
  role: string;
  inside: string[];
  files: string[];
  badge?: string;
  badgeCrown?: Crown;
}

export const PACKAGES: PackageInfo[] = [
  {
    name: "ability",
    role: "Активные способности: реестр, исполнение, кулдауны",
    inside: [
      "21 активка в реестре, каждая — конфиг-тюнингуема",
      "кулдауны с персистом в cooldowns.yml",
      "исполнение синхронное, проверка прав и ресурса до каста",
    ],
    files: ["Ability.java", "AbilityRegistry.java", "CooldownStore.java"],
    badge: "21 активка",
    badgeCrown: "scarlet",
  },
  {
    name: "classsystem",
    role: "Ядро классов: данные игрока, принятие, персист",
    inside: [
      "класс хранится в userdata игрока",
      "титр принятия класса (V1, async-safe)",
      "смена класса — только через /rc menu",
    ],
    files: ["ClassManager.java", "ClassData.java", "AcceptTitle.java"],
  },
  {
    name: "command",
    role: "Командная витрина /rc",
    inside: [
      "/rc [1-5] — прямой выбор слота",
      "/rc menu | hud | reload | debug",
      "табы-подсказки и права на каждую ветку",
    ],
    files: ["RcCommand.java", "RcTabCompleter.java"],
    badge: "/rc",
    badgeCrown: "gold",
  },
  {
    name: "config",
    role: "Конфиг-слой: addDefault + copyDefaults",
    inside: [
      "новые ключи дописываются сами при загрузке",
      "правки админа никогда не затираются",
      "/rc reload подхватывает изменения на лету",
    ],
    files: ["ClassConfig.java", "ConfigMigrator.java"],
  },
  {
    name: "effect",
    role: "Безопасные обёртки над эффектами и частицами",
    inside: [
      "никаких Sound.valueOf — Registry.SOUNDS.get + фолбэк",
      "частицы пакетно, с лимитом на чанк",
      "эффекты отключаемы по конфиг-ключам",
    ],
    files: ["EffectKit.java", "SoundResolver.java"],
  },
  {
    name: "gui",
    role: "Меню выбора класса",
    inside: [
      "витрина слотов I–V с коронами",
      "статы и активки класса до принятия",
      "закрытие по Escape без потери состояния",
    ],
    files: ["ClassMenu.java", "ClassCard.java"],
  },
  {
    name: "hook",
    role: "Graceful-degrade к опциональным плагинам",
    inside: [
      "рефлексия изолирована внутри hook-классов",
      "нет LuckPerms / AuraSkills / PAPI — плагин работает",
      "шаблоны: LuckPermsBackend, SkillLevelProvider",
    ],
    files: ["LuckPermsBackend.java", "SkillLevelProvider.java", "PapiExpansion.java"],
    badge: "LP · AS · PAPI",
    badgeCrown: "gold",
  },
  {
    name: "hud",
    role: "HUD-actionbar",
    inside: [
      "ресурс, кулдауны и класс — в строке actionbar",
      "рендер по тику, без спама пакетами",
      "формат строки целиком в конфиге",
    ],
    files: ["HudRenderer.java", "HudFormat.java"],
    badge: "actionbar",
    badgeCrown: "scarlet",
  },
  {
    name: "passive",
    role: "Пассивные способности",
    inside: [
      "6 пассивок, все — в PassiveListener",
      "каждая вешается только на принятый класс",
      "проверки дешёвые: ранний выход до тяжёлой логики",
    ],
    files: ["PassiveListener.java"],
    badge: "6 пассивок",
    badgeCrown: "scarlet",
  },
  {
    name: "resource",
    role: "Ресурс 0–100 с регеном",
    inside: [
      "пул 0–100 на игрока, реген по конфигу",
      "траты способностей атомарны: проверка → списание",
      "полный пул — abilities на максимальной силе",
    ],
    files: ["ResourcePool.java", "RegenTask.java"],
    badge: "0–100",
    badgeCrown: "gold",
  },
];

export interface ForbiddenRule {
  code: string;
  verdict: "запрещено" | "заменить" | "условно";
  fix: string;
}

export const FORBIDDEN: ForbiddenRule[] = [
  { code: "Component.Builder", verdict: "запрещено", fix: "Не используется вообще. Текст — через разрешённые API проекта." },
  { code: "Location#setPosition", verdict: "запрещено", fix: "Метода нет в API — мутации Location только через конструктор/клон." },
  { code: "EventSubscription#unregister", verdict: "заменить", fix: "Нужно close() — подписка закрывается, а не «разрегистрируется»." },
  { code: "UserDataMutateEvent", verdict: "запрещено", fix: "События нет в рантайме. Мутации userdata — напрямую через ClassData." },
  { code: "Player#isVanished", verdict: "запрещено", fix: "Метода нет в Paper API. Vanish — только через хук, если он появился." },
  { code: "JavaPlugin#getDescription", verdict: "заменить", fix: "Нужно getPluginMeta() — description deprecated в новых рантаймах." },
  { code: "Sound.valueOf", verdict: "заменить", fix: "Нужно Registry.SOUNDS.get(NamespacedKey) + фолбэк на дефолтный звук." },
  { code: "NMS", verdict: "запрещено", fix: "Никаких net.minecraft.* — только Paper API и обёртки effect-пакета." },
  { code: "Рефлексия", verdict: "условно", fix: "Только graceful-degrade к опциональным плагинам, изолированно в hook-классах (шаблон: LuckPermsBackend, SkillLevelProvider)." },
];

export interface ProcessStep {
  num: string;
  title: string;
  body: string;
  chips: string[];
}

export const PROCESS_STEPS: ProcessStep[] = [
  {
    num: "01",
    title: "Коммит = пакет = ран",
    body: "Имена коммитов фиксированы по ТЗ. Один коммит — ровно один пакет работ и ровно один прогон CI. Никаких «заодно поправил».",
    chips: ["build.yml", "pipefail", "артефакт-превью"],
  },
  {
    num: "02",
    title: "СТОП + отчёт",
    body: "После каждого пакета — остановка и отчёт: файлы и строки «было → стало», grep-самопроверка по §3, честное «mvn локально не запускаю» и ожидаемый результат рана.",
    chips: ["было → стало", "grep §3", "без mvn"],
  },
  {
    num: "03",
    title: "[ERROR] из рана",
    body: "Владелец присылает ошибку из CI-рана — фикс делается одним заходом и полным файлом с точным путём. Правки «по памяти» и частичные патчи запрещены.",
    chips: ["один заход", "полный файл", "точный путь"],
  },
  {
    num: "04",
    title: "Два пакета в одном коммите",
    body: "= возврат на рельсы: коммит откатывается и работа переделывается по пакетам. Дисциплина 1.2.0 работала в проде — не ломать.",
    chips: ["возврат на рельсы", "1 коммит = 1 пакет"],
  },
];

export interface LedgerRow {
  id: string;
  figure: string;
  count?: number;
  title: string;
  desc: string;
  chip: string;
  accent: "gold" | "crimson";
}

export const LEDGER: LedgerRow[] = [
  {
    id: "cmd",
    figure: "/rc",
    title: "Командная витрина",
    desc: "Прямой выбор слота 1–5, меню, HUD, reload и debug — всё одной командой с табами.",
    chip: "[1-5|menu|hud|reload|debug]",
    accent: "gold",
  },
  {
    id: "active",
    figure: "",
    count: 21,
    title: "Активная способность",
    desc: "Каждая — отдельный конфиг-ключ: кулдаун, цена ресурса, сила. Баланс правится без кода.",
    chip: "ability-пакет",
    accent: "crimson",
  },
  {
    id: "passive",
    figure: "",
    count: 6,
    title: "Пассивных способностей",
    desc: "Собраны в PassiveListener, вешаются на принятый класс, проверки с ранним выходом.",
    chip: "PassiveListener",
    accent: "crimson",
  },
  {
    id: "res",
    figure: "0–",
    count: 100,
    title: "Шкала ресурса",
    desc: "Пул на игрока с регеном по конфигу; активки тратят ресурс атомарно.",
    chip: "resource-пакет",
    accent: "gold",
  },
  {
    id: "hud",
    figure: "HUD",
    title: "Actionbar-отрисовка",
    desc: "Ресурс, кулдауны и имя класса — в строке actionbar, рендер по тику без спама.",
    chip: "hud-пакет",
    accent: "gold",
  },
  {
    id: "cd",
    figure: ".yml",
    title: "Кулдауны с персистом",
    desc: "Перезаход и рестарт не сбрасывают таймеры — cooldowns.yml переживает всё.",
    chip: "cooldowns.yml",
    accent: "crimson",
  },
  {
    id: "title",
    figure: "V1",
    title: "Титр принятия класса",
    desc: "Полноэкранный титр в момент принятия; реализация V1 полностью async-safe.",
    chip: "async-safe",
    accent: "gold",
  },
  {
    id: "papi",
    figure: "PAPI",
    title: "Плейсхолдеры",
    desc: "Внешние скорборды и меню читают класс и ресурс через %raskolclasses_*%.",
    chip: "%raskolclasses_*%",
    accent: "gold",
  },
  {
    id: "degrade",
    figure: "G/D",
    title: "Graceful-degrade",
    desc: "LuckPerms, AuraSkills и PAPI опциональны: нет хука — плагин работает дальше.",
    chip: "LP · AuraSkills · PAPI",
    accent: "crimson",
  },
];

export interface RoadmapItem {
  title: string;
  desc: string;
  status: "спека" | "в работе" | "тест";
}

export const ROADMAP: RoadmapItem[] = [
  {
    title: "Баланс 21 активки через конфиги",
    desc: "Полный проход по ability-пакету: все числа вынесены в addDefault, правки админа не затираются.",
    status: "в работе",
  },
  {
    title: "cooldowns.yml: миграции схемы",
    desc: "Версионирование схемы файла, аккуратный перенос таймеров между релизами.",
    status: "тест",
  },
  {
    title: "Меню классов: вторая страница",
    desc: "gui-пакет: разделение витрины по коронам, превью пассивок до принятия.",
    status: "в работе",
  },
  {
    title: "HUD: bossbar-режим",
    desc: "Альтернатива actionbar для сборок, где строка занята; режим — конфиг-ключ.",
    status: "спека",
  },
  {
    title: "Новые PAPI-плейсхолдеры",
    desc: "%raskolclasses_resource_pct%, %raskolclasses_crown% и кулдауны по имени активки.",
    status: "спека",
  },
  {
    title: "Хук экономики — graceful-degrade",
    desc: "Цена смены класса через внешнюю экономику; нет плагина — смена бесплатна.",
    status: "спека",
  },
];

export const MARQUEE: string[] = [
  "21 активка",
  "6 пассивок",
  "ресурс 0–100",
  "HUD-actionbar",
  "кулдауны с персистом",
  "%raskolclasses_*%",
  "graceful-degrade",
  "/rc [1-5|menu|hud|reload|debug]",
  "коммит = пакет = ран",
  "Paper 26.2 · Java 21",
];

export const NAV_LINKS: { href: string; label: string }[] = [
  { href: "#hud", label: "HUD" },
  { href: "#process", label: "Процесс" },
  { href: "#arch", label: "Архитектура" },
  { href: "#prod", label: "Прод 1.2.0" },
  { href: "#rules", label: "Правила" },
  { href: "#classes", label: "Классы" },
  { href: "#roadmap", label: "1.3.0" },
];
