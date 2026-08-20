#Requires -Version 5.1
<#
.SYNOPSIS
  Production smoke test for the Smart Expiry & Pantry Waste Tracker backend.

.DESCRIPTION
  Runs a full authenticated flow against the deployed production backend:
  health, register, login, refresh, /me, categories, item CRUD, waste, logout,
  and post-logout refresh rejection.

  A unique throwaway email/password is generated and used only in memory.
  The dedicated smoke-test item is deleted at the end. Nothing is printed
  except a PASS/FAIL summary with sanitized error messages for failures.

.NOTES
  Run with:
    powershell -ExecutionPolicy Bypass -File .\production-smoke-test.ps1

  Compatible with Windows PowerShell 5.1. Uses only built-in cmdlets.
  Never prints tokens, cookies, passwords, or credentials.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Windows PowerShell 5.1 defaults to older TLS; Render requires TLS 1.2+.
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$BaseUrl = 'https://smart-expiry-tracker-pn5i.onrender.com'
$Api = "$BaseUrl/api"

$script:Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:AccessToken = $null
$script:Continue = $true
$script:TestItemId = $null
$script:CategoryId = $null
$script:Email = $null
$script:Password = $null
$script:LastRequest = $null
$script:Results = @()

function Add-TestResult {
    param([string]$Name, [bool]$Pass)
    $detail = ''
    if (-not $Pass -and $null -ne $script:LastRequest) {
        $detail = Get-SanitizedMessage -Raw $script:LastRequest.Raw -Status $script:LastRequest.Status
    }
    $script:Results += [pscustomobject]@{ Name = $Name; Pass = $Pass; Detail = $detail }
    if (-not $Pass) { $script:Continue = $false }
}

function Get-SanitizedMessage {
    param([string]$Raw, [int]$Status)
    $msg = "HTTP $Status"
    if ($Raw) {
        try {
            $parsed = ConvertFrom-ApiJson $Raw
            if ($parsed.message) { $msg = "$msg - $($parsed.message)" }
            elseif ($parsed.status) { $msg = "$msg - $($parsed.status)" }
        }
        catch {
            $clean = ($Raw -replace '\s+', ' ').Trim()
            if ($clean.Length -gt 120) { $clean = $clean.Substring(0, 120) + '...' }
            $msg = "$msg - $clean"
        }
    }
    return $msg
}

function ConvertFrom-ApiJson {
    param([string]$Content)
    if ([string]::IsNullOrWhiteSpace($Content)) { return $null }
    $parsed = ConvertFrom-Json -InputObject $Content
    # PS 5.1 Invoke-WebRequest wraps top-level JSON arrays in an extra [ ]
    # layer: "[{...},{...}]" arrives as "[[{...},{...}]]". Unwrap it so array
    # responses behave identically on PS 5.1 and PowerShell 7. Single-object
    # responses are unaffected.
    if ($parsed -is [System.Array] -and $parsed.Count -eq 1 -and $parsed[0] -is [System.Array]) {
        return $parsed[0]
    }
    return $parsed
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body = $null,
        [switch]$UseAuth
    )
    $headers = @{}
    # PS 5.1 stores request headers into the WebSession and replays them on
    # later calls, so a stale Authorization header would leak into requests
    # that must be unauthenticated. Clear per call (no-op in PowerShell 7).
    $script:Session.Headers.Clear()
    if ($UseAuth) {
        $headers['Authorization'] = 'Bearer ' + $script:AccessToken
    }
    $params = @{
        Method         = $Method
        Uri            = "$Api$Path"
        Headers        = $headers
        WebSession     = $script:Session
        TimeoutSec     = 30
        # PS 5.1 only: without this, Invoke-WebRequest uses the mshtml/DOM
        # parser, which breaks on non-HTML (JSON) responses behind Cloudflare
        # and hangs into "operation has timed out". No-op in PowerShell 7.
        UseBasicParsing = $true
    }
    if ($PSVersionTable.PSVersion.Major -ge 7) {
        # PS 7 only: return non-2xx responses instead of throwing, so the
        # status code and body are captured uniformly (5.1 keeps try/catch).
        $params['SkipHttpErrorCheck'] = $true
    }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json'
        $params['Body'] = ($Body | ConvertTo-Json -Compress -Depth 6)
    }
    try {
        $resp = Invoke-WebRequest @params
        $status = [int]$resp.StatusCode
        $raw = $null
        if ($status -ge 400) { $raw = $resp.Content }
        $script:LastRequest = [pscustomobject]@{ Status = $status; Raw = $raw }
        return [pscustomobject]@{
            Status  = $status
            Success = ($status -lt 400)
            Content = $resp.Content
            Raw     = $raw
        }
    }
    catch {
        $status = -1
        $raw = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    $reader.Dispose()
                }
            }
            catch {}
        }
        if (-not $raw) { $raw = $_.Exception.Message }
        $script:LastRequest = [pscustomobject]@{ Status = $status; Raw = $raw }
        return [pscustomobject]@{
            Status  = $status
            Success = $false
            Content = $null
            Raw     = $raw
        }
    }
}

