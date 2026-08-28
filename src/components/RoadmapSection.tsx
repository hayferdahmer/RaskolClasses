import { ROADMAP } from "../data";
import { SplitCrown } from "./Icons";
import { CrackDivider, Reveal, SectionHead } from "./Bits";

const STATUS_STYLE: Record<string, string> = {
  "спека": "border-line text-mut bg-ink-800",
  "в работе": "border-gold/50 text-gold bg-gold/10",
  "тест": "border-jade/50 text-jade bg-jade/10",
};

export default function RoadmapSection() {
  const inWork = ROADMAP.filter((r) => r.status !== "спека").length;

  return (
    <section id="roadmap" className="relative px-4 pb-10 pt-20 md:px-8 md:pt-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// следующий пакет работ"
          title="Дорожная карта 1.3.0"
          note="Каждый пункт — отдельный коммит с фиксированным именем. Пакеты едут по одному."
        />

        <div className="grid gap-10 lg:grid-cols-[0.8fr_1.2fr] lg:gap-16">
          {/* сводка */}
          <div className="lg:sticky lg:top-24 lg:self-start">
            <Reveal>
              <div className="notch border border-line bg-ink-900 p-6 md:p-8">
                <p className="mono-tag text-gold">релиз</p>
                <p className="mt-2 font-display text-[clamp(3rem,7vw,5rem)] font-black leading-none text-parch">
                  1.3<span className="text-gold">.0</span>
                </p>
                <div className="mt-6">
                  <div className="flex justify-between font-mono text-[11px] text-mut">
                    <span>прогресс пакетов</span>
                    <span className="text-gold">
                      {inWork}/{ROADMAP.length}
                    </span>
                  </div>
                  <div className="mt-2 h-[4px] w-full bg-ink-700">
                    <div
                      className="h-full bg-gradient-to-r from-gold to-crimson"
                      style={{ width: `${(inWork / ROADMAP.length) * 100}%` }}
                    />
                  </div>
                </div>
                <dl className="mt-6 space-y-2.5">
                  {[
                    ["в работе", ROADMAP.filter((r) => r.status === "в работе").length],
                    ["в тесте", ROADMAP.filter((r) => r.status === "тест").length],
                    ["в спеке", ROADMAP.filter((r) => r.status === "спека").length],
                  ].map(([k, v]) => (
                    <div key={k as string} className="flex items-center justify-between border-b border-line-soft pb-2">
                      <dt className="mono-tag text-mut">{k as string}</dt>
                      <dd className="font-display text-lg font-bold text-parch">{v}</dd>
                    </div>
                  ))}
                </dl>
                <p className="mt-5 font-mono text-[11px] leading-relaxed text-mut">
                  api-version остаётся <span className="text-gold">'1.21'</span> — не повышать.
                  Рантайм Paper 26.2, Java 21.
                </p>
              </div>
            </Reveal>
          </div>

          {/* пункты */}
          <div className="space-y-3">
            {ROADMAP.map((item, i) => (
              <Reveal key={item.title} delay={Math.min(i * 70, 300)}>
                <div className="row-lift group flex items-start gap-4 border border-line-soft bg-ink-900/70 px-5 py-4 hover:border-line md:items-center">
                  <span className="font-mono text-[11px] text-mut">1.3.{i}</span>
                  <div className="min-w-0">
                    <h3 className="font-display text-sm font-bold text-parch md:text-base">{item.title}</h3>
                    <p className="mt-1 text-[13px] leading-relaxed text-parch-dim">{item.desc}</p>
                  </div>
                  <span className={`mono-tag ml-auto shrink-0 border px-2.5 py-1 ${STATUS_STYLE[item.status]}`}>
                    {item.status}
                  </span>
                </div>
              </Reveal>
            ))}
          </div>
        </div>

        <CrackDivider flip />

        {/* футер */}
        <footer className="pb-8 pt-6">
          <div className="flex flex-col items-start justify-between gap-6 border-t border-line pt-8 md:flex-row md:items-end">
            <div>
              <div className="flex items-center gap-2.5">
                <SplitCrown size={24} className="text-gold" />
                <p className="font-display text-sm font-bold tracking-[0.18em] text-parch">
                  РАСКОЛ <span className="text-gold">|</span> ДВЕ КОРОНЫ
                </p>
              </div>
              <p className="mt-4 max-w-md font-mono text-[11px] leading-relaxed text-mut">
                Доставка — только полными файлами с точными путями, заливка через GitHub Web UI.
                mvn локально не запускается: CI знает лучше.
              </p>
            </div>
            <div className="font-mono text-[11px] leading-relaxed text-mut md:text-right">
              <p>
                <span className="text-jade">{"//"}</span> © 2026 hayferdahmer —{" "}
                <span className="text-gold">RASKOL Proprietary License v1.0</span>
              </p>
              <p className="mt-1">
                RaskolClasses 1.3.0-dev · прод 1.2.0 · ран CI №214{" "}
                <a href="#top" className="link-rail ml-2 text-parch-dim hover:text-gold">
                  ↑ наверх
                </a>
              </p>
            </div>
          </div>
        </footer>
      </div>
    </section>
  );
}
