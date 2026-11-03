import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { motion, useScroll, useTransform } from 'framer-motion'
import { ArrowRight, Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EASE, useSequence } from './motion'

const layers = ['Dataset integrity', 'Model security', 'Distribution drift', 'Inference provenance']

function VisionFrame() {
  const step = useSequence(true, layers.length, 700, 1200)
  return (
    <div className="relative w-full max-w-[520px]">
      <div className="absolute -inset-6 rounded-[2rem] bg-brand-500/10 blur-3xl" aria-hidden="true" />
      <div className="relative overflow-hidden rounded-2xl border border-white/10 bg-ink-900/80 shadow-2xl">
        <div className="flex items-center gap-1.5 border-b border-white/5 px-4 py-2.5">
          <span className="h-2 w-2 rounded-full bg-white/15" /><span className="h-2 w-2 rounded-full bg-white/15" /><span className="h-2 w-2 rounded-full bg-white/15" />
          <span className="ml-3 font-mono text-[10px] text-slate-500">inference · frame_0481.jpg</span>
        </div>

        {/* abstract camera frame with detections */}
        <div className="relative aspect-[4/3] overflow-hidden bg-gradient-to-b from-ink-800 to-ink-950">
          <svg viewBox="0 0 400 300" className="absolute inset-0 h-full w-full" aria-hidden="true">
            <defs>
              <pattern id="hg" width="20" height="20" patternUnits="userSpaceOnUse"><path d="M20 0H0V20" fill="none" stroke="#38bdf8" strokeOpacity=".07" /></pattern>
            </defs>
            <rect width="400" height="300" fill="url(#hg)" />
            <path d="M0 210 L400 190 L400 300 L0 300Z" fill="#0b1220" />
            {/* simplified figures */}
            <g fill="#1a2540" stroke="#243152">
              <rect x="70" y="120" width="44" height="100" rx="8" />
              <circle cx="92" cy="106" r="14" />
              <rect x="230" y="140" width="38" height="82" rx="8" />
              <circle cx="249" cy="126" r="12" />
            </g>
            {[
              { x: 62, y: 88, w: 60, h: 140, label: 'person 0.94', d: 0.4 },
              { x: 222, y: 108, w: 54, h: 122, label: 'person 0.88', d: 0.7 },
              { x: 76, y: 90, w: 32, h: 26, label: 'helmet 0.91', d: 1.0, teal: true },
            ].map((b) => (
              <motion.g key={b.label} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: b.d + 0.6, duration: 0.6 }}>
                <rect x={b.x} y={b.y} width={b.w} height={b.h} fill="none" stroke={b.teal ? '#2dd4bf' : '#38bdf8'} strokeWidth="1.5" rx="3" />
                <rect x={b.x + (b.teal ? b.w + 4 : 0)} y={b.teal ? b.y : b.y - 14} width={b.label.length * 5.4 + 8} height="14" fill={b.teal ? '#2dd4bf' : '#38bdf8'} rx="2" />
                <text x={b.x + (b.teal ? b.w + 4 : 0) + 4} y={b.teal ? b.y + 10 : b.y - 4} fontSize="8.5" fontFamily="JetBrains Mono, monospace" fill="#0b1220" fontWeight="600">{b.label}</text>
              </motion.g>
            ))}
          </svg>
          <div className="absolute inset-x-0 h-px animate-scan bg-gradient-to-r from-transparent via-teal-400 to-transparent shadow-[0_0_16px_2px_rgba(45,212,191,.5)]" aria-hidden="true" />
        </div>

        {/* verification layers */}
        <ul className="divide-y divide-white/5 border-t border-white/5">
          {layers.map((l, i) => {
            const done = step > i
            return (
              <li key={l} className="flex items-center justify-between px-4 py-2.5">
                <span className="text-xs text-slate-300">{l}</span>
                <span className={`flex items-center gap-1.5 font-mono text-[10px] transition-colors duration-500 ${done ? 'text-teal-400' : 'text-slate-600'}`}>
                  {done ? <><Check size={12} />inspected</> : <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-slate-600" />}
                </span>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}

export default function Hero() {
  const ref = useRef(null)
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start start', 'end start'] })
  const bgY = useTransform(scrollYProgress, [0, 1], ['0%', '25%'])
  const fade = useTransform(scrollYProgress, [0, 0.8], [1, 0])

  const line = (i) => ({ initial: { y: '105%' }, animate: { y: 0 }, transition: { duration: 1, delay: 0.15 + i * 0.14, ease: EASE } })

  return (
    <section ref={ref} className="relative isolate overflow-hidden pt-28 sm:pt-32">
      <motion.div style={{ y: bgY }} className="absolute inset-0 -z-10" aria-hidden="true">
        <div className="grid-bg absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black_30%,transparent_75%)]" />
        <div className="absolute left-1/2 top-0 h-[520px] w-[900px] -translate-x-1/2 rounded-full bg-brand-600/20 blur-[140px]" />
        <div className="absolute right-0 top-40 h-[320px] w-[420px] rounded-full bg-teal-500/10 blur-[120px]" />
      </motion.div>

      <motion.div style={{ opacity: fade }} className="mx-auto grid min-h-[calc(100vh-8rem)] max-w-7xl items-center gap-14 px-5 pb-20 sm:px-8 lg:grid-cols-[1.3fr_1fr]">
        <div>
          <motion.p
            initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, ease: EASE }}
            className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 font-mono text-[11px] uppercase tracking-widest text-teal-300"
          >
            <span className="h-1.5 w-1.5 rounded-full bg-teal-400" /> Offline-first · CV security assessment
          </motion.p>

          <h1 className="font-display text-[2.4rem] font-semibold leading-[1.06] tracking-tight text-white sm:whitespace-nowrap sm:text-5xl lg:text-[3.5rem]">
            <span className="block overflow-hidden pb-[0.1em]"><motion.span className="block" {...line(0)}>Trust Your Vision AI.</motion.span></span>
            <span className="block overflow-hidden pb-[0.1em]"><motion.span className="block bg-gradient-to-r from-brand-400 to-teal-400 bg-clip-text text-transparent" {...line(1)}>Verify Every Layer.</motion.span></span>
          </h1>

          <motion.p
            initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.9, delay: 0.55, ease: EASE }}
            className="mt-7 max-w-xl text-base leading-relaxed text-slate-400 sm:text-lg"
          >
            SentinelCV is an offline-first security assessment platform that evaluates computer vision datasets, models, distribution drift and inference provenance.
          </motion.p>

          <motion.div
            initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.9, delay: 0.7, ease: EASE }}
            className="mt-9 flex flex-wrap gap-3"
          >
            <a href="#problem"><Button size="lg" variant="light">Explore SentinelCV <ArrowRight size={18} /></Button></a>
            <Link to="/dashboard"><Button size="lg" variant="glass">Open Dashboard</Button></Link>
          </motion.div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 28 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 1.1, delay: 0.4, ease: EASE }}
          className="flex justify-center lg:justify-end"
        >
          <VisionFrame />
        </motion.div>
      </motion.div>
    </section>
  )
}
