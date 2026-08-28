import { useMemo, useState } from "react";
import { CLASSES } from "./data";
import Nav from "./components/Nav";
import Masthead from "./components/Masthead";
import ProcessSection from "./components/ProcessSection";
import ArchitectureSection from "./components/ArchitectureSection";
import LedgerSection from "./components/LedgerSection";
import RulesSection from "./components/RulesSection";
import ClassesSection from "./components/ClassesSection";
import RoadmapSection from "./components/RoadmapSection";
import { AmbientBackdrop, CrackDivider, Marquee } from "./components/Bits";

export default function App() {
  const [classId, setClassId] = useState(1);
  const cls = useMemo(() => CLASSES.find((c) => c.id === classId) ?? CLASSES[0], [classId]);

  return (
    <div className="relative min-h-screen bg-ink-950 font-body text-parch antialiased">
      <AmbientBackdrop />
      <Nav />

      <main className="relative z-10">
        <Masthead cls={cls} onClassChange={setClassId} />
        <Marquee />
        <ProcessSection />
        <CrackDivider />
        <ArchitectureSection />
        <LedgerSection />
        <CrackDivider flip />
        <RulesSection />
        <ClassesSection selectedId={classId} onSelect={setClassId} />
        <RoadmapSection />
      </main>
    </div>
  );
}
