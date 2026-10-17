#!/usr/bin/env python3
"""Java/Lua 5.2 interoperability and Trailmakers adapter contract tests (no game needed)."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import struct
from desktop_protocol import HEADER, InputQueue, decode_overlay, resolve_directory, FrameWriter
from lupa.lua52 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
import sys
sys.path.insert(0,str(ROOT/'tests'))
from test_locomotion import LocomotionTests
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
        test.legacyCamera=True # exercise older AddCamera(player, position, direction) overload
        def tick(time):
            test.time = time
            self.lua.globals().update()
        tick(0.3)
        host = self.wire.decode(test.files['trailmakers.frame'])
        def snapshot(seq, blocks=True, creative=False, damage=False, stage=-1, session='mc_test'):
            rows = [['CAPS','HOST_MOTION_1'],['HOST_SESSION',host.session],['PLAYER',0,0,0,0,0,20,1 if creative else 0,1,0],['ORIGIN',0,80,0]]
            if blocks: rows.append(['BLOCK',2,0,2,'minecraft:stone',stage])
            if damage: rows.append(['EVENT',1,'HIT','b1',4,0,0,1,3,'Generic'])
            test.files['minecraft.frame'] = self.wire.encode(session,seq,self.rows(rows))
        snapshot(1,creative=True); tick(0.6)
        self.assertTrue(test.invincible)
        self.assertFalse(test.builder)
        self.assertTrue(test.camera)
        self.assertLessEqual(test.updateInterval,1/60)
        self.assertAlmostEqual(test.cameraPosition.y,6.62)
        self.assertAlmostEqual(test.cameraDirection.z,1)
        self.assertFalse(any(v.visible for _,v in test.spawns.items() if v.trigger), 'First-person camera must not be inside a visible avatar head')
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
        # Camera and movement use the same intent; Minecraft positions cannot move the host.
        test.files['control.frame']=self.wire.encode('mc_test',1,self.rows([['CONTROL',0,0,0,0,0,0,0,90,30,1.54,0,0]]))
        tick(2.25)
        self.assertAlmostEqual(test.cameraDirection.x,0.8660254037844386)
        self.assertAlmostEqual(test.cameraDirection.y,-0.5)
        self.assertTrue(test.bodyStatic)
        self.assertTrue(test.bodyTrigger)
        before=test.cameraActivations
        test.files['control.frame']=self.wire.encode('wrong_session',99,self.rows([['CONTROL',1,0,0,0,0,0,0,0,0,1.62,0,0]]))
        tick(2.4)
        self.assertEqual(test.cameraActivations,before,'Do not restart native camera activation each frame')
        self.assertAlmostEqual(test.cameraDirection.x,0.8660254037844386)
        snapshot(5,blocks=False)
        tick(2.7)
        self.assertAlmostEqual(test.cameraDirection.x,0.8660254037844386)
        self.assertGreater(test.cameraPosition.y,5,'Host ground must support the camera independently of Minecraft')
        # A frozen file is stale even though it can still be read successfully.
        tick(4.8)
        self.assertTrue(test.builder)
        self.assertFalse(test.camera)
        self.assertFalse(test.cameraActive)
        self.assertTrue(test.visible)
        self.assertFalse(test.bodyStatic)
        self.assertFalse(test.bodyTrigger)
        self.assertTrue(test.repair)
        self.assertFalse(test.invincible)
        self.assertFalse(any(v.alive for _,v in test.spawns.items()))
    def test_windows_locked_frame_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            destination=Path(temp)/'desktop.frame'
            destination.write_text('previous complete frame')
            writer=FrameWriter(temp)
            queue=InputQueue()
            queue.event('KEY',69,1,0)
            with patch('desktop_protocol.os.replace',side_effect=PermissionError(13,'Access denied')):
                self.assertFalse(writer.publish(queue.frame(True)))
                self.assertFalse(writer.publish(queue.frame(True)))
                self.assertEqual(destination.read_text(),'previous complete frame')
                self.assertEqual(len(queue.events),1,'A blocked replacement must not discard unacknowledged input')
                self.assertIsNotNone(writer.last_error)
            self.assertTrue(writer.publish(queue.frame(True)))
            decoded=self.wire.decode(destination.read_text())
            self.assertEqual(decoded.sequence,3)
            self.assertEqual(decoded.records[3][3],'KEY')
            self.assertIsNone(writer.last_error)
            self.assertEqual(writer.blocked_for,0)
            other=FrameWriter(temp)
            self.assertNotEqual(writer.temporary,other.temporary)
            with patch('desktop_protocol.os.replace',side_effect=OSError(28,'Disk full')):
                with self.assertRaises(OSError): writer.publish(queue.frame(True))
            writer.close(); other.close()
            self.assertFalse(writer.temporary.exists())
    def test_dynamic_folder_selection(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            dynamic=root/'data_dynamic_willnotbeuploadedtoworkshop'
            dynamic.mkdir()
            (dynamic/'trailmakers.frame').write_text('frame')
            self.assertEqual(resolve_directory(root),dynamic)
            self.assertEqual(resolve_directory(dynamic),dynamic)
            wrong=root/'data_dynamic'
            with self.assertRaises(ValueError): resolve_directory(wrong)
            self.assertFalse(wrong.exists(),'A mistyped path must not silently create an empty bridge folder')
            wrong.mkdir()
            with self.assertRaises(ValueError): resolve_directory(wrong)
            (wrong/'trailmakers.frame').write_text('frame')
            with self.assertRaises(ValueError): resolve_directory(root)
    def test_overlay_framing_and_input_ack(self):
        queue=InputQueue()
        queue.event('KEY',69,1,0)
        queue.event('CHAR',101)
        queue.held.add(69)
        # Input snapshots must be understood by the same Lua/Java text framing.
        snapshot=self.wire.decode(queue.frame(True))
        self.assertEqual(snapshot.session,queue.session)
        self.assertEqual(snapshot.records[1][1],'ACTIVE')
        raw=HEADER.pack(b'MMOV',1,2,2,7,1,1,queue.session.encode())+bytes(range(16))
        overlay=decode_overlay(raw)
        self.assertTrue(overlay.menu)
        self.assertEqual(overlay.pixels,bytes(range(16)))
        for cut in range(len(raw)):
            with self.assertRaises(ValueError): decode_overlay(raw[:cut])
        queue.acknowledge(overlay)
        self.assertEqual([e[1] for e in queue.events],[2])
        other=HEADER.pack(b'MMOV',1,2,2,8,99,0,b'other_session')+bytes(16)
        queue.acknowledge(decode_overlay(other))
        self.assertEqual(len(queue.events),1,'An old compositor frame must not acknowledge a new input session')
        old_session=queue.session
        queue.reset()
        self.assertNotEqual(queue.session,old_session)
        self.assertEqual(queue.events,[])
        self.assertEqual(queue.x,0)
        with self.assertRaises(ValueError): decode_overlay(HEADER.pack(b'MMOV',1,99999,2,8,0,0,b'test'))
    def test_java_interop_and_damage(self):
        java_home = os.environ.get('JAVA_HOME')
        javac = str(Path(java_home)/'bin/javac') if java_home else shutil.which('javac')
        java = str(Path(java_home)/'bin/java') if java_home else shutil.which('java')
        self.assertTrue(javac and java, 'JDK 21 required')
        with tempfile.TemporaryDirectory() as directory:
            d=Path(directory)
            (d/'lua.frame').write_text(self.wire.encode('lua_test',1,self.rows([['ACK',1],['LINK','session_1']])))
            files=[ROOT/'fabric/src/main/java/dev/minemakers/bridge'/f for f in ('Wire.java','BlockDamage.java','DesktopInput.java','HostPose.java')]
            subprocess.run([javac,'--release','21','-d',str(d),*[str(p) for p in files],str(ROOT/'tests/ProtocolTest.java')],check=True)
            subprocess.run([java,'-cp',str(d),'ProtocolTest',str(d)],check=True)
            decoded=self.wire.decode((d/'java.frame').read_text())
            self.assertEqual(decoded.session,'session_1')
            self.assertEqual(decoded.records[2][5],'minecraft:stone')
if __name__ == '__main__': unittest.main(verbosity=2)
