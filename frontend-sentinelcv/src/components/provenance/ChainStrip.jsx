import { cn } from '@/utils/cn'

const tone = {
  VERIFIED: 'border-emerald-300 bg-emerald-50 text-emerald-700',
  FAILED: 'border-red-300 bg-red-50 text-red-700',
  TAMPERED: 'border-red-400 bg-red-100 text-red-800',
  UNAVAILABLE: 'border-slate-300 bg-slate-50 text-slate-500',
}

/** Horizontal visual of the record chain. Each node reflects the status reported for that record. */
export default function ChainStrip({ records, selectedId, onSelect, firstBrokenIndex }) {
  return (
    <div className="overflow-x-auto pb-2">
      <ol className="flex min-w-max items-center">
        {records.map((r, i) => (
          <li key={r.id} className="flex items-center">
            <button
              onClick={() => onSelect(r.id)}
              aria-label={`Record ${r.id}, status ${r.status}`}
              className={cn(
                'flex h-12 w-24 flex-col items-center justify-center rounded-lg border text-[10px] font-mono transition-shadow',
                tone[r.status], selectedId === r.id && 'ring-2 ring-brand-500 ring-offset-1',
              )}
            >
              <span className="font-semibold">#{i}</span>
              <span className="opacity-70">{r.recordHash.slice(0, 8)}</span>
            </button>
            {i < records.length - 1 && (
              <div className="flex w-8 items-center" aria-hidden="true">
                <div className={cn('h-0.5 w-full', firstBrokenIndex != null && i === firstBrokenIndex ? 'border-t-2 border-dashed border-red-400 bg-transparent' : 'bg-emerald-300')} />
              </div>
            )}
          </li>
        ))}
      </ol>
    </div>
  )
}
