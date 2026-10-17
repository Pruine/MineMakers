#!/usr/bin/env python3
"""Java/Lua 5.2 interoperability and Trailmakers adapter contract tests (no game needed)."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
from lupa.lua52 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
class Tests(unittest.TestCase):
    def setUp(self):
        self.lua = LuaRuntime(unpack_returned_tuples=True)
        self.wire = self.lua.execute((ROOT/'mod/data_static/wire.lua').read_text())
    def rows(self, rows):
        return self.lua.table_from([self.lua.table_from(row) for row in rows])
    def test_framing(self):
        frame = self.wire.encode('test', 12, self.rows([['BLOCK',0,0,0,'minecraft:stone',3]]))
        self.assertEqual(self.wire.decode(frame).sequence, 12)
        for cut in range(len(frame)):
            with self.assertRaises(Exception): self.wire.decode(frame[:cut])
        for bad in ['NaN', 'inf', '30000001']:
            with self.assertRaises(Exception): self.wire.number(bad)
        with self.assertRaises(Exception): self.wire.encode('test',1,self.rows([['BAD\nROW']]))
    def test_native_adapter(self):
        self.lua.globals().TEST_ROOT = str(ROOT)
        self.lua.execute((ROOT/'tests/fake_trailmakers.lua').read_text())
        self.lua.execute((ROOT/'mod/main.lua').read_text())
        test = self.lua.globals().TEST
        def tick(time):
            test.time = time
            self.lua.globals().update()
        tick(0.3)
        host = self.wire.decode(test.files['trailmakers.frame'])
        def snapshot(seq, blocks=True, creative=False, damage=False, stage=-1, session='mc_test'):
            rows = [['HOST_SESSION',host.session],['PLAYER',0,0,0,0,0,20,1 if creative else 0,1,0],['ORIGIN',0,80,0]]
            if blocks: rows.append(['BLOCK',2,0,2,'minecraft:stone',stage])
            if damage: rows.append(['EVENT',1,'HIT','b1',4,0,0,1,3,'Generic'])
            test.files['minecraft.frame'] = self.wire.encode(session,seq,self.rows(rows))
        snapshot(1,creative=True); tick(0.6)
        self.assertTrue(test.invincible)
        self.assertFalse(test.builder)
        solid = [v for _,v in test.spawns.items() if v.alive and not v.trigger]
        self.assertEqual(len(solid),1)
        self.assertEqual(solid[0].texture,'stone_0')
        # Accumulated damage changes only the texture, preserving the physical collider.
        snapshot(2,damage=True,stage=4); tick(0.9)
        self.assertEqual(solid[0].texture,'stone_5')
        self.assertEqual(test.damageCalls,1)
        snapshot(3,damage=True,stage=5); tick(1.2)
        self.assertEqual(test.damageCalls,1, 'Repeated event must not damage a contraption twice')
        self.assertEqual(test.forceCalls,1)
        # Malformed snapshot must not despawn existing blocks or refresh heartbeat.
        test.files['minecraft.frame']='MMT1\tbroken\t4\t0\n'
        tick(1.5); self.assertTrue(solid[0].alive)
        snapshot(4,blocks=False); tick(1.8)
        self.assertFalse(solid[0].alive)
        self.lua.globals().MineMakersNativeBlockHit(1,2,3,20,'Projectile')
        tick(2.1)
        out = self.wire.decode(test.files['trailmakers.frame'])
        self.assertTrue(any(r[1]=='EVENT' and r[3]=='BLOCK_HIT' for _,r in out.records.items()))
        # A frozen file is stale even though it can still be read successfully.
        tick(4.2)
        self.assertTrue(test.builder)
        self.assertTrue(test.visible)
        self.assertFalse(test.invincible)
        self.assertFalse(any(v.alive for _,v in test.spawns.items()))
    def test_java_interop_and_damage(self):
        java_home = os.environ.get('JAVA_HOME')
        javac = str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
        java = str(Path(java_home)/'bin/java') if java_home else shutil.which('java')
        self.assertTrue(javac and java, 'JDK 21 required')
        with tempfile.TemporaryDirectory() as directory:
            d=Path(directory)
            (d/'lua.frame').write_text(self.wire.encode('lua_test',1,self.rows([['ACK',1],['LINK','session_1']])))
            files=[ROOT/'fabric/src/main/java/dev/minemakers/bridge'/f for f in ('Wire.java','BlockDamage.java')]
            subprocess.run([javac,'--release','21','-d',str(d),*[str(p) for p in files],str(ROOT/'tests/ProtocolTest.java')],check=True)
            subprocess.run([java,'-cp',str(d),'ProtocolTest',str(d)],check=True)
            decoded=self.wire.decode((d/'java.frame').read_text())
            self.assertEqual(decoded.session,'session_1')
            self.assertEqual(decoded.records[2][5],'minecraft:stone')
if __name__ == '__main__': unittest.main(verbosity=2)
