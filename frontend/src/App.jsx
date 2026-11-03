import { lazy, Suspense } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import LandingLayout from '@/layouts/LandingLayout'
import DashboardLayout from '@/layouts/DashboardLayout'
import LandingPage from '@/pages/LandingPage'
import { Button } from '@/components/ui/button'
import { CardSkeleton } from '@/components/ui/skeleton'

const OverviewPage = lazy(() => import('@/pages/OverviewPage'))
const DatasetsPage = lazy(() => import('@/pages/DatasetsPage'))
const ModelsPage = lazy(() => import('@/pages/ModelsPage'))
const DriftPage = lazy(() => import('@/pages/DriftPage'))
const ProvenancePage = lazy(() => import('@/pages/ProvenancePage'))
const ReportsPage = lazy(() => import('@/pages/ReportsPage'))

function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-ink-950 px-6 text-center text-slate-300">
      <p className="font-mono text-sm text-teal-400">404</p>
      <h1 className="font-display text-3xl font-semibold text-white">This page could not be verified.</h1>
      <p className="max-w-md text-sm text-slate-400">The route you requested does not exist in SentinelCV.</p>
      <div className="flex gap-3">
        <Link to="/"><Button variant="glass">Product overview</Button></Link>
        <Link to="/dashboard"><Button variant="light">Open Dashboard</Button></Link>
      </div>
    </div>
  )
}

const fallback = <div className="space-y-4"><CardSkeleton height="h-24" /><CardSkeleton /></div>

export default function App() {
  return (
    <Routes>
      <Route element={<LandingLayout />}>
        <Route path="/" element={<LandingPage />} />
      </Route>
      <Route path="/dashboard" element={<DashboardLayout />}>
        <Route index element={<Suspense fallback={fallback}><OverviewPage /></Suspense>} />
        <Route path="datasets" element={<Suspense fallback={fallback}><DatasetsPage /></Suspense>} />
        <Route path="models" element={<Suspense fallback={fallback}><ModelsPage /></Suspense>} />
        <Route path="drift" element={<Suspense fallback={fallback}><DriftPage /></Suspense>} />
        <Route path="provenance" element={<Suspense fallback={fallback}><ProvenancePage /></Suspense>} />
        <Route path="reports" element={<Suspense fallback={fallback}><ReportsPage /></Suspense>} />
        <Route path="*" element={<NotFound />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
