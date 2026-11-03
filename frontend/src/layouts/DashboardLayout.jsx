import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import {
  LayoutDashboard, Database, Cpu, Activity, Link2, FileText, PanelLeftClose, PanelLeftOpen, Menu, ChevronRight, ArrowLeft,
} from 'lucide-react'
import { Logo } from '@/components/common/Logo'
import { MockBanner } from '@/components/common/States'
import { cn } from '@/utils/cn'

export const NAV = [
  { to: '/dashboard', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/dashboard/datasets', label: 'Datasets', icon: Database },
  { to: '/dashboard/models', label: 'Models', icon: Cpu },
  { to: '/dashboard/drift', label: 'Drift Monitoring', icon: Activity },
  { to: '/dashboard/provenance', label: 'Inference Provenance', icon: Link2 },
  { to: '/dashboard/reports', label: 'Reports', icon: FileText },
]

function SidebarContent({ collapsed, onNavigate }) {
  return (
    <div className="flex h-full flex-col">
      <div className={cn('flex h-14 items-center border-b border-white/5 px-4', collapsed && 'justify-center px-0')}>
        <Link to="/" aria-label="SentinelCV home"><Logo collapsed={collapsed} /></Link>
      </div>
      <nav className="flex-1 space-y-1 p-3" aria-label="Primary">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to} to={to} end={end} onClick={onNavigate} title={collapsed ? label : undefined}
            className={({ isActive }) => cn(
              'group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              collapsed && 'justify-center px-0',
              isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-slate-100',
            )}
          >
            {({ isActive }) => (
              <>
                {isActive && <span className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-full bg-teal-400" />}
                <Icon size={18} className={isActive ? 'text-teal-400' : ''} />
                {!collapsed && <span>{label}</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-white/5 p-3">
        <Link
          to="/" onClick={onNavigate}
          className={cn('flex items-center gap-2 rounded-lg px-3 py-2 text-xs text-slate-500 hover:bg-white/5 hover:text-slate-300', collapsed && 'justify-center px-0')}
        >
          <ArrowLeft size={14} />{!collapsed && 'Product overview'}
        </Link>
      </div>
    </div>
  )
}

function Breadcrumbs({ pathname }) {
  const current = NAV.find((n) => (n.end ? pathname === n.to : pathname.startsWith(n.to)))
  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm text-slate-500">
      <Link to="/dashboard" className="hover:text-slate-800">Dashboard</Link>
      {current && current.to !== '/dashboard' && (
        <>
          <ChevronRight size={14} className="text-slate-300" />
          <span className="font-medium text-slate-900">{current.label}</span>
        </>
      )}
      {current?.to === '/dashboard' && (
        <>
          <ChevronRight size={14} className="text-slate-300" />
          <span className="font-medium text-slate-900">Overview</span>
        </>
      )}
    </nav>
  )
}

export default function DashboardLayout() {
  const { pathname } = useLocation()
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem('scv.sidebar') === '1' } catch { return false }
  })
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    try { localStorage.setItem('scv.sidebar', collapsed ? '1' : '0') } catch { /* ignore */ }
  }, [collapsed])
  useEffect(() => { setMobileOpen(false); window.scrollTo(0, 0) }, [pathname])

  return (
    <div className="flex min-h-screen bg-surface">
      {/* Desktop sidebar */}
      <aside
        className={cn(
          'sticky top-0 hidden h-screen shrink-0 bg-ink-900 transition-[width] duration-200 ease-out lg:block',
          collapsed ? 'w-[68px]' : 'w-60',
        )}
      >
        <SidebarContent collapsed={collapsed} />
      </aside>

      {/* Mobile drawer */}
      <AnimatePresence>
        {mobileOpen && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <motion.div className="absolute inset-0 bg-ink-950/60" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setMobileOpen(false)} />
            <motion.aside
              className="absolute left-0 top-0 h-full w-64 bg-ink-900"
              initial={{ x: -260 }} animate={{ x: 0 }} exit={{ x: -260 }} transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
            >
              <SidebarContent collapsed={false} onNavigate={() => setMobileOpen(false)} />
            </motion.aside>
          </div>
        )}
      </AnimatePresence>

      <div className="flex min-w-0 flex-1 flex-col">
        <MockBanner />
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur">
          <button className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 lg:hidden" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
            <Menu size={20} />
          </button>
          <button
            className="hidden rounded-md p-1.5 text-slate-500 hover:bg-slate-100 lg:block"
            onClick={() => setCollapsed((c) => !c)} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
          <Breadcrumbs pathname={pathname} />
        </header>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={pathname}
              initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              transition={{ duration: 0.16, ease: 'easeOut' }}
              className="mx-auto max-w-7xl"
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  )
}
