-- MineMakers Trailmakers half. No external Lua packages, HTTP, or unsafe code execution.
local W=tm.os.DoFile('wire')
local C=tm.os.DoFile('config')
local smoother=tm.os.DoFile('view_smoother').new(C.viewSmoothingDelay or 0.035,C.teleportSnapDistance or 4)
tm.os.SetModTargetDeltaTime(C.updateInterval or 1/60)
local anchor=nil
local objects,entities,avatar={},{},{}
local nativeTargets,activeTargets={},{}
local nextTarget=0
local keys={}
local session='tm_'..tostring(math.floor(tm.os.GetRealtimeSinceStartup()*1000))..'_'..tostring(math.random(100000,999999))
local seq,lastMcSeq,lastEvent,hostEvent=0,-1,0,0
local mcSession=nil
local pending={}
local lastRead,lastWrite,lastFresh=0,-100,-100
local player=nil
local mcOrigin={x=0,y=0,z=0}
local slot=0
local pitch=0
local lookYaw=0
local camera=false
local lastViewSeq=-1
local lastViewRead=-100
local lastScene=-100
local sceneRows={}
local oldBuilder=nil
local linked=false
local warned=false
local cameraWarning=false
local puppetWarning=false
local V=tm.vector3.Create
local function point(x,y,z) return {x=x,y=y,z=z} end
local function world(p) return V(anchor.x+p.x,anchor.y+p.y,anchor.z+p.z) end
local function relative(p) return point(p.x-anchor.x,p.y-anchor.y,p.z-anchor.z) end
local function pos(x,y,z) return point(W.number(x),W.number(y),W.number(z)) end
local function exists(o) return o and o.Exists() end
local function destroy(o) if exists(o) then o.Despawn() end end
local function key(p) return string.format('%.4f,%.4f,%.4f',p.x,p.y,p.z) end
local function texture(name)
    if name=='minecraft:tnt' then return 'tnt' end
    if name=='minecraft:bedrock' then return 'bedrock' end
    if string.find(name,'planks',1,true) or string.find(name,'log',1,true) or string.find(name,'wood',1,true) then return 'wood' end
    return 'stone'
end
local function cube(p,tex,scale,solid)
    local o=tm.physics.SpawnCustomObjectRigidbody(world(p),'cube',tex,true,1,tm.enums.physicsMaterial.stone)
    if not o then error('Could not spawn MineMakers object') end
    o.GetTransform().SetScale(scale or 1)
    o.SetIsTrigger(not solid)
    if solid then o.SetIsStatic(true) end
    return o
end
local function applyView(p)
    if not linked or not p then return end
    if C.firstPerson~=false then
        local eye=world(point(p.pos.x,p.pos.y+(p.eyeHeight or C.eyeHeight or 1.62),p.pos.z))
        if not camera then
            tm.players.AddCamera(C.playerId,eye,V(0,0,0),1000)
            camera=true
        end
        local yaw=p.yaw*math.pi/180; local pitchRad=p.pitch*math.pi/180
        tm.players.SetCameraPosition(C.playerId,eye)
        tm.players.SetCameraRotation(C.playerId,V(-math.sin(yaw)*math.cos(pitchRad),-math.sin(pitchRad),math.cos(yaw)*math.cos(pitchRad)))
        tm.players.ActivateCamera(C.playerId,0) -- retain ownership if native input selects another camera
    end
    -- Native controller failures must not prevent first-person camera setup.
    local ok,err=pcall(function()
        if not tm.players.IsPlayerInSeat(C.playerId) then
            local t=tm.players.GetPlayerTransform(C.playerId)
            t.SetPosition(world(p.pos)); t.SetRotation(V(0,-p.yaw,0))
        end
    end)
    if not ok and not puppetWarning then tm.os.Log('MineMakers native follow: '..tostring(err)); puppetWarning=true end
