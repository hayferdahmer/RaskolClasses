import { useEffect, useRef, useState } from "react";

/** true, если пользователь просит меньше движения */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () =>
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = () => setReduced(mq.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);
  return reduced;
}

/** одноразовый IntersectionObserver: элемент попал во вьюпорт */
export function useInView<T extends HTMLElement = HTMLDivElement>(
  threshold = 0.16,
  rootMargin = "0px 0px -6% 0px",
) {
  const ref = useRef<T | null>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (typeof IntersectionObserver === "undefined") {
      setInView(true);
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            setInView(true);
            io.disconnect();
            break;
          }
        }
      },
      { threshold, rootMargin },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [threshold, rootMargin]);
  return { ref, inView };
}

/** плавный счётчик до target, когда active = true */
export function useCountUp(target: number, active: boolean, duration = 1300): number {
  const reduced = usePrefersReducedMotion();
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (!active) return;
    if (reduced) {
      setValue(target);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      setValue(Math.round(target * eased));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, active, duration, reduced]);
  return value;
}

const GLYPHS = "▓▒░/<>\\+=*#%&@RASKOLДВЕКОНЫ";

/** эффект «расшифровки» строки */
export function useScramble(text: string, active: boolean, speed = 26): string {
  const reduced = usePrefersReducedMotion();
  const [out, setOut] = useState(() =>
    reduced ? text : text.replace(/[^ ]/g, "░"),
  );
  useEffect(() => {
    if (reduced) {
      setOut(text);
      return;
    }
    if (!active) return;
    let frame = 0;
    let raf = 0;
    let last = 0;
    const step = (t: number) => {
      if (t - last >= speed) {
        last = t;
        frame += 1;
        const fixed = Math.floor(frame / 2.1);
        let s = "";
        for (let i = 0; i < text.length; i += 1) {
          const ch = text[i];
          if (ch === " ") {
            s += " ";
            continue;
          }
          s += i < fixed ? ch : GLYPHS[(Math.random() * GLYPHS.length) | 0];
        }
        if (fixed >= text.length) {
          setOut(text);
          return;
        }
        setOut(s);
      }
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [text, active, reduced, speed]);
  return out;
}

/** «сейчас» с интервалом — для тиков кулдаунов и регена */
export function useTicker(intervalMs: number, running = true): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs, running]);
  return now;
}
