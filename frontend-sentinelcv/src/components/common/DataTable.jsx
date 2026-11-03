import { ArrowDown, ArrowUp, ArrowUpDown, Search } from 'lucide-react'
import { cn } from '@/utils/cn'
import { Input } from '@/components/ui/input'

export function SearchBox({ value, onChange, placeholder = 'Search…', className }) {
  return (
    <div className={cn('relative w-full max-w-xs', className)}>
      <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
      <Input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} className="pl-9" aria-label={placeholder} />
    </div>
  )
}

export function SortHeader({ label, sortKey, sort, onSort, className }) {
  const active = sort?.key === sortKey
  const Icon = !active ? ArrowUpDown : sort.dir === 'asc' ? ArrowUp : ArrowDown
  return (
    <th
      scope="col" aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={cn('px-4 py-2.5 text-left', className)}
    >
      <button onClick={() => onSort(sortKey)} className="label inline-flex items-center gap-1 hover:text-slate-800">
        {label}
        <Icon size={12} className={active ? 'text-brand-600' : 'text-slate-300'} />
      </button>
    </th>
  )
}

export function Th({ children, className }) {
  return <th scope="col" className={cn('label px-4 py-2.5 text-left', className)}>{children}</th>
}

export function Table({ children, className }) {
  return (
    <div className={cn('overflow-x-auto', className)}>
      <table className="w-full min-w-[640px] border-collapse text-sm">{children}</table>
    </div>
  )
}
