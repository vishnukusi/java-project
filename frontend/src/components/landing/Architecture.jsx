import { motion } from 'framer-motion'
import { Monitor, Server, Database, Cpu, Activity, Link2, Gauge, HardDrive, Binary, KeyRound, Boxes } from 'lucide-react'
import { EASE, Reveal, SectionLabel, StaggerLines } from './motion'

function Tier({ label, tone, children, delay = 0 }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.4 }}
      transition={{ duration: 0.8, delay, ease: EASE }}
      className={`rounded-2xl border p-5 sm:p-6 ${tone}`}
    >
      <p className="mb-4 font-mono text-[11px] uppercase tracking-[0.18em] text-slate-400">{label}</p>
      {children}
    </motion.div>
  )
}

function Chip({ icon: Icon, children, sub }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] px-4 py-3">
      <Icon size={18} className="shrink-0 text-teal-300" />
      <div><p className="text-sm font-medium text-white">{children}</p>{sub && <p className="text-[11px] text-slate-500">{sub}</p>}</div>
    </div>
  )
}

function Connector({ delay = 0 }) {
  return (
    <div className="flex justify-center py-1" aria-hidden="true">
      <svg width="24" height="44" viewBox="0 0 24 44">
        <motion.line
          x1="12" y1="0" x2="12" y2="36" stroke="#38bdf8" strokeWidth="1.5" strokeDasharray="3 4"
          initial={{ pathLength: 0 }} whileInView={{ pathLength: 1 }} viewport={{ once: true, amount: 1 }} transition={{ duration: 0.8, delay, ease: EASE }}
        />
        <motion.path
          d="M6 32 L12 40 L18 32" fill="none" stroke="#38bdf8" strokeWidth="1.5"
          initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true, amount: 1 }} transition={{ duration: 0.4, delay: delay + 0.6 }}
        />
      </svg>
    </div>
  )
}

export default function Architecture() {
  return (
    <section id="architecture" className="border-t border-white/5 py-28 sm:py-36">
      <div className="mx-auto max-w-4xl px-5 sm:px-8">
        <SectionLabel>Technical architecture</SectionLabel>
        <StaggerLines lines={['A React front end', 'on a Spring Boot core.']} className="font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl" />
        <Reveal delay={0.2} className="mt-6 max-w-2xl text-lg text-slate-400">
          The assessment logic lives in the existing Java backend. The interface presents evidence and never performs audits or cryptography itself.
        </Reveal>

        <div className="mt-16">
          <Tier label="Frontend" tone="border-brand-400/30 bg-brand-500/[0.06]">
            <Chip icon={Monitor} sub="Landing page and dashboard">React Frontend</Chip>
          </Tier>
          <Connector />
          <Tier label="Backend" tone="border-teal-400/30 bg-teal-500/[0.05]" delay={0.1}>
            <div className="mb-3"><Chip icon={Server} sub="Java 21 · Maven">Spring Boot Java Backend</Chip></div>
            <p className="mb-3 mt-5 font-mono text-[10px] uppercase tracking-wider text-slate-500">Core assessment modules</p>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Chip icon={Database}>Dataset Auditing</Chip>
              <Chip icon={Cpu}>Model Security</Chip>
              <Chip icon={Activity}>Drift Detection</Chip>
              <Chip icon={Link2}>Inference Provenance</Chip>
              <Chip icon={Gauge}>Risk Assessment</Chip>
            </div>
          </Tier>
          <Connector delay={0.1} />
          <Tier label="Infrastructure" tone="border-white/10 bg-white/[0.02]" delay={0.15}>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Chip icon={HardDrive}>SQLite</Chip>
              <Chip icon={Binary}>ONNX Runtime</Chip>
              <Chip icon={Boxes}>DJL</Chip>
              <Chip icon={KeyRound}>Cryptographic Verification</Chip>
            </div>
          </Tier>
        </div>
      </div>
    </section>
  )
}
