import { useEffect } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import { cn } from '@/utils/cn'

function useEscape(open, onClose) {
  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])
}

function Shell({ open, onClose, children, side }) {
  useEscape(open, onClose)
  return (
    <AnimatePresence>
      {open && (
        <div className={cn('fixed inset-0 z-50 flex', side ? 'justify-end' : 'items-center justify-center p-4')}>
          <motion.div
            className="absolute inset-0 bg-ink-950/50"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }} onClick={onClose}
          />
          {children}
        </div>
      )}
    </AnimatePresence>
  )
}

function Head({ title, description, onClose }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
      <div>
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        {description && <p className="mt-0.5 text-xs text-slate-500">{description}</p>}
      </div>
      <button onClick={onClose} aria-label="Close" className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
        <X size={18} />
      </button>
    </div>
  )
}

export function Dialog({ open, onClose, title, description, children, footer, className }) {
  return (
    <Shell open={open} onClose={onClose}>
      <motion.div
        role="dialog" aria-modal="true" aria-label={title}
        className={cn('relative w-full max-w-lg rounded-xl bg-white shadow-pop', className)}
        initial={{ opacity: 0, y: 8, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 8, scale: 0.98 }} transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
      >
        <Head title={title} description={description} onClose={onClose} />
        <div className="max-h-[70vh] overflow-y-auto p-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-3">{footer}</div>}
      </motion.div>
    </Shell>
  )
}

export function Drawer({ open, onClose, title, description, children, className }) {
  return (
    <Shell open={open} onClose={onClose} side>
      <motion.aside
        role="dialog" aria-modal="true" aria-label={title}
        className={cn('relative flex h-full w-full max-w-xl flex-col bg-white shadow-pop', className)}
        initial={{ x: 48, opacity: 0 }} animate={{ x: 0, opacity: 1 }}
        exit={{ x: 48, opacity: 0 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
      >
        <Head title={title} description={description} onClose={onClose} />
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
      </motion.aside>
    </Shell>
  )
}
