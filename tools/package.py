#!/usr/bin/env python3
"""Package source + Lua mod. Include the remapped Fabric jar only after a successful build."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import argparse

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--source-only', action='store_true', help='Explicitly produce an uncompiled developer bundle')
args = p.parse_args()
jar = root / 'fabric/build/libs/minemakers-fabric-0.4.0.jar'
if not args.source_only and not jar.is_file():
    p.error('Build fabric/gradlew build first; or explicitly select --source-only. No playable release has been built.')
out = root / 'dist' / ('MineMakers-source-0.4.0.zip' if args.source_only else 'MineMakers-experimental-0.4.0.zip')
out.parent.mkdir(exist_ok=True)
with ZipFile(out, 'w', ZIP_DEFLATED) as z:
    for path in sorted((root/'mod').rglob('*')):
        if path.is_file(): z.write(path, 'Trailmakers/' + str(path.relative_to(root/'mod')))
    for folder in ('docs', 'tools', 'tests'):
        for path in sorted((root/folder).rglob('*')):
            if path.is_file() and '__pycache__' not in str(path): z.write(path, str(path.relative_to(root)))
    for path in sorted((root/'fabric').rglob('*')):
        rel = path.relative_to(root)
        if path.is_file() and not any(part in ('.gradle','build','run') for part in rel.parts): z.write(path, str(rel))
    for name in ('README.md','LICENSE','THIRD-PARTY-NOTICES.md','Start-MineMakers-Overlay.cmd','START-HERE.txt'):
        if name.endswith('.cmd'):
            z.writestr(name,(root/name).read_text().replace('\n','\r\n'))
        else: z.write(root/name,name)
    if not args.source_only: z.write(jar, 'Minecraft/mods/' + jar.name)
print(out)
