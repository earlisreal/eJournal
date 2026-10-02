# Read-only diagnostic: uses saved global keys; never prints or changes credentials.
param([ValidateNotNullOrEmpty()][string]$Symbol = 'AAPL')

$taskCredentialsPath = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ejournal\credentials.json'
try {
    $taskCredentials = (Get-Content -LiteralPath $taskCredentialsPath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop).alpaca
} catch {
    throw 'Could not read the saved global Alpaca market-data credentials.'
}
if ([string]::IsNullOrWhiteSpace($taskCredentials.keyId) -or [string]::IsNullOrWhiteSpace($taskCredentials.secretKey)) {
    throw 'Global Alpaca market-data keys are not configured.'
}
$taskHeaders = @{ 'APCA-API-KEY-ID' = $taskCredentials.keyId; 'APCA-API-SECRET-KEY' = $taskCredentials.secretKey }

function Get-AlpacaCheck([string]$Check, [string]$Url) {
    $taskStatus = 0
    $taskServerTime = $null
    $taskMessage = $null
    $taskCode = $null
    try {
        $taskResponse = Invoke-WebRequest -Uri $Url -Headers $taskHeaders -UseBasicParsing -MaximumRedirection 0 -TimeoutSec 20 -ErrorAction Stop
        $taskStatus = [int]$taskResponse.StatusCode
        $taskBody = $taskResponse.Content
        $taskDateHeader = $taskResponse.Headers['Date'] | Select-Object -First 1
        if ($taskDateHeader) {
            $taskServerTime = [DateTimeOffset]::Parse($taskDateHeader, [Globalization.CultureInfo]::InvariantCulture).ToUniversalTime()
        }
    } catch {
        if ($_.Exception.Response) { $taskStatus = [int]$_.Exception.Response.StatusCode }
        $taskBody = $_.ErrorDetails.Message
        $taskMessage = $_.Exception.Message
        if ([string]::IsNullOrWhiteSpace($taskBody) -and $_.Exception.Response) {
            try {
                if ($_.Exception.Response -is [System.Net.HttpWebResponse]) {
                    $taskReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                    try { $taskBody = $taskReader.ReadToEnd() } finally { $taskReader.Dispose() }
                } else {
                    $taskBody = $_.Exception.Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                }
            } catch {}
        }
    }
    try {
        $taskPayload = $taskBody | ConvertFrom-Json -ErrorAction Stop
        $taskCode = $taskPayload.code
        if ($taskPayload.message) { $taskMessage = [string]$taskPayload.message }
    } catch {}
    if ($taskMessage) {
        $taskMessage = $taskMessage.Replace([string]$taskCredentials.keyId, '<REDACTED>').Replace([string]$taskCredentials.secretKey, '<REDACTED>')
    }
    [pscustomobject]@{
        Check = $Check
        Url = $Url
        Status = $taskStatus
        Code = $taskCode
        Message = $taskMessage
        ServerTimeUtc = $taskServerTime
    }
}

$taskEncodedSymbol = [uri]::EscapeDataString($Symbol.Trim().ToUpperInvariant())
$taskIex = Get-AlpacaCheck 'IEX connection' ('https://data.alpaca.markets/v2/stocks/{0}/trades/latest?feed=iex' -f $taskEncodedSymbol)
$taskIex | ConvertTo-Json -Depth 3
$taskNow = [DateTimeOffset]::UtcNow
[pscustomobject]@{
    ClientTimeUtc = $taskNow.ToString('o')
    ClientClockAheadSeconds = if ($taskIex.ServerTimeUtc) { [Math]::Round(($taskNow - $taskIex.ServerTimeUtc).TotalSeconds, 1) } else { $null }
} | ConvertTo-Json
$taskStart = $taskNow.AddDays(-7).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$taskEnd = $taskNow.AddMinutes(-16).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$taskSipUrl = 'https://data.alpaca.markets/v2/stocks/{0}/bars?feed=sip&timeframe=1Min&start={1}&end={2}&limit=1&adjustment=raw' -f $taskEncodedSymbol, $taskStart, $taskEnd
$taskSip = Get-AlpacaCheck 'SIP bars with the app delay' $taskSipUrl
$taskSip | ConvertTo-Json -Depth 3
if ($taskIex.Status -ne 200 -or $taskSip.Status -ne 200) { exit 1 }
