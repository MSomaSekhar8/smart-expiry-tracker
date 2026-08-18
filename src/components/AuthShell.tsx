import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Sun, Moon, Sprout } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/context/ThemeContext'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  return (
    <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label="Toggle theme">
      {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </Button>
  )
}

export function Brand({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} className="flex items-center gap-2">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Sprout className="h-4 w-4" />
      </span>
      <span className="font-serif text-lg font-semibold tracking-tight">Pantry Tracker</span>
    </Link>
  )
}

interface AuthShellProps {
  title: string
  subtitle?: string
  children: ReactNode
}

export default function AuthShell({ title, subtitle, children }: AuthShellProps) {
  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden bg-gradient-to-b from-primary/10 via-background to-background p-4">
      <div className="absolute inset-0 -z-0 opacity-40" aria-hidden />
      <div className="relative z-10 w-full max-w-md animate-in">
        <div className="mb-8 flex flex-col items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg">
            <Sprout className="h-6 w-6" />
          </span>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
        </div>
        <div className="rounded-xl border bg-card p-6 shadow-sm">{children}</div>
        <div className="mt-6 flex items-center justify-center">
          <ThemeToggle />
        </div>
      </div>
    </div>
  )
}