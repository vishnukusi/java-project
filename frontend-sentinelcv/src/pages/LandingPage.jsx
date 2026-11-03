import Hero from '@/components/landing/Hero'
import Problem from '@/components/landing/Problem'
import Workflow from '@/components/landing/Workflow'
import Capabilities from '@/components/landing/Capabilities'
import Architecture from '@/components/landing/Architecture'
import FinalCTA from '@/components/landing/FinalCTA'

export default function LandingPage() {
  return (
    <main>
      <Hero />
      <Problem />
      <Workflow />
      <Capabilities />
      <Architecture />
      <FinalCTA />
    </main>
  )
}
