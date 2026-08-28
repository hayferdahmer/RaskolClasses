import { CLASSES, type RaskolClass } from "../data";
import { useInView } from "../hooks";
import { ABILITY_ICON } from "./Icons";
import { CrownTag, Reveal, SectionHead } from "./Bits";

function StatBars({ cls }: { cls: RaskolClass }) {
  const { ref, inView } = useInView<HTMLDivElement>(0.4);
  const isGold = cls.crown === "gold";
  return (
    <div ref={ref} className="space-y-2">
      {cls.stats.map((s) => (
        <div key={s.label}>
          <div className="mb-1 flex justify-between font-mono text-[10px] text-mut">
            <span>{s.label}</span>
            <span className={isGold ? "text-gold" : "text-crimson"}>{s.value}</span>
          </div>
          <div className="h-[3px] w-full bg-ink-700">
            <div
              className={`h-full transition-all duration-1000 ease-out ${isGold ? "bg-gold" : "bg-crimson"}`}
              style={{ width: inView ? `${s.value}%` : "0%" }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function ClassesSection({
  selectedId,
  onSelect,
}: {
  selectedId: number;
  onSelect: (id: number) => void;
}) {
  return (
    <section id="classes" className="relative px-4 py-20 md:px-8 md:py-28">
      <div className="mx-auto max-w-7xl">
        <SectionHead
          kicker="// /rc [1-5] · две короны, пять слотов"
          title="Классы раскола"
          note="Три слота Золотой короны, два — Алой. Выбор в симуляторе HUD наверху синхронизирован с этим списком."
        />

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {CLASSES.map((cls, i) => {
            const active = cls.id === selectedId;
            const isGold = cls.crown === "gold";
            return (
              <Reveal key={cls.id} delay={Math.min(i * 80, 320)}>
                <button
                  onClick={() => {
                    onSelect(cls.id);
                    document.getElementById("hud")?.scrollIntoView({ block: "nearest" });
                  }}
                  className={`notch group block w-full border bg-ink-900/80 p-5 text-left transition-all duration-300 hover:-translate-y-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold ${
                    active
                      ? isGold
                        ? "border-gold/70 shadow-[0_18px_50px_-24px_rgba(233,180,76,0.45)]"
                        : "border-crimson/70 shadow-[0_18px_50px_-24px_rgba(229,72,77,0.45)]"
                      : "border-line hover:border-line"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-display text-[2.6rem] font-black leading-none text-ink-700 transition-colors duration-300 group-hover:text-line">
                      {cls.slot}
                    </span>
                    <CrownTag crown={cls.crown} />
                  </div>

                  <h3 className="mt-3 font-display text-2xl font-bold text-parch">{cls.name}</h3>
                  <p className={`mono-tag mt-1 ${isGold ? "text-gold" : "text-crimson"}`}>{cls.role}</p>
                  <p className="mt-3 min-h-[60px] text-[13px] leading-relaxed text-parch-dim">{cls.desc}</p>

                  <div className="mt-4 space-y-1.5 border-t border-line-soft pt-4">
                    {cls.abilities.map((a) => {
                      const Icon = ABILITY_ICON[a.icon];
                      return (
                        <div key={a.name} className="flex items-center gap-2.5 text-[12.5px] text-parch-dim">
                          <Icon size={14} className={isGold ? "text-gold/80" : "text-crimson/80"} />
                          <span className="font-semibold text-parch">{a.name}</span>
                          <span className="ml-auto font-mono text-[10.5px] text-mut">
                            {a.cost}⬢ · {a.cd}с
                          </span>
                        </div>
                      );
                    })}
                  </div>

                  <p className="mt-3 border-l-2 border-line pl-2.5 text-[11.5px] leading-relaxed text-mut">
                    <span className="mono-tag text-parch-dim">пассив:</span> {cls.passive}
                  </p>

                  <div className="mt-4">
                    <StatBars cls={cls} />
                  </div>

                  <p
                    className={`mono-tag mt-4 inline-flex items-center gap-1.5 transition-colors ${
                      active ? (isGold ? "text-gold" : "text-crimson") : "text-mut group-hover:text-parch"
                    }`}
                  >
                    {active ? "▸ в HUD-симуляторе" : "выбрать в симулятор"}
                  </p>
                </button>
              </Reveal>
            );
          })}

          {/* служебная карточка */}
          <Reveal delay={360}>
            <div className="notch flex h-full min-h-[280px] flex-col justify-between border border-dashed border-line bg-transparent p-5">
              <div>
                <p className="mono-tag text-mut">{"// примечание"}</p>
                <h3 className="mt-3 font-display text-lg font-bold text-parch-dim">
                  Имена и числа — витринные
                </h3>
                <p className="mt-3 text-[13px] leading-relaxed text-mut">
                  Состав слотов в проде определяется репозиторием и конфигами. Здесь — рабочая
                  модель для демонстрации HUD, регена и кулдаунов: механика 1-в-1 как в 1.2.0.
                </p>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {["/rc menu", "ClassMenu.java", "config.yml"].map((c) => (
                  <span key={c} className="mono-tag border border-line-soft px-2 py-0.5 text-mut">
                    {c}
                  </span>
                ))}
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
