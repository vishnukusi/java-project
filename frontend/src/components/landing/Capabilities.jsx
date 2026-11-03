import { motion } from 'framer-motion'
import { Check, AlertTriangle, ShieldCheck, ShieldAlert, ArrowDown } from 'lucide-react'
import { EASE, Reveal, SectionLabel, StaggerLines, useInViewOnce, useSequence } from './motion'

function Facts({ checks, why, evidence, light }) {
  const label = light ? 'text-teal-700' : 'text-teal-400'
  const body = light ? 'text-slate-600' : 'text-slate-400'
  return (
    <div className="space-y-6">
      <div>
        <p className={`font-mono text-[11px] uppercase tracking-wider ${label}`}>What it checks</p>
        <ul className={`mt-2 space-y-1.5 text-sm ${body}`}>
          {checks.map((c) => <li key={c} className="flex gap-2"><Check size={15} className={`mt-0.5 shrink-0 ${light ? 'text-teal-600' : 'text-teal-400'}`} />{c}</li>)}
        </ul>
      </div>
      <div>
        <p className={`font-mono text-[11px] uppercase tracking-wider ${label}`}>Why it matters</p>
        <p className={`mt-2 text-sm leading-relaxed ${body}`}>{why}</p>
      </div>
      <div>
        <p className={`font-mono text-[11px] uppercase tracking-wider ${label}`}>Evidence produced</p>
        <p className={`mt-2 text-sm leading-relaxed ${body}`}>{evidence}</p>
      </div>
    </div>
  )
}

function Panel({ title, children, className = '' }) {
  return (
    <div className={`overflow-hidden rounded-2xl border border-white/10 bg-ink-900 shadow-2xl ${className}`}>
      <div className="flex items-center gap-1.5 border-b border-white/5 px-4 py-2.5">
        <span className="h-2 w-2 rounded-full bg-white/15" /><span className="h-2 w-2 rounded-full bg-white/15" /><span className="h-2 w-2 rounded-full bg-white/15" />
        <span className="ml-3 font-mono text-[10px] text-slate-500">{title}</span>
      </div>
      {children}
    </div>
  )
}

/* ---------- 1. Dataset integrity ---------- */
function DatasetPanel() {
  const [ref, inView] = useInViewOnce(0.4)
  const step = useSequence(inView, 6, 550, 300)
  const files = [
    ['img_00124.jpg', 'a91f…03c2', 'ok'], ['img_00125.jpg', '5be0…77d1', 'ok'], ['img_00126.jpg', '0c3d…aa19', 'changed'],
    ['img_00127.jpg', '5be0…77d1', 'duplicate'], ['labels/00128.txt', 'e24c…10f8', 'ok'],
  ]
  const tone = { ok: 'text-teal-300', changed: 'text-red-300', duplicate: 'text-amber-300' }
  const text = { ok: 'hash matches', changed: 'differs from manifest', duplicate: 'duplicate of img_00125' }
  return (
    <Panel title="dataset audit · COCO / YOLO">
      <div ref={ref} className="p-5">
        <div className="space-y-1.5 font-mono text-[11px]">
          {files.map(([f, h, s], i) => (
            <div key={f} className={`grid grid-cols-[1.3fr_.9fr_1.4fr] gap-2 rounded px-2 py-1.5 transition-opacity duration-500 ${step > i ? 'opacity-100' : 'opacity-25'}`}>
              <span className="truncate text-slate-300">{f}</span>
              <span className="text-slate-500">{h}</span>
              <span className={tone[s]}>{step > i ? text[s] : '…'}</span>
            </div>
          ))}
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 border-t border-white/5 pt-4 text-xs">
          <div className="rounded-lg border border-white/10 p-3"><p className="text-slate-500">Annotation validation</p><p className={`mt-1 font-medium ${step >= 6 ? 'text-amber-300' : 'text-slate-600'}`}>{step >= 6 ? 'Invalid boxes found' : 'checking…'}</p></div>
          <div className="rounded-lg border border-white/10 p-3"><p className="text-slate-500">Integrity result</p><p className={`mt-1 font-medium ${step >= 6 ? 'text-red-300' : 'text-slate-600'}`}>{step >= 6 ? 'Needs attention' : 'pending'}</p></div>
        </div>
        <p className="mt-3 text-[10px] text-slate-600">Illustrative sample.</p>
      </div>
    </Panel>
  )
}

