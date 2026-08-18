import { useState } from 'react'
import { CalendarClock, Loader2, Send } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/context/AuthContext'
import api, { toErrorMessage } from '@/lib/apiClient'
import type { DigestResult } from '@/lib/types'
import { toast } from 'sonner'

export default function Settings() {
  const { user, logout } = useAuth()
  const [testingDigest, setTestingDigest] = useState(false)
  const isAdmin = user?.role === 'ADMIN'

  const runDigestTest = async () => {
    setTestingDigest(true)
    try {
      const { data } = await api.post<DigestResult>('/admin/digest/test')
      toast.success(
        `Digest run complete — ${data.expiringSoonCount} expiring soon, ${data.expiredCount} expired`,
      )
    } catch (err) {
      toast.error(toErrorMessage(err))
    } finally {
      setTestingDigest(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="text-sm text-muted-foreground">Your account and preferences.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Account</CardTitle>
          <CardDescription>
            Signed in as {user?.displayName || 'no display name'} · {user?.email}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            Role: {user?.role === 'ADMIN' ? 'Coordinator' : 'Member'}
          </div>
          <Button variant="outline" onClick={async () => {
            await logout()
            window.location.href = '/login'
          }}>
            Log out
          </Button>
        </CardContent>
      </Card>

      {isAdmin && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <CalendarClock className="h-4 w-4 text-primary" />
              Daily digest
            </CardTitle>
            <CardDescription>
              Emails expiring/expired items every morning at 07:00. Trigger a run manually to
              verify the pipeline.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => void runDigestTest()} disabled={testingDigest}>
              {testingDigest ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
              {testingDigest ? 'Running…' : 'Test digest now'}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}