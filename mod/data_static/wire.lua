local W={}
local function split(s,delimiter)
    local result={}; local start=1
    while true do
        local a,b=string.find(s,delimiter,start,true)
        if not a then result[#result+1]=string.sub(s,start); break end
        result[#result+1]=string.sub(s,start,a-1); start=b+1
    end
    return result
end
local function token(s) return s and #s>=1 and #s<=64 and not string.find(s,'[^%w_-]') end
function W.number(s)
    local n=tonumber(s)
    if not n or n~=n or math.abs(n)>30000000 then error('Invalid number') end
    return n
end
function W.decode(text)
    if type(text)~='string' or #text>4194304 or string.sub(text,-1)~='\n' or string.find(text,'\r',1,true) then error('Invalid frame') end
    local lines=split(text,'\n'); local h=split(lines[1],'\t')
    if #h~=4 or h[1]~='MMT1' or not token(h[2]) then error('Bad header') end
    local seq,count=W.number(h[3]),W.number(h[4])
    if seq<0 or seq~=math.floor(seq) or count<0 or count>8192 or count~=math.floor(count) or #lines~=count+3 then error('Bad length') end
    if lines[count+2]~='END\t'..h[2]..'\t'..h[3]..'\t'..h[4] then error('Torn frame') end
    local rows={}
    for i=2,count+1 do
        local row=split(lines[i],'\t'); if #row>32 then error('Too many fields') end
        for _,v in ipairs(row) do if #v>256 then error('Field too long') end end
        rows[#rows+1]=row
    end
    return {session=h[2],sequence=seq,records=rows}
end
function W.encode(session,seq,rows)
    if not token(session) or seq<0 or seq~=math.floor(seq) or #rows>8192 then error('Invalid header') end
    local lines={'MMT1\t'..session..'\t'..seq..'\t'..#rows}
    for _,row in ipairs(rows) do
        if #row<1 or #row>32 then error('Invalid row') end
        local values={}
        for _,v in ipairs(row) do
            v=tostring(v)
            if #v>256 or string.find(v,'[\t\r\n]') then error('Invalid field') end
            values[#values+1]=v
        end
        lines[#lines+1]=table.concat(values,'\t')
    end
    lines[#lines+1]='END\t'..session..'\t'..seq..'\t'..#rows
    local result=table.concat(lines,'\n')..'\n'
    if #result>4194304 then error('Frame too large') end
    return result
end
return W
