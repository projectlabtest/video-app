Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = WshShell.CurrentDirectory
' Start the server (hidden)
WshShell.Run "run.bat", 0, False
' Wait for server to start
WScript.Sleep 5000
' Open in app mode (no browser chrome)
url = "http://localhost:8090/"
chromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
edgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
edgePath2 = "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
If fso.FileExists(chromePath) Then
    WshShell.Run """" & chromePath & """ --app=" & url & " --window-size=1280,800", 1, False
ElseIf fso.FileExists(edgePath) Then
    WshShell.Run """" & edgePath & """ --app=" & url & " --window-size=1280,800", 1, False
ElseIf fso.FileExists(edgePath2) Then
    WshShell.Run """" & edgePath2 & """ --app=" & url & " --window-size=1280,800", 1, False
Else
    WshShell.Run "cmd /c start """" """ & url & """", 0, False
End If
Set WshShell = Nothing
Set fso = Nothing