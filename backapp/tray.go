package main

import (
	"fmt"
	"log"
	"os/exec"
	"runtime"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	user32   = windows.NewLazySystemDLL("user32.dll")
	shell32  = windows.NewLazySystemDLL("shell32.dll")
	kernel32 = windows.NewLazySystemDLL("kernel32.dll")

	procRegisterClassExW = user32.NewProc("RegisterClassExW")
	procCreateWindowExW  = user32.NewProc("CreateWindowExW")
	procDefWindowProcW   = user32.NewProc("DefWindowProcW")
	procGetMessageW      = user32.NewProc("GetMessageW")
	procTranslateMessage = user32.NewProc("TranslateMessage")
	procDispatchMessageW = user32.NewProc("DispatchMessageW")
	procPostQuitMessage  = user32.NewProc("PostQuitMessage")
	procCreatePopupMenu  = user32.NewProc("CreatePopupMenu")
	procAppendMenuW      = user32.NewProc("AppendMenuW")
	procTrackPopupMenu   = user32.NewProc("TrackPopupMenu")
	procDestroyMenu      = user32.NewProc("DestroyMenu")
	procGetCursorPos     = user32.NewProc("GetCursorPos")
	procSetForegroundWnd = user32.NewProc("SetForegroundWindow")
	procLoadIconW        = user32.NewProc("LoadIconW")

	procShellNotifyIconW = shell32.NewProc("Shell_NotifyIconW")
	procShellExecuteW    = shell32.NewProc("ShellExecuteW")
	procGetModuleHandleW = kernel32.NewProc("GetModuleHandleW")
)

const (
	NIM_ADD        = 0x00000000
	NIM_MODIFY     = 0x00000001
	NIM_DELETE     = 0x00000002
	NIF_MESSAGE    = 0x00000001
	NIF_ICON       = 0x00000002
	NIF_TIP        = 0x00000004
	NIF_INFO       = 0x00000010

	WM_USER        = 0x0400
	WM_TRAYICON    = WM_USER + 1
	WM_RBUTTONUP   = 0x0205
	WM_LBUTTONDBLCLK = 0x0203
	WM_COMMAND     = 0x0111
	WM_DESTROY     = 0x0002

	MF_STRING      = 0x00000000
	MF_SEPARATOR   = 0x00000800
	MF_DISABLED    = 0x00000002
	MF_GRAYED      = 0x00000001

	TPM_RIGHTALIGN = 0x0008
	TPM_BOTTOMALIGN= 0x0020
	TPM_RETURNCMD  = 0x0100

	IDI_APPLICATION = 32512
)

type NOTIFYICONDATAW struct {
	CbSize           uint32
	HWnd             windows.HWND
	UID              uint32
	UFlags           uint32
	UCallbackMessage uint32
	HIcon            windows.Handle
	SzTip            [128]uint16
	DwState          uint32
	DwStateMask      uint32
	SzInfo           [256]uint16
	UTimeoutOrVersion uint32
	SzInfoTitle      [64]uint16
	DwInfoFlags      uint32
	GuidItem         windows.GUID
	HBalloonIcon     windows.Handle
}

type WNDCLASSEXW struct {
	CbSize        uint32
	Style         uint32
	LpfnWndProc   uintptr
	CbClsExtra    int32
	CbWndExtra    int32
	HInstance     windows.Handle
	HIcon         windows.Handle
	HCursor       windows.Handle
	HbrBackground windows.Handle
	LpszMenuName  *uint16
	LpszClassName *uint16
	HIconSm       windows.Handle
}

type POINT struct {
	X int32
	Y int32
}

type MSG struct {
	HWnd    windows.HWND
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	Pt      POINT
}

const (
	ID_MENU_HEADER     = 1001
	ID_MENU_OPEN_POS   = 1002
	ID_MENU_OPEN_ADMIN = 1006
	ID_MENU_RELOAD     = 1003
	ID_MENU_STATUS     = 1004
	ID_MENU_EXIT       = 1005
)

