import { useRef } from 'react'
import { motion, useScroll, useSpring, useTransform } from 'framer-motion'
import { Database, Cpu, Activity, Link2, ShieldCheck } from 'lucide-react'
import { Reveal, SectionLabel, StaggerLines } from './motion'

const stages = [
  { icon: Database, name: 'Dataset', enters: 'COCO or YOLO dataset', evaluates: 'Structure, annotations, duplicates, file hashes', evidence: 'Integrity results and findings' },
  { icon: Cpu, name: 'Model', enters: 'ONNX model file', evaluates: 'Metadata, compatibility, repeatability, perturbation behavior', evidence: 'Model findings and a decision' },
  { icon: Activity, name: 'Drift', enters: 'Reference and current data', evaluates: 'PSI and Kolmogorov–Smirnov comparison', evidence: 'Drift metrics and severity' },
  { icon: Link2, name: 'Inference', enters: 'Inference records', evaluates: 'SHA-256 hashing and HMAC over each record, linked in a chain', evidence: 'Cryptographically linked records' },
  { icon: ShieldCheck, name: 'Verification', enters: 'Stored provenance chain', evaluates: 'Chain integrity and tampering checks', evidence: 'Verification results · ACCEPT / REVIEW / QUARANTINE' },
]

function Stage({ s, i, progress }) {
  const start = i / stages.length
  const active = useTransform(progress, [start - 0.08, start + 0.06], [0, 1])
  const Icon = s.icon
  return (
    <li className="relative pl-16 sm:pl-20">
      <motion.div
        style={{ opacity: useTransform(active, [0, 1], [0.35, 1]), scale: useTransform(active, [0, 1], [0.92, 1]) }}
        className="absolute left-0 top-0 flex h-12 w-12 items-center justify-center rounded-xl border border-teal-400/40 bg-ink-900 text-teal-300 sm:h-14 sm:w-14"
      >
        <Icon size={22} />
      </motion.div>
      <motion.div style={{ opacity: useTransform(active, [0, 1], [0.4, 1]) }}>
        <p className="font-mono text-xs text-slate-500">STAGE {String(i + 1).padStart(2, '0')}</p>
        <h3 className="mt-1 font-display text-2xl font-semibold text-white">{s.name}</h3>
        <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-3">
          {[['Enters', s.enters], ['SentinelCV evaluates', s.evaluates], ['Evidence produced', s.evidence]].map(([k, v]) => (
            <div key={k} className="rounded-lg border border-white/10 bg-white/[0.02] p-3.5">
              <dt className="font-mono text-[10px] uppercase tracking-wider text-teal-400/80">{k}</dt>
              <dd className="mt-1.5 text-slate-300">{v}</dd>
            </div>
          ))}
        </dl>
      </motion.div>
    </li>
  )
}

export default function Workflow() {
  const ref = useRef(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start 70%', 'end 60%'] })
  const progress = useSpring(scrollYProgress, { stiffness: 90, damping: 24, mass: 0.4 })
  const lineScale = useTransform(progress, [0, 1], [0, 1])

  return (
    <section id="workflow" className="relative border-t border-white/5 bg-ink-900/40 py-28 sm:py-36">
      <div className="mx-auto max-w-5xl px-5 sm:px-8">
        <SectionLabel>How it works</SectionLabel>
        <StaggerLines lines={['Five stages.', 'One chain of evidence.']} className="font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl" />
        <Reveal delay={0.2} className="mt-6 max-w-2xl text-lg text-slate-400">
          Dataset → Model → Drift → Inference → Verification. Each stage inspects an asset and leaves evidence behind.
        </Reveal>

        <div ref={ref} className="relative mt-20">
          <div className="absolute bottom-6 left-6 top-6 w-px bg-white/10 sm:left-7" aria-hidden="true" />
          <motion.div
            className="absolute bottom-6 left-6 top-6 w-px origin-top bg-gradient-to-b from-brand-400 to-teal-400 sm:left-7"
            style={{ scaleY: lineScale }} aria-hidden="true"
          />
          <ol className="space-y-14">
            {stages.map((s, i) => <Stage key={s.name} s={s} i={i} progress={progress} />)}
          </ol>
        </div>
      </div>
    </section>
  )
}
