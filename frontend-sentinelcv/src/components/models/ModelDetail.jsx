import { useState } from 'react'
import { Play } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tabs } from '@/components/common/Tabs'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState } from '@/components/common/States'
import { KV, Meter } from '@/components/common/PageHeader'
import { formatDate } from '@/utils/format'

function IOTable({ title, items }) {
  return (
    <div>
      <p className="label mb-2">{title}</p>
      {items.length === 0 ? <p className="text-sm text-slate-500">None reported.</p> : (
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-xs">
            <thead className="bg-slate-50 text-left text-slate-500"><tr><th className="px-3 py-2">Name</th><th className="px-3 py-2">Shape</th><th className="px-3 py-2">Type</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((t) => (
                <tr key={t.name}><td className="px-3 py-2 font-mono">{t.name}</td><td className="px-3 py-2 font-mono">{t.shape}</td><td className="px-3 py-2 font-mono">{t.dtype}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default function ModelDetail({ model: m, onRunAudit, running }) {
  const [tab, setTab] = useState('inspection')
  const audited = !!m.lastAuditAt

  return (
    <div className="space-y-5">
      <dl className="grid grid-cols-2 gap-4">
        <KV label="Format">{m.format}</KV>
        <KV label="Decision">{m.decision ? <StatusBadge value={m.decision} /> : '—'}</KV>
        <KV label="Size">{m.sizeMb != null ? `${m.sizeMb} MB` : '—'}</KV>
        <KV label="Opset">{m.opset ?? '—'}</KV>
        <KV label="Producer">{m.producer ?? '—'}</KV>
        <KV label="Last audit">{formatDate(m.lastAuditAt)}</KV>
        <div className="col-span-2"><KV label="File hash (SHA-256)" mono>{m.fileHash || '—'}</KV></div>
      </dl>

      <Button onClick={onRunAudit} disabled={running}>
        <Play size={14} />{running ? 'Running audit…' : audited ? 'Re-run audit' : 'Run audit'}
      </Button>

      <Tabs
        value={tab} onChange={setTab}
        tabs={[{ value: 'inspection', label: 'ONNX inspection' }, { value: 'behavior', label: 'Behavior' }, { value: 'findings', label: 'Findings' }, { value: 'history', label: 'History' }]}
      />

      {!audited && tab !== 'history' && (
        <EmptyState title="Not audited yet" description="Run an audit to inspect the model and assess its behavior." />
      )}

      {audited && tab === 'inspection' && (
        <div className="space-y-4">
          {m.inspection && (
            <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <KV label="Compatible">{m.inspection.compatible ? <StatusBadge value="PASSED" label="Yes" /> : <StatusBadge value="FAILED" label="No" />}</KV>
              <KV label="Parameters">{m.inspection.parameters}</KV>
              <KV label="Graph nodes">{m.inspection.nodes}</KV>
              <KV label="External data">{m.inspection.externalData ? 'Yes' : 'No'}</KV>
              {m.inspection.unsupportedOps.length > 0 && (
                <div className="col-span-2 sm:col-span-4">
                  <KV label="Unsupported operators" mono>{m.inspection.unsupportedOps.join(', ')}</KV>
                </div>
              )}
            </dl>
          )}
          <IOTable title="Inputs" items={m.inputs} />
          <IOTable title="Outputs" items={m.outputs} />
        </div>
      )}

      {audited && tab === 'behavior' && (
        <div className="space-y-5">
          <div className="rounded-lg border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-800">Repeatability</p>
              {m.repeatability && <StatusBadge value={m.repeatability.status} />}
            </div>
            {m.repeatability && m.repeatability.runs > 0 ? (
              <p className="mt-2 text-xs text-slate-500">
                {m.repeatability.identical} of {m.repeatability.runs} identical-input runs produced identical outputs. Max deviation: {m.repeatability.maxDeviation}.
              </p>
            ) : <p className="mt-2 text-xs text-slate-500">Not run (model failed structural inspection).</p>}
          </div>
          <div className="rounded-lg border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-800">Perturbation assessment</p>
              {m.perturbation && <StatusBadge value={m.perturbation.status} />}
            </div>
            {m.perturbation?.trials.length ? (
              <ul className="mt-3 space-y-3">
                {m.perturbation.trials.map((t) => (
                  <li key={t.name}>
                    <div className="mb-1 flex justify-between text-xs"><span className="text-slate-700">{t.name}</span><span className="tabular-nums text-slate-500">{t.stability}% stable</span></div>
                    <Meter value={t.stability} tone={t.stability >= 90 ? 'bg-emerald-500' : t.stability >= 75 ? 'bg-amber-500' : 'bg-red-500'} />
                  </li>
                ))}
              </ul>
            ) : <p className="mt-2 text-xs text-slate-500">No perturbation trials recorded.</p>}
          </div>
        </div>
      )}

      {audited && tab === 'findings' && (
        m.findings.length === 0 ? <p className="text-sm text-slate-500">No findings.</p> : (
          <ul className="space-y-2">
            {m.findings.map((f, i) => (
              <li key={i} className="rounded-lg border border-slate-200 p-3">
                <div className="flex items-center justify-between gap-2"><p className="text-sm font-medium text-slate-800">{f.title}</p><StatusBadge value={f.severity} /></div>
                <p className="mt-1 text-xs text-slate-500">{f.detail}</p>
              </li>
            ))}
          </ul>
        )
      )}

      {tab === 'history' && (
        m.history.length === 0 ? <p className="text-sm text-slate-500">No audit history yet.</p> : (
          <ol className="relative space-y-4 border-l border-slate-200 pl-5">
            {m.history.map((h) => (
              <li key={h.id} className="relative">
                <span className="absolute -left-[26px] top-1 h-2.5 w-2.5 rounded-full border-2 border-white bg-brand-500 ring-1 ring-slate-200" />
                <div className="flex items-center gap-2"><StatusBadge value={h.decision} /><span className="text-xs text-slate-500">{formatDate(h.at)}</span></div>
                <p className="mt-1 text-sm text-slate-700">{h.note}</p>
              </li>
            ))}
          </ol>
        )
      )}
    </div>
  )
}
