import { useEffect, useState } from "react";
import { CLASSES, type ClassAbility, type RaskolClass } from "../data";
import { usePrefersReducedMotion, useScramble, useTicker } from "../hooks";
import { ABILITY_ICON, ArrowUpRight, CrownIcon } from "./Icons";
import { CrownTag } from "./Bits";

/* ================= HUD-симулятор ================= */

interface Floater {
  id: number;
  text: string;
}
interface LogLine {
  id: number;
  text: string;
  tone: "ok" | "warn";
}

function HudDemo({
  cls,
  onClassChange,
}: {
  cls: RaskolClass;
  onClassChange: (id: number) => void;
}) {
  const [resource, setResource] = useState(62);
  const [cds, setCds] = useState<Record<string, number>>({});
  const [floaters, setFloaters] = useState<Floater[]>([]);
  const [log, setLog] = useState<LogLine | null>(null);
  const now = useTicker(100);
  const reduced = usePrefersReducedMotion();

  const isGold = cls.crown === "gold";
  const accentText = isGold ? "text-gold" : "text-crimson";
  const accentBg = isGold ? "bg-gold" : "bg-crimson";

  /* реген ресурса: +2 каждые 600 мс */
  useEffect(() => {
    const id = window.setInterval(
      () => setResource((r) => (r >= 100 ? r : Math.min(100, r + 2))),
      600,
    );
    return () => window.clearInterval(id);
  }, []);

  /* смена класса сбрасывает кулдауны */
  useEffect(() => {
    setCds({});
    setLog({
      id: Date.now(),
      text: `Класс принят: ${cls.name} · ${cls.resource} ${resource}/100`,
      tone: "ok",
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cls.id]);

  const cast = (a: ClassAbility) => {
    const readyAt = cds[a.name] ?? 0;
    if (now < readyAt) {
      setLog({
        id: Date.now(),
        text: `«${a.name}» на кд — ещё ${Math.ceil((readyAt - now) / 1000)} с`,
        tone: "warn",
      });
      return;
    }
    if (resource < a.cost) {
      setLog({
        id: Date.now(),
        text: `${cls.resource}: нужно ${a.cost}, есть ${resource}`,
        tone: "warn",
      });
      return;
    }
    setResource((r) => r - a.cost);
    setCds((c) => ({ ...c, [a.name]: Date.now() + a.cd * 1000 }));
    setLog({ id: Date.now(), text: `«${a.name}» — готово через ${a.cd} с`, tone: "ok" });
    if (!reduced) {
      const fid = Date.now() + Math.random();
      setFloaters((f) => [...f, { id: fid, text: `-${a.cost}` }]);
      window.setTimeout(() => setFloaters((f) => f.filter((x) => x.id !== fid)), 1000);
    }
  };

  const filled = Math.round(resource / 5);
  const showLog = log !== null && now - log.id < 2800;

  return (
    <div
      id="hud"
      className="hud-flicker notch relative border border-line bg-ink-900/90 shadow-[0_30px_80px_-30px_rgba(0,0,0,0.8)]"
    >
      {/* шапка симулятора */}
      <div className="flex items-center justify-between border-b border-line-soft px-4 py-3">
        <span className="mono-tag text-mut">raskolclasses · hud-sim</span>
        <span className="flex items-center gap-3">
          <span className="mono-tag flex items-center gap-1.5 text-jade">
            <span className="tps-dot inline-block h-1.5 w-1.5 rounded-full bg-jade" />
            20 TPS
          </span>
          <span className="mono-tag text-mut">api 1.21</span>
        </span>
      </div>

      {/* выбор слота */}
      <div className="border-b border-line-soft px-4 py-3">
        <div className="grid grid-cols-5 gap-1.5">
          {CLASSES.map((c) => {
            const active = c.id === cls.id;
            const cGold = c.crown === "gold";
            return (
              <button
                key={c.id}
                onClick={() => onClassChange(c.id)}
                title={`${c.name} — ${c.crown === "gold" ? "Золотая" : "Алая"} корона`}
                className={`notch-sm border px-1 py-2 font-display text-[11px] font-bold transition-all duration-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold ${
                  active
                    ? cGold
                      ? "border-gold/70 bg-gold/12 text-gold"
                      : "border-crimson/70 bg-crimson/12 text-crimson"
                    : "border-line bg-ink-850 text-mut hover:border-line hover:text-parch hover:bg-ink-800"
                }`}
              >
                /rc {c.id}
              </button>
            );
          })}
        </div>
        <div className="mt-3 flex items-center justify-between gap-3">
          <div>
            <p className="font-display text-xl font-bold text-parch">
              {cls.slot} · {cls.name}
            </p>
            <p className="mt-0.5 text-xs text-mut">{cls.role}</p>
          </div>
          <CrownTag crown={cls.crown} />
        </div>
      </div>

      {/* ресурс */}
      <div className="px-4 py-4">
        <div className="relative">
          <div className="mb-2 flex items-baseline justify-between">
            <span className={`mono-tag ${accentText}`}>{cls.resource}</span>
            <span className="font-mono text-sm font-bold text-parch">
              {resource}
              <span className="text-mut">/100</span>
              <span className="ml-2 text-[10px] font-medium text-jade">+2/тик</span>
            </span>
          </div>
          <div className="flex gap-[3px]">
            {Array.from({ length: 20 }, (_, i) => (
              <div
                key={i}
                className={`h-3 flex-1 transition-colors duration-300 ${
                  i < filled ? `${accentBg} ${i === filled - 1 ? "bar-breathe" : ""}` : "bg-ink-700"
                }`}
              />
            ))}
          </div>
          {/* всплывающие траты */}
          <div className="pointer-events-none absolute -top-1 right-0">
            {floaters.map((f) => (
              <span key={f.id} className={`float-txt absolute right-0 font-mono text-sm font-bold ${accentText}`}>
                {f.text}
              </span>
            ))}
          </div>
        </div>

        {/* активки */}
        <div className="mt-4 grid grid-cols-3 gap-2">
          {cls.abilities.map((a) => {
            const Icon = ABILITY_ICON[a.icon];
            const readyAt = cds[a.name] ?? 0;
            const remaining = Math.max(0, readyAt - now);
            const pct = remaining > 0 ? (remaining / (a.cd * 1000)) * 100 : 0;
            const onCd = remaining > 0;
            return (
              <button
                key={a.name}
                onClick={() => cast(a)}
                className={`group relative overflow-hidden border border-line bg-ink-850 px-2.5 py-3 text-left transition-all duration-200 hover:-translate-y-0.5 focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold ${
                  onCd ? "cursor-not-allowed" : isGold ? "hover:border-gold/60" : "hover:border-crimson/60"
                }`}
              >
                <Icon size={18} className={onCd ? "text-mut" : accentText} />
                <p className="mt-2 text-[11px] font-bold leading-tight text-parch">{a.name}</p>
                <p className="mt-1 font-mono text-[10px] text-mut">
                  <span className={accentText}>{a.cost}⬢</span> · кд {a.cd} с
                </p>
                {onCd && (
                  <>
                    <span
                      className="absolute inset-0"
                      style={{
                        background: `conic-gradient(rgba(11,13,18,0.85) ${pct}%, rgba(11,13,18,0.3) 0)`,
                      }}
                    />
                    <span className="absolute inset-0 flex items-center justify-center font-mono text-sm font-bold text-parch">
                      {Math.ceil(remaining / 1000)}
                    </span>
                  </>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* actionbar */}
      <div className="border-t border-line bg-ink-950 px-4 py-3">
        <p className="truncate text-center font-mono text-[11px]">
          {showLog ? (
            <span className={log.tone === "ok" ? "text-jade" : "text-crimson"}>▸ {log.text}</span>
          ) : (
            <>
              <span className="text-mut">[HUD]</span> <span className="text-parch">{cls.name}</span>
              <span className="text-mut"> · </span>
              <span className={accentText}>{cls.resource.toLowerCase()}</span>
              <span className="text-mut"> </span>
              <span className="text-parch">{resource}/100</span>
              <span className="text-mut"> · пас: </span>
              <span className="text-parch-dim">{cls.passive.split("—")[0]}</span>
            </>
          )}
        </p>
      </div>

      <p className="border-t border-line-soft px-4 py-2 text-center font-mono text-[10px] text-mut">
        интерактив: жмите на активки — кд и ресурс ведут себя как в проде
      </p>
    </div>
  );
}

/* ================= первый экран ================= */

export default function Masthead({
  cls,
  onClassChange,
}: {
  cls: RaskolClass;
  onClassChange: (id: number) => void;
}) {
  const [mounted, setMounted] = useState(false);
  const [mounted2, setMounted2] = useState(false);
  useEffect(() => {
    setMounted(true);
    const t = window.setTimeout(() => setMounted2(true), 380);
    return () => window.clearTimeout(t);
  }, []);

  const line1 = useScramble("RASKOL", mounted, 30);
  const line2 = useScramble("CLASSES", mounted2, 26);

  return (
    <section id="top" className="relative overflow-hidden px-4 pb-16 pt-28 md:px-8 md:pt-36">
      {/* парящие короны */}
      <CrownIcon
        size={150}
        className="crown-drift pointer-events-none absolute -right-6 top-24 text-gold/10 md:right-24"
      />
      <CrownIcon
        crown="scarlet"
        size={110}
        className="crown-drift-b pointer-events-none absolute -left-8 bottom-10 text-crimson/10"
      />

      <div className="relative mx-auto grid max-w-7xl items-start gap-12 lg:grid-cols-[1.05fr_0.95fr]">
        {/* левая колонка —.identity */}
        <div>
          <p className="mono-tag mb-5 text-gold">
            {"// dev.raskol.classes — плагин классов · РАСКОЛ | ДВЕ КОРОНЫ"}
          </p>

          <h1 className="font-display font-black leading-[0.95] tracking-tight">
            <span className="block text-[clamp(2.9rem,8.5vw,6.2rem)] text-parch">{line1}</span>
            <span className="block text-[clamp(2.2rem,6.4vw,4.6rem)] text-transparent [-webkit-text-stroke:1.5px_rgba(233,180,76,0.85)]">
              {line2}
            </span>
          </h1>

          {/* рельса версий */}
          <div className="mt-7 flex flex-wrap items-center gap-3">
            <span className="mono-tag notch-sm inline-flex items-center gap-2 border border-jade/40 bg-jade/10 px-3 py-1.5 text-jade">
              <span className="tps-dot inline-block h-1.5 w-1.5 rounded-full bg-jade" />
              1.2.0 · прод
            </span>
            <ArrowUpRight size={16} className="rotate-45 text-mut" />
            <span className="mono-tag notch-sm inline-flex items-center gap-2 border border-gold/50 bg-gold/12 px-3 py-1.5 text-gold">
              1.3.0 · в работе
            </span>
          </div>
          <div className="mt-4 max-w-sm">
            <div className="h-[3px] w-full bg-ink-700">
              <div className="h-full w-[58%] bg-gradient-to-r from-gold to-crimson transition-all duration-1000" />
            </div>
            <p className="mt-2 font-mono text-[10px] tracking-wider text-mut">
              релиз 1.3.0 — пакет 3/6 · коммит = пакет = ран
            </p>
          </div>

          <p className="mt-7 max-w-xl text-[15px] leading-relaxed text-parch-dim">
            Классы для сервера «РАСКОЛ | ДВЕ КОРОНЫ»:{" "}
            <strong className="font-bold text-parch">21 активная способность</strong>, 6 пассивок,
            ресурс 0–100 с регеном и HUD прямо в actionbar. Всё тюнингуется конфигами, reload — без
            рестарта, а без LuckPerms и PAPI плагин просто работает дальше.
          </p>

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <a
              href="#process"
              className="notch-sm bg-gold px-6 py-3 font-display text-[11px] font-bold uppercase tracking-[0.16em] text-ink-950 transition-transform duration-200 hover:-translate-y-0.5 hover:bg-parch focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold"
            >
              Процесс релиза
            </a>
            <a
              href="#arch"
              className="notch-sm border border-line px-6 py-3 font-display text-[11px] font-bold uppercase tracking-[0.16em] text-parch transition-colors duration-200 hover:border-gold/60 hover:text-gold focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold"
            >
              Архитектура
            </a>
          </div>

          <div className="mt-9 flex flex-wrap gap-2">
            {["Paper 26.2", "Java 21", "api-version '1.21'", "paper-api 1.21.4-R0.1"].map((c) => (
              <span key={c} className="mono-tag border border-line-soft bg-ink-900 px-2.5 py-1 text-mut">
                {c}
              </span>
            ))}
          </div>

          {/* лицензионная шапка */}
          <div className="mt-9 flex items-stretch border border-line-soft bg-ink-900">
            <span className="flex items-center border-r border-line-soft bg-ink-850 px-3 font-mono text-[10px] text-mut">
              .java
            </span>
            <p className="overflow-x-auto whitespace-nowrap px-4 py-3 font-mono text-[11px] text-mut">
              <span className="text-jade">{"//"}</span> © 2026 hayferdahmer —{" "}
              <span className="text-gold">RASKOL Proprietary License v1.0</span>. See LICENSE.
            </p>
          </div>
        </div>

        {/* правая колонка — HUD */}
        <div className="lg:sticky lg:top-24">
          <HudDemo cls={cls} onClassChange={onClassChange} />
        </div>
      </div>
    </section>
  );
}
