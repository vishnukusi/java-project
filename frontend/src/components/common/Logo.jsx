import { cn } from '@/utils/cn'

export function LogoMark({ size = 28, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#111a2e" />
      <path d="M16 5l9 3.5v7c0 5.2-3.6 9.2-9 11.5-5.4-2.3-9-6.3-9-11.5v-7L16 5z" fill="none" stroke="#2dd4bf" strokeWidth="1.8" />
      <circle cx="16" cy="15" r="3.2" fill="#38bdf8" />
      <circle cx="16" cy="15" r="6" fill="none" stroke="#38bdf8" strokeOpacity=".35" strokeWidth="1" />
    </svg>
  )
}

export function Logo({ className, textClass = 'text-white', collapsed = false }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <LogoMark />
      {!collapsed && (
        <span className={cn('font-display text-[17px] font-semibold tracking-tight', textClass)}>
          Sentinel<span className="text-teal-400">CV</span>
        </span>
      )}
    </div>
  )
}
