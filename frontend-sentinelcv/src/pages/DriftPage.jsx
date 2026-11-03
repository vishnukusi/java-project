import { useEffect, useState } from 'react'
import { Area, AreaChart, CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState, ErrorState } from '@/components/common/States'
import { Table, Th } from '@/components/common/DataTable'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { CardSkeleton } from '@/components/ui/skeleton'
import { useAsync } from '@/hooks/useAsync'
import { driftService } from '@/services'
import { psiBands } from '@/data/drift'
import { formatDate } from '@/utils/format'
import { cn } from '@/utils/cn'

function Metric({ label, value, sub, children }) {
  return (
    <div className="px-5 py-4">
      <p className="label">{label}</p>
      <p className="mt-1 font-display text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
      {children}
    </div>
  )
}

export default function DriftPage() {
  const { data, loading, error, reload } = useAsync(() => driftService.list(), [])
  const [compId, setCompId] = useState(null)
  const [featName, setFeatName] = useState(null)

  const comp = data?.find((c) => c.id === compId) || data?.[0]
  const feature = comp?.features.find((f) => f.name === featName) || comp?.features[0]

  useEffect(() => { setFeatName(null) }, [compId])

  if (error) return <><PageHeader title="Drift Monitoring" /><ErrorState message={error} onRetry={reload} /></>

  return (
    <>
      <PageHeader
        title="Drift Monitoring"
        description="Compare a reference distribution with current data using PSI and Kolmogorov–Smirnov tests."
        actions={data?.length ? (
          <Select value={comp?.id || ''} onChange={(e) => setCompId(e.target.value)} className="w-64" aria-label="Select comparison">
            {data.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        ) : null}
      />

      {loading ? (
        <div className="space-y-4"><CardSkeleton height="h-24" /><CardSkeleton /></div>
      ) : !comp ? (
        <Card><EmptyState title="No drift comparisons" description="Drift comparisons appear once a reference and a current dataset are compared." /></Card>
      ) : (
        <div className="space-y-4">
          <Card className="grid grid-cols-2 divide-x divide-y divide-slate-100 lg:grid-cols-4 lg:divide-y-0">
            <Metric label="Severity"><div className="mt-2"><StatusBadge value={comp.severity} /></div></Metric>
            <Metric label="PSI (overall)" value={comp.psi.toFixed(2)} sub="Population Stability Index" />
            <Metric label="KS statistic" value={comp.ks.statistic.toFixed(2)} sub={`p = ${comp.ks.pValue < 0.0001 ? '< 0.0001' : comp.ks.pValue}`} />
            <Metric label="Compared" value={comp.currentLabel} sub={`vs ${comp.referenceLabel} · ${formatDate(comp.computedAt)}`} />
          </Card>

          <div className="grid gap-4 lg:grid-cols-5">
            <Card className="lg:col-span-3">
              <CardHeader title={`Distribution: ${feature?.name}`} subtitle="Reference vs current (relative frequency)" />
              <CardBody className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={feature?.data} margin={{ left: -16, right: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                    <XAxis dataKey="bin" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                    <Tooltip />
                    <Legend iconType="circle" iconSize={8} />
                    <Area type="monotone" dataKey="reference" name="Reference" stroke="#64748b" fill="#64748b" fillOpacity={0.15} animationDuration={600} />
                    <Area type="monotone" dataKey="current" name="Current" stroke="#0ea5e9" fill="#0ea5e9" fillOpacity={0.2} animationDuration={600} />
                  </AreaChart>
                </ResponsiveContainer>
              </CardBody>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader title="PSI trend" subtitle="Overall PSI over time" />
              <CardBody className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={comp.trend} margin={{ left: -16, right: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                    <XAxis dataKey="date" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11 }} axisLine={false} tickLine={false} domain={[0, 'auto']} />
                    <Tooltip />
                    <ReferenceLine y={0.1} stroke="#f59e0b" strokeDasharray="4 4" />
                    <ReferenceLine y={0.25} stroke="#ef4444" strokeDasharray="4 4" />
                    <Line type="monotone" dataKey="psi" name="PSI" stroke="#0d9488" strokeWidth={2} dot={{ r: 3 }} animationDuration={600} />
                  </LineChart>
                </ResponsiveContainer>
              </CardBody>
            </Card>
          </div>

          <Card>
            <CardHeader title="Feature-level drift" subtitle="Select a row to plot its distribution" />
            <Table>
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr><Th>Feature</Th><Th>PSI</Th><Th>KS</Th><Th>p-value</Th><Th>Severity</Th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {comp.features.map((f) => (
                  <tr
                    key={f.name} tabIndex={0} onClick={() => setFeatName(f.name)} onKeyDown={(e) => e.key === 'Enter' && setFeatName(f.name)}
                    className={cn('cursor-pointer transition-colors hover:bg-slate-50 focus:outline-none', feature?.name === f.name && 'bg-sky-50/60')}
                  >
                    <td className="px-4 py-3 font-medium text-slate-900">{f.name}</td>
                    <td className="px-4 py-3 tabular-nums">{f.psi.toFixed(2)}</td>
                    <td className="px-4 py-3 tabular-nums">{f.ks.toFixed(2)}</td>
                    <td className="px-4 py-3 tabular-nums text-slate-500">{f.pValue < 0.0001 ? '< 0.0001' : f.pValue}</td>
                    <td className="px-4 py-3"><StatusBadge value={f.severity} /></td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <div className="border-t border-slate-100 px-5 py-3 text-[11px] text-slate-500">
              Common PSI interpretation: {psiBands.map((b) => `${b.label} ${b.range}`).join(' · ')}. Thresholds shown on the trend chart (0.10, 0.25) are the conventional rule of thumb; confirm the backend’s thresholds.
            </div>
          </Card>
        </div>
      )}
    </>
  )
}
