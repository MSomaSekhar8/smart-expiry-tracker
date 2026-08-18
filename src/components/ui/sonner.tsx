import { Toaster as Sonner, type ToasterProps } from 'sonner'
import { useTheme } from '@/context/ThemeContext'

function Toaster(props: ToasterProps) {
  const { theme } = useTheme()
  return (
    <Sonner
      theme={theme}
      position="bottom-right"
      toastOptions={{
        style: {
          borderRadius: '0.75rem',
        },
      }}
      {...props}
    />
  )
}

export { Toaster }