import { LEDGER } from "../data";
import { useCountUp, useInView } from "../hooks";
import { Reveal, SectionHead } from "./Bits";

function Figure({ prefix, count, active }: { prefix: string; count: number; active: boolean }) {
  const v = useCountUp(count, active, 1400);
  return (
    <span className="font-display text-[clamp(1.8rem,4vw,3.1rem)] font-black leading-none tracking-tight text-parch">
      {prefix}
      {v}
    </span>
  );
}

export default function LedgerSection() {
  const { ref, inView } = useInView<HTMLDivElement>(0.08);

  return (
    <section id="prod" className="relative px-4 py-20 md:px-8 md:py-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// в проде с релиза 1.2.0 — не откатывать"
          title="Что уже работает на сервере"
          note="Рантайм Paper 26.2, компиляция paper-api 1.21.4-R0.1-SNAPSHOT, plugin.yml api-version '1.21' — не повышать."
          accent="crimson"
        />

        <div ref={ref} className="border-t border-line">
          {LEDGER.map((row, i) => (
            <Reveal key={row.id} delay={Math.min(i * 60, 300)}>
              <div
                className="row-lift group relative grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 border-b border-line-soft px-2 py-5 hover:bg-ink-900/70 md:grid-cols-[72px_240px_1fr_auto] md:px-4 md:py-6"
              >
                <span
                  className={`absolute left-0 top-0 h-full w-[3px] origin-top scale-y-0 transition-transform duration-300 group-hover:scale-y-100 ${
                    row.accent === "gold" ? "bg-gold" : "bg-crimson"
                  }`}
                  aria-hidden="true"
                />
                <span className="hidden font-mono text-[11px] text-mut md:block">
                  {String(i + 1).padStart(2, "0")}
                </span>

                <div className="min-w-[110px]">
                  {row.count !== undefined ? (
                    <Figure prefix={row.figure} count={row.count} active={inView} />
                  ) : (
                    <span className="font-display text-[clamp(1.4rem,3vw,2.2rem)] font-black leading-none tracking-tight text-parch">
                      {row.figure}
                    </span>
                  )}
                </div>

                <div className="col-span-2 md:col-span-1">
                  <h3 className="font-display text-sm font-bold uppercase tracking-wide text-parch md:text-base">
                    {row.title}
                  </h3>
                  <p className="mt-1 max-w-xl text-[13px] leading-relaxed text-parch-dim">{row.desc}</p>
                </div>

                <span
                  className={`mono-tag col-start-2 notch-sm justify-self-end px-2.5 py-1 md:col-start-auto ${
                    row.accent === "gold" ? "bg-gold/10 text-gold" : "bg-crimson/10 text-crimson"
                  }`}
                >
                  {row.chip}
                </span>
              </div>
            </Reveal>
          ))}
        </div>

        <Reveal delay={200}>
          <p className="mt-6 font-mono text-[11px] text-mut">
            <span className="text-jade">✓</span> всё выше — факт из прода 1.2.0, покрыт раном CI №214.
            Ручные хотфиксы не откатываем, новые фичи едут в 1.3.0 отдельными пакетами.
          </p>
        </Reveal>
      </div>
    </section>
  );
}
