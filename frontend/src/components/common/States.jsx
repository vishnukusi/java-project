import { AlertCircle, Inbox, FlaskConical } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { USE_MOCK } from '@/services'

export function ErrorState({ message, onRetry }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-red-200 bg-red-50/60 p-10 text-center" role="alert">
      <AlertCircle className="text-red-500" size={28} />
      <div>
        <p className="text-sm font-semibold text-red-800">Couldn’t load data</p>
        <p className="mt-1 max-w-md text-xs text-red-700/80">{message}</p>
      </div>
      {onRetry && <Button variant="outline" size="sm" onClick={onRetry}>Try again</Button>}
    </div>
  )
}

export function EmptyState({ title, description, action, icon: Icon = Inbox }) {
  return (
    <div className="flex flex-col items-center gap-3 p-10 text-center">
      <div className="rounded-full bg-slate-100 p-3 text-slate-400"><Icon size={22} /></div>
      <div>
        <p className="text-sm font-semibold text-slate-800">{title}</p>
        {description && <p className="mt-1 max-w-sm text-xs text-slate-500">{description}</p>}
      </div>
      {action}
    </div>
  )
}

/** Shown on every dashboard page while mock mode is active. */
export function MockBanner() {
  if (!USE_MOCK) return null
  return (
    <div className="flex items-center gap-2 border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-xs text-amber-900" role="status">
      <FlaskConical size={14} className="shrink-0" />
      <span>
        <strong className="font-semibold">Mock mode:</strong> all data shown is sample fixture data, not real audit results.
        Actions are simulated and nothing is sent to a backend.
      </span>
    </div>
  )
}
