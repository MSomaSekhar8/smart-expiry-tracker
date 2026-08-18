import { NavLink, useNavigate } from 'react-router-dom'
import {
  CalendarClock,
  Home,
  LayoutDashboard,
  LineChart,
  LogOut,
  Settings,
  ShoppingBasket,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Brand, ThemeToggle } from '@/components/AuthShell'
import { useAuth } from '@/context/AuthContext'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/items', label: 'My Items', icon: ShoppingBasket },
  { to: '/analytics', label: 'Analytics', icon: LineChart },
]

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const initials =
    (user?.displayName || user?.email || '?')
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('') || '?'

  const displayName = user?.displayName || user?.email || 'User'

  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
        <Brand to="/dashboard" />
        <nav className="hidden items-center gap-1 md:flex">
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex items-center gap-2 rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label="Account menu">
                <Avatar className="h-8 w-8">
                  <AvatarFallback className="bg-primary/15 text-primary text-xs font-semibold">
                    {initials}
                  </AvatarFallback>
                </Avatar>
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel className="flex flex-col gap-0.5">
                <span className="truncate">{displayName}</span>
                <span className="truncate text-xs font-normal text-muted-foreground">{user?.email}</span>
                <span className="mt-0.5 w-fit rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {user?.role === 'ADMIN' ? 'Coordinator' : 'Member'}
                </span>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => navigate('/settings')}>
                <Settings className="h-4 w-4" />
                Settings
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={async () => {
                  await logout()
                  navigate('/login')
                }}
              >
                <LogOut className="h-4 w-4" />
                Log out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
      <nav className="flex items-center justify-around border-t px-2 py-1.5 md:hidden">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'flex flex-col items-center gap-0.5 rounded-md px-4 py-1 text-[11px] font-medium',
                isActive ? 'text-primary' : 'text-muted-foreground',
              )
            }
          >
            <Icon className="h-4 w-4" />
            {label}
          </NavLink>
        ))}
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            cn(
              'flex flex-col items-center gap-0.5 rounded-md px-4 py-1 text-[11px] font-medium',
              isActive ? 'text-primary' : 'text-muted-foreground',
            )
          }
        >
          <Settings className="h-4 w-4" />
          Settings
        </NavLink>
        <button
          onClick={async () => {
            await logout()
            navigate('/login')
          }}
          className="flex flex-col items-center gap-0.5 rounded-md px-4 py-1 text-[11px] font-medium text-muted-foreground"
          aria-label="Log out"
        >
          <LogOut className="h-4 w-4" />
          Log out
        </button>
      </nav>
    </header>
  )
}

export { CalendarClock, Home }