param(
    [string]$OutputDir = "",
    [int]$Cycles = 3,
    [int]$RestMinutes = 3,
    [int]$CycleTimeoutMinutes = 70,
    [string]$Package = "com.example.webviewtestbed",
    [string]$Activity = ".MainActivity"
)

$ErrorActionPreference = "Continue"
$Workspace = (Resolve-Path ".").Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $Workspace ("outputs\download-mission-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$RunnerLog = Join-Path $OutputDir "runner.log"
$SummaryCsv = Join-Path $OutputDir "summary.csv"
$SummaryMd = Join-Path $OutputDir "summary.md"
$StatusJson = Join-Path $OutputDir "status.json"
$StartedAt = Get-Date

function Write-RunnerLog {
    param([string]$Message)
    $line = "{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $Message
    Add-Content -LiteralPath $RunnerLog -Value $line -Encoding UTF8
    Write-Host $line
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & adb @Args 2>&1
}

function Write-Status {
    param(
        [string]$State,
        [int]$Cycle = 0,
        [string]$MissionId = "",
        [string]$Note = ""
    )
    [ordered]@{
        state = $State
        cycle = $Cycle
        missionId = $MissionId
        note = $Note
        outputDir = $OutputDir
        cycles = $Cycles
        startedAt = $StartedAt.ToString("s")
        updatedAt = (Get-Date).ToString("s")
        pid = $PID
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StatusJson -Encoding UTF8
}

function Get-LatestMissionId {
    $lines = Invoke-Adb "shell" "run-as" $Package "ls" "-t" "files/gpt_photo_missions"
    foreach ($line in $lines) {
        $value = $line.ToString().Trim()
        if ($value -match '^\d{8}-\d{6}$') {
            return $value
        }
    }
    return ""
}

function Test-MissionFinished {
    param([string]$MissionId)
    if ([string]::IsNullOrWhiteSpace($MissionId)) {
        return $false
    }
    $tail = Invoke-Adb "exec-out" "run-as" $Package "tail" "-n" "30" "files/gpt_photo_missions/$MissionId/events.jsonl"
    $text = ($tail -join "`n")
    return ($text -match '"event":"mission_complete"' -or
            $text -match '"event":"mission_stop"' -or
            $text -match '"event":"batch_failed"')
}

function Tap-DownloadRunButton {
    Invoke-Adb "shell" "uiautomator" "dump" "/sdcard/webview-window.xml" | Out-Null
    Start-Sleep -Milliseconds 500
    $xml = (Invoke-Adb "exec-out" "cat" "/sdcard/webview-window.xml") -join "`n"
    $match = [regex]::Match($xml, 'text="RUN DL x3"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($match.Success) {
        $x = [int]((([int]$match.Groups[1].Value) + ([int]$match.Groups[3].Value)) / 2)
        $y = [int]((([int]$match.Groups[2].Value) + ([int]$match.Groups[4].Value)) / 2)
        Write-RunnerLog "Tap RUN DL x3 at $x,$y from UIAutomator"
        Invoke-Adb "shell" "input" "tap" "$x" "$y" | Out-Null
        return
    }
    Write-RunnerLog "RUN DL x3 bounds not found; fallback tap 551,286"
    Invoke-Adb "shell" "input" "tap" "551" "286" | Out-Null
}

function Join-ProcessArguments {
    param([string[]]$InputArgs)
    $parts = foreach ($arg in $InputArgs) {
        if ($arg -match '[\s"]') {
            '"' + ($arg -replace '"', '\"') + '"'
        } else {
            $arg
        }
    }
    return ($parts -join " ")
}

function Save-AdbExecOut {
    param(
        [string]$OutFile,
        [string]$ErrFile,
        [string[]]$AdbArgs
    )
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "adb"
    $psi.Arguments = Join-ProcessArguments -InputArgs $AdbArgs
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($psi)
    $stream = [System.IO.File]::Open($OutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
    } finally {
        $stream.Close()
    }
    $errorText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if (-not [string]::IsNullOrWhiteSpace($errorText)) {
        Set-Content -LiteralPath $ErrFile -Value $errorText -Encoding UTF8
    } else {
        Set-Content -LiteralPath $ErrFile -Value "" -Encoding UTF8
    }
}

function Pull-MissionFiles {
    param(
        [int]$Cycle,
        [string]$MissionId
    )
    $prefix = "cycle-{0:D2}-{1}" -f $Cycle, $MissionId
    $eventFile = Join-Path $OutputDir "$prefix-events.jsonl"
    $responseFile = Join-Path $OutputDir "$prefix-responses.md"
    $eventErr = Join-Path $OutputDir "$prefix-events.err.txt"
    $responseErr = Join-Path $OutputDir "$prefix-responses.err.txt"

    Save-AdbExecOut -OutFile $eventFile -ErrFile $eventErr -AdbArgs @("exec-out", "run-as", $Package, "cat", "files/gpt_photo_missions/$MissionId/events.jsonl")
    Save-AdbExecOut -OutFile $responseFile -ErrFile $responseErr -AdbArgs @("exec-out", "run-as", $Package, "cat", "files/gpt_photo_missions/$MissionId/responses.md")
    return [ordered]@{
        events = $eventFile
        responses = $responseFile
    }
}

function Get-PropertyValue {
    param($Object, [string]$Name)
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Parse-Mission {
    param(
        [int]$Cycle,
        [string]$MissionId,
        [string]$EventFile,
        [string]$LogcatFile,
        [datetime]$CycleStarted,
        [datetime]$CycleEnded
    )
    $counts = @{}
    $batches = @{}
    $lastEvent = ""
    $kind = ""
    $snippetPlan = ""
    $downloadResults = New-Object System.Collections.Generic.List[string]
    $sendClicks = 0
    $enterDispatches = 0

    if (Test-Path -LiteralPath $EventFile) {
        foreach ($line in Get-Content -LiteralPath $EventFile -Encoding UTF8) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            try {
                $obj = $line | ConvertFrom-Json
            } catch {
                continue
            }
            $name = [string](Get-PropertyValue $obj "event")
            if ([string]::IsNullOrWhiteSpace($name)) {
                continue
            }
            $payload = Get-PropertyValue $obj "payload"
            if ($null -eq $payload) {
                $payload = $obj
            }
            $lastEvent = $name
            if (-not $counts.ContainsKey($name)) {
                $counts[$name] = 0
            }
            $counts[$name] = [int]$counts[$name] + 1

            if ($name -eq "mission_start") {
                $kind = [string](Get-PropertyValue $payload "kind")
                $snippetPlan = [string](Get-PropertyValue $payload "snippetPlan")
            }
            if ($name -eq "send_attempt") {
                $result = [string](Get-PropertyValue $payload "result")
                if ($result -match "clicked-send-button") {
                    $sendClicks += 1
                }
                if ($result -match "enter-dispatched") {
                    $enterDispatches += 1
                }
            }

            $batchNo = Get-PropertyValue $payload "batch"
            if ($null -eq $batchNo) {
                $batchNo = Get-PropertyValue $payload "number"
            }
            if ($null -ne $batchNo -and "$batchNo" -match '^\d+$') {
                $batchNo = [int]$batchNo
                if (-not $batches.ContainsKey($batchNo)) {
                    $batches[$batchNo] = [ordered]@{
                        batch = $batchNo
                        strategy = ""
                        reason = ""
                        assistantChars = ""
                        downloadStrategy = ""
                        downloadResult = ""
                    }
                }
                $strategy = Get-PropertyValue $payload "strategy"
                if ($null -ne $strategy) {
                    $strategyName = Get-PropertyValue $strategy "name"
                    if (-not [string]::IsNullOrWhiteSpace([string]$strategyName)) {
                        $batches[$batchNo]["strategy"] = [string]$strategyName
                    }
                }
                if ($name -eq "batch_finish") {
                    $reason = Get-PropertyValue $payload "reason"
                    if ($null -eq $reason) {
                        $reason = Get-PropertyValue $payload "finishReason"
                    }
                    $chars = Get-PropertyValue $payload "finalAssistantChars"
                    if ($null -eq $chars) {
                        $chars = Get-PropertyValue $payload "lastAssistantLen"
                    }
                    $batches[$batchNo]["reason"] = [string]$reason
                    $batches[$batchNo]["assistantChars"] = [string]$chars
                }
                if ($name -eq "download_phase_finish") {
                    $downloadResult = [string](Get-PropertyValue $payload "downloadResult")
                    $downloadStrategy = [string](Get-PropertyValue $payload "downloadStrategy")
                    $batches[$batchNo]["downloadResult"] = $downloadResult
                    $batches[$batchNo]["downloadStrategy"] = $downloadStrategy
                    $downloadResults.Add(("{0}:{1}:{2}" -f $batchNo, $downloadStrategy, $downloadResult))
                }
            }
        }
    }

    function Count-Event([string]$Key) {
        if ($counts.ContainsKey($Key)) {
            return [int]$counts[$Key]
        }
        return 0
    }

    $state = "partial"
    if ((Count-Event "mission_complete") -gt 0) {
        $state = "complete"
    } elseif ((Count-Event "mission_stop") -gt 0) {
        $state = "stopped"
    } elseif ((Count-Event "batch_failed") -gt 0) {
        $state = "failed"
    }

    $stable = 0
    $timeout = 0
    $strategyBits = New-Object System.Collections.Generic.List[string]
    foreach ($key in ($batches.Keys | Sort-Object)) {
        $b = $batches[$key]
        if ($b["reason"] -eq "stable") {
            $stable += 1
        }
        if ($b["reason"] -eq "timeout") {
            $timeout += 1
        }
        $strategyBits.Add(("{0}:{1}:{2}:{3}:dl={4}" -f $b["batch"], $b["strategy"], $b["reason"], $b["assistantChars"], $b["downloadResult"]))
    }

    $crashSignals = 0
    if (Test-Path -LiteralPath $LogcatFile) {
        $crashSignals = (Select-String -LiteralPath $LogcatFile -Pattern 'FATAL EXCEPTION|Fatal signal|Force finishing|ANR in' -CaseSensitive:$false).Count
    }

    return [pscustomobject]@{
        Cycle = $Cycle
        MissionId = $MissionId
        Kind = $kind
        State = $state
        CycleStarted = $CycleStarted.ToString("s")
        CycleEnded = $CycleEnded.ToString("s")
        DurationMinutes = [math]::Round(($CycleEnded - $CycleStarted).TotalMinutes, 2)
        LastEvent = $lastEvent
        BatchStarted = (Count-Event "batch_start")
        BatchFinished = (Count-Event "batch_finish")
        StableBatches = $stable
        TimeoutBatches = $timeout
        BatchFailures = (Count-Event "batch_failed")
        FileChooserRequested = (Count-Event "file_chooser_requested")
        AutoDelivered = (Count-Event "auto_file_chooser_delivered")
        DownloadPhases = (Count-Event "download_phase_finish")
        DownloadSaved = (Count-Event "download_saved")
        DownloadEnqueued = (Count-Event "download_enqueued")
        DownloadSaveFailed = (Count-Event "download_save_failed")
        DownloadEnqueueFailed = (Count-Event "download_enqueue_failed")
        SendButtonClicks = $sendClicks
        EnterDispatches = $enterDispatches
        CrashSignals = $crashSignals
        SnippetPlan = $snippetPlan
        Strategies = ($strategyBits -join " ; ")
        DownloadResults = ($downloadResults -join " ; ")
        EventFile = $EventFile
        ResponseFile = $EventFile.Replace("-events.jsonl", "-responses.md")
        LogcatFile = $LogcatFile
    }
}

function Rewrite-SummaryMarkdown {
    param([string]$StateNote = "")
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Download Mission RUN DL x3")
    $lines.Add("")
    $lines.Add("- Output: $OutputDir")
    $lines.Add("- Started: $($StartedAt.ToString('s'))")
    $lines.Add("- Updated: $((Get-Date).ToString('s'))")
    $lines.Add("- Target cycles: $Cycles")
    if (-not [string]::IsNullOrWhiteSpace($StateNote)) {
        $lines.Add("- State: $StateNote")
    }
    $lines.Add("")
    $lines.Add("|Cycle|Mission|State|Finished|Stable|Timeout|Failures|Saved|Enqueued|Save Fail|Crashes|Strategies|")
    $lines.Add("|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|")
    if (Test-Path -LiteralPath $SummaryCsv) {
        foreach ($row in Import-Csv -LiteralPath $SummaryCsv) {
            $strategies = ($row.Strategies -replace '\|', '/' -replace ';', '<br>')
            $lines.Add("|$($row.Cycle)|$($row.MissionId)|$($row.State)|$($row.BatchFinished)|$($row.StableBatches)|$($row.TimeoutBatches)|$($row.BatchFailures)|$($row.DownloadSaved)|$($row.DownloadEnqueued)|$($row.DownloadSaveFailed)|$($row.CrashSignals)|$strategies|")
        }
    }
    $lines.Add("")
    $lines.Add("Strategies rotate through photo-menu image redraw/download, file-input long text/native save, and file-menu downloadable text/blob bridge.")
    $lines.Add("Raw files are stored next to this summary as cycle event logs, response snapshots, and logcat captures.")
    $lines | Set-Content -LiteralPath $SummaryMd -Encoding UTF8
}

Write-RunnerLog "Starting download mission runner. OutputDir=$OutputDir Cycles=$Cycles RestMinutes=$RestMinutes CycleTimeoutMinutes=$CycleTimeoutMinutes"
Write-Status "starting" 0 "" "initializing"
Rewrite-SummaryMarkdown "starting"

try {
    Invoke-Adb "shell" "svc" "power" "stayon" "true" | Out-Null
    Invoke-Adb "shell" "pm" "grant" $Package "android.permission.READ_MEDIA_IMAGES" | Out-Null
    Invoke-Adb "shell" "pm" "grant" $Package "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" | Out-Null

    for ($cycle = 1; $cycle -le $Cycles; $cycle++) {
        $cycleStarted = Get-Date
        Write-RunnerLog "Cycle $cycle starting"
        Write-Status "cycle-starting" $cycle "" "restarting app"

        Invoke-Adb "shell" "input" "keyevent" "KEYCODE_WAKEUP" | Out-Null
        Invoke-Adb "shell" "wm" "dismiss-keyguard" | Out-Null
        Invoke-Adb "logcat" "-c" | Out-Null
        Invoke-Adb "shell" "am" "force-stop" $Package | Out-Null
        Start-Sleep -Seconds 2
        Invoke-Adb "shell" "am" "start" "-n" "$Package/$Activity" | Out-Null
        Start-Sleep -Seconds 8

        $beforeMission = Get-LatestMissionId
        Tap-DownloadRunButton

        $missionId = ""
        $cycleDeadline = (Get-Date).AddMinutes($CycleTimeoutMinutes)
        while ((Get-Date) -lt $cycleDeadline) {
            Start-Sleep -Seconds 20
            if ([string]::IsNullOrWhiteSpace($missionId)) {
                $candidate = Get-LatestMissionId
                if (-not [string]::IsNullOrWhiteSpace($candidate) -and $candidate -ne $beforeMission) {
                    $missionId = $candidate
                    Write-RunnerLog "Cycle $cycle mission detected: $missionId"
                    Write-Status "cycle-running" $cycle $missionId "mission detected"
                }
            } else {
                if (Test-MissionFinished $missionId) {
                    Write-RunnerLog "Cycle $cycle mission finished: $missionId"
                    break
                }
                Write-Status "cycle-running" $cycle $missionId "waiting for mission finish"
            }
        }

        if ([string]::IsNullOrWhiteSpace($missionId)) {
            $missionId = Get-LatestMissionId
            Write-RunnerLog "Cycle $cycle did not detect a new mission id; latest=$missionId"
        }

        $logNameMission = if ($missionId) { $missionId } else { "unknown" }
        $logcatFile = Join-Path $OutputDir ("cycle-{0:D2}-{1}-logcat.txt" -f $cycle, $logNameMission)
        & adb logcat -d -v time 2>&1 | Set-Content -LiteralPath $logcatFile -Encoding UTF8

        if (-not [string]::IsNullOrWhiteSpace($missionId)) {
            $files = Pull-MissionFiles $cycle $missionId
            $cycleEnded = Get-Date
            $row = Parse-Mission $cycle $missionId $files.events $logcatFile $cycleStarted $cycleEnded
            $row | Export-Csv -LiteralPath $SummaryCsv -NoTypeInformation -Append -Encoding UTF8
            Write-RunnerLog ("Cycle {0} summary: mission={1} state={2} finished={3} stable={4} saved={5} enqueued={6} crashes={7}" -f $cycle, $missionId, $row.State, $row.BatchFinished, $row.StableBatches, $row.DownloadSaved, $row.DownloadEnqueued, $row.CrashSignals)
            Write-Status "cycle-complete" $cycle $missionId ("state={0}" -f $row.State)
        } else {
            $cycleEnded = Get-Date
            [pscustomobject]@{
                Cycle = $cycle
                MissionId = ""
                Kind = ""
                State = "no-mission-id"
                CycleStarted = $cycleStarted.ToString("s")
                CycleEnded = $cycleEnded.ToString("s")
                DurationMinutes = [math]::Round(($cycleEnded - $cycleStarted).TotalMinutes, 2)
                LastEvent = ""
                BatchStarted = 0
                BatchFinished = 0
                StableBatches = 0
                TimeoutBatches = 0
                BatchFailures = 0
                FileChooserRequested = 0
                AutoDelivered = 0
                DownloadPhases = 0
                DownloadSaved = 0
                DownloadEnqueued = 0
                DownloadSaveFailed = 0
                DownloadEnqueueFailed = 0
                SendButtonClicks = 0
                EnterDispatches = 0
                CrashSignals = (Select-String -LiteralPath $logcatFile -Pattern 'FATAL EXCEPTION|Fatal signal|Force finishing|ANR in' -CaseSensitive:$false).Count
                SnippetPlan = ""
                Strategies = ""
                DownloadResults = ""
                EventFile = ""
                ResponseFile = ""
                LogcatFile = $logcatFile
            } | Export-Csv -LiteralPath $SummaryCsv -NoTypeInformation -Append -Encoding UTF8
            Write-Status "cycle-complete" $cycle "" "no mission id"
        }

        Rewrite-SummaryMarkdown ("cycle $cycle complete")

        if ($cycle -lt $Cycles) {
            $sleepSeconds = [math]::Max(0, $RestMinutes * 60)
            Write-RunnerLog "Resting for $sleepSeconds seconds"
            Write-Status "resting" $cycle $missionId "between cycles"
            Start-Sleep -Seconds $sleepSeconds
        }
    }

    Write-RunnerLog "Download mission runner complete"
    Write-Status "complete" $Cycles "" "all cycles done"
    Rewrite-SummaryMarkdown "complete"
} catch {
    Write-RunnerLog ("Runner error: " + $_.Exception.Message)
    Write-Status "error" 0 "" $_.Exception.Message
    Rewrite-SummaryMarkdown ("error: " + $_.Exception.Message)
} finally {
    Invoke-Adb "shell" "svc" "power" "stayon" "false" | Out-Null
    Write-RunnerLog "Runner stopped"
}