var (
	trayHWnd windows.HWND
	nid      NOTIFYICONDATAW
	onReloadFunc func()
	onExitFunc   func()
)

// wndProc handles Windows events for the tray
func wndProc(hWnd windows.HWND, msg uint32, wParam, lParam uintptr) uintptr {
	switch msg {
	case WM_TRAYICON:
		if lParam == WM_RBUTTONUP || lParam == WM_LBUTTONDBLCLK {
			showContextMenu(hWnd)
		}
		return 0
	case WM_DESTROY:
		removeTrayIcon()
		procPostQuitMessage.Call(0)
		return 0
	default:
		r, _, _ := procDefWindowProcW.Call(uintptr(hWnd), uintptr(msg), wParam, lParam)
		return r
	}
}

// showContextMenu displays right-click popup menu for the tray icon
func showContextMenu(hWnd windows.HWND) {
	hMenu, _, _ := procCreatePopupMenu.Call()
	if hMenu == 0 {
		return
	}
	defer procDestroyMenu.Call(hMenu)

	// Menu items
	title, _ := windows.UTF16PtrFromString("☀️ SunPOS Engine (Port 8888)")
	openPos, _ := windows.UTF16PtrFromString("🖥️ เปิดหน้าจอ POS หน้าร้าน (http://localhost:5173/pos)")
	openAdmin, _ := windows.UTF16PtrFromString("🏢 เปิดระบบหลังบ้าน HQ & คลัง (http://localhost:5173/login)")
	reload, _ := windows.UTF16PtrFromString("🔄 Reload / Restart Engine")
	status, _ := windows.UTF16PtrFromString("🟢 Status: Auto-Recovery Active")
	exit, _ := windows.UTF16PtrFromString("❌ Exit SunPOS")

	procAppendMenuW.Call(hMenu, uintptr(MF_STRING|MF_DISABLED|MF_GRAYED), ID_MENU_HEADER, uintptr(unsafe.Pointer(title)))
	procAppendMenuW.Call(hMenu, uintptr(MF_STRING|MF_DISABLED), ID_MENU_STATUS, uintptr(unsafe.Pointer(status)))
	procAppendMenuW.Call(hMenu, uintptr(MF_SEPARATOR), 0, 0)
	procAppendMenuW.Call(hMenu, uintptr(MF_STRING), ID_MENU_OPEN_POS, uintptr(unsafe.Pointer(openPos)))
	procAppendMenuW.Call(hMenu, uintptr(MF_STRING), ID_MENU_OPEN_ADMIN, uintptr(unsafe.Pointer(openAdmin)))
	procAppendMenuW.Call(hMenu, uintptr(MF_SEPARATOR), 0, 0)
	procAppendMenuW.Call(hMenu, uintptr(MF_STRING), ID_MENU_RELOAD, uintptr(unsafe.Pointer(reload)))
	procAppendMenuW.Call(hMenu, uintptr(MF_STRING), ID_MENU_EXIT, uintptr(unsafe.Pointer(exit)))

	var pt POINT
	procGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))

	procSetForegroundWnd.Call(uintptr(hWnd))
	cmd, _, _ := procTrackPopupMenu.Call(
		hMenu,
		uintptr(TPM_BOTTOMALIGN|TPM_RIGHTALIGN|TPM_RETURNCMD),
		uintptr(pt.X),
		uintptr(pt.Y),
		0,
		uintptr(hWnd),
		0,
	)

	switch int(cmd) {
	case ID_MENU_OPEN_POS:
		openBrowser("http://localhost:5173/pos")
	case ID_MENU_OPEN_ADMIN:
		openBrowser("http://localhost:5173/login")
	case ID_MENU_RELOAD:
		if onReloadFunc != nil {
			go onReloadFunc()
		}
	case ID_MENU_EXIT:
		if onExitFunc != nil {
			onExitFunc()
		}
		removeTrayIcon()
		procPostQuitMessage.Call(0)
	}
}

