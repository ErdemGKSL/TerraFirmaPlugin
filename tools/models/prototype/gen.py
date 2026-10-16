import json, uuid, base64, zlib, struct, sys
def U(): return str(uuid.uuid4())
def png(w,h,px):  # px: function(x,y)->(r,g,b,a)
    raw=b''.join(b'\x00'+b''.join(bytes(px(x,y)) for x in range(w)) for y in range(h))
    def ch(t,d): c=struct.pack('>I',len(d))+t+d; return c+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+ch(b'IDAT',zlib.compress(raw))+ch(b'IEND',b'')
tex=png(64,32,lambda x,y:(120+(x*7)%40,80+(y*5)%30,50,255))
elements=[];groups=[];outl=[]
def cube(name,frm,to,uv,inflate=0):
    e={"name":name,"box_uv":True,"rescale":False,"locked":False,"render_order":"default","allow_mirror_modeling":True,
       "from":frm,"to":to,"autouv":0,"color":0,"origin":[(frm[i]+to[i])/2 for i in range(3)],"uv_offset":uv,
       "faces":{f:{"uv":[0,0,0,0],"texture":0} for f in ["north","east","south","west","up","down"]},
       "type":"cube","uuid":U()}
    elements.append(e); return e["uuid"]
def group(name,origin,children,rot=(0,0,0)):
    g={"name":name,"origin":list(origin),"color":0,"rotation":list(rot),"uuid":U(),"export":True,"mirror_uv":False,"isOpen":False,"locked":False,"visibility":True,"autouv":0}
    groups.append(g); return {"uuid":g["uuid"],"isOpen":False,"children":children}
body=cube("body",[-6,10,-8],[6,20,10],[18,4])
head=cube("head",[-4,16,-14],[4,24,-8],[0,0])
legs=[]
for i,(x,z) in enumerate([(-5,-7),(1,-7),(-5,5),(1,5)]):
    c=cube(f"leg{i}",[x,0,z],[x+4,12,z+4],[0,16])
    legs.append(group(["leg_fl","leg_fr","leg_bl","leg_br"][i],[x+2,12,z+2],[c]))
root=group("root",[0,0,0],[group("body",[0,15,0],[body]),group("h_head",[0,20,-8],[head])]+legs)
outl=[root]
gid={g["name"]:g["uuid"] for g in groups}
def rotkf(t,x): return {"channel":"rotation","data_points":[{"x":str(x),"y":"0","z":"0"}],"uuid":U(),"time":t,"color":-1,"interpolation":"linear"}
anim={"uuid":U(),"name":"walk","loop":"loop","override":False,"length":1.0,"snapping":20,"selected":False,"anim_time_update":"","blend_weight":"","start_delay":"","loop_delay":"",
 "animators":{gid[n]:{"name":n,"type":"bone","keyframes":[rotkf(0,30*s),rotkf(0.5,-30*s),rotkf(1.0,30*s)]} for n,s in [("leg_fl",1),("leg_fr",-1),("leg_bl",-1),("leg_br",1)]}}
anim["animators"][gid["h_head"]]={"name":"h_head","type":"bone","keyframes":[{"channel":"rotation","data_points":[{"x":"math.sin(query.anim_time*360)*5","y":"0","z":"0"}],"uuid":U(),"time":0,"color":-1,"interpolation":"linear"}]}
m={"meta":{"format_version":"5.0","model_format":"free","box_uv":True},"name":"test_cow","model_identifier":"","visible_box":[1,1,0],"variable_placeholders":"","variable_placeholder_buttons":[],"timeline_setups":[],"unhandled_root_fields":{},
 "resolution":{"width":64,"height":32},"elements":elements,"groups":groups,"outliner":outl,
 "textures":[{"path":"","name":"test_cow.png","folder":"","namespace":"","id":"0","width":64,"height":32,"uv_width":64,"uv_height":32,"particle":False,"use_as_default":False,"layers_enabled":False,"sync_to_project":"","render_mode":"default","render_sides":"auto","frame_time":1,"frame_order_type":"loop","frame_order":"","frame_interpolate":False,"visible":True,"internal":True,"saved":False,"uuid":U(),"source":"data:image/png;base64,"+base64.b64encode(tex).decode()}],
 "animations":[anim]}
json.dump(m,open(sys.argv[1],'w'),indent=1)
