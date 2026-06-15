param(
    [string]$OutputDir = "",
    [int]$Hours = 8,
    [int]$RestMinutes = 10,
    [int]$CycleTimeoutMinutes = 55,
    [string]$Package = "com.example.webviewtestbed",
    [string]$Activity = ".MainActivity"
)

$ErrorActionPreference = "Continue"
$Workspace = (Resolve-Path ".").Path
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $Workspace ("outputs\overnight-webview-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$RunnerLog = Join-Path $OutputDir "runner.log"
$SummaryCsv = Join-Path $OutputDir "summary.csv"
$SummaryMd = Join-Path $OutputDir "summary.md"
$StatusJson = Join-Path $OutputDir "status.json"
$StartedAt = Get-Date
$Deadline = $StartedAt.AddHours($Hours)

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
    $payload = [ordered]@{
        state = $State
        cycle = $Cycle
        missionId = $MissionId
        note = $Note
        outputDir = $OutputDir
        startedAt = $StartedAt.ToString("s")
        deadline = $Deadline.ToString("s")
        updatedAt = (Get-Date).ToString("s")
        pid = $PID
    }
    $payload | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StatusJson -Encoding UTF8
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
    $tail = Invoke-Adb "exec-out" "run-as" $Package "tail" "-n" "20" "files/gpt_photo_missions/$MissionId/events.jsonl"
    $text = ($tail -join "`n")
    return ($text -match '"event":"mission_complete"' -or
            $text -match '"event":"mission_stop"' -or
            $text -match '"event":"batch_failed"')
}

