import { Doughnut } from 'react-chartjs-2'
import { STATUS_COLORS, registerCharts } from '@/lib/chart'
import type { ItemWithStatus } from '@/lib/types'
import { useTheme } from '@/context/ThemeContext'

registerCharts()

interface CategoryDonutProps {
  items: ItemWithStatus[]
  loading?: boolean
}

export function CategoryDonut({ items, loading }: CategoryDonutProps) {
  const { theme } = useTheme()
  const dark = theme === 'dark'

  if (loading) {
    return <div className="h-56 animate-pulse rounded-lg bg-muted" aria-label="Loading chart" />
  }

  const counts = new Map<string, number>()
  for (const item of items) {
    counts.set(item.category, (counts.get(item.category) ?? 0) + 1)
  }
  const entries = [...counts.entries()].sort((a, b) => b[1] - a[1])
  if (entries.length === 0) {
    return <div className="flex h-56 items-center justify-center text-sm text-muted-foreground">No items yet</div>
  }

  const palette = ['#10b981', '#0ea5e9', '#f59e0b', '#8b5cf6', '#f43f5e', '#14b8a6']
  const data = {
    labels: entries.map(([name]) => name),
    datasets: [
      {
        data: entries.map(([, count]) => count),
        backgroundColor: entries.map((_, i) => palette[i % palette.length]),
        borderWidth: 0,
      },
    ],
  }

  return (
    <div className="relative h-56">
      <Doughnut
        data={data}
        options={{
          responsive: true,
          maintainAspectRatio: false,
          cutout: '68%',
          plugins: {
            legend: {
              position: 'bottom',
              labels: {
                color: dark ? '#d1d5db' : '#374151',
                boxWidth: 10,
                boxHeight: 10,
                usePointStyle: true,
                padding: 12,
              },
            },
            tooltip: {
              backgroundColor: dark ? '#1f2937' : '#ffffff',
              titleColor: dark ? '#f9fafb' : '#111827',
              bodyColor: dark ? '#d1d5db' : '#374151',
              borderColor: dark ? '#374151' : '#e5e7eb',
              borderWidth: 1,
            },
          },
        }}
      />
    </div>
  )
}