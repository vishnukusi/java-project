import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, AlertTriangle, Info } from 'lucide-react'
import { cn } from '@/utils/cn'

const ToastContext = createContext(null)
const icons = { success: CheckCircle2, error: AlertTriangle, info: Info }
const tone = {
  success: 'text-emerald-600',
  error: 'text-red-600',
  info: 'text-brand-600',
}

export function ToastProvider({ children }) {
  const [items, setItems] = useState([])
  const push = useCallback((type, message) => {
    const id = Math.random().toString(36).slice(2)
    setItems((s) => [...s, { id, type, message }])
    setTimeout(() => setItems((s) => s.filter((i) => i.id !== id)), 4000)
  }, [])
  const api = useMemo(() => ({
    success: (m) => push('success', m),
    error: (m) => push('error', m),
    info: (m) => push('info', m),
  }), [push])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 flex-col gap-2" aria-live="polite">
        <AnimatePresence>
          {items.map((t) => {
            const Icon = icons[t.type]
            return (
              <motion.div
                key={t.id} layout
                initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: 24 }}
                transition={{ duration: 0.2 }}
                className="pointer-events-auto flex items-start gap-2.5 rounded-lg border border-slate-200 bg-white p-3 text-sm shadow-pop"
              >
                <Icon size={18} className={cn('mt-0.5 shrink-0', tone[t.type])} />
                <span className="text-slate-700">{t.message}</span>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}