function Tap-RunButton {
    Invoke-Adb "shell" "uiautomator" "dump" "/sdcard/webview-window.xml" | Out-Null
    Start-Sleep -Milliseconds 500
    $xml = (Invoke-Adb "exec-out" "cat" "/sdcard/webview-window.xml") -join "`n"
    $match = [regex]::Match($xml, 'text="RUN PHOTO x6"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($match.Success) {
        $x = [int]((([int]$match.Groups[1].Value) + ([int]$match.Groups[3].Value)) / 2)
        $y = [int]((([int]$match.Groups[2].Value) + ([int]$match.Groups[4].Value)) / 2)
        Write-RunnerLog "Tap RUN PHOTO x6 at $x,$y from UIAutomator"
        Invoke-Adb "shell" "input" "tap" "$x" "$y" | Out-Null
        return
    }
    Write-RunnerLog "RUN PHOTO x6 bounds not found; fallback tap 264,286"
    Invoke-Adb "shell" "input" "tap" "264" "286" | Out-Null
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

    & adb exec-out run-as $Package cat "files/gpt_photo_missions/$MissionId/events.jsonl" 2>$eventErr |
        Set-Content -LiteralPath $eventFile -Encoding UTF8
    & adb exec-out run-as $Package cat "files/gpt_photo_missions/$MissionId/responses.md" 2>$responseErr |
        Set-Content -LiteralPath $responseFile -Encoding UTF8
    return [ordered]@{
        events = $eventFile
        responses = $responseFile
    }
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

    $sendClicks = 0
    $enterDispatches = 0
    if (Test-Path -LiteralPath $EventFile) {
        foreach ($line in Get-Content -LiteralPath $EventFile -Encoding UTF8) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            $eventMatch = [regex]::Match($line, '"event":"([^"]+)"')
            if (-not $eventMatch.Success) {
                continue
            }
            $name = $eventMatch.Groups[1].Value
            $lastEvent = $name
            if (-not $counts.ContainsKey($name)) {
                $counts[$name] = 0
            }
            $counts[$name] = [int]$counts[$name] + 1

            $batchMatch = [regex]::Match($line, '"batch":(\d+)')
            if ($batchMatch.Success) {
                $batchNo = [int]$batchMatch.Groups[1].Value
                if (-not $batches.ContainsKey($batchNo)) {
                    $batches[$batchNo] = [ordered]@{
                        batch = $batchNo
                        strategy = ""
                        reason = ""
                        assistantChars = ""
                    }
                }
                $strategyMatch = [regex]::Match($line, '"strategy":\{"name":"([^"]+)"')
                if ($strategyMatch.Success) {
                    $batches[$batchNo]["strategy"] = $strategyMatch.Groups[1].Value
                }
                if ($name -eq "batch_finish") {
                    $reasonMatch = [regex]::Match($line, '"reason":"([^"]+)"')
                    if (-not $reasonMatch.Success) {
                        $reasonMatch = [regex]::Match($line, '"finishReason":"([^"]+)"')
                    }
                    if ($reasonMatch.Success) {
                        $batches[$batchNo]["reason"] = $reasonMatch.Groups[1].Value
                    }
                    $charsMatch = [regex]::Match($line, '"finalAssistantChars":(\d+)')
                    if ($charsMatch.Success) {
                        $batches[$batchNo]["assistantChars"] = $charsMatch.Groups[1].Value
                    }
                }
                if ($name -eq "send_attempt") {
                    if ($line -match 'clicked-send-button') {
                        $sendClicks += 1
                    }
                    if ($line -match 'enter-dispatched') {
                        $enterDispatches += 1
                    }
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
        $strategyBits.Add(("{0}:{1}:{2}:{3}" -f $b["batch"], $b["strategy"], $b["reason"], $b["assistantChars"]))
    }

    $crashSignals = 0
    if (Test-Path -LiteralPath $LogcatFile) {
        $crashSignals = (Select-String -LiteralPath $LogcatFile -Pattern 'FATAL EXCEPTION|Fatal signal|Force finishing|ANR in' -CaseSensitive:$false).Count
    }

    return [pscustomobject]@{
        Cycle = $Cycle
        MissionId = $MissionId
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
        SendButtonClicks = $sendClicks
        EnterDispatches = $enterDispatches
        CrashSignals = $crashSignals
        Strategies = ($strategyBits -join " ; ")
        EventFile = $EventFile
        ResponseFile = $EventFile.Replace("-events.jsonl", "-responses.md")
        LogcatFile = $LogcatFile
    }
}

function Rewrite-SummaryMarkdown {
    param([string]$StateNote = "")
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Overnight WebView test")
    $lines.Add("")
    $lines.Add("- Output: $OutputDir")
    $lines.Add("- Started: $($StartedAt.ToString('s'))")
    $lines.Add("- Deadline: $($Deadline.ToString('s'))")
    $lines.Add("- Updated: $((Get-Date).ToString('s'))")
    if (-not [string]::IsNullOrWhiteSpace($StateNote)) {
        $lines.Add("- State: $StateNote")
    }
    $lines.Add("")
    $lines.Add("|Cycle|Mission|State|Finished|Stable|Timeout|Failures|Delivered|Send clicks|Enter tries|Crashes|Strategies|")
    $lines.Add("|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|")
    if (Test-Path -LiteralPath $SummaryCsv) {
        foreach ($row in Import-Csv -LiteralPath $SummaryCsv) {
            $strategies = ($row.Strategies -replace '\|', '/' -replace ';', '<br>')
            $lines.Add("|$($row.Cycle)|$($row.MissionId)|$($row.State)|$($row.BatchFinished)|$($row.StableBatches)|$($row.TimeoutBatches)|$($row.BatchFailures)|$($row.AutoDelivered)|$($row.SendButtonClicks)|$($row.EnterDispatches)|$($row.CrashSignals)|$strategies|")
        }
    }
    $lines.Add("")
    $lines.Add("Raw files are stored next to this summary as cycle event logs, response snapshots, and logcat captures.")
    $lines | Set-Content -LiteralPath $SummaryMd -Encoding UTF8
}

Write-RunnerLog "Starting overnight runner. OutputDir=$OutputDir Hours=$Hours RestMinutes=$RestMinutes CycleTimeoutMinutes=$CycleTimeoutMinutes"
Write-Status "starting" 0 "" "initializing"
Rewrite-SummaryMarkdown "starting"

try {
    Invoke-Adb "shell" "svc" "power" "stayon" "true" | Out-Null
    Invoke-Adb "shell" "pm" "grant" $Package "android.permission.READ_MEDIA_IMAGES" | Out-Null
    Invoke-Adb "shell" "pm" "grant" $Package "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" | Out-Null

    $cycle = 0
    while ((Get-Date) -lt $Deadline) {
        $cycle += 1
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
        Tap-RunButton

        $missionId = ""
        $cycleDeadline = (Get-Date).AddMinutes($CycleTimeoutMinutes)
        if ($cycleDeadline -gt $Deadline) {
            $cycleDeadline = $Deadline
        }

        while ((Get-Date) -lt $cycleDeadline) {
            Start-Sleep -Seconds 30
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

        $logcatFile = Join-Path $OutputDir ("cycle-{0:D2}-{1}-logcat.txt" -f $cycle, $(if ($missionId) { $missionId } else { "unknown" }))
        & adb logcat -d -v time 2>&1 | Set-Content -LiteralPath $logcatFile -Encoding UTF8

        if (-not [string]::IsNullOrWhiteSpace($missionId)) {
            $files = Pull-MissionFiles $cycle $missionId
            $cycleEnded = Get-Date
            $row = Parse-Mission $cycle $missionId $files.events $logcatFile $cycleStarted $cycleEnded
            $row | Export-Csv -LiteralPath $SummaryCsv -NoTypeInformation -Append -Encoding UTF8
            Write-RunnerLog ("Cycle {0} summary: mission={1} state={2} finished={3} stable={4} timeout={5} crashes={6}" -f $cycle, $missionId, $row.State, $row.BatchFinished, $row.StableBatches, $row.TimeoutBatches, $row.CrashSignals)
            Write-Status "cycle-complete" $cycle $missionId ("state={0}" -f $row.State)
        } else {
            $cycleEnded = Get-Date
            $row = [pscustomobject]@{
                Cycle = $cycle
                MissionId = ""
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
                SendButtonClicks = 0
                EnterDispatches = 0
                CrashSignals = (Select-String -LiteralPath $logcatFile -Pattern 'FATAL EXCEPTION|Fatal signal|Force finishing|ANR in' -CaseSensitive:$false).Count
                Strategies = ""
                EventFile = ""
                ResponseFile = ""
                LogcatFile = $logcatFile
            }
            $row | Export-Csv -LiteralPath $SummaryCsv -NoTypeInformation -Append -Encoding UTF8
            Write-Status "cycle-complete" $cycle "" "no mission id"
        }

        Rewrite-SummaryMarkdown ("cycle $cycle complete")

        $remainingSeconds = [int][math]::Floor(($Deadline - (Get-Date)).TotalSeconds)
        if ($remainingSeconds -le 0) {
            break
        }
        $sleepSeconds = [math]::Min($RestMinutes * 60, $remainingSeconds)
        Write-RunnerLog "Resting for $sleepSeconds seconds"
        Write-Status "resting" $cycle $missionId "between cycles"
        Start-Sleep -Seconds $sleepSeconds
    }

    Write-RunnerLog "Overnight runner reached deadline"
    Write-Status "complete" $cycle "" "deadline reached"
    Rewrite-SummaryMarkdown "complete"
} catch {
    Write-RunnerLog ("Runner error: " + $_.Exception.Message)
    Write-Status "error" 0 "" $_.Exception.Message
    Rewrite-SummaryMarkdown ("error: " + $_.Exception.Message)
} finally {
    Invoke-Adb "shell" "svc" "power" "stayon" "false" | Out-Null
    Write-RunnerLog "Runner stopped"
}
