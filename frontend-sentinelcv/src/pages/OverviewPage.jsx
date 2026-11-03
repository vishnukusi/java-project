import { Link } from 'react-router-dom'
import { Bar, BarChart, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { ArrowRight } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ErrorState, EmptyState } from '@/components/common/States'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { CardSkeleton } from '@/components/ui/skeleton'
import { useAsync } from '@/hooks/useAsync'
import { overviewService } from '@/services'
import { formatDate } from '@/utils/format'

const COLORS = { ACCEPT: '#10b981', REVIEW: '#f59e0b', QUARANTINE: '#ef4444' }

function Stat({ label, value, tone }) {
  return (
    <div className="px-5 py-4">
      <p className="label">{label}</p>
      <p className={`mt-1 font-display text-3xl font-semibold tabular-nums ${tone || 'text-slate-900'}`}>{value}</p>
    </div>
  )
}

export default function OverviewPage() {
  const { data, loading, error, reload } = useAsync(() => overviewService.get(), [])

  if (error) return <><PageHeader title="Overview" /><ErrorState message={error} onRetry={reload} /></>

  return (
    <>
      <PageHeader title="Overview" description="Operational summary of dataset, model and provenance assessments." />

      {loading || !data ? (
        <div className="space-y-4"><CardSkeleton height="h-24" /><div className="grid gap-4 lg:grid-cols-2"><CardSkeleton /><CardSkeleton /></div></div>
      ) : (
        <div className="space-y-4">
          <Card className="grid grid-cols-2 divide-x divide-y divide-slate-100 sm:grid-cols-3 lg:grid-cols-5 lg:divide-y-0">
            <Stat label="Datasets audited" value={data.totals.datasets} />
            <Stat label="Models audited" value={data.totals.models} />
            <Stat label="Accepted" value={data.totals.accepted} tone="text-emerald-600" />
            <Stat label="Review required" value={data.totals.review} tone="text-amber-600" />
            <Stat label="Quarantined" value={data.totals.quarantined} tone="text-red-600" />
          </Card>

          <div className="grid gap-4 lg:grid-cols-5">
            <Card className="lg:col-span-2">
              <CardHeader title="Risk distribution" subtitle="Latest decision per audited asset" />
              <CardBody className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={data.riskDistribution} dataKey="value" nameKey="name" innerRadius={55} outerRadius={85} paddingAngle={2} animationDuration={600}>
                      {data.riskDistribution.map((d) => <Cell key={d.name} fill={COLORS[d.name]} />)}
                    </Pie>
                    <Tooltip />
                    <Legend iconType="circle" iconSize={8} />
                  </PieChart>
                </ResponsiveContainer>
              </CardBody>
            </Card>

            <Card className="lg:col-span-3">
              <CardHeader title="Assessment summary" subtitle="Decisions by asset type" />
              <CardBody className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={data.assessmentsByType} layout="vertical" margin={{ left: 8 }}>
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} axisLine={false} tickLine={false} />
                    <YAxis type="category" dataKey="type" tick={{ fontSize: 12 }} axisLine={false} tickLine={false} width={70} />
                    <Tooltip cursor={{ fill: '#f1f5f9' }} />
                    <Legend iconType="circle" iconSize={8} />
                    <Bar dataKey="ACCEPT" stackId="a" fill={COLORS.ACCEPT} animationDuration={600} />
                    <Bar dataKey="REVIEW" stackId="a" fill={COLORS.REVIEW} animationDuration={600} />
                    <Bar dataKey="QUARANTINE" stackId="a" fill={COLORS.QUARANTINE} radius={[0, 4, 4, 0]} animationDuration={600} />
                  </BarChart>
                </ResponsiveContainer>
              </CardBody>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Recent audit activity" />
              {data.recentActivity.length === 0 ? (
                <EmptyState title="No audits yet" description="Register a dataset or model to run your first audit." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {data.recentActivity.map((a) => (
                    <li key={a.id} className="flex items-center justify-between gap-3 px-5 py-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-slate-800">{a.subject}</p>
                        <p className="text-xs text-slate-500">{a.kind} audit · {formatDate(a.at)}</p>
                      </div>
                      <StatusBadge value={a.decision} />
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardHeader title="Recent security findings" action={<Link to="/dashboard/reports" className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:underline">Reports <ArrowRight size={12} /></Link>} />
              {data.recentFindings.length === 0 ? (
                <EmptyState title="No findings" description="Findings appear here after an audit completes." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {data.recentFindings.map((f, i) => (
                    <li key={i} className="flex items-center justify-between gap-3 px-5 py-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-slate-800">{f.title}</p>
                        <p className="text-xs text-slate-500">{f.subject} · {formatDate(f.at)}</p>
                      </div>
                      <StatusBadge value={f.severity} />
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </div>
      )}
    </>
  )
}
