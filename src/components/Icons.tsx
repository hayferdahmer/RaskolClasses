import type { SVGProps } from "react";
import type { Crown } from "../data";

type P = SVGProps<SVGSVGElement> & { size?: number };

const base = (size: number | undefined, props: P) => ({
  width: size ?? 20,
  height: size ?? 20,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.7,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  ...props,
});

/* Двойная корона-монограмма: две короны, наложенные со сдвигом */
export function SplitCrown({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M2.5 17.5h11l-1.4-6.4-3 2.6-1.6-4.9-1.6 4.9-3-2.6L2.5 17.5Z" />
      <path d="M10.5 20h11l-1.4-6.4-3 2.6-1.6-4.9-1.6 4.9-3-2.6L10.5 20Z" opacity="0.55" />
    </svg>
  );
}

export function CrownIcon({ crown, size, ...p }: P & { crown?: Crown }) {
  const two = crown === "scarlet";
  return (
    <svg {...base(size, p)}>
      <path d="M4 17.5h16l-1.8-8.4-3.9 3.3L12 6.5 9.7 12.4 5.8 9.1 4 17.5Z" />
      <path d="M4.6 20.5h14.8" />
      {two && <path d="M12 6.5V3.8" strokeDasharray="1.5 2" />}
    </svg>
  );
}

export function SwordIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M19.5 4.5 9 15" />
      <path d="M19.5 4.5 15 4l-.6 4.4" opacity="0.6" />
      <path d="M7 13.5 10.5 17" />
      <path d="M5 19l1.5-1.5M4 20l1-1" />
    </svg>
  );
}

export function ShieldIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M12 3.5 5.5 6v6c0 4.2 2.8 7 6.5 8.5 3.7-1.5 6.5-4.3 6.5-8.5V6L12 3.5Z" />
      <path d="M12 8v6" opacity="0.6" />
    </svg>
  );
}

export function OrbIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <circle cx="12" cy="12" r="7.5" />
      <circle cx="12" cy="12" r="2.6" opacity="0.8" />
      <path d="M12 4.5V2.8M12 21.2v-1.7M4.5 12H2.8M21.2 12h-1.7" opacity="0.5" />
    </svg>
  );
}

export function MaskIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M4.5 5.5c2.5 1 5 1.5 7.5 1.5s5-.5 7.5-1.5c.6 4.6-.4 8.5-2.6 11-1.7 1.9-3.4 3-4.9 3s-3.2-1.1-4.9-3c-2.2-2.5-3.2-6.4-2.6-11Z" />
      <path d="M8.5 11.5h2M13.5 11.5h2" />
    </svg>
  );
}

export function HerbIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M12 20.5v-7" />
      <path d="M12 13.5c0-3.5-2.5-6-6.5-6C5.5 11 8 13.5 12 13.5Z" />
      <path d="M12 10.5c0-3 2.2-5.2 5.7-5.2 0 3-2.2 5.2-5.7 5.2Z" opacity="0.7" />
    </svg>
  );
}

export function FolderIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M3.5 6.5h5l1.8 2h10.2v10a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-12Z" />
    </svg>
  );
}

export function FileIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M6 3.5h8L18 7.5v13H6v-17Z" />
      <path d="M14 3.5v4h4" opacity="0.6" />
    </svg>
  );
}

export function TerminalIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <rect x="3" y="4.5" width="18" height="15" rx="1" />
      <path d="m7 9 3 3-3 3M12.5 15H17" />
    </svg>
  );
}

export function BoltIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M13 2.5 5 13.5h5L10.5 21.5 19 10h-5.5L13 2.5Z" />
    </svg>
  );
}

export function HourglassIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M6.5 3.5h11M6.5 20.5h11" />
      <path d="M8 3.5v3.2L12 11l4-4.3V3.5M8 20.5v-3.2L12 13l4 4.3v3.2" />
    </svg>
  );
}

export function CheckIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}

export function CrossIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}

export function ArrowUpRight({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M7 17 17 7M9 7h8v8" />
    </svg>
  );
}

export function GearIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v2.4M12 18.1v2.4M3.5 12h2.4M18.1 12h2.4M6 6l1.7 1.7M16.3 16.3 18 18M18 6l-1.7 1.7M7.7 16.3 6 18" />
    </svg>
  );
}

export function PulseIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M2.5 12h4l2.5-6.5 4 13L15.5 12h6" />
    </svg>
  );
}

export function HookIcon({ size, ...p }: P) {
  return (
    <svg {...base(size, p)}>
      <path d="M12 3.5v9a4 4 0 0 0 8 0V11" />
      <circle cx="12" cy="3.5" r="1" fill="currentColor" stroke="none" />
      <path d="M4 20.5v-6a3 3 0 0 1 6 0" opacity="0.6" />
    </svg>
  );
}

export const ABILITY_ICON: Record<string, (p: P) => ReturnType<typeof SwordIcon>> = {
  sword: SwordIcon,
  shield: ShieldIcon,
  orb: OrbIcon,
  mask: MaskIcon,
  herb: HerbIcon,
};
