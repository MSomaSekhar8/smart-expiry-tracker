import {
  Chart as ChartJS,
  ArcElement,
  BarElement,
  CategoryScale,
  Filler,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
  type ChartOptions,
} from 'chart.js'

export const STATUS_COLORS = {
  SAFE: '#10b981',
  EXPIRING: '#f59e0b',
  EXPIRED: '#f43f5e',
}

export function registerCharts() {
  ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Tooltip,
    Legend,
    Filler,
  )
}

export const chartBaseOptions = (dark: boolean): ChartOptions<'line'> => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: dark ? '#1f2937' : '#ffffff',
      titleColor: dark ? '#f9fafb' : '#111827',
      bodyColor: dark ? '#d1d5db' : '#374151',
      borderColor: dark ? '#374151' : '#e5e7eb',
      borderWidth: 1,
      padding: 10,
      cornerRadius: 8,
    },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: { color: dark ? '#9ca3af' : '#6b7280' },
    },
    y: {
      beginAtZero: true,
      grid: { color: dark ? '#37415133' : '#e5e7eb66' },
      ticks: { color: dark ? '#9ca3af' : '#6b7280' },
    },
  },
})

export function monthLabel(month: string): string {
  const [, mm] = month.split('-')
  const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const idx = Number(mm) - 1
  return names[idx] ?? month
}