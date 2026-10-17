-- Contract double, NOT a substitute for Trailmakers/MoonSharp acceptance testing.
TEST={time=0,files={},textures={},spawns={},labels={},invincible=false,builder=true,damageCalls=0,forceCalls=0}
local T=TEST
local function event() return {add=function(f) end} end
local position={x=0,y=5,z=0}
local function vector(x,y,z) return {x=x or 0,y=y or 0,z=z or 0} end
local function transform()
    local t={position=vector(0,0,0)}
    t.SetPosition=function(p) t.position=p end
    t.SetScale=function(s) t.scale=s end
    t.SetRotation=function(x,y,z) end
    t.GetPositionWorld=function() return position end
    t.Forward=function() return vector(0,0,1) end
    return t
end
local body={Exists=function() return true end,SetIsVisible=function(v) T.visible=v end,
    GetIsStatic=function() return T.bodyStatic or false end,SetIsStatic=function(v) T.bodyStatic=v end,
    SetIsTrigger=function(v) T.bodyTrigger=v end}
T.ray=function(origin,direction,distance)
    if direction.y>=0 then return nil end
    local t=(4-origin.y)/direction.y
    if t>=0 and t<=distance then return t end
end
T.block={}
T.block.Exists=function() return true end
T.block.IsDead=function() return false end
T.block.GetPosition=function() return vector(0,5,2) end
T.block.GetSize=function() return vector(2,2,2) end
T.block.Equals=function(b) return b==T.block end
T.block.DoDamageAmount=function(damage,owner,kind) assert(damage>=0); T.damageCalls=T.damageCalls+1 end
T.block.GetStructure=function() return {AddForce=function(x,y,z,kind) assert(kind=='Impulse'); T.forceCalls=T.forceCalls+1 end} end
local structure={GetBlocks=function() return {T.block} end}
tm={os={},physics={},players={},playerUI={},input={},vector3={Create=vector}}
setmetatable(tm,{__index=function(_,name) error('cannot access field '..name..' of userdata<Trailmakers.Mods.Api.ModApiRoot>') end})
tm.os.DoFile=function(name) return assert(loadfile(TEST_ROOT..'/mod/data_static/'..name..'.lua'))() end
tm.os.SetModTargetDeltaTime=function(dt) T.updateInterval=dt end
tm.os.GetRealtimeSinceStartup=function() return T.time end
tm.os.Log=function(s) T.lastLog=s end
tm.os.ReadAllText_Dynamic=function(path) assert(T.files[path], 'not found'); return T.files[path] end
tm.os.WriteAllText_Dynamic=function(path,s) T.files[path]=s end
tm.physics.AddTexture=function(path,name)
    local f=assert(io.open(TEST_ROOT..'/mod/'..path,'rb'),'Asset path is relative to mod root: '..path); f:close()
    T.textures[name]=true
end
tm.physics.AddMesh=function(path,name) assert(path=='data_static/cube.obj'); local f=assert(io.open(TEST_ROOT..'/mod/'..path,'r')); f:close() end
tm.physics.SpawnCustomObjectRigidbody=function(p,mesh,texture,kinematic,mass,material)
    assert(T.textures[texture], 'Unknown texture '..texture)
    assert(mesh=='cube' and kinematic and mass==1 and material=='Stone')
    local o={alive=true,texture=texture,transform=transform()}
    o.Exists=function() return o.alive end
    o.Despawn=function() o.alive=false end
    o.GetTransform=function() return o.transform end
    o.SetTexture=function(tex) assert(T.textures[tex]); o.texture=tex end
    o.SetIsTrigger=function(v) o.trigger=v end
    o.SetIsStatic=function(v) o.static=v end
    o.SetIsVisible=function(v) o.visible=v end
    T.spawns[#T.spawns+1]=o; return o
end
tm.physics.RaycastData=function(origin,direction,distance,ignore)
    local distance=T.ray(origin,direction,distance)
    return {DidHit=function() return distance~=nil end,GetHitDistance=function() return distance end,
        GetHitPosition=function() return vector(origin.x+direction.x*distance,origin.y+direction.y*distance,origin.z+direction.z*distance) end}
end
tm.players.GetPlayerGameObject=function(id) assert(id==0); return body end
tm.players.GetPlayerTransform=function(id) assert(id==0); return transform() end
tm.players.SetPlayerIsInvincible=function(id,v) assert(id==0); T.invincible=v end
tm.players.GetRepairEnabled=function(id) return T.repair~=false end
tm.players.SetRepairEnabled=function(id,v) T.repair=v end
tm.players.GetBuilderEnabled=function(id) return T.builder end
tm.players.SetBuilderEnabled=function(id,v) T.builder=v end
tm.players.IsPlayerInSeat=function(id) return false end
tm.players.CurrentPlayers=function() return {{playerId=0}} end
tm.players.GetPlayerStructures=function(id) return {structure} end
tm.players.OnPlayerLeft=event(); tm.players.OnPlayerDied=event()
tm.input.OnPlayerKeyDown=event(); tm.input.OnPlayerKeyUp=event()
tm.playerUI.AddUILabel=function(id,key,value) T.labels[key]=value end
tm.playerUI.SetUIValue=function(id,key,value) T.labels[key]=value end

tm.players.AddCamera=function(id,p,r,priority) assert(id==0 and not T.camera); if T.legacyCamera and priority then error('three arguments required') end; T.camera=true; T.cameraPosition=p end
tm.players.ActivateCamera=function(id,fade) assert(T.camera); T.cameraActive=true; T.cameraActivations=(T.cameraActivations or 0)+1 end
tm.players.SetCameraPosition=function(id,p) assert(T.camera); T.cameraPosition=p end
tm.players.SetCameraRotation=function(id,d) assert(T.camera); T.cameraDirection=d end
tm.players.DeactivateCamera=function(id,fade) T.cameraActive=false end
tm.players.RemoveCamera=function(id) T.camera=false end
