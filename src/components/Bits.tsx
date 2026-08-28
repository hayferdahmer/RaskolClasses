import type { CSSProperties, ReactNode } from "react";
import { useInView } from "../hooks";
import { MARQUEE } from "../data";
import { SplitCrown } from "./Icons";

/* ---------- scroll reveal ---------- */
export function Reveal({
  children,
  delay = 0,
  className = "",
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const { ref, inView } = useInView<HTMLDivElement>();
  return (
    <div
      ref={ref}
      className={`rv ${inView ? "rv-in" : ""} ${className}`}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </div>
  );
}

/* ---------- line-mask reveal для заголовков ---------- */
export function LineReveal({
  children,
  delay = 0,
  className = "",
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const { ref, inView } = useInView<HTMLSpanElement>();
  return (
    <span ref={ref} className={`lr-mask ${inView ? "lr-in" : ""} ${className}`}>
      <span className="lr-line" style={{ transitionDelay: `${delay}ms` } as CSSProperties}>
        {children}
      </span>
    </span>
  );
}

/* ---------- шапка раздела ---------- */
export function SectionHead({
  kicker,
  title,
  note,
  accent = "gold",
}: {
  kicker: string;
  title: string;
  note?: string;
  accent?: "gold" | "crimson";
}) {
  const accentColor = accent === "gold" ? "text-gold" : "text-crimson";
  return (
    <div className="mb-10 flex flex-wrap items-end justify-between gap-4 md:mb-14">
      <div>
        <p className={`mono-tag mb-3 ${accentColor}`}>{kicker}</p>
        <h2 className="font-display text-[clamp(1.6rem,4.2vw,3rem)] font-bold leading-[1.08] tracking-tight text-parch">
          <LineReveal>{title}</LineReveal>
        </h2>
      </div>
      {note && (
        <p className="max-w-xs border-l border-line pl-4 font-mono text-[11px] leading-relaxed text-mut">
          {note}
        </p>
      )}
    </div>
  );
}

/* ---------- бегущая строка ---------- */
export function Marquee() {
  const items = [...MARQUEE, ...MARQUEE];
  return (
    <div className="relative overflow-hidden border-y border-line bg-ink-900/80 py-3">
      <div className="ticker-track flex items-center gap-10">
        {items.map((item, i) => (
          <span key={i} className="flex items-center gap-10">
            <span className="mono-tag whitespace-nowrap text-parch-dim">{item}</span>
            <SplitCrown size={14} className={i % 2 === 0 ? "text-gold" : "text-crimson"} />
          </span>
        ))}
      </div>
      <div className="pointer-events-none absolute inset-y-0 left-0 w-24 bg-gradient-to-r from-ink-950 to-transparent" />
      <div className="pointer-events-none absolute inset-y-0 right-0 w-24 bg-gradient-to-l from-ink-950 to-transparent" />
    </div>
  );
}

/* ---------- трещина-разделитель «раскол» ---------- */
export function CrackDivider({ flip = false }: { flip?: boolean }) {
  const { ref, inView } = useInView<HTMLDivElement>(0.4);
  return (
    <div ref={ref} className={`${inView ? "crack-in" : ""} flex justify-center py-2`}>
      <svg
        viewBox="0 0 900 60"
        className={`h-10 w-full max-w-4xl ${flip ? "-scale-x-100" : ""}`}
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path
          className="crack-path"
          pathLength={1}
          d="M0 30 H360 L395 12 L420 44 L450 8 L480 50 L505 22 L540 30 H900"
          fill="none"
          stroke="url(#crackGrad)"
          strokeWidth="1.4"
        />
        <defs>
          <linearGradient id="crackGrad" x1="0" x2="1" y1="0" y2="0">
            <stop offset="0%" stopColor="#e9b44c" stopOpacity="0.1" />
            <stop offset="45%" stopColor="#e9b44c" stopOpacity="0.9" />
            <stop offset="55%" stopColor="#e5484d" stopOpacity="0.9" />
            <stop offset="100%" stopColor="#e5484d" stopOpacity="0.1" />
          </linearGradient>
        </defs>
      </svg>
    </div>
  );
}

/* ---------- фоновые слои ---------- */
export function AmbientBackdrop() {
  return (
    <>
      <div className="grid-veil pointer-events-none fixed inset-0 z-0" aria-hidden="true" />
      <div className="glow-crimson pointer-events-none fixed -left-40 -top-40 z-0 h-[42rem] w-[42rem]" aria-hidden="true" />
      <div className="glow-gold pointer-events-none fixed -bottom-52 -right-44 z-0 h-[46rem] w-[46rem]" aria-hidden="true" />
      <div className="noise-veil pointer-events-none fixed inset-0 z-[60]" aria-hidden="true" />
    </>
  );
}

/* ---------- коронарный бейдж ---------- */
export function CrownTag({ crown }: { crown: "gold" | "scarlet" }) {
  const isGold = crown === "gold";
  return (
    <span
      className={`mono-tag notch-sm inline-flex items-center gap-1.5 px-2.5 py-1 ${
        isGold
          ? "bg-gold/10 text-gold"
          : "bg-crimson/10 text-crimson"
      }`}
    >
      <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor" stroke="none">
        <path d="M4 17.5h16l-1.8-8.4-3.9 3.3L12 6.5 9.7 12.4 5.8 9.1 4 17.5Z" />
      </svg>
      {isGold ? "Золотая корона" : "Алая корона"}
    </span>
  );
}
