export type BarcodeFormat = 'EAN_13' | 'EAN_8' | 'UPC_A' | 'UPC_E' | 'OTHER'

export type BarcodeValidation = { ok: true; format: BarcodeFormat } | { ok: false }

function isDigits(value: string): boolean {
  return /^\d+$/.test(value)
}

function checkDigit(payload: string): number {
  const n = payload.length
  let sum = 0
  for (let i = 0; i < n; i++) {
    const weight = (n - 1 - i) % 2 === 0 ? 3 : 1
    sum += (payload.charCodeAt(i) - 48) * weight
  }
  return (10 - (sum % 10)) % 10
}

export function isValidEAN13(value: string): boolean {
  return /^\d{13}$/.test(value) && checkDigit(value.slice(0, 12)) === Number(value[12])
}

export function isValidEAN8(value: string): boolean {
  return /^\d{8}$/.test(value) && checkDigit(value.slice(0, 7)) === Number(value[7])
}

export function isValidUPCA(value: string): boolean {
  return /^\d{12}$/.test(value) && checkDigit(value.slice(0, 11)) === Number(value[11])
}

export function validateBarcode(value: string, formatName?: string): BarcodeValidation {
  const text = value.trim()
  if (!text) return { ok: false }

  if (formatName === 'EAN_13') return isValidEAN13(text) ? { ok: true, format: 'EAN_13' } : { ok: false }
  if (formatName === 'EAN_8') return isValidEAN8(text) ? { ok: true, format: 'EAN_8' } : { ok: false }
  if (formatName === 'UPC_A') return isValidUPCA(text) ? { ok: true, format: 'UPC_A' } : { ok: false }

  if (formatName) {
    return { ok: true, format: 'OTHER' }
  }

  if (isDigits(text)) {
    if (text.length === 13) return isValidEAN13(text) ? { ok: true, format: 'EAN_13' } : { ok: false }
    if (text.length === 8) return isValidEAN8(text) ? { ok: true, format: 'EAN_8' } : { ok: false }
    if (text.length === 12) return isValidUPCA(text) ? { ok: true, format: 'UPC_A' } : { ok: false }
    return { ok: true, format: 'OTHER' }
  }
  return { ok: true, format: 'OTHER' }
}