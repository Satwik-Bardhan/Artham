# ═══════════════════════════════════════════════════════════
#  Artham: Final Delta Migration — Firebase → Supabase
#  Run this RIGHT BEFORE publishing the new app update.
#  It catches any new cashbooks, transactions, and categories
#  that users created since the last migration.
# ═══════════════════════════════════════════════════════════
$ErrorActionPreference = "Continue"

$FIREBASE_DB_URL = "https://artham-67-default-rtdb.firebaseio.com"
$FIREBASE_SECRET = if ($env:FIREBASE_SECRET) { $env:FIREBASE_SECRET } else { Read-Host "Enter FIREBASE_SECRET" }

$SUPABASE_URL = "https://pgrgcpyysvuzozylgump.supabase.co"
$SUPABASE_SERVICE_KEY = if ($env:SUPABASE_SERVICE_KEY) { $env:SUPABASE_SERVICE_KEY } else { Read-Host "Enter SUPABASE_SERVICE_KEY" }

# ───────────────────────────────────────
#  Helper Functions
# ───────────────────────────────────────
function Firebase-Get {
    param([string]$Path)
    $url = "$FIREBASE_DB_URL/$Path.json?auth=$FIREBASE_SECRET"
    try {
        return Invoke-RestMethod -Uri $url -Method Get -ContentType "application/json"
    } catch {
        Write-Host "  [ERROR] Firebase GET $Path : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Supabase-Upsert {
    param([string]$Table, [object]$Data)
    $url = "$SUPABASE_URL/rest/v1/${Table}?on_conflict=id"
    $headers = @{
        "apikey" = $SUPABASE_SERVICE_KEY
        "Authorization" = "Bearer $SUPABASE_SERVICE_KEY"
        "Content-Type" = "application/json"
        "Prefer" = "resolution=merge-duplicates"
    }
    $body = $Data | ConvertTo-Json -Depth 10 -Compress
    try {
        $null = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body -ContentType "application/json; charset=utf-8"
        return $true
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errBody = $reader.ReadToEnd()
            Write-Host "  [ERROR] Supabase $Table : $errBody" -ForegroundColor Red
        } else {
            Write-Host "  [ERROR] Supabase $Table : $($_.Exception.Message)" -ForegroundColor Red
        }
        return $false
    }
}

function Supabase-GetIds {
    param([string]$Table)
    $headers = @{
        "apikey" = $SUPABASE_SERVICE_KEY
        "Authorization" = "Bearer $SUPABASE_SERVICE_KEY"
    }
    $all = @()
    $offset = 0
    $pageSize = 1000
    do {
        $url = "$SUPABASE_URL/rest/v1/${Table}?select=id&offset=$offset&limit=$pageSize"
        $page = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        if ($page -and $page.Count -gt 0) {
            $all += $page
            $offset += $page.Count
        } else {
            break
        }
    } while ($page.Count -eq $pageSize)
    return $all
}

# ═══════════════════════════════════════
#  STEP 1: Load Firebase User → Supabase User ID mapping
# ═══════════════════════════════════════
Write-Host "`n=== STEP 1: Loading user mappings ===" -ForegroundColor Cyan
$headers = @{
    "apikey" = $SUPABASE_SERVICE_KEY
    "Authorization" = "Bearer $SUPABASE_SERVICE_KEY"
}
$supabaseUsers = Invoke-RestMethod -Uri "$SUPABASE_URL/rest/v1/users?select=id,firebase_uid" -Headers $headers -Method Get
$uidToIdMap = @{}
foreach ($u in $supabaseUsers) {
    if ($u.firebase_uid) {
        $uidToIdMap[$u.firebase_uid] = $u.id
    }
}
Write-Host "  Loaded $($uidToIdMap.Count) user mappings." -ForegroundColor Green

# ═══════════════════════════════════════
#  STEP 2: Load existing IDs from Supabase (to find delta)
# ═══════════════════════════════════════
Write-Host "`n=== STEP 2: Loading existing Supabase IDs ===" -ForegroundColor Cyan

