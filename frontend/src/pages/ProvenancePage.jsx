import { useState } from 'react'
import { ShieldCheck, ShieldAlert } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { EmptyState, ErrorState } from '@/components/common/States'
import { SearchBox, SortHeader, Table, Th } from '@/components/common/DataTable'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Select } from '@/components/ui/input'
import { Drawer } from '@/components/ui/overlays'
import { CardSkeleton, TableSkeleton } from '@/components/ui/skeleton'
import { useToast } from '@/components/ui/toast'
import ChainStrip from '@/components/provenance/ChainStrip'
import RecordDetail, { statusLabel } from '@/components/provenance/RecordDetail'
import { useAsync } from '@/hooks/useAsync'
import { useSortFilter } from '@/hooks/useSortFilter'
import { provenanceService, USE_MOCK } from '@/services'
import { formatDate, truncateHash } from '@/utils/format'

export default function ProvenancePage() {
  const toast = useToast()
  const { data, loading, error, reload, setData } = useAsync(() => provenanceService.list(), [])
  const [status, setStatus] = useState('ALL')
  const [selectedId, setSelectedId] = useState(null)
  const [verifying, setVerifying] = useState(false)

  const records = data?.records || []
  const filtered = records.filter((r) => status === 'ALL' || r.status === status)
  const { rows, query, setQuery, sort, toggleSort } = useSortFilter(filtered, { searchKeys: ['id', 'model', 'input', 'status'] })
  const selected = records.find((r) => r.id === selectedId)

  const counts = records.reduce((a, r) => ({ ...a, [r.status]: (a[r.status] || 0) + 1 }), {})
  const chain = data?.chain

  const verify = async () => {
    setVerifying(true)
    try {
      const res = await provenanceService.verifyChain()
      setData(res)
      toast.info(USE_MOCK ? 'Mock mode: sample verification result shown. No cryptography was run.' : 'Verification result received from backend.')
    } catch (e) { toast.error(e.message) } finally { setVerifying(false) }
  }

  if (error) return <><PageHeader title="Inference Provenance" /><ErrorState message={error} onRetry={reload} /></>

  const intact = chain?.status === 'VALID'

  return (
    <>
      <PageHeader
        title="Inference Provenance"
        description="Inspect cryptographically linked inference records, HMAC results and chain integrity."
        actions={<Button onClick={verify} disabled={verifying || loading}><ShieldCheck size={16} />{verifying ? 'Requesting…' : 'Verify chain'}</Button>}
      />

      {loading ? (
        <div className="space-y-4"><CardSkeleton height="h-28" /><CardSkeleton /></div>
      ) : records.length === 0 ? (
        <Card><EmptyState title="No inference records" description="Records appear once inferences are logged with provenance enabled." /></Card>
      ) : (
        <div className="space-y-4">
          <Card className={intact ? 'border-emerald-200' : 'border-red-200'}>
            <CardBody className="flex flex-wrap items-center gap-4">
              <div className={`rounded-full p-2.5 ${intact ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>
                {intact ? <ShieldCheck size={22} /> : <ShieldAlert size={22} />}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-900">{intact ? 'Chain intact' : 'Chain integrity problem reported'}</p>
                <p className="text-xs text-slate-500">
                  {chain.totalRecords} records{chain.firstBrokenIndex != null && ` · first break at #${chain.firstBrokenIndex}`}
                  {chain.note && ` · ${chain.note}`}
                </p>
              </div>
              <div className="flex flex-wrap gap-2 text-xs">
                {['VERIFIED', 'FAILED', 'TAMPERED', 'UNAVAILABLE'].map((s) => (
                  <span key={s} className="rounded-md border border-slate-200 px-2 py-1 text-slate-600"><span className="font-semibold tabular-nums">{counts[s] || 0}</span> {statusLabel[s].toLowerCase()}</span>
                ))}
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Record chain" subtitle="Each record includes the hash of the previous one. Select a node for details." />
            <CardBody><ChainStrip records={records} selectedId={selectedId} onSelect={setSelectedId} firstBrokenIndex={chain.firstBrokenIndex} /></CardBody>
          </Card>

          <Card>
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 p-4">
              <SearchBox value={query} onChange={setQuery} placeholder="Search records…" />
              <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-52" aria-label="Filter by status">
                <option value="ALL">All statuses</option>
                {Object.entries(statusLabel).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </Select>
            </div>
            {rows.length === 0 ? <EmptyState title="No records match your filters" /> : (
              <Table>
                <thead className="border-b border-slate-100 bg-slate-50/60">
                  <tr>
                    <SortHeader label="Record" sortKey="id" sort={sort} onSort={toggleSort} />
                    <SortHeader label="Model" sortKey="model" sort={sort} onSort={toggleSort} />
                    <Th>Record hash</Th>
                    <Th>Status</Th>
                    <SortHeader label="Recorded" sortKey="recordedAt" sort={sort} onSort={toggleSort} />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((r) => (
                    <tr key={r.id} tabIndex={0} onClick={() => setSelectedId(r.id)} onKeyDown={(e) => e.key === 'Enter' && setSelectedId(r.id)}
                      className="cursor-pointer transition-colors hover:bg-slate-50 focus:bg-slate-50 focus:outline-none">
                      <td className="px-4 py-3 font-mono text-xs font-medium text-slate-900">{r.id}</td>
                      <td className="px-4 py-3 text-slate-600">{r.model}</td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-500">{truncateHash(r.recordHash)}</td>
                      <td className="px-4 py-3"><StatusBadge value={r.status} label={statusLabel[r.status]} /></td>
                      <td className="px-4 py-3 text-slate-500">{formatDate(r.recordedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
        </div>
      )}

      <Drawer open={!!selected} onClose={() => setSelectedId(null)} title={selected?.id} description="Inference record details">
        {selected && <RecordDetail record={selected} />}
      </Drawer>
    </>
  )
}
