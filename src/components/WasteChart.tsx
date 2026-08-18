import { Line } from 'react-chartjs-2'
import { chartBaseOptions, monthLabel, registerCharts, STATUS_COLORS } from '@/lib/chart'
import type { MonthlyWasteResponse } from '@/lib/types'
import { useTheme } from '@/context/ThemeContext'

registerCharts()

interface WasteChartProps {
  data: MonthlyWasteResponse | null
  loading?: boolean
}

export function WasteChart({ data, loading }: WasteChartProps) {
  const { theme } = useTheme()
  const dark = theme === 'dark'

  if (loading || !data) {
    return <div className="h-64 animate-pulse rounded-lg bg-muted" aria-label="Loading chart" />
  }

  const labels = data.months.map((m) => monthLabel(m.month))
  const chartData = {
    labels,
    datasets: [
      {
        label: 'Items wasted',
        data: data.months.map((m) => m.wastedItems),
        borderColor: STATUS_COLORS.EXPIRED,
        backgroundColor: `${STATUS_COLORS.EXPIRED}22`,
        fill: true,
        tension: 0.35,
        pointRadius: 3,
        pointBackgroundColor: STATUS_COLORS.EXPIRED,
      },
    ],
  }

  return (
    <div className="h-64">
      <Line data={chartData} options={chartBaseOptions(dark)} />
    </div>
  )
}