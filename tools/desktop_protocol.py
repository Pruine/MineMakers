"""Portable protocol helpers shared by the Windows compositor and offline tests."""
from dataclasses import dataclass
import struct
import uuid
from pathlib import Path

HEADER = struct.Struct('<4sIIIQQI64s')

@dataclass(frozen=True)
class Overlay:
    width: int
    height: int
    sequence: int
    ack: int
    menu: bool
    session: str
    pixels: bytes

def decode_overlay(data):
    if len(data) < HEADER.size: raise ValueError('Incomplete overlay header')
    magic, version, width, height, seq, ack, flags, session = HEADER.unpack_from(data)
    if magic != b'MMOV' or version != 1 or not 1 <= width <= 1280 or not 1 <= height <= 720:
        raise ValueError('Unsupported overlay frame')
    if len(data) != HEADER.size + width * height * 4: raise ValueError('Incomplete overlay image')
    session = session.split(b'\0', 1)[0].decode('ascii')
    if not session or len(session) > 64 or any(not (c.isalnum() or c in '_-') for c in session):
        raise ValueError('Invalid overlay session')
    return Overlay(width, height, seq, ack, bool(flags & 1), session, data[HEADER.size:])

def encode_frame(session, seq, rows):
    if len(rows) > 8192: raise ValueError('Too many rows')
    lines = [f'MMT1\t{session}\t{seq}\t{len(rows)}']
    for row in rows:
        values = [str(value) for value in row]
        if not 1 <= len(values) <= 32 or any(len(v) > 256 or any(c in v for c in '\t\r\n') for v in values):
            raise ValueError('Invalid frame field')
        lines.append('\t'.join(values))
    lines.append(f'END\t{session}\t{seq}\t{len(rows)}')
    return '\n'.join(lines) + '\n'

class InputQueue:
    def __init__(self):
        self.session = str(uuid.uuid4())
        self.sequence = self.event_id = 0
        self.events = []
        self.held = set()
        self.x = self.y = 0.0
        self.cursor_x = self.cursor_y = 0.5
    def reset(self):
        self.__init__()
    def event(self, kind, *args):
        if len(self.events) >= 256: raise BufferError('Minecraft is not acknowledging input; release capture with F8')
        self.event_id += 1
        self.events.append(['EVENT', self.event_id, kind, *args])
    def acknowledge(self, overlay):
        if overlay.session == self.session:
            self.events = [row for row in self.events if row[1] > overlay.ack]
    def frame(self, active):
        self.sequence += 1
        rows = [['ACTIVE', int(active)], ['POINTER', self.x, self.y, self.cursor_x, self.cursor_y]]
        rows += [['HELD', k] for k in sorted(self.held)]
        rows += self.events
        return encode_frame(self.session, self.sequence, rows)


def resolve_directory(path):
    """Select the directory Trailmakers actually writes; never create an empty lookalike."""
    root = Path(path).expanduser().resolve()
    if not root.is_dir():
        raise ValueError('Choose the existing Trailmakers folder containing trailmakers.frame. The helper will not create a new folder.')
    if (root / 'trailmakers.frame').is_file():
        return root
    matches = [root / name for name in ('data_dynamic_willnotbeuploadedtoworkshop', 'data_dynamic')
               if (root / name / 'trailmakers.frame').is_file()]
    if len(matches) == 1:
        return matches[0]
    if len(matches) > 1:
        raise ValueError('Both dynamic folders contain frames. Choose the exact folder used by the running Trailmakers mod.')
    raise ValueError('No trailmakers.frame found. Start the Trailmakers mod, then select its existing data_dynamic_willnotbeuploadedtoworkshop folder containing the frame files.')
