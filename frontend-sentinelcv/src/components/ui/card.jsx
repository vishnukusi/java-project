import { cn } from '@/utils/cn'

export function Card({ className, ...props }) {
  return <div className={cn('rounded-xl border border-slate-200 bg-white shadow-card', className)} {...props} />
}
export function CardHeader({ className, title, subtitle, action, ...props }) {
  return (
    <div className={cn('flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4', className)} {...props}>
      <div>
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}
export function CardBody({ className, ...props }) {
  return <div className={cn('p-5', className)} {...props} />
}
