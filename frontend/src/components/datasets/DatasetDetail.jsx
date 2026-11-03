import { useState } from 'react'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Play } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tabs } from '@/components/common/Tabs'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState } from '@/components/common/States'
import { KV } from '@/components/common/PageHeader'
import { formatDate, formatNumber } from '@/utils/format'

export default function DatasetDetail({ dataset: d, onRunAudit, running }) {
  const [tab, setTab] = useState('audit')
  const audited = !!d.lastAuditAt

  return (
    <div className="space-y-5">
      <dl className="grid grid-cols-2 gap-4">
        <KV label="Format">{d.format}</KV>
        <KV label="Decision">{d.decision ? <StatusBadge value={d.decision} /> : '—'}</KV>
        <KV label="Images">{formatNumber(d.images)}</KV>
        <KV label="Annotations">{formatNumber(d.annotations)}</KV>
        <KV label="Classes">{formatNumber(d.classes)}</KV>
        <KV label="Last audit">{formatDate(d.lastAuditAt)}</KV>
        <div className="col-span-2"><KV label="Manifest hash (SHA-256)" mono>{d.manifestHash || '—'}</KV></div>
      </dl>

      <Button onClick={onRunAudit} disabled={running}>
        <Play size={14} />{running ? 'Running audit…' : audited ? 'Re-run audit' : 'Run audit'}
      </Button>

      <Tabs
        value={tab} onChange={setTab}
        tabs={[{ value: 'audit', label: 'Audit results' }, { value: 'annotations', label: 'Annotations' }, { value: 'duplicates', label: 'Duplicates' }, { value: 'history', label: 'History' }]}
      />

      {!audited && (
        <EmptyState title="Not audited yet" description="Run an audit to see integrity checks, annotation statistics and findings." />
      )}

      {audited && tab === 'audit' && (
        <div className="space-y-4">
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
            {d.checks.map((c) => (
              <li key={c.name} className="flex items-start justify-between gap-3 px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-slate-800">{c.name}</p>
                  <p className="text-xs text-slate-500">{c.detail}</p>
                </div>
                <StatusBadge value={c.status} />
              </li>
            ))}
          </ul>
          <div>
            <p className="label mb-2">Findings</p>
            {d.findings.length === 0 ? <p className="text-sm text-slate-500">No findings.</p> : (
              <ul className="space-y-2">
                {d.findings.map((f, i) => (
                  <li key={i} className="rounded-lg border border-slate-200 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium text-slate-800">{f.title}</p>
                      <StatusBadge value={f.severity} />
                    </div>
                    <p className="mt-1 text-xs text-slate-500">{f.detail}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {audited && tab === 'annotations' && (
        d.annotationStats ? (
          <div className="space-y-4">
            <dl className="grid grid-cols-3 gap-4">
              <KV label="Valid">{formatNumber(d.annotationStats.valid)}</KV>
              <KV label="Invalid">{formatNumber(d.annotationStats.invalid)}</KV>
              <KV label="Empty images">{formatNumber(d.annotationStats.emptyImages)}</KV>
            </dl>
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={d.annotationStats.perClass} layout="vertical" margin={{ left: 8 }}>
                  <XAxis type="number" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} width={70} />
                  <Tooltip cursor={{ fill: '#f1f5f9' }} />
                  <Bar dataKey="count" fill="#0ea5e9" radius={[0, 4, 4, 0]} animationDuration={500} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <p className="text-[11px] text-slate-500">Instances per class. Top classes shown.</p>
          </div>
        ) : <p className="text-sm text-slate-500">No annotation statistics available.</p>
      )}

      {audited && tab === 'duplicates' && (
        d.duplicates ? (
          <dl className="grid grid-cols-2 gap-4">
            <KV label="Duplicate groups">{formatNumber(d.duplicates.groups)}</KV>
            <KV label="Images involved">{formatNumber(d.duplicates.images)}</KV>
            <div className="col-span-2 text-xs text-slate-500">
              Groups of images with identical content. Per-file listing depends on backend support (see API_INTEGRATION.md).
            </div>
          </dl>
        ) : <p className="text-sm text-slate-500">No duplicate detection results.</p>
      )}

      {tab === 'history' && (
        d.history.length === 0 ? <p className="text-sm text-slate-500">No audit history yet.</p> : (
          <ol className="relative space-y-4 border-l border-slate-200 pl-5">
            {d.history.map((h) => (
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
