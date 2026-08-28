import { FORBIDDEN, type ForbiddenRule } from "../data";
import { ArrowUpRight, CheckIcon, CrossIcon, GearIcon, TerminalIcon } from "./Icons";
import { Reveal, SectionHead } from "./Bits";

const VERDICT_STYLE: Record<ForbiddenRule["verdict"], string> = {
  "запрещено": "bg-crimson/12 text-crimson border-crimson/40",
  "заменить": "bg-gold/12 text-gold border-gold/40",
  "условно": "bg-ink-700/60 text-parch-dim border-line",
};

function VerdictIcon({ v }: { v: ForbiddenRule["verdict"] }) {
  if (v === "запрещено") return <CrossIcon size={15} className="text-crimson" />;
  if (v === "заменить") return <ArrowUpRight size={15} className="text-gold" />;
  return <GearIcon size={15} className="text-parch-dim" />;
}

export default function RulesSection() {
  return (
    <section id="rules" className="relative px-4 py-20 md:px-8 md:py-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// §3 grep-самопроверка по каждому пакету"
          title="Запрещённые конструкции"
          note="Список проверяется grep'ом перед каждым коммитом. Совпадение = пакет не едет."
          accent="crimson"
        />

        <div className="grid gap-10 lg:grid-cols-[1.15fr_0.85fr] lg:gap-14">
          {/* линтер-список */}
          <div className="notch border border-line bg-ink-900">
            <div className="flex items-center justify-between border-b border-line-soft px-4 py-2.5">
              <span className="mono-tag text-mut">raskol-lint · §3</span>
              <span className="mono-tag text-crimson">9 правил</span>
            </div>
            <ul>
              {FORBIDDEN.map((r, i) => (
                <Reveal key={r.code} delay={Math.min(i * 50, 250)}>
                  <li className="group border-b border-line-soft px-4 py-4 transition-colors last:border-b-0 hover:bg-ink-850/80 md:px-5">
                    <div className="flex items-center gap-3">
                      <VerdictIcon v={r.verdict} />
                      <code className="font-mono text-[13px] font-bold text-parch">{r.code}</code>
                      <span
                        className={`mono-tag ml-auto border px-2 py-0.5 ${VERDICT_STYLE[r.verdict]}`}
                      >
                        {r.verdict}
                      </span>
                    </div>
                    <p className="mt-2 pl-7 text-[13px] leading-relaxed text-mut transition-colors duration-300 group-hover:text-parch-dim">
                      {r.fix}
                    </p>
                  </li>
                </Reveal>
              ))}
            </ul>
          </div>

          {/* самопроверка + конфиг-философия */}
          <div className="space-y-6">
            <Reveal delay={100}>
              <div className="border border-line bg-ink-900">
                <div className="flex items-center gap-2 border-b border-line-soft px-4 py-2.5">
                  <TerminalIcon size={15} className="text-gold" />
                  <span className="mono-tag text-mut">самопроверка пакета</span>
                </div>
                <div className="space-y-1.5 px-4 py-4 font-mono text-[11px] leading-relaxed">
                  <p className="text-mut">
                    <span className="text-parch">$</span> grep -rnE{" "}
                    <span className="text-crimson">
                      "Component.Builder|isVanished|Sound.valueOf|NMS"
                    </span>{" "}
                    src/
                  </p>
                  <p className="text-jade">↳ 0 совпадений — пакет чист</p>
                  <p className="pt-1 text-mut">
                    <span className="text-parch">$</span> grep -c{" "}
                    <span className="text-gold">"RASKOL Proprietary License v1.0"</span> src/**/*.java
                  </p>
                  <p className="text-jade">↳ во всех .java — шапка на месте</p>
                </div>
              </div>
            </Reveal>

            <Reveal delay={180}>
              <div className="notch border border-line bg-ink-900">
                <div className="border-b border-line-soft px-4 py-3">
                  <p className="mono-tag text-gold">{"// §4 конфиг-философия"}</p>
                  <h3 className="mt-1 font-display text-lg font-bold text-parch">
                    addDefault + copyDefaults
                  </h3>
                </div>
                <div className="px-4 py-4">
                  <div className="border border-line-soft bg-ink-950 px-4 py-3 font-mono text-[11px] leading-relaxed">
                    <p className="text-mut">{"// ClassConfig.java — новые ключи 1.3.0"}</p>
                    <p className="text-parch">
                      cfg.<span className="text-gold">addDefault</span>(
                      <span className="text-crimson">"abilities.roscherk.cooldown"</span>, 8.0);
                    </p>
                    <p className="text-parch">
                      cfg.<span className="text-gold">addDefault</span>(
                      <span className="text-crimson">"resource.regen.per-tick"</span>, 2);
                    </p>
                    <p className="text-parch">
                      cfg.options().<span className="text-gold">copyDefaults</span>(
                      <span className="text-jade">true</span>);
                    </p>
                    <p className="text-parch">
                      <span className="text-gold">saveConfig</span>();{" "}
                      <span className="text-mut">{"// правки админа остаются"}</span>
                    </p>
                  </div>

                  <ul className="mt-4 space-y-2.5">
                    {[
                      "Новые ключи дописываются сами при загрузке плагина",
                      "Правки админа в config.yml никогда не затираются",
                      "Все новые фичи — конфиг-тюнингуемы, /rc reload подхватывает",
                    ].map((t) => (
                      <li key={t} className="flex items-start gap-2.5 text-[13px] leading-relaxed text-parch-dim">
                        <CheckIcon size={14} className="mt-0.5 shrink-0 text-jade" />
                        {t}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </Reveal>
          </div>
        </div>
      </div>
    </section>
  );
}
