import { Check, X, Minus } from 'lucide-react'
import { StatusBadge } from '@/components/common/StatusBadge'
import { KV } from '@/components/common/PageHeader'
import { formatDate } from '@/utils/format'
import { USE_MOCK } from '@/services'

export const statusLabel = {
  VERIFIED: 'Verified',
  FAILED: 'Verification failed',
  TAMPERED: 'Tampering detected',
  UNAVAILABLE: 'Verification unavailable',
}

function Check3({ label, value }) {
  const Icon = value === true ? Check : value === false ? X : Minus
  const cls = value === true ? 'text-emerald-600 bg-emerald-50' : value === false ? 'text-red-600 bg-red-50' : 'text-slate-500 bg-slate-100'
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5">
      <span className="text-sm text-slate-700">{label}</span>
      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>
        <Icon size={12} />{value === true ? 'Match' : value === false ? 'Mismatch' : 'Not available'}
      </span>
    </div>
  )
}

export default function RecordDetail({ record: r }) {
  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <StatusBadge value={r.status} label={statusLabel[r.status]} />
        <span className="text-xs text-slate-500">{formatDate(r.recordedAt)}</span>
      </div>
      {r.reason && <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">{r.reason}</p>}

      <dl className="grid grid-cols-2 gap-4">
        <KV label="Model">{r.model}</KV>
        <KV label="Input">{r.input}</KV>
        <KV label="Top confidence">{r.topConfidence}</KV>
        <KV label="Chain position">#{r.index}</KV>
      </dl>

      <div className="space-y-2">
        <Check3 label="HMAC" value={r.hmacMatch} />
        <Check3 label="Link to previous record" value={r.linkValid} />
      </div>

      <dl className="space-y-3">
        <KV label="Input hash (SHA-256)" mono>{r.inputHash}</KV>
        <KV label="Output hash (SHA-256)" mono>{r.outputHash}</KV>
        <KV label="Previous record hash" mono>{r.previousHash}</KV>
        <KV label="Record hash" mono>{r.recordHash}</KV>
        <KV label="HMAC" mono>{r.hmac || 'Not available'}</KV>
      </dl>

      <p className="text-[11px] text-slate-500">
        {USE_MOCK
          ? 'Mock mode: hashes and statuses are sample values. The frontend does not compute or verify cryptography.'
          : 'Statuses are reported by the backend. The frontend does not compute or verify cryptography.'}
      </p>
    </div>
  )
}
