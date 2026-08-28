import { NAV_LINKS } from "../data";
import { SplitCrown } from "./Icons";

export default function Nav() {
  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-line-soft bg-ink-950/85 backdrop-blur-sm">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-4 px-4 md:px-8">
        <a href="#top" className="group flex items-center gap-2.5">
          <SplitCrown size={22} className="text-gold transition-transform duration-500 group-hover:rotate-[8deg]" />
          <span className="font-display text-[13px] font-bold tracking-[0.18em] text-parch">
            RASKOL<span className="text-gold">/</span>CLASSES
          </span>
        </a>

        <nav className="hidden items-center gap-6 lg:flex">
          {NAV_LINKS.map((l) => (
            <a
              key={l.href}
              href={l.href}
              className="link-rail mono-tag text-mut transition-colors hover:text-parch"
            >
              {l.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <span className="mono-tag notch-sm hidden items-center gap-1.5 bg-ink-800 px-2.5 py-1 text-parch-dim sm:inline-flex">
            <span className="tps-dot inline-block h-1.5 w-1.5 rounded-full bg-jade" />
            prod 1.2.0
          </span>
          <span className="mono-tag notch-sm inline-flex items-center bg-gold/12 px-2.5 py-1 text-gold">
            v1.3.0-dev
          </span>
        </div>
      </div>
    </header>
  );
}
