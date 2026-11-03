import { motion } from 'framer-motion'
import { AlertTriangle, Check, X } from 'lucide-react'
import { Reveal, SectionLabel, StaggerLines, useInViewOnce, useSequence } from './motion'

function DatasetDemo() {
  const [ref, inView] = useInViewOnce(0.5)
  const step = useSequence(inView, 18, 110, 400)
  const bad = new Set([4, 11])
  return (
    <div ref={ref}>
      <div className="grid grid-cols-6 gap-1.5">
        {Array.from({ length: 18 }).map((_, i) => {
          const checked = step > i
          const flagged = checked && bad.has(i)
          return (
            <div
              key={i}
              className={`flex aspect-square items-center justify-center rounded border transition-colors duration-300 ${
                flagged ? 'border-red-400/60 bg-red-500/15 text-red-300' : checked ? 'border-teal-400/30 bg-teal-400/10 text-teal-300' : 'border-white/10 bg-white/[0.03] text-transparent'
              }`}
            >
              {flagged ? <X size={13} /> : <Check size={13} />}
            </div>
          )
        })}
      </div>
      <p className="mt-4 font-mono text-[11px] text-slate-500">
        {step >= 18 ? <span className="text-red-300">2 files no longer match their recorded hash</span> : `hashing files… ${Math.min(step, 18)}/18`}
      </p>
    </div>
  )
}

function ModelDemo() {
  const [ref, inView] = useInViewOnce(0.5)
  const step = useSequence(inView, 3, 1000, 300)
  return (
    <div ref={ref} className="space-y-3">
      <div className="relative h-28 overflow-hidden rounded-lg border border-white/10 bg-ink-950">
        <svg viewBox="0 0 200 112" className="h-full w-full" aria-hidden="true">
          <polygon points="100,18 138,56 100,94 62,56" fill="#ef4444" fillOpacity=".85" />
          <text x="100" y="62" textAnchor="middle" fontSize="16" fontWeight="700" fill="#fff" fontFamily="Space Grotesk, sans-serif">STOP</text>
        </svg>
        <motion.div
          className="absolute inset-0 mix-blend-screen"
          style={{ backgroundImage: 'radial-gradient(rgba(255,255,255,.55) 1px, transparent 1px)', backgroundSize: '5px 5px' }}
          initial={{ opacity: 0 }} animate={{ opacity: step >= 2 ? 0.55 : 0 }} transition={{ duration: 0.8 }}
        />
      </div>
      <div className="flex items-center justify-between rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 font-mono text-[11px]">
        <span className="text-slate-500">{step >= 2 ? 'prediction after small noise' : 'prediction'}</span>
        <span className={step >= 3 ? 'text-amber-300' : 'text-teal-300'}>{step >= 3 ? 'speed-limit 0.81' : 'stop 0.97'}</span>
      </div>
      <p className="font-mono text-[11px] text-slate-500">
        {step >= 3 ? <span className="text-amber-300">behavior changed under a minor perturbation</span> : 'applying perturbation…'}
      </p>
    </div>
  )
}

function InferenceDemo() {
  const [ref, inView] = useInViewOnce(0.5)
  const step = useSequence(inView, 3, 1000, 300)
  const tampered = step >= 2
  return (
    <div ref={ref} className="space-y-3 font-mono text-[11px]">
      <div className="rounded-lg border border-white/10 bg-ink-950 p-3">
        <div className="flex justify-between"><span className="text-slate-500">output</span><span className={tampered ? 'text-red-300' : 'text-slate-200'}>{tampered ? '"no helmet" 0.94' : '"helmet" 0.94'}</span></div>
        <div className="mt-2 flex justify-between"><span className="text-slate-500">stored hash</span><span className="text-slate-300">9f2c…e41a</span></div>
        <div className="mt-2 flex justify-between"><span className="text-slate-500">recomputed</span><span className={tampered ? 'text-red-300' : 'text-slate-300'}>{step >= 3 ? (tampered ? '71b8…03dd' : '9f2c…e41a') : '…'}</span></div>
      </div>
      <div className={`flex items-center gap-2 rounded-lg border px-3 py-2 transition-colors duration-500 ${step >= 3 ? 'border-red-400/40 bg-red-500/10 text-red-300' : 'border-white/10 text-slate-500'}`}>
        {step >= 3 ? <><AlertTriangle size={13} />hash mismatch — record altered after logging</> : 'record logged'}
      </div>
      <p className="text-[10px] text-slate-600">Illustrative values.</p>
    </div>
  )
}

const problems = [
  { n: '01', title: 'Dataset integrity risks', text: 'Files can be changed, annotations can be invalid, duplicates can skew training — and nothing in the model output reveals it.', Demo: DatasetDemo },
  { n: '02', title: 'Model reliability and behavioral risks', text: 'A model file can be unsupported or structurally suspicious, and its predictions can shift under tiny input changes.', Demo: ModelDemo },
  { n: '03', title: 'Inference tampering and provenance risks', text: 'If an inference record can be edited after the fact, there is no basis to trust what the system claims it predicted.', Demo: InferenceDemo },
]

export default function Problem() {
  return (
    <section id="problem" className="relative border-t border-white/5 py-28 sm:py-36">
      <div className="mx-auto max-w-7xl px-5 sm:px-8">
        <SectionLabel>The problem</SectionLabel>
        <StaggerLines
          lines={['A prediction is not proof.']}
          className="font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl"
        />
        <Reveal delay={0.2} className="mt-6 max-w-2xl text-lg text-slate-400">
          Computer vision systems produce confident outputs whether or not their data, model or records can be trusted. Three layers can fail silently.
        </Reveal>

        <div className="mt-16 grid gap-5 lg:grid-cols-3">
          {problems.map(({ n, title, text, Demo }, i) => (
            <Reveal key={n} delay={i * 0.12} amount={0.2} className="flex flex-col rounded-2xl border border-white/10 bg-white/[0.02] p-6 transition-colors duration-300 hover:border-white/20">
              <span className="font-mono text-xs text-teal-400">{n}</span>
              <h3 className="mt-3 font-display text-xl font-semibold text-white">{title}</h3>
              <p className="mt-2 min-h-[4.5rem] text-sm leading-relaxed text-slate-400">{text}</p>
              <div className="mt-6 flex-1 border-t border-white/5 pt-6"><Demo /></div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  )
}
