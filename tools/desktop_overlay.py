#!/usr/bin/env python3
"""Windows desktop compositor for real Minecraft UI/hand pixels over Trailmakers.

No external Python packages. Capture is explicitly toggled with F8, only while
Trailmakers is the foreground window. No keyboard input is logged. Alt-Tab,
Windows keys, and Alt-F4 release capture and remain available to Windows.
"""
import argparse
import hashlib
import os
from pathlib import Path
import struct
import sys
import time
from desktop_protocol import InputQueue, decode_overlay, resolve_directory, FrameWriter


def run(directory, title):
    if sys.platform != 'win32': raise SystemExit('This helper requires Windows 10/11 and Python 3.10+.')
    import ctypes as c
    from ctypes import wintypes as w
    user, gdi, kernel = c.windll.user32, c.windll.gdi32, c.windll.kernel32
    # Use physical coordinates consistently on high-DPI desktops.
    try: user.SetProcessDpiAwarenessContext(c.c_void_p(-4))
    except (AttributeError, OSError): user.SetProcessDPIAware()
    LRESULT=c.c_ssize_t
    WNDPROC=c.WINFUNCTYPE(LRESULT,w.HWND,w.UINT,w.WPARAM,w.LPARAM)
    HOOKPROC=c.WINFUNCTYPE(LRESULT,c.c_int,w.WPARAM,w.LPARAM)
    class WC(c.Structure):
        _fields_=[('style',w.UINT),('proc',WNDPROC),('cbClsExtra',c.c_int),('cbWndExtra',c.c_int),
                  ('instance',w.HINSTANCE),('icon',w.HANDLE),('cursor',w.HANDLE),('background',w.HBRUSH),
                  ('menu',w.LPCWSTR),('name',w.LPCWSTR)]
    class SIZE(c.Structure): _fields_=[('cx',c.c_long),('cy',c.c_long)]
    class BLEND(c.Structure): _fields_=[('op',c.c_ubyte),('flags',c.c_ubyte),('alpha',c.c_ubyte),('format',c.c_ubyte)]
    class BI(c.Structure):
        _fields_=[('size',w.DWORD),('width',c.c_long),('height',c.c_long),('planes',w.WORD),('bits',w.WORD),
                  ('compression',w.DWORD),('imageSize',w.DWORD),('xppm',c.c_long),('yppm',c.c_long),('used',w.DWORD),('important',w.DWORD)]
    class RID(c.Structure): _fields_=[('page',w.USHORT),('usage',w.USHORT),('flags',w.DWORD),('target',w.HWND)]
    class RH(c.Structure): _fields_=[('type',w.DWORD),('size',w.DWORD),('device',w.HANDLE),('param',w.WPARAM)]
    class KBD(c.Structure): _fields_=[('vk',w.DWORD),('scan',w.DWORD),('flags',w.DWORD),('time',w.DWORD),('extra',c.c_size_t)]
    class MOUSE(c.Structure): _fields_=[('pt',w.POINT),('data',w.DWORD),('flags',w.DWORD),('time',w.DWORD),('extra',c.c_size_t)]
    def api(lib,name,result,args):
        f=getattr(lib,name); f.restype=result; f.argtypes=args; return f
    api(user,'DefWindowProcW',LRESULT,[w.HWND,w.UINT,w.WPARAM,w.LPARAM])
    api(user,'CreateWindowExW',w.HWND,[w.DWORD,w.LPCWSTR,w.LPCWSTR,w.DWORD,c.c_int,c.c_int,c.c_int,c.c_int,w.HWND,w.HMENU,w.HINSTANCE,c.c_void_p])
    api(user,'RegisterClassW',w.ATOM,[c.POINTER(WC)])
    api(user,'GetForegroundWindow',w.HWND,[])
    api(user,'GetWindowTextW',c.c_int,[w.HWND,w.LPWSTR,c.c_int])
    api(user,'GetClientRect',w.BOOL,[w.HWND,c.POINTER(w.RECT)])
    api(user,'ClientToScreen',w.BOOL,[w.HWND,c.POINTER(w.POINT)])
    api(user,'GetDC',w.HDC,[w.HWND]); api(user,'ReleaseDC',c.c_int,[w.HWND,w.HDC])
    api(user,'SetWindowsHookExW',w.HANDLE,[c.c_int,HOOKPROC,w.HINSTANCE,w.DWORD])
    api(user,'UnhookWindowsHookEx',w.BOOL,[w.HANDLE])
    api(user,'CallNextHookEx',LRESULT,[w.HANDLE,c.c_int,w.WPARAM,w.LPARAM])
    api(user,'RegisterRawInputDevices',w.BOOL,[c.POINTER(RID),w.UINT,w.UINT])
    api(user,'GetRawInputData',w.UINT,[w.HANDLE,w.UINT,c.c_void_p,c.POINTER(w.UINT),w.UINT])
    api(user,'UpdateLayeredWindow',w.BOOL,[w.HWND,w.HDC,c.POINTER(w.POINT),c.POINTER(SIZE),w.HDC,c.POINTER(w.POINT),w.DWORD,c.POINTER(BLEND),w.DWORD])
    api(user,'ShowWindow',w.BOOL,[w.HWND,c.c_int]); api(user,'DestroyWindow',w.BOOL,[w.HWND])
    api(user,'SetTimer',c.c_size_t,[w.HWND,c.c_size_t,w.UINT,c.c_void_p])
    api(user,'KillTimer',w.BOOL,[w.HWND,c.c_size_t])
    api(user,'GetMessageW',w.BOOL,[c.POINTER(w.MSG),w.HWND,w.UINT,w.UINT])
    api(user,'TranslateMessage',w.BOOL,[c.POINTER(w.MSG)])
    api(user,'DispatchMessageW',LRESULT,[c.POINTER(w.MSG)])
    api(user,'SetCursorPos',w.BOOL,[c.c_int,c.c_int])
    api(user,'GetKeyboardLayout',w.HANDLE,[w.DWORD])
    api(user,'GetWindowThreadProcessId',w.DWORD,[w.HWND,c.POINTER(w.DWORD)])
    api(user,'ToUnicodeEx',c.c_int,[w.UINT,w.UINT,c.POINTER(c.c_ubyte),w.LPWSTR,c.c_int,w.UINT,w.HANDLE])
    api(kernel,'GetModuleHandleW',w.HINSTANCE,[w.LPCWSTR])
    api(kernel,'CreateMutexW',w.HANDLE,[c.c_void_p,w.BOOL,w.LPCWSTR])
    api(kernel,'GetLastError',w.DWORD,[])
    api(kernel,'CloseHandle',w.BOOL,[w.HANDLE])
    api(gdi,'CreateCompatibleDC',w.HDC,[w.HDC]); api(gdi,'DeleteDC',w.BOOL,[w.HDC])
    api(gdi,'CreateDIBSection',w.HBITMAP,[w.HDC,c.POINTER(BI),w.UINT,c.POINTER(c.c_void_p),w.HANDLE,w.DWORD])
    api(gdi,'SelectObject',w.HANDLE,[w.HDC,w.HANDLE]); api(gdi,'DeleteObject',w.BOOL,[w.HANDLE])
    api(gdi,'StretchDIBits',c.c_int,[w.HDC,c.c_int,c.c_int,c.c_int,c.c_int,c.c_int,c.c_int,c.c_int,c.c_int,c.c_void_p,c.POINTER(BI),w.UINT,w.DWORD])
    api(gdi,'SetStretchBltMode',c.c_int,[w.HDC,c.c_int])
    q=InputQueue(); directory=resolve_directory(directory)
    identity=hashlib.sha256(os.path.normcase(str(directory)).encode('utf-8')).hexdigest()
    mutex=kernel.CreateMutexW(None,False,'Local\\MineMakersOverlay_'+identity)
    mutex_error=kernel.GetLastError()
    if not mutex: raise OSError('Cannot create overlay instance guard')
    if mutex_error==183:
        kernel.CloseHandle(mutex)
        raise SystemExit('An overlay helper is already running for this folder. Close that helper window before starting another.')
    writer=FrameWriter(directory)
    state={'armed':False,'capture':False,'target':None,'rect':None,'frame':None,'fresh':-100.0,
           'mtime':None,'raw_seq':None,'failed':False,'vkeys':set(),'bitmap':None,'old':None,'bits':None,'size':None,'write_warning':False,'last_draw':None}
    dc=user.GetDC(None); mem=gdi.CreateCompatibleDC(dc)
    if not dc or not mem: raise OSError('Cannot create overlay device context')
    def foreground():
        hwnd=user.GetForegroundWindow(); buf=c.create_unicode_buffer(512)
        user.GetWindowTextW(hwnd,buf,len(buf))
        return hwnd if title.casefold() in buf.value.casefold() else None
    def modifiers():
        keys=state['vkeys']
        return (1 if keys & {16,160,161} else 0)|(2 if keys & {17,162,163} else 0)|(4 if keys & {18,164,165} else 0)
    def release():
        state['capture']=False; q.reset(); state['vkeys'].clear()
        state['frame']=None; state['fresh']=-100.0; state['raw_seq']=None
    def emit(kind,*args):
        try: q.event(kind,*args)
        except BufferError as e:
            release(); state['armed']=False; print(str(e),flush=True)
    def write(active):
        success=writer.publish(q.frame(active))
        if success:
            if state['write_warning']: print('Frame writing recovered.',flush=True)
            state['write_warning']=False
        elif writer.blocked_for>=2 and not state['write_warning']:
            print('Still waiting to replace '+str(writer.destination)+'. A reader may be holding it open, or it may be read-only. Close other helper instances; if this persists, check this file\'s permissions. No administrator elevation is required by the helper.',file=sys.stderr,flush=True)
            state['write_warning']=True
        return success
    def render(frame,rect):
        left,top,width,height=rect
        if state['size']!=(width,height):
            if state['bitmap']:
                gdi.SelectObject(mem,state['old']); gdi.DeleteObject(state['bitmap'])
            bi=BI(c.sizeof(BI),width,height,1,32,0,width*height*4,0,0,0,0)
            bits=c.c_void_p(); bitmap=gdi.CreateDIBSection(dc,c.byref(bi),0,c.byref(bits),None,0)
            if not bitmap or not bits.value: raise OSError('Cannot allocate overlay bitmap')
            state.update(bitmap=bitmap,old=gdi.SelectObject(mem,bitmap),bits=bits,size=(width,height))
        source=BI(c.sizeof(BI),frame.width,frame.height,1,32,0,len(frame.pixels),0,0,0,0)
        pixels=c.create_string_buffer(frame.pixels)
        gdi.SetStretchBltMode(mem,3) # COLORONCOLOR preserves BGRA samples, including alpha
        result=gdi.StretchDIBits(mem,0,0,width,height,0,0,frame.width,frame.height,pixels,c.byref(source),0,0x00CC0020)
        if result in (0,-1): raise OSError('Cannot scale overlay bitmap')
        if frame.menu:
            # Cursor is virtual; raw mouse works even if Trailmakers locks the OS cursor.
            colors=c.cast(state['bits'],c.POINTER(c.c_uint32))
            px=int(q.cursor_x*width); py=int(q.cursor_y*height)
            for y in range(17):
                for x in range(min(11,y//2+1)):
                    xx,yy=px+x,py+y
                    if 0<=xx<width and 0<=yy<height:
                        colors[(height-1-yy)*width+xx]=0xFF101010 if x==0 or x==y//2 or y==16 else 0xFFFFFFFF
        blend=BLEND(0,0,255,1); at=w.POINT(left,top); zero=w.POINT(0,0); size=SIZE(width,height)
        if not user.UpdateLayeredWindow(hwnd,dc,c.byref(at),c.byref(size),mem,c.byref(zero),0,c.byref(blend),2):
            raise OSError('UpdateLayeredWindow failed; use windowed/borderless Trailmakers')
        user.ShowWindow(hwnd,4) # SW_SHOWNOACTIVATE
    def pulse():
        target=foreground(); state['target']=target
        rect=None
        if target:
            r=w.RECT(); origin=w.POINT(0,0)
            if user.GetClientRect(target,c.byref(r)) and user.ClientToScreen(target,c.byref(origin)):
                if 1<=r.right<=7680 and 1<=r.bottom<=4320: rect=(origin.x,origin.y,r.right,r.bottom)
        state['rect']=rect
        try:
            path=directory/'overlay.bgra'; st=path.stat()
            if st.st_size>4*1280*720+100: raise ValueError('Oversized overlay')
            if st.st_mtime_ns!=state['mtime']:
                frame=decode_overlay(path.read_bytes())
                state['mtime']=st.st_mtime_ns
                if frame.session==q.session and (frame.session,frame.sequence)!=state['raw_seq']:
                    state['frame']=frame; state['raw_seq']=(frame.session,frame.sequence); state['fresh']=time.monotonic(); q.acknowledge(frame)
        except (OSError,ValueError,UnicodeError): pass
        fresh=time.monotonic()-state['fresh']<1.5
        capture=bool(state['armed'] and rect and fresh)
        if not capture and state['capture']:
            release(); state['armed']=False
        state['capture']=capture
        # ACTIVE is sent while arming so Minecraft can generate the very first overlay.
        write(bool(state['armed'] and rect))
        if capture:
            frame=state['frame']
            draw_key=(frame.session,frame.sequence,rect,q.cursor_x if frame.menu else 0,q.cursor_y if frame.menu else 0)
            if draw_key!=state['last_draw']:
                render(frame,rect); state['last_draw']=draw_key
            user.SetCursorPos(rect[0]+rect[2]//2,rect[1]+rect[3]//2)
        else:
            user.ShowWindow(hwnd,0); state['last_draw']=None
    VK={0x08:259,0x09:258,0x0D:257,0x1B:256,0x20:32,0x21:266,0x22:267,0x23:269,0x24:268,
        0x25:263,0x26:265,0x27:262,0x28:264,0x2D:260,0x2E:261,0xA0:340,0xA1:344,
        0xA2:341,0xA3:345,0xA4:342,0xA5:346,0x10:340,0x11:341,0x12:342,
        0xBA:59,0xBB:61,0xBC:44,0xBD:45,0xBE:46,0xBF:47,0xC0:96,0xDB:91,0xDC:92,0xDD:93,0xDE:39}
    def glfw(vk):
        if 48<=vk<=90: return vk
        if 0x70<=vk<=0x87: return 290+vk-0x70
        return VK.get(vk)
    def characters(k):
        if modifiers() & (2|4): return
        keyboard=(c.c_ubyte*256)()
        for vk in state['vkeys']: keyboard[vk]=0x80
        for generic,others in [(16,{160,161}),(17,{162,163}),(18,{164,165})]:
            if state['vkeys'] & others: keyboard[generic]=0x80
        keyboard[20]=user.GetKeyState(20)&1
        target=state['target']; thread=user.GetWindowThreadProcessId(target,None)
        text=c.create_unicode_buffer(8)
        n=user.ToUnicodeEx(k.vk,k.scan,keyboard,text,len(text),4,user.GetKeyboardLayout(thread))
        if n>0:
            for char in text.value[:n]:
                if 32<=ord(char)<=65535: emit('CHAR',ord(char))
    @HOOKPROC
    def keyboard_hook(code,msg,data):
        if code>=0:
            k=c.cast(data,c.POINTER(KBD)).contents; down=msg in (0x100,0x104)
            if k.vk==0x77 and foreground(): # F8 always releases capture, even before the first rendered frame
                if down and not (k.flags&0x80) and k.vk not in state['vkeys']:
                    state['armed']=not state['armed']; release()
                    print('Overlay '+('armed; waiting for Minecraft pixels' if state['armed'] else 'released'),flush=True)
                if down: state['vkeys'].add(k.vk)
                else: state['vkeys'].discard(k.vk)
                return 1
            if state['capture'] and foreground():
                # Let Windows see Alt itself so the subsequent Alt-Tab/Alt-F4 shortcut works.
                if k.vk in (0x12,0xA4,0xA5):
                    if down: state['vkeys'].add(k.vk)
                    else: state['vkeys'].discard(k.vk)
                    return user.CallNextHookEx(None,code,msg,data)
                if k.vk in (0x5B,0x5C) or (k.vk in (0x09,0x73) and modifiers()&4):
                    state['armed']=False; release()
                else:
                    repeat=k.vk in state['vkeys']
                    if down: state['vkeys'].add(k.vk)
                    else: state['vkeys'].discard(k.vk)
                    key=glfw(k.vk)
                    if key is not None:
                        if down: q.held.add(key)
                        else: q.held.discard(key)
                        emit('KEY',key,(2 if repeat else 1) if down else 0,modifiers())
                        if down: characters(k)
                    return 1
        return user.CallNextHookEx(None,code,msg,data)
    @HOOKPROC
    def mouse_hook(code,msg,data):
        if code>=0 and state['capture'] and foreground():
            m=c.cast(data,c.POINTER(MOUSE)).contents
            buttons={0x201:(0,1),0x202:(0,0),0x204:(1,1),0x205:(1,0),0x207:(2,1),0x208:(2,0)}
            if msg in buttons:
                button,action=buttons[msg]; emit('BUTTON',button,action,modifiers(),q.cursor_x,q.cursor_y); return 1
            if msg==0x20A:
                emit('SCROLL',c.c_short(m.data>>16).value/120); return 1
        return user.CallNextHookEx(None,code,msg,data)
    @WNDPROC
    def procedure(handle,msg,wp,lp):
        try:
            if msg==0xFF and state['capture'] and foreground(): # WM_INPUT: raw relative mouse movement
                size=w.UINT(0); user.GetRawInputData(w.HANDLE(lp),0x10000003,None,c.byref(size),c.sizeof(RH))
                if c.sizeof(RH)+24<=size.value<=4096:
                    buf=c.create_string_buffer(size.value)
                    if user.GetRawInputData(w.HANDLE(lp),0x10000003,buf,c.byref(size),c.sizeof(RH))!=0xFFFFFFFF:
                        header=RH.from_buffer_copy(buf)
                        flags=struct.unpack_from('<H',buf,c.sizeof(RH))[0]
                        if header.type==0 and not flags&1:
                            dx,dy=struct.unpack_from('<ii',buf,c.sizeof(RH)+12)
                            frame=state['frame']; rect=state['rect']
                            if frame and frame.menu and rect:
                                q.cursor_x=max(0,min(1,q.cursor_x+dx/rect[2])); q.cursor_y=max(0,min(1,q.cursor_y+dy/rect[3]))
                            else: q.x+=dx; q.y+=dy
            elif msg==0x113: pulse()
            elif msg==0x2: user.PostQuitMessage(0)
        except Exception as e:
            release(); state['armed']=False
            if not state['failed']: print('Overlay stopped: '+str(e),file=sys.stderr,flush=True); state['failed']=True
        return user.DefWindowProcW(handle,msg,wp,lp)
    instance=kernel.GetModuleHandleW(None)
    klass=WC(0,procedure,0,0,instance,None,None,None,None,'MineMakersDesktopOverlay')
    if not user.RegisterClassW(c.byref(klass)): raise OSError('Cannot register overlay window class')
    hwnd=user.CreateWindowExW(0x80000|0x20|0x80|0x8|0x08000000,klass.name,'MineMakers overlay',0x80000000,0,0,1,1,None,None,instance,None)
    if not hwnd: raise OSError('Cannot create overlay window')
    device=RID(1,2,0x100,hwnd)
    if not user.RegisterRawInputDevices(c.byref(device),1,c.sizeof(RID)): raise OSError('Cannot register raw mouse input')
    kh=user.SetWindowsHookExW(13,keyboard_hook,instance,0); mh=user.SetWindowsHookExW(14,mouse_hook,instance,0)
    if not kh or not mh: raise OSError('Cannot install game input hooks')
    user.SetTimer(hwnd,1,16,None)
    print('Focus Trailmakers and press F8. Press F8 again to release. Ctrl+C closes this helper.',flush=True)
    try:
        message=w.MSG()
        while user.GetMessageW(c.byref(message),None,0,0)>0:
            user.TranslateMessage(c.byref(message)); user.DispatchMessageW(c.byref(message))
    finally:
        release()
        try: write(False)
        except OSError: pass
        writer.close(); kernel.CloseHandle(mutex)
        user.UnhookWindowsHookEx(kh); user.UnhookWindowsHookEx(mh); user.KillTimer(hwnd,1)
        if state['bitmap']: gdi.SelectObject(mem,state['old']); gdi.DeleteObject(state['bitmap'])
        gdi.DeleteDC(mem); user.ReleaseDC(None,dc); user.DestroyWindow(hwnd)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory',nargs='?',type=Path,help='Existing folder containing trailmakers.frame, normally data_dynamic_willnotbeuploadedtoworkshop')
    p.add_argument('--title',default='Trailmakers',help='Foreground window title must contain this text (default: Trailmakers)')
    args=p.parse_args()
    if not args.title.strip(): p.error('A specific game window title is required')
    if args.directory is None:
        print('The helper lives in tools. Frame files stay in the Trailmakers mod folder.')
        print('Paste the full path to the EXISTING folder containing trailmakers.frame')
        print('(normally named data_dynamic_willnotbeuploadedtoworkshop).')
        try: args.directory=Path(input('Frame folder: ').strip().strip('"'))
        except (EOFError,KeyboardInterrupt): raise SystemExit('No folder selected.')
    try: directory=resolve_directory(args.directory)
    except ValueError as e: p.error(str(e))
    print('Using Trailmakers frame folder: '+str(directory))
    run(directory,args.title)
