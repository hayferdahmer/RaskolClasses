import { PROCESS_STEPS } from "../data";
import { PulseIcon, TerminalIcon } from "./Icons";
import { Reveal, SectionHead } from "./Bits";

export default function ProcessSection() {
  return (
    <section id="process" className="relative px-4 py-20 md:px-8 md:py-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// §2 процесс — дисциплина 1.2.0, работала в проде"
          title="Коммит = пакет = ран"
          note="Владелец не программист: доставка только полными файлами с точными путями, заливка через GitHub Web UI."
          accent="crimson"
        />

        <div className="grid gap-10 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16">
          {/* липкая левая колонка */}
          <div className="lg:sticky lg:top-24 lg:self-start">
            <Reveal>
              <p className="text-[15px] leading-relaxed text-parch-dim">
                Релиз едет по рельсам, которые доказали себя на 1.2.0: каждый коммит — законченный
                пакет работ с фиксированным именем, каждый пакет — отдельный прогон CI. После пакета —{" "}
                <strong className="text-parch">СТОП и отчёт</strong>, а не «пока пишу следующий».
              </p>
            </Reveal>

            {/* CI-терминал */}
            <Reveal delay={120} className="mt-8">
              <div className="notch border border-line bg-ink-900">
                <div className="flex items-center gap-2 border-b border-line-soft px-4 py-2.5">
                  <TerminalIcon size={15} className="text-gold" />
                  <span className="mono-tag text-mut">.github/workflows/build.yml</span>
                  <span className="mono-tag ml-auto text-jade">ran ✓ 214</span>
                </div>
                <div className="space-y-1.5 px-4 py-4 font-mono text-[11.5px] leading-relaxed">
                  <p className="text-mut">
                    <span className="text-parch">$</span> set -o <span className="text-gold">pipefail</span>
                  </p>
                  <p className="text-mut">
                    <span className="text-parch">$</span> mvn -B package | tee build.log
                  </p>
                  <p className="text-mut">
                    <span className="text-parch">$</span> grep -E{" "}
                    <span className="text-crimson">'^\[WARNING\].*deprecated'</span> build.log
                  </p>
                  <p className="text-mut">
                    <span className="text-crimson">↳ совпало — ран красный</span>, деплой не едет
                  </p>
                  <p className="pt-2 text-mut">
                    <span className="text-jade">✓</span> артефакт-превью — в каждом ране
                  </p>
                </div>
              </div>
            </Reveal>

            {/* гейт */}
            <Reveal delay={220} className="mt-6">
              <div className="flex items-center gap-3 border border-line-soft bg-ink-900 px-4 py-3">
                <PulseIcon size={18} className="shrink-0 text-crimson" />
                <p className="text-xs leading-relaxed text-mut">
                  <span className="font-bold text-parch">Deprecation-гейт:</span> любое{" "}
                  <code className="font-mono text-crimson">[WARNING] … deprecated</code> красит ран.
                  getPluginMeta() вместо getDescription, Registry.SOUNDS вместо Sound.valueOf.
                </p>
              </div>
            </Reveal>
          </div>

          {/* шаги */}
          <div className="relative">
            <span className="absolute bottom-6 left-[13px] top-6 w-px bg-gradient-to-b from-gold/60 via-line to-crimson/50" aria-hidden="true" />
            <div className="space-y-8">
              {PROCESS_STEPS.map((s, i) => (
                <Reveal key={s.num} delay={i * 90}>
                  <div className="row-lift relative flex gap-5 border border-line-soft bg-ink-900/70 p-5 hover:border-line md:p-6">
                    <span
                      className={`relative z-10 mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full border font-mono text-[11px] font-bold ${
                        i % 2 === 0 ? "border-gold/60 bg-ink-950 text-gold" : "border-crimson/60 bg-ink-950 text-crimson"
                      }`}
                    >
                      {s.num}
                    </span>
                    <div>
                      <h3 className="font-display text-base font-bold text-parch md:text-lg">
                        {s.title}
                      </h3>
                      <p className="mt-2 text-sm leading-relaxed text-parch-dim">{s.body}</p>
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {s.chips.map((c) => (
                          <span key={c} className="mono-tag border border-line-soft bg-ink-950 px-2 py-0.5 text-mut">
                            {c}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </Reveal>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
