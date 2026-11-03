import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Reveal, StaggerLines } from './motion'

export default function FinalCTA() {
  return (
    <section className="relative isolate overflow-hidden border-t border-white/5 py-32 sm:py-44">
      <div className="absolute inset-0 -z-10" aria-hidden="true">
        <div className="grid-bg absolute inset-0 [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]" />
        <div className="absolute left-1/2 top-1/2 h-[420px] w-[760px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand-600/20 blur-[130px]" />
      </div>
      <div className="mx-auto max-w-3xl px-5 text-center sm:px-8">
        <StaggerLines
          lines={['Make Your Vision AI', 'Verifiable.']}
          className="font-display text-4xl font-semibold tracking-tight text-white sm:text-6xl"
        />
        <Reveal delay={0.25} className="mx-auto mt-6 max-w-xl text-lg text-slate-400">
          Inspect your datasets. Evaluate your models. Monitor drift. Verify inference integrity.
        </Reveal>
        <Reveal delay={0.4} className="mt-10">
          <Link to="/dashboard"><Button size="lg" variant="light">Launch SentinelCV <ArrowRight size={18} /></Button></Link>
        </Reveal>
      </div>
    </section>
  )
}
