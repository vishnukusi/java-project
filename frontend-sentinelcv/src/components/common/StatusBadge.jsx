import { CheckCircle2, AlertTriangle, XCircle, Clock, ShieldAlert, HelpCircle, Loader2, MinusCircle } from 'lucide-react'
import { cn } from '@/utils/cn'

const tones = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/25',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20',
}

const map = {
  // decisions
  ACCEPT: ['green', CheckCircle2, 'Accept'],
  REVIEW: ['amber', AlertTriangle, 'Review'],
  QUARANTINE: ['red', ShieldAlert, 'Quarantine'],
  // check status
  PASSED: ['green', CheckCircle2, 'Passed'],
  WARNING: ['amber', AlertTriangle, 'Warning'],
  FAILED: ['red', XCircle, 'Failed'],
  PENDING: ['slate', Clock, 'Pending'],
  SKIPPED: ['slate', MinusCircle, 'Skipped'],
  // severity
  INFO: ['blue', CheckCircle2, 'Info'],
  LOW: ['blue', AlertTriangle, 'Low'],
  MEDIUM: ['amber', AlertTriangle, 'Medium'],
  HIGH: ['red', XCircle, 'High'],
  // drift
  NONE: ['green', CheckCircle2, 'None'],
  MODERATE: ['amber', AlertTriangle, 'Moderate'],
  SEVERE: ['red', ShieldAlert, 'Severe'],
  // provenance
  VERIFIED: ['green', CheckCircle2, 'Verified'],
  TAMPERED: ['red', ShieldAlert, 'Tampering detected'],
  UNAVAILABLE: ['slate', HelpCircle, 'Verification unavailable'],
  BROKEN: ['red', ShieldAlert, 'Chain broken'],
  VALID: ['green', CheckCircle2, 'Chain intact'],
  // report
  READY: ['green', CheckCircle2, 'Ready'],
  GENERATING: ['blue', Loader2, 'Generating'],
}
// FAILED means "Verification failed" in the provenance context; callers pass label override.

export function StatusBadge({ value, label, className }) {
  const [tone, Icon, text] = map[value] || ['slate', HelpCircle, value ?? '—']
  return (
    <span className={cn('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset', tones[tone], className)}>
      <Icon size={12} className={value === 'GENERATING' ? 'animate-spin' : ''} />
      {label || text}
    </span>
  )
}
