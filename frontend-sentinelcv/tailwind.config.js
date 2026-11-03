/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
        display: ['"Space Grotesk"', 'Inter', 'sans-serif'],
      },
      colors: {
        ink: { 950: '#070b14', 900: '#0b1220', 800: '#111a2e', 700: '#1a2540', 600: '#243152' },
        brand: { 400: '#38bdf8', 500: '#0ea5e9', 600: '#0284c7' },
        teal: { 400: '#2dd4bf', 500: '#14b8a6', 600: '#0d9488' },
        surface: '#f6f8fb',
      },
      boxShadow: {
        card: '0 1px 2px rgba(15,23,42,.04), 0 1px 3px rgba(15,23,42,.06)',
        pop: '0 12px 40px -12px rgba(15,23,42,.25)',
      },
      keyframes: {
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        scan: { '0%': { top: '0%' }, '100%': { top: '100%' } },
      },
      animation: { scan: 'scan 4s ease-in-out infinite alternate' },
    },
  },
  plugins: [],
}
