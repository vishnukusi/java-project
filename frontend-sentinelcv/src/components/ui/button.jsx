import { forwardRef } from 'react'
import { cn } from '@/utils/cn'

const variants = {
  default: 'bg-brand-600 text-white hover:bg-brand-500 shadow-sm',
  dark: 'bg-ink-900 text-white hover:bg-ink-700',
  outline: 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
  ghost: 'text-slate-600 hover:bg-slate-100',
  light: 'bg-white text-ink-900 hover:bg-slate-100',
  glass: 'border border-white/15 bg-white/5 text-white hover:bg-white/10',
  danger: 'bg-red-600 text-white hover:bg-red-500',
}
const sizes = { sm: 'h-8 px-3 text-xs', md: 'h-9 px-4 text-sm', lg: 'h-12 px-6 text-base' }

export const Button = forwardRef(function Button(
  { className, variant = 'default', size = 'md', ...props }, ref,
) {
  return (
    <button
      ref={ref}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors duration-150',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2',
        'disabled:pointer-events-none disabled:opacity-50',
        variants[variant], sizes[size], className,
      )}
      {...props}
    />
  )
})
