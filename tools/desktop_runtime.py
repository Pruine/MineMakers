"""Independent input and image workers. The Win32 message thread only handles input/state."""
import threading
import time

class InputPump:
    def __init__(self, writer):
        self.writer=writer; self.latest=None; self.error=None; self.count=0
        self.stop=threading.Event()
        self.thread=threading.Thread(target=self._run,name='minemakers-input',daemon=True)
        self.thread.start()
    def submit(self, text): self.latest=text
    def _run(self):
        last=None
        while not self.stop.is_set():
            text=self.latest
            if text is not None and text!=last:
                try:
                    if self.writer.publish(text): last=text; self.count+=1; self.error=None
                except OSError as error: self.error=str(error)
            self.stop.wait(1/120)
        try:
            if self.latest is not None: self.writer.publish(self.latest)
        except OSError: pass
        self.writer.close()
    def close(self, final_text):
        self.latest=final_text; self.stop.set(); self.thread.join(2)

class ImagePump:
    """Read/compose off the input thread, retaining only the latest frame and presentation request."""
    def __init__(self, read, present, hide, close, notify=lambda: None):
        self.read,self.present,self.hide,self.cleanup=read,present,hide,close
        self.notify=notify; self.report=None
        self.request=(False,'',None,.5,.5)
        self.latest=None; self.displayed=None; self.error=None
        self.reads=self.presents=0; self.read_time=self.draw_time=0.0
        self.stop=threading.Event()
        self.thread=threading.Thread(target=self._run,name='minemakers-compositor',daemon=True)
        self.thread.start()
    def submit(self, active,session,rect,cursor): self.request=(active,session,rect,*cursor)
    def _run(self):
        last=None; visible=False
        try:
            while not self.stop.is_set():
                started=time.monotonic()
                try: frame=self.read()
                except (OSError,ValueError,UnicodeError): frame=None
                self.read_time+=time.monotonic()-started
                if frame is not None:
                    self.latest=(frame,time.monotonic()); self.reads+=1
                request=self.request
                frame=self.latest[0] if self.latest else None
                if request[0] and request[2] and frame and frame.session==request[1]:
                    key=(frame.session,frame.sequence,request[2],request[3:] if frame.menu else None)
                    if key!=last:
                        started=time.monotonic()
                        if self.present(frame,request):
                            self.displayed=(frame,time.monotonic()); self.presents+=1; last=key; visible=True; self.notify()
                        self.draw_time+=time.monotonic()-started
                elif visible:
                    self.hide(); visible=False; last=None
                report=self.report
                if report is not None:
                    self.report=None
                    try:
                        import os
                        path,text=report; temp=path.with_name(path.name+'.tmp'); temp.write_text(text,encoding='utf-8'); os.replace(temp,path)
                    except OSError: pass
                self.stop.wait(.002)
        except Exception as error: self.error=str(error)
        finally:
            self.hide(); self.cleanup()
    def close(self): self.stop.set(); self.thread.join(2)
