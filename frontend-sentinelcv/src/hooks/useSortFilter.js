import { useMemo, useState } from 'react'

/** Client-side search + sort for table data. */
export function useSortFilter(rows, { searchKeys = [], initialSort = null } = {}) {
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState(initialSort) // { key, dir: 'asc' | 'desc' }

  const result = useMemo(() => {
    let out = rows || []
    const q = query.trim().toLowerCase()
    if (q) out = out.filter((r) => searchKeys.some((k) => String(r[k] ?? '').toLowerCase().includes(q)))
    if (sort) {
      out = [...out].sort((a, b) => {
        const av = a[sort.key] ?? ''
        const bv = b[sort.key] ?? ''
        const cmp = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv))
        return sort.dir === 'asc' ? cmp : -cmp
      })
    }
    return out
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, query, sort])

  const toggleSort = (key) =>
    setSort((s) => (s?.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

  return { rows: result, query, setQuery, sort, toggleSort }
}
