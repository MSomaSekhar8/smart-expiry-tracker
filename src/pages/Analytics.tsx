import { useCallback, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { WasteChart } from '@/components/WasteChart'
import { useQuery } from '@/lib/useQuery'
import api, { toErrorMessage } from '@/lib/apiClient'
import { formatINR } from '@/lib/money'
import type { MonthlyWasteResponse } from '@/lib/types'

export default function Analytics() {
  const [months, setMonths] = useState('6')

  const fetcher = useCallback(
    () =>
      api
        .get<MonthlyWasteResponse>('/analytics/monthly-waste', { params: { months } })
        .then((r) => r.data),
    [months],
  )

  const { data, loading, error, refetch } = useQuery<MonthlyWasteResponse>(fetcher, [months])

  const wasteTotal = data?.totalWasted ?? 0
  const costTotal = data?.totalCostLost ?? 0

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
          <p className="text-sm text-muted-foreground">How much has left your pantry as waste.</p>
        </div>
        <Select value={months} onValueChange={setMonths}>
          <SelectTrigger className="w-32" aria-label="Time range">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="3">3 months</SelectItem>
            <SelectItem value="6">6 months</SelectItem>
            <SelectItem value="12">12 months</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {error ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-destructive">{toErrorMessage(error)}</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Items wasted</CardTitle>
              <CardDescription>Last {months} months</CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-16 w-full" />
              ) : (
                <p className="font-serif text-4xl font-semibold">{wasteTotal}</p>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Estimated cost lost</CardTitle>
              <CardDescription>Last {months} months</CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <Skeleton className="h-16 w-full" />
              ) : (
                <p className="font-serif text-4xl font-semibold">{formatINR(costTotal)}</p>
              )}
            </CardContent>
          </Card>
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle className="text-base">Waste trend</CardTitle>
              <CardDescription>Items wasted per month</CardDescription>
            </CardHeader>
            <CardContent>
              <WasteChart data={data} loading={loading} />
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">By the numbers</CardTitle>
          <CardDescription>Monthly breakdown</CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : !data || data.months.length === 0 ? (
            <p className="text-sm text-muted-foreground">No data yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="py-2 pr-4 font-medium">Month</th>
                    <th className="py-2 pr-4 font-medium">Items wasted</th>
                    <th className="py-2 font-medium">Cost lost</th>
                  </tr>
                </thead>
                <tbody>
                  {[...data.months].reverse().map((m) => (
                    <tr key={m.month} className="border-b last:border-0">
                      <td className="py-2 pr-4 font-medium">{m.month}</td>
                      <td className="py-2 pr-4">{m.wastedItems}</td>
                      <td className="py-2">{formatINR(m.costLost)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}