# ---------------------------------------------------------------------------
# Test sequence
# ---------------------------------------------------------------------------
do {
    # 1. HEALTH
    $r = Invoke-Api -Method GET -Path '/health'
    $statusUp = $false
    if ($r.Success) {
        $statusUp = ((ConvertFrom-ApiJson $r.Content).status -eq 'UP')
    }
    Add-TestResult 'Health' ($r.Success -and $statusUp)
    if (-not $script:Continue) { break }

    # Unique throwaway test identity (never real user data)
    $script:Email = "smoke.$([Guid]::NewGuid().ToString('N'))@example.com"
    $script:Password = ([Guid]::NewGuid().ToString('N') + 'Xx9!')

    # 2. REGISTER
    $r = Invoke-Api -Method POST -Path '/auth/register' -Body @{
        email       = $script:Email
        password    = $script:Password
        displayName = 'Render Smoke Test'
    }
    Add-TestResult 'Register' ($r.Success -and $r.Status -eq 201)
    if (-not $script:Continue) { break }
    $script:AccessToken = ((ConvertFrom-ApiJson $r.Content).accessToken)

    # 3. LOGIN
    $r = Invoke-Api -Method POST -Path '/auth/login' -Body @{
        email    = $script:Email
        password = $script:Password
    }
    Add-TestResult 'Login' ($r.Success -and $r.Status -eq 200)
    if (-not $script:Continue) { break }
    $script:AccessToken = ((ConvertFrom-ApiJson $r.Content).accessToken)

    # 4. REFRESH (cookie only, no Authorization header; rotation updates the cookie)
    $r = Invoke-Api -Method POST -Path '/auth/refresh'
    Add-TestResult 'Refresh' ($r.Success -and $r.Status -eq 200)
    if (-not $script:Continue) { break }
    $script:AccessToken = ((ConvertFrom-ApiJson $r.Content).accessToken)

    # 5. AUTHENTICATED /ME
    $r = Invoke-Api -Method GET -Path '/auth/me' -UseAuth
    if ($r.Status -eq 401) {
        Write-Host 'Authenticated /me failed with 401.' -ForegroundColor Red
        Add-TestResult 'Authenticated /me' $false
        break
    }
    Add-TestResult 'Authenticated /me' ($r.Success -and $r.Status -eq 200)
    if (-not $script:Continue) { break }

    # 6. CATEGORIES
    $r = Invoke-Api -Method GET -Path '/categories' -UseAuth
    $categoryList = @()
    if ($r.Success) { $categoryList = @(ConvertFrom-ApiJson $r.Content) }
    Add-TestResult 'Categories' ($r.Success -and $categoryList.Count -gt 0)
    if (-not $script:Continue) { break }
    $script:CategoryId = $categoryList[0].id

    $today = (Get-Date).ToString('yyyy-MM-dd')
    $expiry = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')

    # 7. CREATE ITEM
    $r = Invoke-Api -Method POST -Path '/items' -UseAuth -Body @{
        name           = 'Render Smoke Test Item'
        barcode        = '990000000001'
        categoryId     = $script:CategoryId
        quantity       = 2
        unit           = 'kg'
        purchaseDate   = $today
        expiryDate     = $expiry
        shelfLifeDays  = 30
        notes          = 'Temporary production smoke test'
    }
    Add-TestResult 'Create Item' ($r.Success -and $r.Status -eq 201)
    if (-not $script:Continue) { break }
    $script:TestItemId = ((ConvertFrom-ApiJson $r.Content).id)

    # 8. GET ITEM
    $r = Invoke-Api -Method GET -Path "/items/$($script:TestItemId)" -UseAuth
    $idMatches = $false
    if ($r.Success) { $idMatches = (((ConvertFrom-ApiJson $r.Content).id) -eq $script:TestItemId) }
    Add-TestResult 'Get Item' ($r.Success -and $idMatches)
    if (-not $script:Continue) { break }

    # 9. UPDATE ITEM
    $r = Invoke-Api -Method PUT -Path "/items/$($script:TestItemId)" -UseAuth -Body @{
        name           = 'Render Smoke Test Item Updated'
        barcode        = '990000000001'
        categoryId     = $script:CategoryId
        quantity       = 3
        unit           = 'kg'
        purchaseDate   = $today
        expiryDate     = $expiry
        shelfLifeDays  = 30
        notes          = 'Updated production smoke test'
    }
    $updated = $false
    if ($r.Success) {
        $updatedItem = (ConvertFrom-ApiJson $r.Content)
        $updated = ([decimal]$updatedItem.quantity -eq 3 -and $updatedItem.unit -eq 'kg')
    }
    Add-TestResult 'Update Item' ($r.Success -and $updated)
    if (-not $script:Continue) { break }

    # 10. MARK WASTED
    $r = Invoke-Api -Method POST -Path "/items/$($script:TestItemId)/waste" -UseAuth -Body @{
        quantityWasted    = 1
        estimatedCostLost = 50
    }
    Add-TestResult 'Mark Wasted' $r.Success
    if (-not $script:Continue) { break }

    # 11. LIST ITEMS
    # markWasted deletes the item from the pantry by design (only a WasteLog
    # remains), so the dedicated test item must NOT appear in the list.
    $r = Invoke-Api -Method GET -Path '/items' -UseAuth
    $present = $false
    if ($r.Success) {
        $present = [bool](@(ConvertFrom-ApiJson $r.Content) | Where-Object { $_.id -eq $script:TestItemId })
    }
    Add-TestResult 'List Items' ($r.Success -and -not $present)
    if (-not $script:Continue) { break }

    # 12. DELETE ITEM
    # The waste step already removed the dedicated test item, so DELETE must
    # report 404 "Item not found" - confirming the item is gone and that no
    # other (production) item was touched.
    $r = Invoke-Api -Method DELETE -Path "/items/$($script:TestItemId)" -UseAuth
    Add-TestResult 'Delete Item' ($r.Status -eq 404)
    if (-not $script:Continue) { break }

    # 13. UNAUTHENTICATED /ME
    $r = Invoke-Api -Method GET -Path '/auth/me'
    Add-TestResult 'Unauthenticated /me' ($r.Status -eq 401)
    if (-not $script:Continue) { break }

    # 14. LOGOUT (refresh cookie still available)
    $r = Invoke-Api -Method POST -Path '/auth/logout'
    Add-TestResult 'Logout' $r.Success
    if (-not $script:Continue) { break }

    # 15. REFRESH AFTER LOGOUT (session revoked -> must fail)
    $r = Invoke-Api -Method POST -Path '/auth/refresh'
    Add-TestResult 'Refresh after logout' ($r.Status -eq 401)
} while ($false)

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
$allPassed = -not ($script:Results | Where-Object { -not $_.Pass })
$stepNames = @(
    'Health', 'Register', 'Login', 'Refresh', 'Authenticated /me',
    'Categories', 'Create Item', 'Get Item', 'Update Item', 'Mark Wasted',
    'List Items', 'Delete Item', 'Unauthenticated /me', 'Logout',
    'Refresh after logout'
)

