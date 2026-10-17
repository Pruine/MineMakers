-- Render-only interpolation. Never predicts through collisions or changes Minecraft physics.
local M={}
local function copy(p)
    return {pos={x=p.pos.x,y=p.pos.y,z=p.pos.z},yaw=p.yaw,pitch=p.pitch,eyeHeight=p.eyeHeight}
end
function M.new(delay,snapDistance)
    local s={samples={},delay=math.max(0,math.min(0.2,delay or 0.035)),snapDistance=snapDistance or 4}
    function s:reset() self.samples={} end
    function s:push(p,time)
        local last=self.samples[#self.samples]
        if last and time<last.time then return false end
        if last then
            local d=p.pos; local q=last.pose.pos
            if (d.x-q.x)^2+(d.y-q.y)^2+(d.z-q.z)^2>self.snapDistance^2 then self:reset(); last=nil end
        end
        local entry={pose=copy(p),time=time}
        if last and time==last.time then self.samples[#self.samples]=entry else self.samples[#self.samples+1]=entry end
        while #self.samples>16 do table.remove(self.samples,1) end
        return true
    end
    function s:sample(time)
        local samples=self.samples
        if #samples==0 then return nil end
        local at=time-self.delay
        if at<=samples[1].time then return copy(samples[1].pose) end
        for i=2,#samples do
            local a,b=samples[i-1],samples[i]
            if at<=b.time then
                local t=(at-a.time)/(b.time-a.time)
                local p,q=a.pose,b.pose
                local function lerp(x,y) return x+(y-x)*t end
                local yawDelta=(q.yaw-p.yaw+180)%360-180
                return {pos={x=lerp(p.pos.x,q.pos.x),y=lerp(p.pos.y,q.pos.y),z=lerp(p.pos.z,q.pos.z)},
                    yaw=p.yaw+yawDelta*t,pitch=lerp(p.pitch,q.pitch),eyeHeight=lerp(p.eyeHeight or 1.62,q.eyeHeight or 1.62)}
            end
        end
        return copy(samples[#samples].pose) -- no extrapolation when frames stop
    end
    return s
end
return M