end
local function cleanup()
    smoother:reset()
    if camera then
        tm.players.DeactivateCamera(C.playerId,0)
        tm.players.RemoveCamera(C.playerId)
        camera=false
    end
    for _,b in pairs(objects) do destroy(b.object) end
    for _,e in pairs(entities) do destroy(e) end
    for _,a in pairs(avatar) do destroy(a) end
    objects,entities,avatar={},{},{}
    if linked then
        local body=tm.players.GetPlayerGameObject(C.playerId)
        if exists(body) then body.SetIsVisible(true) end
        tm.players.SetPlayerIsInvincible(C.playerId,false)
        if oldBuilder~=nil then tm.players.SetBuilderEnabled(C.playerId,oldBuilder) end
        linked=false
    end
end
local function message(text) tm.playerUI.SetUIValue(C.playerId,'mm_status',text) end
local function commit(rows,newSession)
    -- Validate all rows before changing any objects or executing an event.
    local p=nil; local blocks,es,events={},{},{}; local hotbar={}
    local ack=nil; local origin=nil; local handshaken=false
    for _,r in ipairs(rows) do
        if r[1]=='PLAYER' then
            if #r~=10 then error('Bad PLAYER row') end
            p={pos=pos(r[2],r[3],r[4]),yaw=W.number(r[5]),pitch=W.number(r[6]),health=W.number(r[7]),creative=r[8]=='1',alive=r[9]=='1',armor=W.number(r[10])}
        elseif r[1]=='BLOCK' then
            if #r~=6 then error('Bad BLOCK row') end
            if #blocks<C.maxBlocks then blocks[#blocks+1]={pos=pos(r[2],r[3],r[4]),material=r[5],stage=W.number(r[6])} end
        elseif r[1]=='ENTITY' then
            if #r~=6 then error('Bad ENTITY row') end
            es[#es+1]={id=r[2],kind=r[3],pos=pos(r[4],r[5],r[6])}
        elseif r[1]=='EVENT' then
            if #r~=10 or r[3]~='HIT' then error('Bad event') end
            events[#events+1]={id=W.number(r[2]),target=r[4],damage=W.number(r[5]),direction=pos(r[6],r[7],r[8]),knockback=W.number(r[9]),kind=r[10]}
        elseif r[1]=='SLOT' then
            if #r~=4 then error('Bad SLOT row') end
            hotbar[#hotbar+1]='['..(W.number(r[2])+1)..'] '..string.gsub(r[3],'minecraft:','')..' x'..W.number(r[4])
        elseif r[1]=='HOST_SESSION' then handshaken=r[2]==session
        elseif r[1]=='HOST_ACK' then ack=W.number(r[2])
        elseif r[1]=='ORIGIN' then origin=pos(r[2],r[3],r[4]) end
    end
    if not p or not handshaken then error('Waiting for current host handshake') end
    if newSession then cleanup(); lastEvent=0; lastViewSeq=-1; sceneRows={}; lastScene=-100 end
    if not anchor then
        local q=tm.players.GetPlayerTransform(C.playerId).GetPositionWorld()
        anchor=point(q.x,q.y,q.z)
    end
    if not linked then
        oldBuilder=tm.players.GetBuilderEnabled(C.playerId)
        tm.players.SetBuilderEnabled(C.playerId,false)
        linked=true
    end
    if lastViewSeq>=0 and player then
        p.pos=player.pos; p.yaw=player.yaw; p.pitch=player.pitch; p.eyeHeight=player.eyeHeight
    end
    player=p; if origin then mcOrigin=origin end
    local body=tm.players.GetPlayerGameObject(C.playerId)
    if exists(body) then body.SetIsVisible(false) end
    tm.players.SetPlayerIsInvincible(C.playerId,p.creative)
    if not keys.j and not keys.l then lookYaw=p.yaw end
    if not keys.up and not keys.down then pitch=p.pitch end
    if lastViewSeq<0 then
        smoother:push(p,tm.os.GetRealtimeSinceStartup())
        applyView(p)
    end
    local seen={}
    for _,b in ipairs(blocks) do
        local k=key(b.pos); seen[k]=true; local previous=objects[k]
        local tex=texture(b.material)..'_'..math.max(0,math.min(10,math.floor(b.stage+1)))
        if not previous then previous={object=cube(point(b.pos.x+0.5,b.pos.y+0.5,b.pos.z+0.5),tex,1,true)}; objects[k]=previous
        elseif previous.texture~=tex then previous.object.SetTexture(tex) end
        previous.texture=tex
    end
    for k,b in pairs(objects) do if not seen[k] then destroy(b.object); objects[k]=nil end end
    seen={}
    for _,e in ipairs(es) do
        seen[e.id]=true
        if not entities[e.id] then entities[e.id]=cube(e.pos,string.find(e.kind,'tnt',1,true) and 'tnt_0' or 'pearl',0.2,false) end
        entities[e.id].GetTransform().SetPosition(world(e.pos))
    end
    for id,e in pairs(entities) do if not seen[id] then destroy(e); entities[id]=nil end end
    local parts={
        {name='head',x=0,y=1.5,z=0,scale=V(0.5,0.5,0.5),tex='skin'},
        {name='body',x=0,y=1,z=0,scale=V(0.5,0.6,0.25),tex=p.armor>0 and 'armor' or 'shirt'},
        {name='left_arm',x=-0.375,y=1,z=0,scale=V(0.25,0.6,0.25),tex=p.armor>0 and 'armor' or 'skin'},
        {name='right_arm',x=0.375,y=1,z=0,scale=V(0.25,0.6,0.25),tex=p.armor>0 and 'armor' or 'skin'},
        {name='left_leg',x=-0.125,y=0.4,z=0,scale=V(0.25,0.6,0.25),tex='pants'},
        {name='right_leg',x=0.125,y=0.4,z=0,scale=V(0.25,0.6,0.25),tex='pants'},
    }
    local angle=-p.yaw*math.pi/180
    for _,a in ipairs(parts) do
        if not avatar[a.name] then avatar[a.name]=cube(p.pos,a.tex,a.scale,false) end
        local o=avatar[a.name]; o.SetIsVisible(p.alive and C.firstPerson==false); o.SetTexture(a.tex)
        o.GetTransform().SetPosition(world(point(p.pos.x+a.x*math.cos(angle),p.pos.y+a.y,p.pos.z+a.x*math.sin(angle))))
        o.GetTransform().SetRotation(0,-p.yaw,0)
    end
    table.sort(events,function(a,b) return a.id<b.id end)
    for _,e in ipairs(events) do
        if e.id>lastEvent then
            -- At-most-once effects, including retries after an engine force call fails.
            lastEvent=e.id
            local b=nativeTargets[e.target]
            if b and b.Exists() and not b.IsDead() then
                b.DoDamageAmount(math.max(0,math.min(100,e.damage))*C.nativeDamageScale,C.playerId,e.kind)
                local d=e.direction; local force=math.max(0,math.min(20,e.knockback))
                b.GetStructure().AddForce(d.x*force,d.y*force,d.z*force,tm.enums.forceType.impulse)
            end
        end
    end
    if ack then for i=#pending,1,-1 do if pending[i][2]<=ack then table.remove(pending,i) end end end
    tm.playerUI.SetUIValue(C.playerId,'mm_hotbar',table.concat(hotbar,' | '))
    message(string.format('Java linked | HP %.1f | %s | slot %d',p.health,p.creative and 'Creative' or 'Survival',slot+1))
end
local function enqueue(kind,...)
    if not linked or #pending>=256 then return false end
    hostEvent=hostEvent+1
    local row={'EVENT',hostEvent,kind}; local args={...}
    for _,v in ipairs(args) do row[#row+1]=v end
    pending[#pending+1]=row; return true
end
-- Integration points for a FUTURE version-specific native weapon bridge.
-- Public Trailmakers Lua provides no damage callbacks for custom objects; these are NOT wired automatically.
function MineMakersNativeBlockHit(x,y,z,damage,kind)
    return enqueue('BLOCK_HIT',x,y,z,damage,kind or 'Generic')
end
function MineMakersNativePlayerHit(damage,vx,vy,vz)
    return enqueue('PLAYER_HIT',damage,vx or 0,vy or 0,vz or 0)
end
local function targets(rows)
    local seen={}; local count=0
    for _,owner in ipairs(tm.players.CurrentPlayers()) do
        for _,s in ipairs(tm.players.GetPlayerStructures(owner.playerId)) do
            for _,b in ipairs(s.GetBlocks()) do
                if count>=C.maxTargets then break end
                local p=relative(b.GetPosition())
                local dx,dy,dz=p.x-player.pos.x,p.y-player.pos.y,p.z-player.pos.z
                if b.Exists() and not b.IsDead() and dx*dx+dy*dy+dz*dz<900 then
                    local id=nil
                    for old,ref in pairs(nativeTargets) do if ref.Exists() and ref.Equals(b) then id=old; break end end
                    if not id then nextTarget=nextTarget+1; id='b'..nextTarget end
                    nativeTargets[id]=b; seen[id]=true; count=count+1
                    local size=b.GetSize()
                    rows[#rows+1]={'TARGET',id,p.x,p.y,p.z,size.x*C.builderUnit,size.y*C.builderUnit,size.z*C.builderUnit}
                end
            end
        end
    end
    for id in pairs(nativeTargets) do if not seen[id] then nativeTargets[id]=nil end end
    activeTargets=seen
end
local function terrain(rows)
    local p=player.pos; local radius=C.terrainRadius
    for x=-radius,radius do for z=-radius,radius do
        local gx=math.floor(p.x)+x; local gz=math.floor(p.z)+z
        -- Starting near the feet inside our native capsule avoids raycasting its head.
        local nearSelf=math.abs(gx+0.5-p.x)<0.65 and math.abs(gz+0.5-p.z)<0.65
        local origin=world(point(gx+0.5,p.y+(nearSelf and 0.05 or 3),gz+0.5))
        local hit=tm.physics.RaycastData(origin,V(0,-1,0),C.terrainDepth,true)
        if hit.DidHit() then
            local surface=relative(hit.GetHitPosition())
            -- Do not mirror our own placed blocks back as foreign terrain.
            local own=objects[key(point(gx,math.floor(surface.y-0.01),gz))]
            if not own then
                local y=math.floor(surface.y-0.01)
                rows[#rows+1]={'COLLIDER',gx,y,gz,gx+1,y+1,gz+1}
            end
        end
    end end
end
local function flag(k) return keys[k] and 1 or 0 end
local function readView(now)
    if not linked or not mcSession then return end
    local ok,text=pcall(tm.os.ReadAllText_Dynamic,'view.frame')
    if not ok then return end
    local valid,frame=pcall(W.decode,text)
    if not valid or frame.session~=mcSession or frame.sequence<=lastViewSeq or #frame.records~=1 then return end
    local r=frame.records[1]
    if #r~=8 or r[1]~='VIEW' then return end
    local p={pos=pos(r[2],r[3],r[4]),yaw=W.number(r[5]),pitch=W.number(r[6]),eyeHeight=W.number(r[7])}
    if p.eyeHeight<0 or p.eyeHeight>3 then return end
    smoother:push(p,now)
    player.pos=p.pos; player.yaw=p.yaw; player.pitch=p.pitch; player.eyeHeight=p.eyeHeight
    if not keys.j and not keys.l then lookYaw=p.yaw end
    if not keys.up and not keys.down then pitch=p.pitch end
    lastViewSeq=frame.sequence; lastFresh=now
end
local function write()
    seq=seq+1
    local rows={}
    if linked and player and mcSession then
        local dt=C.inputInterval or C.interval
        lookYaw=lookYaw+((keys.l and 1 or 0)-(keys.j and 1 or 0))*(C.turnSpeed or 120)*dt
        pitch=math.max(-89,math.min(89,pitch+((keys.down and 1 or 0)-(keys.up and 1 or 0))*(C.turnSpeed or 120)*dt))
        local yaw=lookYaw
        rows[#rows+1]={'LINK',mcSession}
        rows[#rows+1]={'ACK',lastEvent}
        rows[#rows+1]={'INPUT',flag('w'),flag('s'),flag('a'),flag('d'),flag('space'),flag('left shift'),flag('f'),flag('g'),yaw,pitch,0,slot}
        local now=tm.os.GetRealtimeSinceStartup()
        if now-lastScene>=C.interval then
            lastScene=now; sceneRows={}; targets(sceneRows); terrain(sceneRows)
        end
        for _,row in ipairs(sceneRows) do rows[#rows+1]=row end
        for _,event in ipairs(pending) do rows[#rows+1]=event end
    end
    tm.os.WriteAllText_Dynamic('trailmakers.frame',W.encode(session,seq,rows))
end
local function keyDown(p,k)
    if p.playerId~=C.playerId then return end
    k=string.lower(k); keys[k]=true
    local n=tonumber(k); if n and n>=1 and n<=9 then slot=n-1 end

end
local function keyUp(p,k) if p.playerId==C.playerId then keys[string.lower(k)]=false end end
function update()
    local now=tm.os.GetRealtimeSinceStartup()
    if now-lastViewRead>=(C.viewInterval or 0.02) then
        lastViewRead=now
        local ok,err=pcall(readView,now)
        if not ok and not warned then tm.os.Log('MineMakers camera: '..tostring(err)); warned=true end
    end
    if linked then
        local pose=smoother:sample(now)
        if pose then
            local ok,err=pcall(applyView,pose)
            if not ok and not cameraWarning then
                tm.os.Log('MineMakers first-person camera failed: '..tostring(err))
                message('First-person camera failed; see mod log')
                cameraWarning=true
            elseif ok then cameraWarning=false end
        end
    end
    if now-lastRead>=C.interval then
        lastRead=now
        local ok,text=pcall(tm.os.ReadAllText_Dynamic,'minecraft.frame')
        if ok then
            local valid,frame=pcall(W.decode,text)
            if valid and (frame.session~=mcSession or frame.sequence>lastMcSeq) then
                local applied,err=pcall(commit,frame.records,frame.session~=mcSession)
                if applied then
                    if mcSession~=frame.session then pending={}; hostEvent=0 end
                    mcSession=frame.session; lastMcSeq=frame.sequence; lastFresh=now; warned=false
                elseif not warned then tm.os.Log('MineMakers rejected snapshot: '..tostring(err)); warned=true end
            end
        end
        if now-lastFresh>C.timeout and linked then cleanup(); keys={}; message('Java disconnected; controls restored') end
    end
    if now-lastWrite>=(C.inputInterval or C.interval) then
        lastWrite=now
        local ok,err=pcall(write)
        if not ok and not warned then tm.os.Log('MineMakers bridge: '..tostring(err)); warned=true end
    end
end
for _,tex in ipairs({'skin','shirt','pants','armor','pearl'}) do tm.physics.AddTexture(tex..'.png',tex) end
for _,material in ipairs({'wood','stone','bedrock','tnt'}) do
    for stage=0,10 do local t=material..'_'..stage; tm.physics.AddTexture(t..'.png',t) end
end
tm.physics.AddMesh('cube.obj','cube')
tm.playerUI.AddUILabel(C.playerId,'mm_status','MineMakers: waiting for Minecraft Java')
tm.playerUI.AddUILabel(C.playerId,'mm_hotbar','F: attack/mine | G: use/place | 1-9: hotbar | arrows: pitch')
tm.input.OnPlayerKeyDown.add(keyDown)
tm.input.OnPlayerKeyUp.add(keyUp)
tm.players.OnPlayerLeft.add(function(p) if p.playerId==C.playerId then cleanup(); keys={} end end)

tm.players.OnPlayerDied.add(function(p)
    if p.playerId==C.playerId and linked and player and not player.creative then
        -- Lua exposes death, not incoming damage. This coarse fallback cannot apply armor before native death.
        MineMakersNativePlayerHit(100,0,0,0)
    end
end)
