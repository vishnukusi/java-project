import { cn } from '@/utils/cn'

export function Skeleton({ className }) {
  return <div className={cn('skeleton h-4 w-full', className)} aria-hidden="true" />
}

export function TableSkeleton({ rows = 5, cols = 5 }) {
  return (
    <div className="space-y-3 p-5" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="grid gap-4" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0,1fr))` }}>
          {Array.from({ length: cols }).map((__, c) => <Skeleton key={c} className="h-5" />)}
        </div>
      ))}
    </div>
  )
}

export function CardSkeleton({ height = 'h-64' }) {
  return <div className={cn('skeleton w-full rounded-xl', height)} role="status" aria-label="Loading" />
}
