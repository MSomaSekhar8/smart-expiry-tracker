import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Navbar from '@/components/Navbar'
import { Toaster } from '@/components/ui/sonner'
import { useAuth } from '@/context/AuthContext'
import Analytics from '@/pages/Analytics'
import Dashboard from '@/pages/Dashboard'
import ItemForm from '@/pages/ItemForm'
import ItemList from '@/pages/ItemList'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import Settings from '@/pages/Settings'
import { Skeleton } from '@/components/ui/skeleton'

function FullPageLoader() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4">
      <Skeleton className="h-12 w-12 rounded-2xl" />
      <p className="text-sm text-muted-foreground">Loading your pantry…</p>
    </div>
  )
}

function AppShell() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageLoader />

  const isAuthPage = location.pathname === '/login' || location.pathname === '/register'

  if (!user && !isAuthPage) {
    return <Navigate to="/login" replace />
  }
  if (user && isAuthPage) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <div className="min-h-dvh">
      <Toaster />
      {!isAuthPage && <Navbar />}
      <main>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/items" element={<ItemList />} />
          <Route path="/items/new" element={<ItemForm />} />
          <Route path="/items/:id/edit" element={<ItemForm />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export default AppShell