-- Host-owned player motion. Queries Trailmakers physics directly, in metres.
-- A swept box sampled with rays is used because the public Lua API has no capsule cast.
local M={}
local R,SKIN=0.29,0.015
local offsets={-R,0,R}
local function copy(p) return {x=p.x,y=p.y,z=p.z} end
local function clamp(v,a,b) return math.max(a,math.min(b,v)) end
function M.new(position,ray)
    local s={pos=copy(position),vx=0,vy=0,vz=0,grounded=false,flying=false,crouch=false,fall=0}
    function s:teleport(p) self.pos=copy(p); self.vx=0; self.vy=0; self.vz=0; self.fall=0; self.grounded=false end
    local function vertical(p,dy,height)
        local allowed=math.abs(dy); local down=dy<0
        for _,x in ipairs(offsets) do for _,z in ipairs(offsets) do
            local origin={x=p.x+x,y=p.y+(down and 0.08 or height-0.08),z=p.z+z}
            local hit=ray(origin,{x=0,y=down and -1 or 1,z=0},allowed+0.08+SKIN)
            if hit then allowed=math.min(allowed,math.max(0,hit.distance-0.08-SKIN)) end
        end end
        return down and -allowed or allowed
    end
    local function horizontal(p,delta,axis,height)
        if math.abs(delta)<0.000001 then return delta end
        local sign=delta<0 and -1 or 1; local allowed=math.abs(delta)
        for _,side in ipairs(offsets) do for _,y in ipairs({0.08,0.6,height-0.08}) do
            local origin={x=p.x,y=p.y+y,z=p.z}
            origin[axis=='x' and 'z' or 'x']=origin[axis=='x' and 'z' or 'x']+side
            local dir={x=0,y=0,z=0}; dir[axis]=sign
            local hit=ray(origin,dir,allowed+R+SKIN)
            if hit then allowed=math.min(allowed,math.max(0,hit.distance-R-SKIN)) end
        end end
        return sign*allowed
    end
    function s:step(input,dt)
        dt=clamp(dt,0,1/30)
        self.crouch=input.sneak and not input.flying
        self.flying=input.creative and input.flying or false
        local height=self.crouch and 1.5 or 1.8
        -- Do not stand into a low ceiling.
        if self.height and height>self.height and vertical(self.pos,height-self.height,self.height)<height-self.height-0.001 then height=self.height; self.crouch=true end
        self.height=height
        local yaw=(input.yaw or 0)*math.pi/180
        local f,side=input.forward or 0,input.sideways or 0
        local length=math.sqrt(f*f+side*side); if length>1 then f=f/length; side=side/length end
        local speed=self.flying and 10.89 or (self.crouch and 1.295 or (input.sprint and 5.612 or 4.317))
        local dx=(-math.sin(yaw)*f+math.cos(yaw)*side)*speed
        local dz=(math.cos(yaw)*f+math.sin(yaw)*side)*speed
        local blend=1-math.exp(-(self.grounded and 18 or (self.flying and 12 or 5))*dt)
        self.vx=self.vx+(dx-self.vx)*blend; self.vz=self.vz+(dz-self.vz)*blend
        if self.flying then self.vy=((input.jump and 1 or 0)-(input.sneak and 1 or 0))*speed; self.fall=0
        elseif self.grounded and input.jump then self.vy=8.4; self.grounded=false
        else self.vy=math.max(-78.4,self.vy-32*dt) end
        local priorY=self.pos.y
        for _,axis in ipairs({'x','z'}) do
            local delta=(axis=='x' and self.vx or self.vz)*dt
            local moved=horizontal(self.pos,delta,axis,height)
            if not self.flying and self.grounded and math.abs(moved-delta)>0.001 then
                -- A stair is accepted only if there is headroom and supporting ground.
                local up=vertical(self.pos,0.6,height); local raised=copy(self.pos); raised.y=raised.y+up
                local across=horizontal(raised,delta,axis,height); raised[axis]=raised[axis]+across
                local down=vertical(raised,-up-0.1,height)
                if math.abs(across)>math.abs(moved)+0.001 and down>-up-0.1+0.001 then
                    self.pos=raised; self.pos.y=self.pos.y+down; moved=0
                end
            end
            local next=copy(self.pos); next[axis]=next[axis]+moved
            if self.crouch and self.grounded and vertical(next,-0.6,height)<=-0.599 then moved=0 end
            self.pos[axis]=self.pos[axis]+moved
        end
        local dy=self.vy*dt; local moved=vertical(self.pos,dy,height)
        self.pos.y=self.pos.y+moved
        local landed=not self.flying and dy<0 and moved>dy+0.00001
        if landed then self.vy=0
        elseif dy>0 and moved<dy-0.00001 then self.vy=0 end
        self.grounded=landed or (not self.flying and self.vy<=0 and vertical(self.pos,-0.04,height)>-0.0399)
        if self.pos.y<priorY and not self.flying then self.fall=self.fall+priorY-self.pos.y end
        local fall=0
        if self.grounded then fall=self.fall; self.fall=0 end
        return fall
    end
    return s
end
return M
