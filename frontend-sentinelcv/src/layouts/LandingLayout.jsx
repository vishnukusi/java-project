import { useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { Logo } from '@/components/common/Logo'
import { Button } from '@/components/ui/button'
import { cn } from '@/utils/cn'

const links = [
  { href: '#problem', label: 'Problem' },
  { href: '#workflow', label: 'Workflow' },
  { href: '#capabilities', label: 'Capabilities' },
  { href: '#architecture', label: 'Architecture' },
]

export default function LandingLayout() {
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <div className="min-h-screen bg-ink-950 text-slate-200">
      <header
        className={cn(
          'fixed inset-x-0 top-0 z-50 transition-colors duration-300',
          scrolled ? 'border-b border-white/5 bg-ink-950/80 backdrop-blur-md' : 'border-b border-transparent',
        )}
      >
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-5 sm:px-8">
          <Link to="/" aria-label="SentinelCV home"><Logo /></Link>
          <nav className="hidden items-center gap-7 md:flex" aria-label="Sections">
            {links.map((l) => (
              <a key={l.href} href={l.href} className="text-sm text-slate-400 transition-colors hover:text-white">{l.label}</a>
            ))}
          </nav>
          <Link to="/dashboard"><Button variant="glass" size="sm">Open Dashboard</Button></Link>
        </div>
      </header>
      <Outlet />
      <footer className="border-t border-white/5 py-10">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-5 text-xs text-slate-500 sm:flex-row sm:px-8">
          <Logo textClass="text-slate-300" />
          <p>Offline-first security assessment for computer vision systems.</p>
        </div>
      </footer>
    </div>
  )
}
