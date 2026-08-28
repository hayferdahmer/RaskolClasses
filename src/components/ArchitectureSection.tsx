import { useState } from "react";
import { PACKAGES } from "../data";
import { BoltIcon, CheckIcon, FileIcon, FolderIcon, GearIcon, HookIcon } from "./Icons";
import { Reveal, SectionHead } from "./Bits";

export default function ArchitectureSection() {
  const [active, setActive] = useState(0);
  const pkg = PACKAGES[active];

  return (
    <section id="arch" className="relative px-4 py-20 md:px-8 md:py-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// §1 состояние — пакеты репозитория 1.2.0"
          title="Архитектура по пакетам"
          note="dev.raskol.classes.* — 10 пакетов. Источник правды — репозиторий: перед любой правкой читается текущий файл."
        />

        <div className="grid gap-8 lg:grid-cols-[0.85fr_1.15fr] lg:gap-12">
          {/* дерево */}
          <div className="lg:sticky lg:top-24 lg:self-start">
            <Reveal>
              <div className="notch border border-line bg-ink-900">
                <div className="flex items-center justify-between border-b border-line-soft px-4 py-2.5">
                  <span className="mono-tag text-mut">дерево пакетов</span>
                  <span className="mono-tag text-gold">10/10</span>
                </div>
                <div className="px-2 py-2">
                  <p className="flex items-center gap-2 px-2 py-1.5 font-mono text-xs text-parch">
                    <FolderIcon size={14} className="text-gold" />
                    dev.raskol.classes
                  </p>
                  <div className="ml-3 border-l border-line-soft pl-2">
                    {PACKAGES.map((p, i) => (
                      <button
                        key={p.name}
                        onClick={() => setActive(i)}
                        onMouseEnter={() => setActive(i)}
                        className={`group flex w-full items-center gap-2 border-l-2 px-3 py-[7px] text-left font-mono text-[12.5px] transition-all duration-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold ${
                          i === active
                            ? "border-gold bg-gold/8 text-gold"
                            : "border-transparent text-mut hover:border-line hover:text-parch"
                        }`}
                      >
                        <span className={i === active ? "text-gold" : "text-line"}>└─</span>
                        {p.name}
                        <span
                          className={`ml-auto h-1 w-1 rounded-full transition-colors ${
                            i === active ? "bg-gold" : "bg-transparent group-hover:bg-line"
                          }`}
                        />
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </Reveal>

            <Reveal delay={140}>
              <div className="mt-5 grid grid-cols-3 gap-2 text-center">
                {[
                  { icon: BoltIcon, v: "21", l: "активок" },
                  { icon: GearIcon, v: "6", l: "пассивок" },
                  { icon: HookIcon, v: "3", l: "хука" },
                ].map((s) => (
                  <div key={s.l} className="border border-line-soft bg-ink-900 px-2 py-3">
                    <s.icon size={16} className="mx-auto mb-1.5 text-gold" />
                    <p className="font-display text-lg font-bold text-parch">{s.v}</p>
                    <p className="mono-tag mt-0.5 text-mut">{s.l}</p>
                  </div>
                ))}
              </div>
            </Reveal>
          </div>

          {/* панель деталей */}
          <div>
            <Reveal>
              <div key={pkg.name} className="notch rv-in border border-line bg-ink-900/80">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line-soft px-5 py-4">
                  <div className="flex items-center gap-3">
                    <FolderIcon size={20} className="text-gold" />
                    <h3 className="font-mono text-base font-bold text-parch md:text-lg">
                      dev.raskol.classes.<span className="text-gold">{pkg.name}</span>
                    </h3>
                  </div>
                  {pkg.badge && (
                    <span
                      className={`mono-tag notch-sm px-2.5 py-1 ${
                        pkg.badgeCrown === "scarlet"
                          ? "bg-crimson/12 text-crimson"
                          : "bg-gold/12 text-gold"
                      }`}
                    >
                      {pkg.badge}
                    </span>
                  )}
                </div>

                <div className="px-5 py-5 md:px-7 md:py-6">
                  <p className="font-display text-sm font-bold uppercase tracking-wide text-parch-dim">
                    {pkg.role}
                  </p>

                  <ul className="mt-4 space-y-2.5">
                    {pkg.inside.map((line) => (
                      <li key={line} className="flex items-start gap-2.5 text-sm leading-relaxed text-parch-dim">
                        <CheckIcon size={14} className="mt-1 shrink-0 text-jade" />
                        {line}
                      </li>
                    ))}
                  </ul>

                  <div className="mt-6 border-t border-line-soft pt-4">
                    <p className="mono-tag mb-2.5 text-mut">ключевые файлы</p>
                    <div className="flex flex-wrap gap-2">
                      {pkg.files.map((f) => (
                        <span
                          key={f}
                          className="flex items-center gap-1.5 border border-line-soft bg-ink-950 px-2.5 py-1.5 font-mono text-[11.5px] text-parch-dim transition-colors hover:border-gold/50 hover:text-gold"
                        >
                          <FileIcon size={12} className="text-gold/70" />
                          {f}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </Reveal>

            {/* принцип чтения репо */}
            <Reveal delay={120}>
              <div className="mt-5 border border-line-soft bg-ink-900 px-5 py-4">
                <p className="font-mono text-[11.5px] leading-relaxed text-mut">
                  <span className="text-jade">$</span> перед любой правкой —{" "}
                  <span className="text-parch">читай текущий файл из репо</span>. Ручные хотфиксы и код
                  1.2 <span className="text-gold">не откатывать</span>: прод важнее красоты.
                </p>
              </div>
            </Reveal>
          </div>
        </div>
      </div>
    </section>
  );
}
