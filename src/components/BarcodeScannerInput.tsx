import { useEffect, useRef, useState } from 'react'
import { Barcode, Camera, Loader2, ScanLine } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { toErrorMessage } from '@/lib/apiClient'
import api from '@/lib/apiClient'
import type { BarcodeLookupResult } from '@/lib/types'
import { toast } from 'sonner'

interface BarcodeScannerInputProps {
  value: string
  onChange: (barcode: string, product?: BarcodeLookupResult | null) => void
  disabled?: boolean
}

export function BarcodeScannerInput({ value, onChange, disabled }: BarcodeScannerInputProps) {
  const [scanning, setScanning] = useState(false)
  const [lookingUp, setLookingUp] = useState(false)
  const [lastResult, setLastResult] = useState<BarcodeLookupResult | null>(null)
  const scannerRef = useRef<HTMLDivElement | null>(null)
  const html5QrCodeRef = useRef<{ stop: () => Promise<void> } | null>(null)

  useEffect(() => {
    return () => {
      html5QrCodeRef.current?.stop().catch(() => {})
    }
  }, [])

  const startScanner = async () => {
    if (scanning) return
    const { Html5Qrcode } = await import('html5-qrcode')
    const scanner = new Html5Qrcode('barcode-reader')
    html5QrCodeRef.current = scanner
    setScanning(true)
    try {
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 220, height: 120 } },
        (decodedText) => {
          onChange(decodedText)
          scanner.stop().catch(() => {})
          setScanning(false)
          void lookup(decodedText)
        },
        () => {},
      )
    } catch (err) {
      setScanning(false)
      toast.error(toErrorMessage(err))
    }
  }

  const stopScanner = () => {
    html5QrCodeRef.current?.stop().catch(() => {})
    setScanning(false)
  }

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
        {scanning ? (
          <Button type="button" variant="outline" onClick={stopScanner}>
            Stop
          </Button>
        ) : (
          <Button type="button" variant="outline" onClick={() => void startScanner()} disabled={disabled}>
            <Camera className="h-4 w-4" />
            Scan
          </Button>
        )}
      </div>
      {scanning && <div id="barcode-reader" ref={scannerRef} className="overflow-hidden rounded-lg border" />}
      {lastResult && (
        <p className="text-xs text-muted-foreground">
          {lastResult.name ?? 'Product'} {lastResult.brand ? `· ${lastResult.brand}` : ''}{' '}
          {lastResult.cached && '· cached'}
        </p>
      )}
    </div>
  )
}