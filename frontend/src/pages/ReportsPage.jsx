import { useState } from 'react'
import { Download, FileText } from 'lucide-react'
import { PageHeader, KV } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState, ErrorState } from '@/components/common/States'
import { SearchBox } from '@/components/common/DataTable'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { CardSkeleton } from '@/components/ui/skeleton'
import { useToast } from '@/components/ui/toast'
import { useAsync } from '@/hooks/useAsync'
import { useSortFilter } from '@/hooks/useSortFilter'
import { reportService, USE_MOCK } from '@/services'
import { formatDate } from '@/utils/format'
import { cn } from '@/utils/cn'

export default function ReportsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => reportService.list(), [])
  const [type, setType] = useState('ALL')
  const filtered = (data || []).filter((r) => type === 'ALL' || r.type === type)
  const { rows, query, setQuery } = useSortFilter(filtered, { searchKeys: ['title', 'subject', 'type'] })
  const [selectedId, setSelectedId] = useState(null)
  const [downloading, setDownloading] = useState(false)

  const selected = (data || []).find((r) => r.id === selectedId) || rows[0]

  const download = async () => {
    setDownloading(true)
    try {
      const { blob, filename } = await reportService.download(selected)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = filename; a.click()
      URL.revokeObjectURL(url)
      toast.success(USE_MOCK ? `Downloaded ${filename} (mock export).` : `Downloaded ${filename}.`)
    } catch (e) { toast.error(e.message) } finally { setDownloading(false) }
  }

  if (error) return <><PageHeader title="Reports" /><ErrorState message={error} onRetry={reload} /></>

  return (
    <>
      <PageHeader title="Reports" description="Audit reports generated for datasets, models and drift comparisons." />

      {loading ? (
        <div className="grid gap-4 lg:grid-cols-5"><div className="lg:col-span-2"><CardSkeleton /></div><div className="lg:col-span-3"><CardSkeleton /></div></div>
      ) : (data || []).length === 0 ? (
        <Card><EmptyState icon={FileText} title="No reports yet" description="Reports appear after an audit generates one." /></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-5">
          <Card className="lg:col-span-2">
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
              <SearchBox value={query} onChange={setQuery} placeholder="Search reports…" className="max-w-[200px]" />
              <Select value={type} onChange={(e) => setType(e.target.value)} className="w-32" aria-label="Filter by type">
                <option value="ALL">All types</option><option value="Dataset">Dataset</option><option value="Model">Model</option><option value="Drift">Drift</option>
              </Select>
            </div>
            {rows.length === 0 ? <EmptyState title="No reports match your filters" /> : (
              <ul className="divide-y divide-slate-100">
                {rows.map((r) => (
                  <li key={r.id}>
                    <button
                      onClick={() => setSelectedId(r.id)}
                      className={cn('w-full px-4 py-3 text-left transition-colors hover:bg-slate-50', selected?.id === r.id && 'bg-sky-50/60')}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-sm font-medium text-slate-900">{r.title}</p>
                        <StatusBadge value={r.status} />
                      </div>
                      <p className="mt-0.5 text-xs text-slate-500">{r.type} · {formatDate(r.generatedAt)}</p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="lg:col-span-3">
            {!selected ? <EmptyState title="Select a report" /> : (
              <>
                <CardHeader
                  title={selected.title} subtitle={selected.subject}
                  action={<Button onClick={download} disabled={selected.status !== 'READY' || downloading}><Download size={15} />{downloading ? 'Preparing…' : 'Download'}</Button>}
                />
                <CardBody className="space-y-5">
                  <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                    <KV label="Type">{selected.type}</KV>
                    <KV label="Format">{selected.format}</KV>
                    <KV label="Size">{selected.sizeKb ? `${selected.sizeKb} KB` : '—'}</KV>
                    <KV label="Decision">{selected.decision ? <StatusBadge value={selected.decision} /> : '—'}</KV>
                  </dl>
                  <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-5">
                    <p className="label mb-2">Preview{USE_MOCK && ' (sample)'}</p>
                    {selected.status === 'GENERATING' ? (
                      <div className="space-y-2"><div className="skeleton h-4 w-2/3" /><div className="skeleton h-4 w-full" /><div className="skeleton h-4 w-5/6" /><p className="pt-2 text-xs text-slate-500">This report is still being generated.</p></div>
                    ) : (
                      <>
                        <p className="text-sm text-slate-700">{selected.summary}</p>
                        <p className="label mb-1.5 mt-4">Sections</p>
                        <ol className="list-decimal space-y-1 pl-5 text-sm text-slate-600">
                          {selected.sections.map((s) => <li key={s}>{s}</li>)}
                        </ol>
                      </>
                    )}
                  </div>
                  {USE_MOCK && <p className="text-[11px] text-slate-500">Mock mode: the download is a small text file marked as a mock export, not a real report.</p>}
                </CardBody>
              </>
            )}
          </Card>
        </div>
      )}
    </>
  )
}
