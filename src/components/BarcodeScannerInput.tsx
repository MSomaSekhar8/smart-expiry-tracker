import { useEffect, useRef, useState } from 'react'
import { Barcode, Camera, ImagePlus, Loader2, ScanLine } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { toErrorMessage } from '@/lib/apiClient'
import api from '@/lib/apiClient'
import { validateBarcode } from '@/lib/barcodeValidation'
import type { BarcodeLookupResult } from '@/lib/types'
import type { Html5Qrcode } from 'html5-qrcode'
import { toast } from 'sonner'

interface BarcodeScannerInputProps {
  value: string
  onChange: (barcode: string, product?: BarcodeLookupResult | null) => void
  disabled?: boolean
}

type ScannerStatus = 'idle' | 'starting' | 'running' | 'stopping' | 'decoding' | 'error'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const REQUIRED_CONFIRMATIONS = 3
const OBSERVATION_WINDOW = 7
const MAX_CONFIRMATION_TIME_MS = 3000

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

function imageDecodeErrorMessage(err: unknown): string {
  const message = err instanceof Error ? err.message : String(err)
  const lower = message.toLowerCase()
  if (lower.includes('enough patterns') || lower.includes('decode failed') || lower.includes('not found')) {
    return 'No barcode found in this image. Try a clearer photo with the barcode fully visible.'
  }
  if (lower.includes('parse error') || lower.includes('image') || lower.includes('load')) {
    return 'That file could not be read as an image. Choose a clear photo of a barcode.'
  }
  return 'That image could not be decoded. Choose a clearer photo and try again.'
}