Write-Host ''
Write-Host '========================================' -ForegroundColor Cyan
Write-Host 'SMART EXPIRY TRACKER' -ForegroundColor Cyan
Write-Host 'PRODUCTION SMOKE TEST' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
foreach ($name in $stepNames) {
    $result = $script:Results | Where-Object { $_.Name -eq $name }
    if ($result) {
        if ($result.Pass) { Write-Host ('[PASS] ' + $name) -ForegroundColor Green }
        else { Write-Host ('[FAIL] ' + $name) -ForegroundColor Red }
    }
    else {
        Write-Host ('[SKIP] ' + $name) -ForegroundColor DarkYellow
    }
}
Write-Host '========================================' -ForegroundColor Cyan

if (-not $allPassed) {
    Write-Host ''
    Write-Host 'FAILED TESTS:' -ForegroundColor Red
    foreach ($f in ($script:Results | Where-Object { -not $_.Pass })) {
        Write-Host ('  Endpoint: ' + $f.Name)
        if ($f.Detail) { Write-Host ('  ' + $f.Detail) }
    }
    Write-Host ''
    Write-Host 'RESULT: FAIL' -ForegroundColor Red
}
else {
    Write-Host 'RESULT: PASS' -ForegroundColor Green
}
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''

# Safety: never leave test credentials in the session output.
$script:AccessToken = $null
$script:Password = $null
$script:Email = $null