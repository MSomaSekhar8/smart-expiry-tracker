import { useEffect, useRef, useState } from 'react'
import { Barcode, Camera, Loader2, ScanLine } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { toErrorMessage } from '@/lib/apiClient'
import api from '@/lib/apiClient'
import type { BarcodeLookupResult } from '@/lib/types'
import type { Html5Qrcode } from 'html5-qrcode'
import { toast } from 'sonner'

interface BarcodeScannerInputProps {
  value: string
  onChange: (barcode: string, product?: BarcodeLookupResult | null) => void
  disabled?: boolean
}

type ScannerStatus = 'idle' | 'starting' | 'running' | 'stopping' | 'error'

function cameraErrorMessage(err: unknown): string {
  const message = err instanceof Error ? err.message : String(err)
  const lower = message.toLowerCase()
  if (lower.includes('permission') || lower.includes('notallowed') || lower.includes('denied')) {
    return 'Camera permission was denied. Allow camera access in your browser and try again.'
  }
  if (lower.includes('notfound') || lower.includes('no camera')) {
    return 'No camera was found on this device.'
  }
  if (lower.includes('notreadable') || lower.includes('in use') || lower.includes('already')) {
    return 'The camera could not be started — it may be in use by another application.'
  }
  if (lower.includes('overconstrained')) {
    return 'No camera matching the required settings was found.'
  }
  return 'The camera could not be started. Check camera permissions and try again.'
}

export function BarcodeScannerInput({ value, onChange, disabled }: BarcodeScannerInputProps) {
  const [status, setStatus] = useState<ScannerStatus>('idle')
  const [scannerError, setScannerError] = useState<string | null>(null)
  const [lookingUp, setLookingUp] = useState(false)
  const [lastResult, setLastResult] = useState<BarcodeLookupResult | null>(null)
  const scannerInstanceRef = useRef<Html5Qrcode | null>(null)
  const scanHandledRef = useRef(false)

  const lookup = async (code: string) => {
    if (!/^\d{8,14}$/.test(code)) {
      toast.error("That barcode doesn't look valid (8–14 digits)")
      return
    }
    setLookingUp(true)
    try {
      const { data } = await api.get<BarcodeLookupResult>(`/barcode/${code}`)
      setLastResult(data)
      onChange(code, data)
      toast.success(
        data.name ? `Found: ${data.name}${data.brand ? ` (${data.brand})` : ''}` : 'Barcode recognized',
      )
    } catch (err) {
      toast.error(toErrorMessage(err))
    } finally {
      setLookingUp(false)
    }
  }

  const stopInstance = async (instance: Html5Qrcode | null) => {
    if (!instance) return
    try {
      if (instance.isScanning) await instance.stop()
      instance.clear()
    } catch {}
  }

  const stopScanner = async () => {
    setStatus('stopping')
    const instance = scannerInstanceRef.current
    scannerInstanceRef.current = null
    await stopInstance(instance)
    scanHandledRef.current = false
    setStatus('idle')
  }

  const handleDetected = (decodedText: string) => {
    if (scanHandledRef.current) return
    scanHandledRef.current = true
    onChange(decodedText)
    void lookup(decodedText)
    void stopScanner()
  }

  const startScanner = () => {
    if (status === 'starting' || status === 'running' || status === 'stopping') return
    setScannerError(null)
    scanHandledRef.current = false
    setStatus('starting')
  }

  useEffect(() => {
    if (status !== 'starting') return
    let cancelled = false

    const start = async () => {
      let instance: Html5Qrcode | null = null
      try {
        const { Html5Qrcode } = await import('html5-qrcode')
        if (cancelled) return
        instance = new Html5Qrcode('barcode-reader')
        scannerInstanceRef.current = instance
        await instance.start(
          { facingMode: 'environment' },
          { fps: 10, qrbox: { width: 220, height: 120 } },
          (decodedText) => {
            void handleDetected(decodedText)
          },
          () => {},
        )
        if (cancelled) {
          await stopInstance(instance)
          return
        }
        setStatus('running')
      } catch (err) {
        if (cancelled) return
        scannerInstanceRef.current = null
        if (instance) {
          try {
            instance.clear()
          } catch {}
        }
        setScannerError(cameraErrorMessage(err))
        setStatus('error')
      }
    }

    void start()

    return () => {
      cancelled = true
    }
  }, [status])

  useEffect(() => {
    return () => {
      const instance = scannerInstanceRef.current
      scannerInstanceRef.current = null
      if (instance) {
        try {
          if (instance.isScanning) void instance.stop().catch(() => {})
          instance.clear()
        } catch {}
      }
    }
  }, [])

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Barcode className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="Scan or type a barcode"
            inputMode="numeric"
            value={value}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void lookup(value)
            }}
          />
        </div>
        {value ? (
          <Button
            type="button"
            variant="outline"
            disabled={disabled || lookingUp}
            onClick={() => void lookup(value)}
          >
            {lookingUp ? <Loader2 className="h-4 w-4 animate-spin" /> : <ScanLine className="h-4 w-4" />}
            Look up
          </Button>
        ) : null}
        {status === 'running' ? (
          <Button type="button" variant="outline" onClick={() => void stopScanner()}>
            Stop
          </Button>
        ) : status === 'starting' || status === 'stopping' ? (
          <Button type="button" variant="outline" disabled>
            <Loader2 className="h-4 w-4 animate-spin" />
            {status === 'starting' ? 'Starting…' : 'Stopping…'}
          </Button>
        ) : (
          <Button type="button" variant="outline" onClick={() => void startScanner()} disabled={disabled}>
            <Camera className="h-4 w-4" />
            Scan
          </Button>
        )}
      </div>
      {(status === 'starting' || status === 'running' || status === 'stopping') && (
        <div id="barcode-reader" className="overflow-hidden rounded-lg border" />
      )}
      {status === 'error' && scannerError && (
        <p className="text-xs font-medium text-destructive">{scannerError}</p>
      )}
      {lastResult && (
        <p className="text-xs text-muted-foreground">
          {lastResult.name ?? 'Product'} {lastResult.brand ? `· ${lastResult.brand}` : ''}{' '}
          {lastResult.cached && '· cached'}
        </p>
      )}
    </div>
  )
}