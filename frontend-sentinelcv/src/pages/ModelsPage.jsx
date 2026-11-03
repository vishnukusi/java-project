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
import ModelDetail from '@/components/models/ModelDetail'
import RegisterModelDialog from '@/components/models/RegisterModelDialog'
import { useAsync } from '@/hooks/useAsync'
import { useSortFilter } from '@/hooks/useSortFilter'
import { modelService } from '@/services'
import { formatDate } from '@/utils/format'

export default function ModelsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => modelService.list(), [])
  const [decision, setDecision] = useState('ALL')
  const filtered = (data || []).filter((m) => decision === 'ALL' || (decision === 'NONE' ? !m.decision : m.decision === decision))
  const { rows, query, setQuery, sort, toggleSort } = useSortFilter(filtered, { searchKeys: ['name', 'format', 'decision'] })

  const [selectedId, setSelectedId] = useState(null)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [running, setRunning] = useState(false)
  const selected = (data || []).find((m) => m.id === selectedId)

  const register = async (payload) => {
    try {
      const item = await modelService.register(payload)
      await reload()
      setRegisterOpen(false)
      toast.success(`Model “${item.name}” registered.`)
    } catch (e) { toast.error(e.message); throw e }
  }

  const runAudit = async () => {
    setRunning(true)
    try {
      await modelService.runAudit(selectedId)
      await reload()
      toast.success('Audit finished.')
    } catch (e) { toast.error(e.message) } finally { setRunning(false) }
  }

  return (
    <>
      <PageHeader
        title="Models"
        description="Register ONNX models and assess structure, repeatability and behavior under perturbation."
        actions={<Button onClick={() => setRegisterOpen(true)}><Plus size={16} />Register model</Button>}
      />

      {error ? <ErrorState message={error} onRetry={reload} /> : (
        <Card>
          <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
            <SearchBox value={query} onChange={setQuery} placeholder="Search models…" />
            <Select value={decision} onChange={(e) => setDecision(e.target.value)} className="w-40" aria-label="Filter by decision">
              <option value="ALL">All decisions</option><option value="ACCEPT">Accept</option><option value="REVIEW">Review</option>
              <option value="QUARANTINE">Quarantine</option><option value="NONE">Not audited</option>
            </Select>
          </div>
          {loading ? <TableSkeleton cols={5} /> : rows.length === 0 ? (
            <EmptyState
              title={data?.length ? 'No models match your filters' : 'No models registered'}
              description={data?.length ? 'Try clearing the search or decision filter.' : 'Register an ONNX model to begin.'}
              action={!data?.length && <Button onClick={() => setRegisterOpen(true)}>Register model</Button>}
            />
          ) : (
            <Table>
              <thead className="border-b border-slate-100 bg-slate-50/60">
                <tr>
                  <SortHeader label="Name" sortKey="name" sort={sort} onSort={toggleSort} />
                  <Th>Format</Th>
                  <SortHeader label="Size" sortKey="sizeMb" sort={sort} onSort={toggleSort} />
                  <Th>Decision</Th>
                  <SortHeader label="Last audit" sortKey="lastAuditAt" sort={sort} onSort={toggleSort} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((m) => (
                  <tr key={m.id} tabIndex={0} onClick={() => setSelectedId(m.id)} onKeyDown={(e) => e.key === 'Enter' && setSelectedId(m.id)}
                    className="cursor-pointer transition-colors hover:bg-slate-50 focus:bg-slate-50 focus:outline-none">
                    <td className="px-4 py-3 font-medium text-slate-900">{m.name}</td>
                    <td className="px-4 py-3"><span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-600">{m.format}</span></td>
                    <td className="px-4 py-3 tabular-nums text-slate-600">{m.sizeMb != null ? `${m.sizeMb} MB` : '—'}</td>
                    <td className="px-4 py-3">{m.decision ? <StatusBadge value={m.decision} /> : <StatusBadge value="PENDING" label="Not audited" />}</td>
                    <td className="px-4 py-3 text-slate-500">{formatDate(m.lastAuditAt)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card>
      )}

      <Drawer open={!!selected} onClose={() => setSelectedId(null)} title={selected?.name} description="Model inspection, behavior and audit history">
        {selected && <ModelDetail key={selected.id} model={selected} onRunAudit={runAudit} running={running} />}
      </Drawer>
      <RegisterModelDialog open={registerOpen} onClose={() => setRegisterOpen(false)} onSubmit={register} />
    </>
  )
}