Write-Host "  Loading cashbook IDs..."
$existingCashbooks = @{}
(Supabase-GetIds "cashbooks") | ForEach-Object { $existingCashbooks[$_.id] = $true }
Write-Host "  Existing cashbooks: $($existingCashbooks.Count)"

Write-Host "  Loading transaction IDs..."
$existingTransactions = @{}
(Supabase-GetIds "transactions") | ForEach-Object { $existingTransactions[$_.id] = $true }
Write-Host "  Existing transactions: $($existingTransactions.Count)"

Write-Host "  Loading category IDs..."
$existingCategories = @{}
(Supabase-GetIds "categories") | ForEach-Object { $existingCategories[$_.id] = $true }
Write-Host "  Existing categories: $($existingCategories.Count)"

# ═══════════════════════════════════════
#  STEP 3: Read all Firebase data & find new items
# ═══════════════════════════════════════
Write-Host "`n=== STEP 3: Reading Firebase data ===" -ForegroundColor Cyan
$allUsers = Firebase-Get "users"
if (-not $allUsers) {
    Write-Host "Failed to read from Firebase." -ForegroundColor Red
    exit 1
}

$firebaseUids = @()
$allUsers.PSObject.Properties | ForEach-Object { $firebaseUids += $_.Name }
Write-Host "  Firebase users found: $($firebaseUids.Count)"

$newCashbooks = 0
$newTransactions = 0
$newCategories = 0
$skippedUsers = 0
$errors = 0

