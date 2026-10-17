#!/usr/bin/env python3
"""Generate original tiny textures and a 1 m cube; no Minecraft art is redistributed."""
from pathlib import Path
import random
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'mod' / 'data_static'

def png(path, pixels, size=16):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for rgb in row for c in rgb) for row in pixels)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))

def generate():
    ROOT.mkdir(parents=True, exist_ok=True)
    colors = {'wood': (146, 100, 53), 'stone': (120, 124, 130), 'bedrock': (55, 57, 62), 'tnt': (187, 46, 39),
              'skin': (185, 138, 102), 'shirt': (27, 153, 156), 'pants': (66, 63, 137), 'armor': (158, 177, 190), 'pearl': (27, 126, 111)}
    for name, color in colors.items():
        stages = range(11) if name in ('wood', 'stone', 'bedrock', 'tnt') else (None,)
        for stage in stages:
            rng = random.Random(42)
            pixels = []
            for y in range(16):
                row = []
                for x in range(16):
                    jitter = rng.randint(-18, 18)
                    c = tuple(max(0, min(255, n+jitter)) for n in color)
                    if name == 'wood' and y % 4 == 0: c = (74, 50, 29)
                    if name == 'tnt' and 5 <= y <= 10: c = (222, 218, 204)
                    # Deterministic branching cracks. Higher stages retain all earlier cracks.
                    if stage:
                        for branch in range(stage):
                            cx = (7 + (y // 2) * (1 if branch % 2 else -1) + branch * 3) % 16
                            if x == cx and y >= (branch * 3) % 12: c = (16, 15, 15)
                    row.append(c)
                pixels.append(row)
            png(ROOT / (f'{name}_{stage}.png' if stage is not None else f'{name}.png'), pixels)
    faces = [((-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)),
             ((1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)),
             ((1,-1,1),(1,-1,-1),(1,1,-1),(1,1,1)),
             ((-1,-1,-1),(-1,-1,1),(-1,1,1),(-1,1,-1)),
             ((-1,1,1),(1,1,1),(1,1,-1),(-1,1,-1)),
             ((-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1))]
    lines = ['# Original MineMakers 1 metre cube, centered at origin']
    for face in faces:
        for v in face: lines.append('v ' + ' '.join(str(n / 2) for n in v))
    lines += ['vt 0 0', 'vt 1 0', 'vt 1 1', 'vt 0 1']
    for i in range(6):
        v = i * 4 + 1
        lines += [f'f {v}/1 {v+1}/2 {v+2}/3', f'f {v}/1 {v+2}/3 {v+3}/4']
    (ROOT / 'cube.obj').write_text('\n'.join(lines) + '\n')

if __name__ == '__main__': generate()