/* ---------- 2. Model security ---------- */
function ModelPanel() {
  const [ref, inView] = useInViewOnce(0.4)
  const step = useSequence(inView, 4, 500, 300)
  const bars = [['Gaussian noise', 97], ['Brightness shift', 94], ['JPEG compression', 88], ['Random occlusion', 71]]
  return (
    <Panel title="model inspection · .onnx">
      <div ref={ref} className="grid gap-5 p-5 sm:grid-cols-2">
        <div className="space-y-4 text-xs">
          <div>
            <p className="font-mono text-[10px] uppercase tracking-wider text-slate-500">Input</p>
            <div className="mt-1.5 rounded-lg border border-white/10 p-2.5 font-mono text-slate-300">images · float32<br /><span className="text-slate-500">[1, 3, 640, 640]</span></div>
          </div>
          <div>
            <p className="font-mono text-[10px] uppercase tracking-wider text-slate-500">Output</p>
            <div className="mt-1.5 rounded-lg border border-white/10 p-2.5 font-mono text-slate-300">output0 · float32<br /><span className="text-slate-500">[1, 10, 8400]</span></div>
          </div>
          <div className="flex items-center justify-between rounded-lg border border-white/10 p-2.5">
            <span className="text-slate-400">Repeatability</span>
            <span className={`font-mono ${step >= 1 ? 'text-teal-300' : 'text-slate-600'}`}>{step >= 1 ? '20/20 identical' : '…'}</span>
          </div>
        </div>
        <div>
          <p className="font-mono text-[10px] uppercase tracking-wider text-slate-500">Perturbation stability</p>
          <ul className="mt-2 space-y-3">
            {bars.map(([n, v], i) => (
              <li key={n}>
                <div className="mb-1 flex justify-between text-[11px]"><span className="text-slate-300">{n}</span><span className="font-mono text-slate-500">{step > i ? `${v}%` : '—'}</span></div>
                <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
                  <motion.div
                    className={`h-full rounded-full ${v >= 90 ? 'bg-teal-400' : v >= 75 ? 'bg-amber-400' : 'bg-red-400'}`}
                    initial={{ width: 0 }} animate={{ width: step > i ? `${v}%` : 0 }} transition={{ duration: 0.8, ease: EASE }}
                  />
                </div>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-[10px] text-slate-600">Illustrative sample.</p>
        </div>
      </div>
    </Panel>
  )
}

/* ---------- 3. Drift ---------- */
const gaussPath = (center, spread, w = 600, h = 160) => {
  const pts = Array.from({ length: 60 }, (_, i) => {
    const x = i / 59
    return [x * w, h - h * 0.92 * Math.exp(-((x - center) ** 2) / (2 * spread ** 2))]
  })
  return `M${pts.map((p) => p.join(',')).join(' L')} L${w},${h} L0,${h}Z`
}

function DriftPanel() {
  const [ref, inView] = useInViewOnce(0.4)
  const step = useSequence(inView, 3, 900, 400)
  const psi = ['0.02', '0.11', '0.31'][Math.max(0, step - 1)] ?? '0.02'
  const ks = ['0.03', '0.12', '0.24'][Math.max(0, step - 1)] ?? '0.03'
  const severity = step >= 3 ? ['Significant', 'text-red-300'] : step >= 2 ? ['Moderate', 'text-amber-300'] : ['Stable', 'text-teal-300']
  return (
    <Panel title="drift · reference vs current" className="w-full">
      <div ref={ref} className="grid gap-6 p-6 md:grid-cols-[1fr_220px]">
        <div>
          <svg viewBox="0 0 600 160" className="h-auto w-full" role="img" aria-label="Reference and current distribution curves">
            <path d={gaussPath(0.45, 0.13)} fill="#94a3b8" fillOpacity=".18" stroke="#94a3b8" strokeWidth="1.5" />
            <motion.path
              d={gaussPath(0.45, 0.13)} fill="#38bdf8" fillOpacity=".22" stroke="#38bdf8" strokeWidth="1.5"
              initial={{ x: 0 }} animate={{ x: step >= 3 ? 130 : step >= 2 ? 55 : 0 }} transition={{ duration: 1.1, ease: EASE }}
            />
          </svg>
          <div className="mt-2 flex gap-5 text-[11px] text-slate-400">
            <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-slate-400" />Reference</span>
            <span className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-full bg-brand-400" />Current</span>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3 md:grid-cols-1">
          {[['PSI', psi], ['KS statistic', ks]].map(([k, v]) => (
            <div key={k} className="rounded-lg border border-white/10 p-3"><p className="text-[11px] text-slate-500">{k}</p><p className="mt-1 font-display text-2xl font-semibold tabular-nums text-white">{v}</p></div>
          ))}
          <div className="rounded-lg border border-white/10 p-3"><p className="text-[11px] text-slate-500">Severity</p><p className={`mt-1 font-display text-xl font-semibold ${severity[1]}`}>{severity[0]}</p></div>
        </div>
      </div>
      <p className="px-6 pb-4 text-[10px] text-slate-600">Illustrative animation, not real data.</p>
    </Panel>
  )
}

/* ---------- 4. Provenance ---------- */
function ProvenancePanel() {
  const [ref, inView] = useInViewOnce(0.4)
  const step = useSequence(inView, 5, 650, 300)
  const recs = [['#0', '4d1a…c9'], ['#1', '8e07…2b'], ['#2', 'f3a9…71'], ['#3', '12cc…e0']]
  return (
    <Panel title="provenance chain · SHA-256 + HMAC">
      <div ref={ref} className="p-5">
        <ol className="space-y-0">
          {recs.map(([n, h], i) => {
            const broken = i === 3
            const done = step > i
            return (
              <li key={n}>
                <div className={`flex items-center justify-between rounded-lg border px-3 py-2.5 font-mono text-[11px] transition-colors duration-500 ${
                  !done ? 'border-white/10 text-slate-600' : broken ? 'border-red-400/40 bg-red-500/10 text-red-300' : 'border-teal-400/30 bg-teal-400/5 text-teal-300'}`}>
                  <span>record {n}</span>
                  <span className="text-slate-500">{h}</span>
                  <span className="flex items-center gap-1">{done ? (broken ? <><ShieldAlert size={13} />tampered</> : <><ShieldCheck size={13} />linked</>) : '…'}</span>
                </div>
                {i < recs.length - 1 && <div className="flex justify-center py-1 text-slate-600"><ArrowDown size={14} /></div>}
              </li>
            )
          })}
        </ol>
        <div className={`mt-4 flex items-center gap-2 rounded-lg border px-3 py-2 text-xs transition-colors duration-500 ${step >= 5 ? 'border-red-400/40 bg-red-500/10 text-red-300' : 'border-white/10 text-slate-500'}`}>
          <AlertTriangle size={14} />{step >= 5 ? 'Chain integrity: break detected at record #3' : 'verifying chain…'}
        </div>
        <p className="mt-3 text-[10px] text-slate-600">Illustrative sample.</p>
      </div>
    </Panel>
  )
}

function Heading({ label, lines, light }) {
  return (
    <>
      <SectionLabel tone={light ? 'text-teal-700' : 'text-teal-400'}>{label}</SectionLabel>
      <StaggerLines lines={lines} className={`font-display text-3xl font-semibold tracking-tight sm:text-4xl ${light ? 'text-ink-900' : 'text-white'}`} />
    </>
  )
}

export default function Capabilities() {
  return (
    <section id="capabilities" className="border-t border-white/5">
      <div className="mx-auto max-w-7xl px-5 pt-28 sm:px-8 sm:pt-36">
        <SectionLabel>Core capabilities</SectionLabel>
        <StaggerLines lines={['Four ways to', 'verify a vision system.']} className="font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl" />
      </div>

      {/* Dataset — dark, text left / panel right */}
      <div className="py-24 sm:py-32">
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 sm:px-8 lg:grid-cols-2">
          <div>
            <Heading label="01 · Dataset integrity" lines={['Know the data', 'before you trust the model.']} />
            <Reveal delay={0.15} className="mt-8">
              <Facts
                checks={['Parses COCO and YOLO datasets', 'Validates annotations and inspects structure', 'Detects duplicate data', 'Computes integrity hashes']}
                why="Corrupted, modified or duplicated training data silently degrades every model built on it."
                evidence="Hash manifest, annotation validation results, duplicate groups and dataset findings."
              />
            </Reveal>
          </div>
          <Reveal y={40}><DatasetPanel /></Reveal>
        </div>
      </div>

      {/* Model — light band, panel left / text right */}
      <div className="bg-slate-100 py-24 sm:py-32">
        <div className="mx-auto grid max-w-7xl items-center gap-12 px-5 sm:px-8 lg:grid-cols-2">
          <Reveal y={40} className="order-2 lg:order-1"><ModelPanel /></Reveal>
          <div className="order-1 lg:order-2">
            <Heading light label="02 · Model security" lines={['Inspect the model.', 'Test how it behaves.']} />
            <Reveal delay={0.15} className="mt-8">
              <Facts
                light
                checks={['Inspects ONNX structure and metadata', 'Reads input/output definitions', 'Checks repeatability across runs', 'Runs perturbation-based behavioral tests']}
                why="A model can be structurally unusual or unstable long before it fails in production."
                evidence="Inspection results, repeatability and perturbation outcomes, findings and a final decision."
              />
            </Reveal>
          </div>
        </div>
      </div>

      {/* Drift — dark, wide panel above, text in columns below */}
      <div className="py-24 sm:py-32">
        <div className="mx-auto max-w-5xl px-5 sm:px-8">
          <Heading label="03 · Distribution drift" lines={['Detect when the world', 'stops matching the training data.']} />
          <Reveal y={40} className="mt-12"><DriftPanel /></Reveal>
          <Reveal delay={0.1} className="mt-12 grid gap-8 md:grid-cols-3">
            {[
              ['What it checks', 'Reference vs current distributions using the Population Stability Index (PSI) and Kolmogorov–Smirnov (KS) testing, with feature-level analysis where supported.'],
              ['Why it matters', 'When input data shifts, a model that once passed review can quietly become unreliable.'],
              ['Evidence produced', 'PSI and KS metrics, per-feature results and a drift severity assessment.'],
            ].map(([k, v]) => (
              <div key={k}><p className="font-mono text-[11px] uppercase tracking-wider text-teal-400">{k}</p><p className="mt-2 text-sm leading-relaxed text-slate-400">{v}</p></div>
            ))}
          </Reveal>
        </div>
      </div>

      {/* Provenance — darkest, text left / chain right */}
      <div className="relative overflow-hidden bg-ink-950 py-24 sm:py-32">
        <div className="absolute inset-0 grid-bg opacity-60 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" aria-hidden="true" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-5 sm:px-8 lg:grid-cols-2">
          <div>
            <Heading label="04 · Inference provenance" lines={['Prove what the', 'system actually predicted.']} />
            <Reveal delay={0.15} className="mt-8">
              <Facts
                checks={['SHA-256 hashing of inference records', 'HMAC-based verification', 'Cryptographic linking between records', 'Chain integrity and tampering detection']}
                why="If a record can be edited unnoticed, its predictions cannot be used as evidence."
                evidence="Verification results per record, chain integrity status and tampering findings."
              />
            </Reveal>
          </div>
          <Reveal y={40}><ProvenancePanel /></Reveal>
        </div>
      </div>
    </section>
  )
}