export function BarcodeScannerInput({ value, onChange, disabled }: BarcodeScannerInputProps) {
  const [status, setStatus] = useState<ScannerStatus>('idle')
  const [scannerError, setScannerError] = useState<string | null>(null)
  const [cameraHint, setCameraHint] = useState<string | null>(null)
  const [lookingUp, setLookingUp] = useState(false)
  const [lastResult, setLastResult] = useState<BarcodeLookupResult | null>(null)
  const scannerInstanceRef = useRef<Html5Qrcode | null>(null)
  const scanHandledRef = useRef(false)
  const stopRequestedRef = useRef(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const observationsRef = useRef<string[]>([])
  const confirmationTimerRef = useRef<number | null>(null)
  const hasCandidateRef = useRef(false)

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
    stopRequestedRef.current = true
    setStatus('stopping')
    const instance = scannerInstanceRef.current
    scannerInstanceRef.current = null
    await stopInstance(instance)
    resetConfirmation()
    scanHandledRef.current = false
    setCameraHint(null)
    setStatus('idle')
  }

  const clearConfirmationTimer = () => {
    if (confirmationTimerRef.current !== null) {
      window.clearTimeout(confirmationTimerRef.current)
      confirmationTimerRef.current = null
    }
  }

  const resetConfirmation = () => {
    clearConfirmationTimer()
    observationsRef.current = []
    hasCandidateRef.current = false
  }

  const acceptBarcode = (code: string) => {
    if (scanHandledRef.current) return
    clearConfirmationTimer()
    setCameraHint('Barcode captured')
    handleDetected(code)
  }

  const handleCameraResult = (decodedText: string, formatName?: string) => {
    if (scanHandledRef.current || stopRequestedRef.current) return
    const validation = validateBarcode(decodedText, formatName)
    if (!validation.ok) {
      setCameraHint('Barcode could not be confirmed. Hold the product steady and try again.')
      return
    }
    if (!hasCandidateRef.current) {
      hasCandidateRef.current = true
      setCameraHint('Barcode detected — hold steady…')
    }
    const observations = observationsRef.current
    observations.push(decodedText)
    if (observations.length > OBSERVATION_WINDOW) observations.shift()

    const counts = new Map<string, number>()
    for (const value of observations) counts.set(value, (counts.get(value) ?? 0) + 1)
    const ranked = [...counts.entries()].sort((a, b) => b[1] - a[1])
    const top = ranked[0]
    const runnerUp = ranked[1]
    if (top[1] >= REQUIRED_CONFIRMATIONS && (runnerUp ? top[1] >= 2 * runnerUp[1] : true)) {
      acceptBarcode(top[0])
      return
    }
    if (confirmationTimerRef.current === null) {
      confirmationTimerRef.current = window.setTimeout(() => {
        confirmationTimerRef.current = null
        observationsRef.current = []
        hasCandidateRef.current = false
        setCameraHint(null)
      }, MAX_CONFIRMATION_TIME_MS)
    }
  }

  const handleDetected = (decodedText: string) => {
    if (scanHandledRef.current) return
    scanHandledRef.current = true
    stopRequestedRef.current = true
    onChange(decodedText)
    void lookup(decodedText)
    void stopScanner()
  }

  const startScanner = () => {
    if (status === 'starting' || status === 'running' || status === 'stopping' || status === 'decoding') {
      return
    }
    stopRequestedRef.current = false
    setScannerError(null)
    setCameraHint(null)
    scanHandledRef.current = false
    resetConfirmation()
    setStatus('starting')
  }

  const decodeImage = async (file: File) => {
    if (status === 'decoding') return
    if (status === 'starting' || status === 'running' || status === 'stopping') {
      await stopScanner()
    }
    scanHandledRef.current = false
    setScannerError(null)
    setStatus('decoding')
    let instance: Html5Qrcode | null = null
    try {
      const { Html5Qrcode } = await import('html5-qrcode')
      instance = new Html5Qrcode('barcode-reader')
      const result = await instance.scanFileV2(file)
      const validation = validateBarcode(result.decodedText, result.result?.format?.formatName)
      if (!validation.ok) {
        setScannerError('The barcode in this image could not be validated. Try a clearer photo.')
        return
      }
      handleDetected(result.decodedText)
    } catch (err) {
      setScannerError(imageDecodeErrorMessage(err))
    } finally {
      if (instance) {
        try {
          instance.clear()
        } catch {}
      }
      setStatus('idle')
    }
  }

  const handleImageSelected = (file: File | null) => {
    if (!file) return
    if (status === 'decoding') return
    if (!file.type.startsWith('image/')) {
      setScannerError('Please choose an image file.')
      return
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setScannerError('That image is too large. Please choose one under 10 MB.')
      return
    }
    void decodeImage(file)
  }

  useEffect(() => {
    if (status !== 'starting') return
    let cancelled = false

    const start = async () => {
      let instance: Html5Qrcode | null = null
      try {
        const { Html5Qrcode } = await import('html5-qrcode')
        if (cancelled || stopRequestedRef.current) return
        instance = new Html5Qrcode('barcode-reader')
        scannerInstanceRef.current = instance
        await instance.start(
          { facingMode: 'environment' },
          {
            fps: 10,
            qrbox: (viewfinderWidth, viewfinderHeight) => ({
              width: Math.min(Math.floor(viewfinderWidth * 0.8), 260),
              height: Math.min(Math.floor(viewfinderHeight * 0.4), 140),
            }),
          },
          (decodedText, result) => {
            handleCameraResult(decodedText, result?.result?.format?.formatName)
          },
          () => {},
        )
        if (cancelled || stopRequestedRef.current) {
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
      clearConfirmationTimer()
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
        ) : status === 'decoding' ? (
          <Button type="button" variant="outline" disabled>
            <Loader2 className="h-4 w-4 animate-spin" />
            Decoding…
          </Button>
        ) : (
          <Button type="button" variant="outline" onClick={() => void startScanner()} disabled={disabled}>
            <Camera className="h-4 w-4" />
            Scan
          </Button>
        )}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          className="sr-only"
          aria-label="Upload a barcode image"
          onChange={(e) => {
            const file = e.target.files?.[0] ?? null
            e.target.value = ''
            handleImageSelected(file)
          }}
        />
        <Button
          type="button"
          variant="outline"
          disabled={disabled || status === 'decoding'}
          onClick={() => fileInputRef.current?.click()}
        >
          <ImagePlus className="h-4 w-4" />
          Upload image
        </Button>
      </div>
      {(status === 'starting' || status === 'running' || status === 'stopping' || status === 'decoding') && (
        <div id="barcode-reader" className="overflow-hidden rounded-lg border" />
      )}
      {status === 'running' && (
        <p className="text-xs text-muted-foreground">{cameraHint ?? 'Align the barcode inside the frame'}</p>
      )}
      {scannerError && <p className="text-xs font-medium text-destructive">{scannerError}</p>}
      {lastResult && (
        <p className="text-xs text-muted-foreground">
          {lastResult.name ?? 'Product'} {lastResult.brand ? `· ${lastResult.brand}` : ''}{' '}
          {lastResult.cached && '· cached'}
        </p>
      )}
    </div>
  )
}