func removeTrayIcon() {
	if nid.HWnd != 0 {
		procShellNotifyIconW.Call(uintptr(NIM_DELETE), uintptr(unsafe.Pointer(&nid)))
	}
}

// ShowTrayNotification displays a balloon tooltip notification
func ShowTrayNotification(title, message string) {
	if nid.HWnd == 0 {
		return
	}
	tUTF16, _ := windows.UTF16FromString(title)
	mUTF16, _ := windows.UTF16FromString(message)

	copy(nid.SzInfoTitle[:], tUTF16)
	copy(nid.SzInfo[:], mUTF16)
	nid.UFlags = NIF_INFO | NIF_TIP | NIF_MESSAGE | NIF_ICON
	nid.DwInfoFlags = 0x00000001 // NIIF_INFO

	procShellNotifyIconW.Call(uintptr(NIM_MODIFY), uintptr(unsafe.Pointer(&nid)))
}

func openBrowser(url string) {
	var err error
	switch runtime.GOOS {
	case "windows":
		err = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	case "darwin":
		err = exec.Command("open", url).Start()
	default:
		err = exec.Command("xdg-open", url).Start()
	}
	if err != nil {
		log.Printf("Failed to open browser: %v", err)
	}
}

// RunSystemTray initializes and runs the Windows System Tray message loop
func RunSystemTray(reloadCb func(), exitCb func()) error {
	onReloadFunc = reloadCb
	onExitFunc = exitCb

	className, _ := windows.UTF16PtrFromString("SunPOS_TrayWindow")
	hInst, _, _ := procGetModuleHandleW.Call(0)
	hInstance := windows.Handle(hInst)

	hIcon, _, _ := procLoadIconW.Call(0, uintptr(IDI_APPLICATION))

	wndClass := WNDCLASSEXW{
		CbSize:        uint32(unsafe.Sizeof(WNDCLASSEXW{})),
		LpfnWndProc:   syscall.NewCallback(wndProc),
		HInstance:     hInstance,
		HIcon:         windows.Handle(hIcon),
		LpszClassName: className,
	}

	atom, _, err := procRegisterClassExW.Call(uintptr(unsafe.Pointer(&wndClass)))
	if atom == 0 {
		return fmt.Errorf("RegisterClassExW failed: %w", err)
	}

	hWnd, _, err := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(className)),
		uintptr(unsafe.Pointer(className)),
		0,
		0, 0, 0, 0,
		0, 0,
		uintptr(hInstance),
		0,
	)
	if hWnd == 0 {
		return fmt.Errorf("CreateWindowExW failed: %w", err)
	}
	trayHWnd = windows.HWND(hWnd)

	// Setup Notification Data
	nid.CbSize = uint32(unsafe.Sizeof(nid))
	nid.HWnd = trayHWnd
	nid.UID = 1
	nid.UFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP
	nid.UCallbackMessage = WM_TRAYICON
	nid.HIcon = windows.Handle(hIcon)

	tipUTF16, _ := windows.UTF16FromString("SunPOS Backend Service (Port 8888)")
	copy(nid.SzTip[:], tipUTF16)

	r, _, err := procShellNotifyIconW.Call(uintptr(NIM_ADD), uintptr(unsafe.Pointer(&nid)))
	if r == 0 {
		return fmt.Errorf("Shell_NotifyIconW failed: %w", err)
	}

	log.Println("[Tray] SunPOS system tray icon started successfully in taskbar")
	ShowTrayNotification("SunPOS Service Ready", "Backend running on http://localhost:8888\nAuto-crash recovery is enabled.")

	// Message Loop
	var msg MSG
	for {
		res, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&msg)), 0, 0, 0)
		if int32(res) <= 0 {
			break
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&msg)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&msg)))
	}

	return nil
}