foreach ($firebaseUid in $firebaseUids) {
    $userData = $allUsers.$firebaseUid
    $supabaseUserId = $uidToIdMap[$firebaseUid]
    if (-not $supabaseUserId) {
        $skippedUsers++
        continue
    }

    $userName = if ($userData.userName) { $userData.userName } elseif ($userData.name) { $userData.name } else { "Unknown" }

    # ─── Process Cashbooks ───
    $cashbooksData = $userData.cashbooks
    if (-not $cashbooksData) { continue }

    $cbIds = @()
    $cashbooksData.PSObject.Properties | ForEach-Object { $cbIds += $_.Name }

    foreach ($cbId in $cbIds) {
        $cbData = $cashbooksData.$cbId

        # ─── Upsert Cashbook (if new) ───
        if (-not $existingCashbooks.ContainsKey($cbId)) {
            $cbName = if ($cbData.cashbookName) { $cbData.cashbookName } elseif ($cbData.name) { $cbData.name } else { "My Cashbook" }
            $cbRecord = @{
                id = $cbId
                user_id = $supabaseUserId
                name = $cbName
                description = if ($cbData.description) { $cbData.description } else { $null }
                category = if ($cbData.category) { $cbData.category } else { $null }
                theme_color = if ($cbData.themeColor) { $cbData.themeColor } else { $null }
                theme_icon = if ($cbData.themeIcon) { $cbData.themeIcon } else { $null }
                currency = if ($cbData.currency) { $cbData.currency } else { "INR" }
                total_balance = if ($cbData.totalBalance) { [double]$cbData.totalBalance } else { 0.0 }
                transaction_count = if ($cbData.transactionCount) { [int]$cbData.transactionCount } else { 0 }
                is_active = $true
                is_current = if ($null -ne $cbData.isCurrent) { [bool]$cbData.isCurrent } else { $false }
                is_favorite = if ($null -ne $cbData.isFavorite) { [bool]$cbData.isFavorite } else { $false }
            }
            if (Supabase-Upsert "cashbooks" $cbRecord) {
                $newCashbooks++
                Write-Host "  [NEW CB] $userName / $cbName" -ForegroundColor Green
            } else { $errors++ }
        }

        # ─── Upsert Transactions (only new ones) ───
        $txData = $cbData.transactions
        if ($txData) {
            $txIds = @()
            $txData.PSObject.Properties | ForEach-Object { $txIds += $_.Name }

            $txBatch = @()
            foreach ($txId in $txIds) {
                if ($existingTransactions.ContainsKey($txId)) { continue }

                $tx = $txData.$txId
                $txRecord = @{
                    id = $txId
                    cashbook_id = $cbId
                    amount = if ($tx.amount) { [double]$tx.amount } else { 0.0 }
                    type = if ($tx.type) { $tx.type } else { "OUT" }
                    transaction_category = if ($tx.transactionCategory) { $tx.transactionCategory } else { $null }
                    party_name = if ($tx.partyName) { $tx.partyName } else { $null }
                    payment_mode = if ($tx.paymentMode) { $tx.paymentMode } else { $null }
                    remark = if ($tx.remark) { $tx.remark } else { $null }
                    timestamp = if ($tx.timestamp) { [long]$tx.timestamp } else { 0 }
                    tags = if ($tx.tags) { $tx.tags } else { $null }
                    location = if ($tx.location) { $tx.location } else { $null }
                    attachment_uri = if ($tx.attachmentUri) { $tx.attachmentUri } else { $null }
                    auto_frequency = if ($tx.autoFrequency) { $tx.autoFrequency } else { $null }
                    tax_rate = if ($tx.taxRate) { [double]$tx.taxRate } else { 0.0 }
                    tax_amount = if ($tx.taxAmount) { [double]$tx.taxAmount } else { 0.0 }
                    tax_inclusive = if ($null -ne $tx.taxInclusive) { [bool]$tx.taxInclusive } else { $false }
                }
                $txBatch += $txRecord
            }

            if ($txBatch.Count -gt 0) {
                if (Supabase-Upsert "transactions" $txBatch) {
                    $newTransactions += $txBatch.Count
                    Write-Host "  [NEW TX] $userName / $cbId : $($txBatch.Count) transactions" -ForegroundColor Yellow
                } else { $errors++ }
            }
        }

        # ─── Upsert Categories (only new ones) ───
        $catData = $cbData.categories
        if ($catData) {
            $catIds = @()
            $catData.PSObject.Properties | ForEach-Object { $catIds += $_.Name }

            $catBatch = @()
            foreach ($catId in $catIds) {
                if ($existingCategories.ContainsKey($catId)) { continue }

                $cat = $catData.$catId
                $fbType = if ($cat.type) { $cat.type } else { "OUT" }
                $supaType = switch ($fbType) {
                    "IN" { "Income" }
                    "UNIVERSAL" { "Expense" }
                    default { "Expense" }
                }
                $catRecord = @{
                    id = $catId
                    cashbook_id = $cbId
                    name = if ($cat.categoryName) { $cat.categoryName } elseif ($cat.name) { $cat.name } else { "Other" }
                    type = $supaType
                    color_hex = if ($cat.colorHex) { $cat.colorHex } else { $null }
                    icon_res_id = if ($cat.iconResId) { [int]$cat.iconResId } else { $null }
                    is_custom = if ($null -ne $cat.isCustom) { [bool]$cat.isCustom } else { $true }
                }
                $catBatch += $catRecord
            }

            if ($catBatch.Count -gt 0) {
                if (Supabase-Upsert "categories" $catBatch) {
                    $newCategories += $catBatch.Count
                    Write-Host "  [NEW CAT] $userName / $cbId : $($catBatch.Count) categories" -ForegroundColor Magenta
                } else { $errors++ }
            }
        }
    }
}

# ═══════════════════════════════════════
#  SUMMARY
# ═══════════════════════════════════════
Write-Host "`n══════════════════════════════════════" -ForegroundColor White
Write-Host "  FINAL DELTA MIGRATION SUMMARY" -ForegroundColor Cyan
Write-Host "══════════════════════════════════════" -ForegroundColor White
Write-Host "  New cashbooks migrated:    $newCashbooks" -ForegroundColor Green
Write-Host "  New transactions migrated: $newTransactions" -ForegroundColor Yellow
Write-Host "  New categories migrated:   $newCategories" -ForegroundColor Magenta
Write-Host "  Users skipped (no map):    $skippedUsers" -ForegroundColor Gray
Write-Host "  Errors:                    $errors" -ForegroundColor $(if ($errors -gt 0) { "Red" } else { "Green" })
Write-Host "══════════════════════════════════════" -ForegroundColor White

if ($errors -eq 0) {
    Write-Host "`n  ✅ SAFE TO PUBLISH THE APP UPDATE!" -ForegroundColor Green
} else {
    Write-Host "`n  ⚠️  Fix the errors above before publishing." -ForegroundColor Red
}
