import { useEffect, useRef, useState } from 'react'
import { motion, useInView } from 'framer-motion'

// Shared easing: a gentle expo-out curve for all landing reveals.
export const EASE = [0.22, 1, 0.36, 1]

/** Fade + rise reveal triggered once when scrolled into view. Transform/opacity only (GPU-friendly). */
export function Reveal({ children, delay = 0, y = 24, className, as = 'div', amount = 0.3 }) {
  const Tag = motion[as]
  return (
    <Tag
      className={className}
      initial={{ opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount }}
      transition={{ duration: 0.8, delay, ease: EASE }}
    >
      {children}
    </Tag>
  )
}

/** Staggered line-by-line headline reveal. `lines` is an array of strings. */
export function StaggerLines({ lines, className, lineClass, delay = 0 }) {
  // Observe the (unclipped) heading itself; the inner lines are translated out of their
  // overflow-hidden wrappers, so observing them directly would never report as visible.
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, amount: 0.6 })
  return (
    <h2 ref={ref} className={className}>
      {lines.map((line, i) => (
        <span key={i} className="block overflow-hidden pb-[0.12em]">
          <motion.span
            className={`block ${lineClass || ''}`}
            initial={{ y: '105%' }}
            animate={{ y: inView ? 0 : '105%' }}
            transition={{ duration: 0.9, delay: delay + i * 0.12, ease: EASE }}
          >
            {line}
          </motion.span>
        </span>
      ))}
    </h2>
  )
}

/** Returns [ref, inView] — one-shot in-view flag for demos that run a short scripted sequence. */
export function useInViewOnce(amount = 0.4) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, amount })
  return [ref, inView]
}

/** Runs `steps` times at `interval` ms once `active` becomes true; returns the current step (0..steps). */
export function useSequence(active, steps, interval = 600, startDelay = 300) {
  const [step, setStep] = useState(0)
  useEffect(() => {
    if (!active) return undefined
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (reduce) { setStep(steps); return undefined }
    const timers = []
    for (let i = 1; i <= steps; i++) timers.push(setTimeout(() => setStep(i), startDelay + i * interval))
    return () => timers.forEach(clearTimeout)
  }, [active, steps, interval, startDelay])
  return step
}

export function SectionLabel({ children, tone = 'text-teal-400' }) {
  return <p className={`mb-4 font-mono text-xs uppercase tracking-[0.2em] ${tone}`}>{children}</p>
}
