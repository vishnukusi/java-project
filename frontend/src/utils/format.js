export function formatDate(iso, withTime = true) {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  })
}

export const formatNumber = (n) => (n == null ? '—' : n.toLocaleString('en-US'))

export function truncateHash(h, head = 10, tail = 6) {
  if (!h) return '—'
  return h.length <= head + tail + 1 ? h : `${h.slice(0, head)}…${h.slice(-tail)}`
}
