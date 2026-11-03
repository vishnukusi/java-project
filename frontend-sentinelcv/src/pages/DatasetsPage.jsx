import { useState } from 'react'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState, ErrorState } from '@/components/common/States'
import { SearchBox, SortHeader, Table, Th } from '@/components/common/DataTable'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { Drawer } from '@/components/ui/overlays'
import { TableSkeleton } from '@/components/ui/skeleton'
import { useToast } from '@/components/ui/toast'
import DatasetDetail from '@/components/datasets/DatasetDetail'
import RegisterDatasetDialog from '@/components/datasets/RegisterDatasetDialog'
import { useAsync } from '@/hooks/useAsync'
import { useSortFilter } from '@/hooks/useSortFilter'
import { datasetService } from '@/services'
import { formatDate, formatNumber } from '@/utils/format'

export default function DatasetsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => datasetService.list(), [])
  const [format, setFormat] = useState('ALL')
  const filtered = (data || []).filter((d) => format === 'ALL' || d.format === format)
  const { rows, query, setQuery, sort, toggleSort } = useSortFilter(filtered, { searchKeys: ['name', 'format', 'decision'] })

  const [selectedId, setSelectedId] = useState(null)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [running, setRunning] = useState(false)
  const selected = (data || []).find((d) => d.id === selectedId)

  const register = async (payload) => {
    try {
      const item = await datasetService.register(payload)
      await reload()
      setRegisterOpen(false)
      toast.success(`Dataset “${item.name}” registered.`)
    } catch (e) { toast.error(e.message); throw e }
  }

  const runAudit = async () => {
    setRunning(true)
    try {
      await datasetService.runAudit(selectedId)
      await reload()
      toast.success('Audit finished.')
    } catch (e) { toast.error(e.message) } finally { setRunning(false) }
  }

  return (
    <>
      <PageHeader
        title="Datasets"
        description="Register COCO and YOLO datasets and audit their integrity, annotations and duplicates."
        actions={<Button onClick={() => setRegisterOpen(true)}><Plus size={16} />Register dataset</Button>}
      />

      {error ? <ErrorState message={error} onRetry={reload} /> : (
        <Card>
          <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
            <SearchBox value={query} onChange={setQuery} placeholder="Search datasets…" />
            <Select value={format} onChange={(e) => setFormat(e.target.value)} className="w-32" aria-label="Filter by format">
              <option value="ALL">All formats</option><option value="COCO">COCO</option><option value="YOLO">YOLO</option>
            </Select>
          </div>
          {loading ? <TableSkeleton cols={6} /> : rows.length === 0 ? (
            <EmptyState
              title={data?.length ? 'No datasets match your filters' : 'No datasets registered'}
              description={data?.length ? 'Try clearing the search or format filter.' : 'Register a dataset to begin auditing.'}
              action={!data?.length && <Button onClick={() => setRegisterOpen(true)}>Register dataset</Button>}
            />
          ) : (
            <Table>
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr>
                  <SortHeader label="Name" sortKey="name" sort={sort} onSort={toggleSort} />
                  <SortHeader label="Format" sortKey="format" sort={sort} onSort={toggleSort} />
                  <SortHeader label="Images" sortKey="images" sort={sort} onSort={toggleSort} />
                  <Th>Integrity</Th>
                  <Th>Decision</Th>
                  <SortHeader label="Last audit" sortKey="lastAuditAt" sort={sort} onSort={toggleSort} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((d) => (
                  <tr key={d.id} tabIndex={0} onClick={() => setSelectedId(d.id)} onKeyDown={(e) => e.key === 'Enter' && setSelectedId(d.id)}
                    className="cursor-pointer transition-colors hover:bg-slate-50 focus:bg-slate-50 focus:outline-none">
                    <td className="px-4 py-3 font-medium text-slate-900">{d.name}</td>
                    <td className="px-4 py-3"><span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-600">{d.format}</span></td>
                    <td className="px-4 py-3 tabular-nums text-slate-600">{formatNumber(d.images)}</td>
                    <td className="px-4 py-3"><StatusBadge value={d.integrity} /></td>
                    <td className="px-4 py-3">{d.decision ? <StatusBadge value={d.decision} /> : <span className="text-slate-400">—</span>}</td>
                    <td className="px-4 py-3 text-slate-500">{formatDate(d.lastAuditAt)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      )}

      <Drawer open={!!selected} onClose={() => setSelectedId(null)} title={selected?.name} description="Dataset details and audit history">
        {selected && <DatasetDetail key={selected.id} dataset={selected} onRunAudit={runAudit} running={running} />}
      </Drawer>
      <RegisterDatasetDialog open={registerOpen} onClose={() => setRegisterOpen(false)} onSubmit={register} />
    </>
  )
}
