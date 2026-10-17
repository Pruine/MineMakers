"""Exercise the Lua controller against geometric surfaces, not a mocked collision result."""
from pathlib import Path
import unittest
from lupa.lua52 import LuaRuntime
ROOT=Path(__file__).resolve().parents[1]
class LocomotionTests(unittest.TestCase):
    def setUp(self):
        self.lua=LuaRuntime(unpack_returned_tuples=True)
        self.module=self.lua.execute((ROOT/'mod/data_static/locomotion.lua').read_text())
        self.boxes=[(-100,-10,-100,100,0,100)]
    def table(self,**values): return self.lua.table_from(values)
    def ray(self,origin,direction,length):
        nearest=None
        for box in self.boxes:
            near,far=-float('inf'),float('inf')
            for i,axis in enumerate(('x','y','z')):
                p,d=origin[axis],direction[axis]
                if abs(d)<1e-10:
                    if p<box[i] or p>box[i+3]: far=-1;break
                else:
                    a,b=(box[i]-p)/d,(box[i+3]-p)/d
                    near=max(near,min(a,b));far=min(far,max(a,b))
            # Unity rays ignore colliders containing their origin.
            if 0<=near<=far and near<=length and (nearest is None or near<nearest): nearest=near
        return None if nearest is None else self.table(distance=nearest)
    def player(self,x=0,y=0.015,z=0): return self.module.new(self.table(x=x,y=y,z=z),self.ray)
    def run_motion(self,p,seconds=1,dt=1/120,**intent):
        intent=dict({'forward':0,'sideways':0,'yaw':0},**intent)
        data=self.table(**intent)
        for _ in range(round(seconds/dt)): p.step(p,data,dt)
    def test_cardinal_movement_matches_camera(self):
        for yaw,axis,sign in [(0,'z',1),(90,'x',-1),(180,'z',-1),(270,'x',1)]:
            with self.subTest(yaw=yaw):
                p=self.player();self.run_motion(p,forward=1,yaw=yaw)
                self.assertGreater(p.pos[axis]*sign,3)
                self.assertAlmostEqual(p.pos['x' if axis=='z' else 'z'],0,places=5)
                self.assertAlmostEqual(p.pos.y,.015,places=5)
    def test_no_floor_rounding_or_fall_through_thin_platform(self):
        self.boxes=[(-10,-.02,-10,10,.37,10)]
        p=self.player(y=2);self.run_motion(p,seconds=3)
        self.assertAlmostEqual(p.pos.y,.385,places=5)
        self.assertTrue(p.grounded)
    def test_walls_ceilings_and_jump(self):
        self.boxes.append((-10,0,2,10,4,2.05))
        p=self.player();self.run_motion(p,seconds=2,forward=1)
        self.assertLessEqual(p.pos.z,2-.29-.015+.00001)
        p=self.player();self.run_motion(p,seconds=.2,jump=True)
        self.assertGreater(p.pos.y,.8)
        self.boxes.append((-10,2,-10,10,2.05,10))
        p=self.player();self.run_motion(p,seconds=.2,jump=True)
        self.assertLessEqual(p.pos.y+1.8,2-.015+.00001)
    def test_step_height_crouch_edge_and_flight(self):
        self.boxes.append((-10,0,1,10,.5,3))
        p=self.player();self.run_motion(p,seconds=.6,forward=1)
        self.assertGreater(p.pos.z,1)
        self.assertAlmostEqual(p.pos.y,.515,places=4)
        self.boxes=[(-2,-10,-2,2,0,2)]
        p=self.player();self.run_motion(p,seconds=4,forward=1,sneak=True)
        self.assertLess(p.pos.z,2.3)
        self.assertGreater(p.pos.y,0)
        self.boxes=[]
        p=self.player();self.run_motion(p,seconds=1,creative=True,flying=True,jump=True)
        self.assertGreater(p.pos.y,10)
        self.assertFalse(p.grounded)
    def test_frame_rate_independence_and_no_stale_prediction(self):
        a,b=self.player(),self.player()
        self.run_motion(a,forward=1,dt=1/60);self.run_motion(b,forward=1,dt=1/120)
        self.assertLess(abs(a.pos.z-b.pos.z),.05)
        self.run_motion(a,seconds=1)
        stopped=a.pos.z;self.run_motion(a,seconds=1)
        self.assertLess(a.pos.z-stopped,.001)
    def test_cannot_stand_into_low_ceiling(self):
        self.boxes.append((-10,1.6,-10,10,1.7,10))
        p=self.player();self.run_motion(p,seconds=.1,sneak=True)
        self.run_motion(p,seconds=.1)
        self.assertTrue(p.crouch)
        self.assertEqual(p.height,1.5)
