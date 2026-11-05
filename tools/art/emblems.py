import math, random

C = 200
def polar(r, deg, cx=C, cy=C):
    a = math.radians(deg)
    return cx + r*math.cos(a), cy + r*math.sin(a)

def mirror(s):
    return f'<g transform="translate(400,0) scale(-1,1)">{s}</g>'

def rot(s, ang, cx=C, cy=C):
    return f'<g transform="rotate({ang} {cx} {cy})">{s}</g>'

# ---------------------------------------------------------------- ring styles
RUNES = ["M0,-9 V9 M0,-3 L6,-8 M0,2 L6,-3", "M-4,9 V-9 L4,-3 V9", "M-4,-9 V9 M-4,-4 L4,0 L-4,4",
         "M-3,-9 V9 M-3,-9 L5,-3 M-3,-3 L5,3", "M-4,9 V-9 L3,-9 L5,-4 L-4,0 L4,9", "M5,-7 L-3,0 L5,7",
         "M-5,-8 L5,8 M5,-8 L-5,8", "M-4,9 V-9 L4,-4 L-4,1", "M-4,-9 V9 M4,-9 V9 M-4,-1 L4,2",
         "M0,-9 V9 M-5,-4 L5,2", "M0,-9 V9", "M-3,-9 L3,-3 L-3,3 L3,9"]

def ring_runes(col, shadow="#0a0f18", n=24):
    s = ""
    for i in range(n):
        th = i*360/n - 90
        x, y = polar(164, th)
        d = RUNES[i % len(RUNES)]
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+90:.1f})">'
              f'<path d="{d}" transform="translate(.8 1.2)" stroke="{shadow}" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity=".7"/>'
              f'<path d="{d}" stroke="{col}" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></g>')
    return s

def ring_leaves(c1, c2, vine, n=22):
    s = f'<circle cx="{C}" cy="{C}" r="164" fill="none" stroke="{vine}" stroke-width="2.4"/>'
    leaf = "M0,-12 C7,-6 7,6 0,12 C-7,6 -7,-6 0,-12 Z"
    for i in range(n):
        th = i*360/n
        for side, off in ((1, 14), (-1, 14)):
            pass
        x, y = polar(164, th)
        lean = 38 if i % 2 == 0 else -38
        col = c1 if i % 2 == 0 else c2
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+lean:.1f})">'
              f'<path d="{leaf}" fill="{col}" stroke="#0b1a10" stroke-width="1"/><path d="M0,-10 V10" stroke="#0b1a10" stroke-width=".9" opacity=".7"/></g>')
        xb, yb = polar(164, th + 180/n)
        s += f'<circle cx="{xb:.1f}" cy="{yb:.1f}" r="3.2" fill="#e8b54a" stroke="#4a2f08" stroke-width=".8"/>'
    return s

def ring_rose(gold, blue, dark, n=16):
    s = f'<circle cx="{C}" cy="{C}" r="164" fill="none" stroke="{gold}" stroke-width="1.2" opacity=".7"/>'
    for i in range(n):
        th = i*360/n - 90
        x, y = polar(164, th)
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+90:.1f})">'
              f'<path d="M-9,11 V-1 A9,9 0 0 1 9,-1 V11 Z" fill="{blue}" stroke="{gold}" stroke-width="1.6"/>'
              f'<circle cx="0" cy="-1" r="3" fill="{gold}"/><path d="M0,3 V10" stroke="{gold}" stroke-width="1.2"/></g>')
        xb, yb = polar(164, th + 180/n)
        s += f'<path d="M{xb:.1f},{yb-5:.1f} l4,5 l-4,5 l-4,-5 Z" fill="{gold}" opacity=".85"/>'
    return s

def ring_meander(gold, dark, n=30):
    s = (f'<circle cx="{C}" cy="{C}" r="174" fill="none" stroke="{gold}" stroke-width="1.4" opacity=".8"/>'
         f'<circle cx="{C}" cy="{C}" r="154" fill="none" stroke="{gold}" stroke-width="1.4" opacity=".8"/>')
    for i in range(n):
        th = i*360/n - 90
        x, y = polar(164, th)
        d = "M-15,7 V-7 H9 V3 H-5 V-1 H3"
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+90:.1f})">'
              f'<path d="{d}" transform="translate(.7 1)" stroke="{dark}" stroke-width="3.2" fill="none" opacity=".7"/>'
              f'<path d="{d}" stroke="{gold}" stroke-width="2.6" fill="none" stroke-linejoin="miter"/></g>')
    return s

def ring_rope(c1, c2, dark, n=54):
    s = ""
    for i in range(n):
        th = i*360/n
        x, y = polar(164, th)
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+90+38:.1f})"><line x1="-9" y1="0" x2="9" y2="0" stroke="{dark}" stroke-width="7" stroke-linecap="round"/>'
              f'<line x1="-8" y1="0" x2="8" y2="0" stroke="{c1}" stroke-width="5" stroke-linecap="round"/><line x1="-3" y1="-1" x2="3" y2="-1" stroke="#fff" stroke-width="1" opacity=".35"/></g>')
    for i in range(n):
        th = i*360/n + 180/n
        x, y = polar(164, th)
        s += (f'<g transform="translate({x:.1f} {y:.1f}) rotate({th+90-38:.1f})"><line x1="-9" y1="0" x2="9" y2="0" stroke="{dark}" stroke-width="7" stroke-linecap="round"/>'
              f'<line x1="-8" y1="0" x2="8" y2="0" stroke="{c2}" stroke-width="5" stroke-linecap="round"/></g>')
    return s

def ring_thorns(bone, dark, glow, n=26):
    s = f'<circle cx="{C}" cy="{C}" r="164" fill="none" stroke="{dark}" stroke-width="3"/>'
    for i in range(n):
        th = i*360/n
        long = i % 2 == 0
        a0, a1 = th - 5.5, th + 5.5
        x0, y0 = polar(178, a0); x1, y1 = polar(178, a1); xt, yt = polar(154 if long else 164, th)
        s += f'<path d="M{x0:.1f},{y0:.1f} L{xt:.1f},{yt:.1f} L{x1:.1f},{y1:.1f} Z" fill="{bone}" stroke="{dark}" stroke-width="1"/>'
        xg, yg = polar(168 if long else 171, th)
        s += f'<circle cx="{xg:.1f}" cy="{yg:.1f}" r="1.6" fill="{glow}" class="th"/>'
    return s

def ring_rays(g1, g2, n=36):
    s = ""
    for i in range(n):
        th = i*360/n
        long = i % 2 == 0
        a0, a1 = th - 4.2, th + 4.2
        x0, y0 = polar(152, a0); x1, y1 = polar(152, a1); xt, yt = polar(179 if long else 168, th)
        s += f'<path d="M{x0:.1f},{y0:.1f} L{xt:.1f},{yt:.1f} L{x1:.1f},{y1:.1f} Z" fill="{g1 if long else g2}" stroke="#7a4f10" stroke-width=".8"/>'
    return s

def ring_scales(c1, c2, edge, n=40):
    s = ""
    for i in range(n):
        th = i*360/n
        x, y = polar(170, th)
        s += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="10.5" fill="{c1}" stroke="{edge}" stroke-width="1.1"/>'
    for i in range(n):
        th = i*360/n + 180/n
        x, y = polar(158, th)
        s += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="10.5" fill="{c2}" stroke="{edge}" stroke-width="1.1"/>'
    return s

def gem(x, y, col, hi="#fff", r=8):
    return (f'<g transform="translate({x:.1f} {y:.1f}) rotate(45)"><rect x="{-r}" y="{-r}" width="{2*r}" height="{2*r}" rx="2" fill="{col}" stroke="#1a1208" stroke-width="1.4"/>'
            f'<rect x="{-r*.45}" y="{-r*.45}" width="{r*.9}" height="{r*.9}" fill="{hi}" opacity=".55"/></g>')

# ---------------------------------------------------------------- medallion frame
def medal(k, pal, ring_svg, icon_svg, css="", extra_defs="", gems=None, under=""):
    gems = gems or pal["gem"]
    defs = f'''
<linearGradient id="{k}-metal" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{pal['m1']}"/><stop offset=".3" stop-color="{pal['m2']}"/><stop offset=".55" stop-color="{pal['m3']}"/><stop offset=".8" stop-color="{pal['m2']}"/><stop offset="1" stop-color="{pal['m1']}"/></linearGradient>
<linearGradient id="{k}-metal2" x1="1" y1="1" x2="0" y2="0"><stop offset="0" stop-color="{pal['m1']}"/><stop offset=".4" stop-color="{pal['m2']}"/><stop offset=".7" stop-color="{pal['m3']}"/><stop offset="1" stop-color="{pal['m2']}"/></linearGradient>
<radialGradient id="{k}-field" cx="200" cy="178" r="150" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{pal['f1']}"/><stop offset=".6" stop-color="{pal['f2']}"/><stop offset="1" stop-color="{pal['f3']}"/></radialGradient>
<radialGradient id="{k}-vig" cx="200" cy="200" r="146" gradientUnits="userSpaceOnUse"><stop offset=".62" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".46"/></radialGradient>
<radialGradient id="{k}-ringfill" cx="200" cy="200" r="180" gradientUnits="userSpaceOnUse"><stop offset=".78" stop-color="{pal['ring2']}"/><stop offset="1" stop-color="{pal['ring1']}"/></radialGradient>
<filter id="{k}-bl" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="5"/></filter>
<filter id="{k}-bl2" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2"/></filter>
<filter id="{k}-grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="4"/><feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 .95  0 0 0 0 .85  0 0 0 .7 -.28"/></filter>
<clipPath id="{k}-clip"><circle cx="200" cy="200" r="144"/></clipPath>
{extra_defs}'''
    g = []
    a = g.append
    a(f'<circle cx="200" cy="208" r="186" fill="#000" opacity=".4" filter="url(#{k}-bl)"/>')
    a(f'<circle cx="200" cy="200" r="188" fill="url(#{k}-metal)"/>')
    a(f'<circle cx="200" cy="200" r="188" fill="none" stroke="{pal["edge"]}" stroke-width="1.6"/>')
    a(f'<circle cx="200" cy="200" r="183" fill="none" stroke="{pal["m1"]}" stroke-width="1.4" opacity=".7"/>')
    a(f'<circle cx="200" cy="200" r="179" fill="url(#{k}-ringfill)" stroke="{pal["edge"]}" stroke-width="1.2"/>')
    a(ring_svg)
    a(f'<circle cx="200" cy="200" r="150" fill="url(#{k}-metal2)" stroke="{pal["edge"]}" stroke-width="1.4"/>')
    a(f'<circle cx="200" cy="200" r="145" fill="{pal["edge"]}"/>')
    a(f'<circle cx="200" cy="200" r="144" fill="url(#{k}-field)"/>')
    a(f'<g clip-path="url(#{k}-clip)">{under}{icon_svg}</g>')
    a(f'<circle cx="200" cy="200" r="144" fill="url(#{k}-vig)"/>')
    a(f'<circle cx="200" cy="200" r="144" fill="none" stroke="#000" stroke-opacity=".55" stroke-width="3"/>')
    a(f'<path d="M92,110 A126,126 0 0 1 228,76" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" opacity=".10"/>')
    for ang in (-90, 0, 90, 180):
        x, y = polar(164, ang)
        a(gem(x, y, gems, pal.get("gemhi", "#fff"), 7))
    a(f'<circle cx="200" cy="200" r="188" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="1" stroke-dasharray="2 9"/>')
    a(f'<circle cx="200" cy="200" r="188" filter="url(#{k}-grain)" opacity=".35" style="mix-blend-mode:soft-light"/>')
    reduce = "@media (prefers-reduced-motion:reduce){*{animation:none!important}}"
    return defs, "".join(g), css + reduce

# ================================================================ ICONS
def icon_warrior():
    k = "w"
    steel = "url(#w-steel)"
    sword = f'''
<g>
 <path d="M200,40 L211,62 V236 H189 V62 Z" fill="url(#w-blade)" stroke="#1a2230" stroke-width="1.6" stroke-linejoin="round"/>
 <path d="M200,52 V232" stroke="#1a2230" stroke-width="2" opacity=".55"/>
 <path d="M200,46 L206,64 V230 H200 Z" fill="#fff" opacity=".18"/>
 <rect x="162" y="236" width="76" height="11" rx="3" fill="url(#w-gold)" stroke="#2a1a08" stroke-width="1.4"/>
 <circle cx="162" cy="241.5" r="5" fill="url(#w-gold)" stroke="#2a1a08" stroke-width="1.2"/><circle cx="238" cy="241.5" r="5" fill="url(#w-gold)" stroke="#2a1a08" stroke-width="1.2"/>
 <rect x="194" y="247" width="12" height="40" fill="#5a2a1a" stroke="#1d0d08" stroke-width="1.2"/>
 <path d="M194,254 H206 M194,262 H206 M194,270 H206 M194,278 H206" stroke="#caa15a" stroke-width="1.6" opacity=".7"/>
 <circle cx="200" cy="294" r="9" fill="url(#w-gold)" stroke="#2a1a08" stroke-width="1.4"/>
</g>'''
    axe = f'''
<g>
 <rect x="195.5" y="64" width="9" height="236" rx="3" fill="#5a331c" stroke="#1d0d08" stroke-width="1.4"/>
 <path d="M195.5,100 H204.5 M195.5,110 H204.5" stroke="#8a5a30" stroke-width="1.3"/>
 <path d="M204,74 C250,56 270,104 252,142 C242,124 226,118 204,120 Z" fill="url(#w-blade)" stroke="#1a2230" stroke-width="1.6" stroke-linejoin="round"/>
 <path d="M208,84 C238,76 252,104 244,128" fill="none" stroke="#fff" stroke-width="2" opacity=".35"/>
 <path d="M196,88 L172,100 L196,114 Z" fill="url(#w-blade)" stroke="#1a2230" stroke-width="1.4" stroke-linejoin="round"/>
 <path d="M218,92 L236,96 M222,104 L238,108" stroke="#1a2230" stroke-width="1.4" opacity=".6"/>
 <path d="M195.5,292 L200,312 L204.5,292 Z" fill="url(#w-blade)" stroke="#1a2230" stroke-width="1.2"/>
</g>'''
    helm = '''
<g>
 <!-- horns -->
 <path d="M148,176 C108,176 92,142 104,100 C112,126 130,142 156,152 Z" fill="url(#w-bone)" stroke="#2a2216" stroke-width="1.6" stroke-linejoin="round"/>
 <path d="M252,176 C292,176 308,142 296,100 C288,126 270,142 244,152 Z" fill="url(#w-bone)" stroke="#2a2216" stroke-width="1.6" stroke-linejoin="round"/>
 <path d="M112,128 L124,124 M122,146 L136,140" stroke="#2a2216" stroke-width="1.3" opacity=".6"/><path d="M288,128 L276,124 M278,146 L264,140" stroke="#2a2216" stroke-width="1.3" opacity=".6"/>
 <!-- cheek guards -->
 <path d="M142,208 L146,266 L170,282 L180,232 Z" fill="url(#w-steel)" stroke="#121a26" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M258,208 L254,266 L230,282 L220,232 Z" fill="url(#w-steel)" stroke="#121a26" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M150,226 L168,240 M152,244 L170,256" stroke="#121a26" stroke-width="1.4" opacity=".55"/><path d="M250,226 L232,240 M248,244 L230,256" stroke="#121a26" stroke-width="1.4" opacity=".55"/>
 <!-- dome -->
 <path d="M138,216 C134,150 160,106 200,98 C240,106 266,150 262,216 Z" fill="url(#w-steel)" stroke="#121a26" stroke-width="2" stroke-linejoin="round"/>
 <path d="M200,98 C186,130 184,176 188,216 M200,98 C214,130 216,176 212,216" fill="none" stroke="#121a26" stroke-width="1.6" opacity=".55"/>
 <path d="M200,98 C168,128 158,170 160,214 M200,98 C232,128 242,170 240,214" fill="none" stroke="#121a26" stroke-width="1.2" opacity=".35"/>
 <path d="M168,128 C180,112 192,106 200,104" fill="none" stroke="#fff" stroke-width="3" opacity=".35" stroke-linecap="round"/>
 <!-- brow band -->
 <path d="M136,196 C170,208 230,208 264,196 L264,216 C230,228 170,228 136,216 Z" fill="url(#w-gold)" stroke="#2a1a08" stroke-width="1.8" stroke-linejoin="round"/>
 <g fill="#2a1a08"><circle cx="152" cy="208" r="2.2"/><circle cx="176" cy="214" r="2.2"/><circle cx="224" cy="214" r="2.2"/><circle cx="248" cy="208" r="2.2"/></g>
 <!-- nose guard -->
 <path d="M192,214 H208 L206,274 Q200,282 194,274 Z" fill="url(#w-steel)" stroke="#121a26" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M200,218 V272" stroke="#fff" stroke-width="1.6" opacity=".3"/>
 <!-- eyes -->
 <path d="M168,230 L188,236 L186,246 L170,242 Z" fill="#0a0d14"/><path d="M232,230 L212,236 L214,246 L230,242 Z" fill="#0a0d14"/>
 <path d="M174,236 L186,240 L184,244 L175,241 Z" fill="#ff7a3a" class="w-eye"/><path d="M226,236 L214,240 L216,244 L225,241 Z" fill="#ff7a3a" class="w-eye"/>
</g>'''
    glow = '<circle cx="200" cy="190" r="118" fill="url(#w-aura)"/>'
    snow = "".join(f'<circle class="w-sn" cx="{x}" cy="{y}" r="{r}" fill="#dfeaf5" style="animation-delay:{d}s"/>' for x, y, r, d in
                   [(70, 90, 1.8, 0), (330, 120, 1.5, -1.5), (94, 300, 2, -3), (312, 290, 1.7, -4.5), (60, 200, 1.4, -2), (344, 220, 1.6, -5.5)])
    icon = f'{glow}{rot(sword, 45)}{rot(axe, -45)}<g transform="translate(200 206) scale(.84) translate(-200 -206)">{helm}</g>{snow}'
    defs = '''
<linearGradient id="w-steel" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#b9c7d6"/><stop offset=".45" stop-color="#7d90a6"/><stop offset="1" stop-color="#3c4a5c"/></linearGradient>
<linearGradient id="w-blade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#9fb0c2"/><stop offset=".5" stop-color="#e7eef6"/><stop offset="1" stop-color="#6c7f94"/></linearGradient>
<linearGradient id="w-gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f0cf7a"/><stop offset="1" stop-color="#9a6a1c"/></linearGradient>
<linearGradient id="w-bone" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f1e8d0"/><stop offset="1" stop-color="#a99a78"/></linearGradient>
<radialGradient id="w-aura"><stop offset="0" stop-color="#ffb070" stop-opacity=".55"/><stop offset="1" stop-color="#ff8a4a" stop-opacity="0"/></radialGradient>'''
    css = '''.w-eye{animation:w-eye 3.2s ease-in-out infinite}@keyframes w-eye{0%,100%{opacity:.7}50%{opacity:1}}
.w-sn{opacity:0;animation:w-sn 7s linear infinite}@keyframes w-sn{0%{transform:translate(0,-8px);opacity:0}20%{opacity:.9}100%{transform:translate(10px,26px);opacity:0}}'''
    pal = dict(m1="#e6eef6", m2="#8fa3b8", m3="#3b4a5e", edge="#0c121c", f1="#7a96ba", f2="#3f5878", f3="#16233a",
               ring1="#16202e", ring2="#223044", gem="#e0592a", gemhi="#ffd2a0")
    return medal(k, pal, ring_runes("#c9d7e6", "#05080e"), icon, css, defs)

def icon_hunter():
    half_d = "M200,112 C192,112 184,116 178,122 L160,84 L138,138 C128,154 124,174 130,194 L112,208 L134,210 L124,232 L150,226 C156,250 172,266 188,278 L200,282"
    inner = '''<path d="M160,96 L168,124 L146,140 Z" fill="#3a2a22" opacity=".85"/>
<path d="M200,128 C190,136 182,150 180,168 M154,170 L172,176 M150,190 L170,192 M156,210 L174,206 M168,236 L182,226" fill="none" stroke="#0d130d" stroke-width="1.6" opacity=".55" stroke-linecap="round"/>
<path d="M182,252 C186,240 194,236 200,236 V282 Z" fill="#cfc4aa" opacity=".9"/>
<path d="M142,158 C150,150 160,148 168,150" fill="none" stroke="#e7e0cc" stroke-width="2.4" opacity=".5" stroke-linecap="round"/>'''
    half = f'<path d="{half_d} Z" fill="url(#h-fur)"/>{inner}<path d="{half_d}" fill="none" stroke="#0d130d" stroke-width="2.2" stroke-linejoin="round"/>'
    wolf = half + mirror(half)
    face = '''
<path d="M166,176 L190,186 L186,196 L164,188 Z" fill="#0a0d08"/><path d="M234,176 L210,186 L214,196 L236,188 Z" fill="#0a0d08"/>
<path d="M172,181 L188,188 L185,193 L170,186 Z" fill="#ffc04a" class="h-eye"/><path d="M228,181 L212,188 L215,193 L230,186 Z" fill="#ffc04a" class="h-eye"/>
<path d="M188,262 Q200,252 212,262 L206,274 Q200,279 194,274 Z" fill="#0a0d08"/><ellipse cx="200" cy="260" rx="9" ry="5" fill="#161c14"/>
<path d="M196,262 Q200,258 204,262" stroke="#fff" stroke-width="1.2" opacity=".4" fill="none"/>
<path d="M200,276 V288" stroke="#0a0d08" stroke-width="2"/><path d="M190,286 Q200,300 210,286" fill="none" stroke="#0a0d08" stroke-width="2"/>
<path d="M186,278 L190,290 L194,278 M206,278 L210,290 L214,278" fill="#f4efe2" stroke="#0a0d08" stroke-width="1"/>'''
    arrow = '''
<g>
 <rect x="197.5" y="56" width="5" height="240" rx="2" fill="#a97a46" stroke="#2a1a0a" stroke-width="1.2"/>
 <path d="M200,34 L211,64 L200,58 L189,64 Z" fill="url(#h-tip)" stroke="#1a1a1a" stroke-width="1.4" stroke-linejoin="round"/>
 <path d="M200,292 L190,314 L200,306 L210,314 Z" fill="#6fa355" stroke="#17301a" stroke-width="1.2"/><path d="M200,286 L190,304 L200,298 L210,304 Z" fill="#8bc16c" stroke="#17301a" stroke-width="1.2"/>
 <path d="M200,278 L192,294 L200,288 L208,294 Z" fill="#a8d48a" stroke="#17301a" stroke-width="1.2"/>
</g>'''
    bow = '''
<g>
 <path d="M84,252 C108,336 292,336 316,252" fill="none" stroke="#2a1a0a" stroke-width="13" stroke-linecap="round"/>
 <path d="M84,252 C108,336 292,336 316,252" fill="none" stroke="url(#h-wood)" stroke-width="9" stroke-linecap="round"/>
 <path d="M104,290 C140,330 260,330 296,290" fill="none" stroke="#fff" stroke-width="1.5" opacity=".25"/>
 <path d="M84,252 L316,252" stroke="#e8dcc0" stroke-width="1.6" opacity=".7"/>
 <path d="M92,262 q-6,10 4,18 M308,262 q6,10 -4,18" fill="none" stroke="#caa15a" stroke-width="2.4"/>
 <path d="M196,327 h8" stroke="#caa15a" stroke-width="3" stroke-linecap="round"/>
</g>'''
    leaves = ''.join(f'<path d="M0,-10 C6,-5 6,5 0,10 C-6,5 -6,-5 0,-10Z" transform="translate({x} {y}) rotate({a})" fill="{c}" stroke="#0b1a10" stroke-width="1"/>'
                     for x, y, a, c in [(102, 130, -40, "#6fa355"), (92, 152, -75, "#4f8040"), (298, 130, 40, "#6fa355"), (308, 152, 75, "#4f8040"), (110, 108, -20, "#8bc16c"), (290, 108, 20, "#8bc16c")])
    glow = '<circle cx="200" cy="186" r="112" fill="url(#h-aura)"/>'
    motes = "".join(f'<circle class="h-m" cx="{x}" cy="{y}" r="{r}" fill="#ffd978" style="animation-delay:{d}s"/>' for x, y, r, d in
                    [(70, 160, 2, 0), (330, 180, 2.2, -1.4), (104, 82, 1.6, -2.8), (300, 76, 1.8, -4.2), (60, 240, 1.6, -3), (344, 248, 1.8, -5)])
    icon = f'{glow}{rot(arrow, 40)}{rot(arrow, -40)}{bow}{wolf}{face}{leaves}{motes}'
    defs = '''
<linearGradient id="h-fur" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#b6b8ab"/><stop offset=".5" stop-color="#7d8277"/><stop offset="1" stop-color="#3f443c"/></linearGradient>
<linearGradient id="h-wood" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#8a5a2c"/><stop offset=".5" stop-color="#d09a5a"/><stop offset="1" stop-color="#7a4a22"/></linearGradient>
<linearGradient id="h-tip" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e7eef0"/><stop offset="1" stop-color="#7d8c92"/></linearGradient>
<radialGradient id="h-aura"><stop offset="0" stop-color="#ffc04a" stop-opacity=".42"/><stop offset="1" stop-color="#ffc04a" stop-opacity="0"/></radialGradient>'''
    css = '''.h-eye{animation:h-eye 4s ease-in-out infinite}@keyframes h-eye{0%,90%,100%{opacity:1}94%{opacity:.15}}
.h-m{opacity:0;animation:h-m 6s ease-in-out infinite}@keyframes h-m{0%{transform:translate(0,6px);opacity:0}30%{opacity:1}100%{transform:translate(8px,-24px);opacity:0}}'''
    pal = dict(m1="#f0d49a", m2="#c19149", m3="#5c3c12", edge="#1a1004", f1="#5a8a60", f2="#2c4a34", f3="#0e1e16",
               ring1="#10200f", ring2="#1c3418", gem="#ffb43a", gemhi="#fff0c0")
    return medal("h", pal, ring_leaves("#6fa355", "#4a7a3a", "#a67a38"), icon, css, defs)

def icon_priest():
    feather = "M0,0 C10,-14 30,-26 56,-26 C48,-12 28,-2 0,0 Z"
    def wing():
        s = ""
        for row, (n, L, base, col) in enumerate([(5, 1.0, -78, "url(#p-w1)"), (5, .84, -64, "url(#p-w2)"), (4, .66, -48, "url(#p-w3)")]):
            for i in range(n):
                ang = base + i*(150/ (n))* .62 - 10
                s += f'<path d="{feather}" transform="translate(150 196) rotate({ang+ i*0:.0f}) scale({L*1.55:.2f} {L*1.2:.2f})" fill="{col}" stroke="#7a5a1c" stroke-width=".9"/>'
        return s
    # wings: build explicit fans on the left, mirror to right
    left = ""
    for row, (n, L, a0, step, col) in enumerate([(6, 1.5, 200, 17, "url(#p-w1)"), (6, 1.22, 196, 17, "url(#p-w2)"), (5, .95, 190, 18, "url(#p-w3)")]):
        for i in range(n):
            ang = a0 + i*step
            left += f'<path d="{feather}" transform="translate(152 196) rotate({ang}) scale({L:.2f} {L*.95:.2f})" fill="{col}" stroke="#8a6a24" stroke-width=".9"/>'
    wings = f'<g class="p-wing">{left}{mirror(left)}</g>'
    rays = ""
    for i in range(32):
        th = i*360/32
        L = 140 if i % 2 == 0 else 112
        x0, y0 = polar(40, th - 2.2, 200, 190); x1, y1 = polar(40, th + 2.2, 200, 190); xt, yt = polar(L, th, 200, 190)
        rays += f'<path d="M{x0:.1f},{y0:.1f} L{xt:.1f},{yt:.1f} L{x1:.1f},{y1:.1f} Z" fill="url(#p-ray)"/>'
    cross = '''
<g>
 <path d="M188,104 H212 V148 H262 V172 H212 V300 H188 V172 H138 V148 H188 Z" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="2.2" stroke-linejoin="round"/>
 <circle cx="200" cy="96" r="13" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="2"/><circle cx="200" cy="308" r="13" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="2"/>
 <circle cx="130" cy="160" r="13" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="2"/><circle cx="270" cy="160" r="13" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="2"/>
 <circle cx="200" cy="88" r="8" fill="url(#p-gold)" stroke="#5a3d0c" stroke-width="1.6"/>
 <path d="M194,118 V292 M146,160 H254" stroke="#fff" stroke-width="2" opacity=".35"/>
 <path d="M201,120 V292 M146,166 H254" stroke="#5a3d0c" stroke-width="1" opacity=".4"/>
 <circle cx="200" cy="160" r="15" fill="#1a2f66" stroke="#5a3d0c" stroke-width="2.2"/><circle cx="200" cy="160" r="10" fill="#4f7de0"/><circle cx="196" cy="156" r="3.4" fill="#fff" opacity=".8"/>
</g>'''
    halo = '<circle cx="200" cy="160" r="66" fill="none" stroke="url(#p-gold)" stroke-width="5" opacity=".95"/><circle cx="200" cy="160" r="58" fill="url(#p-aura)"/><circle cx="200" cy="160" r="72" fill="none" stroke="#fff" stroke-width="1" opacity=".5" stroke-dasharray="3 5"/>'
    candle = lambda x: f'''
<g transform="translate({x} 286)"><rect x="-6" y="0" width="12" height="26" rx="2" fill="#f4ecd2" stroke="#6a5a30" stroke-width="1.2"/>
<path d="M0,-2 C-7,-12 -2,-18 0,-26 C2,-18 7,-12 0,-2 Z" fill="#ffd45a" class="p-fl"/><path d="M0,-4 C-3,-10 -1,-13 0,-17 C1,-13 3,-10 0,-4 Z" fill="#fff7d0" class="p-fl"/></g>'''
    sparks = "".join(f'<path class="p-sp" d="M0,-5 L1.4,-1.4 L5,0 L1.4,1.4 L0,5 L-1.4,1.4 L-5,0 L-1.4,-1.4Z" transform="translate({x} {y})" fill="#fff6d0" style="animation-delay:{d}s"/>' for x, y, d in
                     [(84, 100, 0), (318, 108, -1.2), (68, 226, -2.4), (336, 232, -3.6), (130, 66, -4.8), (270, 62, -.6)])
    icon = f'<circle cx="200" cy="190" r="140" fill="url(#p-sky)"/>{rays}{wings}{halo}{cross}{candle(104)}{candle(296)}{sparks}'
    defs = '''
<linearGradient id="p-gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff2b8"/><stop offset=".45" stop-color="#e8b94a"/><stop offset="1" stop-color="#8a5f12"/></linearGradient>
<linearGradient id="p-w1" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fffaf0"/><stop offset="1" stop-color="#e3d2a6"/></linearGradient>
<linearGradient id="p-w2" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#f6ecd2"/><stop offset="1" stop-color="#cdb784"/></linearGradient>
<linearGradient id="p-w3" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#e9dcb8"/><stop offset="1" stop-color="#b79c62"/></linearGradient>
<radialGradient id="p-ray" cx="200" cy="190" r="140" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff4c0" stop-opacity=".65"/><stop offset="1" stop-color="#fff4c0" stop-opacity="0"/></radialGradient>
<radialGradient id="p-aura"><stop offset="0" stop-color="#fff8d6" stop-opacity=".7"/><stop offset="1" stop-color="#ffe9a0" stop-opacity="0"/></radialGradient>
<radialGradient id="p-sky" cx="200" cy="170" r="150" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#6c8fe0" stop-opacity=".5"/><stop offset="1" stop-color="#6c8fe0" stop-opacity="0"/></radialGradient>'''
    css = '''.p-fl{transform-box:fill-box;transform-origin:50% 100%;animation:p-fl 1.6s ease-in-out infinite alternate}@keyframes p-fl{from{transform:scale(1,.9) rotate(-3deg)}to{transform:scale(.92,1.08) rotate(3deg)}}
.p-sp{opacity:0;animation:p-sp 4.4s ease-in-out infinite;transform-box:fill-box;transform-origin:center}@keyframes p-sp{0%,100%{opacity:0;transform:scale(.4)}50%{opacity:1;transform:scale(1.2)}}
.p-wing{transform-box:fill-box;transform-origin:center;animation:p-w 6s ease-in-out infinite alternate}@keyframes p-w{from{transform:scale(1)}to{transform:scale(1.025)}}'''
    pal = dict(m1="#fff2c0", m2="#e0b24a", m3="#7a5512", edge="#2a1c04", f1="#3b5aa8", f2="#1f3170", f3="#0a1235",
               ring1="#0e1a46", ring2="#1a2d6e", gem="#e8b94a", gemhi="#fff6d0")
    return medal("p", pal, ring_rose("#e8c46a", "#16286a", "#2a1c04"), icon, css, defs)

def icon_mage():
    stars = ""
    random.seed(11)
    pts = [(96, 100), (126, 72), (300, 98), (274, 70), (82, 190), (322, 196), (110, 250), (292, 256), (160, 60), (240, 56)]
    for i, (x, y) in enumerate(pts):
        s = random.choice([4, 5, 6])
        stars += (f'<path class="m-st" d="M0,-{s} L{s*.28:.1f},-{s*.28:.1f} L{s},0 L{s*.28:.1f},{s*.28:.1f} L0,{s} L-{s*.28:.1f},{s*.28:.1f} L-{s},0 L-{s*.28:.1f},-{s*.28:.1f} Z" transform="translate({x} {y})" fill="#dff6ff" style="animation-delay:{-i*.7:.1f}s"/>')
    const = '<path d="M96,100 L126,72 L160,60 L240,56 L274,70 L300,98" fill="none" stroke="#b8a4ff" stroke-width="1" opacity=".5" stroke-dasharray="2 4"/>'
    orb = '''
<g>
 <circle cx="200" cy="150" r="64" fill="url(#m-aura)"/>
 <ellipse cx="200" cy="150" rx="86" ry="24" transform="rotate(-24 200 150)" fill="none" stroke="#b8a4ff" stroke-width="2" opacity=".8" class="m-orb1"/>
 <ellipse cx="200" cy="150" rx="86" ry="24" transform="rotate(24 200 150)" fill="none" stroke="#7fe6ff" stroke-width="1.6" opacity=".7" class="m-orb2"/>
 <circle cx="200" cy="150" r="42" fill="url(#m-orb)" stroke="#2a1a5a" stroke-width="2"/>
 <path d="M168,150 C176,128 200,124 214,134 C196,134 186,146 192,162 C180,170 170,164 168,150 Z" fill="#fff" opacity=".35"/>
 <path d="M184,170 C206,176 226,164 230,146" fill="none" stroke="#7fe6ff" stroke-width="2" opacity=".7"/>
 <circle cx="186" cy="136" r="7" fill="#fff" opacity=".65"/>
 <path d="M158,172 C148,150 156,124 176,112" fill="none" stroke="url(#m-gold)" stroke-width="6" stroke-linecap="round"/>
 <path d="M242,172 C252,150 244,124 224,112" fill="none" stroke="url(#m-gold)" stroke-width="6" stroke-linecap="round"/>
 <path d="M178,194 C190,206 210,206 222,194" fill="none" stroke="url(#m-gold)" stroke-width="7" stroke-linecap="round"/>
 <path d="M190,106 C196,98 204,98 210,106" fill="none" stroke="url(#m-gold)" stroke-width="6" stroke-linecap="round"/>
 <path d="M200,98 L206,108 L200,114 L194,108 Z" fill="#7fe6ff" stroke="#1a3a5a" stroke-width="1.2"/>
</g>'''
    shaft = '''
<g>
 <path d="M193,204 L207,204 L205,338 L195,338 Z" fill="url(#m-wood)" stroke="#1a1030" stroke-width="1.8"/>
 <path d="M193,222 L207,230 M193,246 L207,254 M193,270 L207,278 M193,294 L207,302 M193,318 L207,326" stroke="url(#m-gold)" stroke-width="3" stroke-linecap="round"/>
 <rect x="186" y="208" width="28" height="9" rx="3" fill="url(#m-gold)" stroke="#4a2f08" stroke-width="1.4"/>
 <rect x="188" y="326" width="24" height="8" rx="3" fill="url(#m-gold)" stroke="#4a2f08" stroke-width="1.4"/>
 <path d="M196,214 V330" stroke="#fff" stroke-width="1.4" opacity=".25"/>
</g>'''
    crescent = '<path d="M100,256 A104,104 0 0 0 300,256 A150,150 0 0 1 100,256 Z" fill="url(#m-gold)" stroke="#4a2f08" stroke-width="2"/><path d="M112,262 A96,96 0 0 0 288,262" fill="none" stroke="#fff" stroke-width="1.8" opacity=".4"/>'
    icon = f'<circle cx="200" cy="170" r="140" fill="url(#m-sky)"/>{const}{stars}{crescent}{shaft}{orb}'
    defs = '''
<linearGradient id="m-gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff0b0"/><stop offset=".5" stop-color="#d9a940"/><stop offset="1" stop-color="#7a5212"/></linearGradient>
<linearGradient id="m-wood" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5a3a8a"/><stop offset=".5" stop-color="#8a62c8"/><stop offset="1" stop-color="#3a2466"/></linearGradient>
<radialGradient id="m-orb" cx="190" cy="140" r="52" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ffffff"/><stop offset=".25" stop-color="#9ff0ff"/><stop offset=".6" stop-color="#7a5af0"/><stop offset="1" stop-color="#2a1a78"/></radialGradient>
<radialGradient id="m-aura"><stop offset="0" stop-color="#9ff0ff" stop-opacity=".55"/><stop offset="1" stop-color="#7a5af0" stop-opacity="0"/></radialGradient>
<radialGradient id="m-sky" cx="200" cy="150" r="150" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#8a62ff" stop-opacity=".45"/><stop offset="1" stop-color="#8a62ff" stop-opacity="0"/></radialGradient>'''
    css = '''.m-st{animation:m-st 3.4s ease-in-out infinite;transform-box:fill-box;transform-origin:center}@keyframes m-st{0%,100%{opacity:.3;transform:scale(.7)}50%{opacity:1;transform:scale(1.15)}}
.m-orb1{transform-box:fill-box;transform-origin:center;animation:m-o1 9s linear infinite}@keyframes m-o1{to{transform:rotate(360deg)}}
.m-orb2{transform-box:fill-box;transform-origin:center;animation:m-o1 13s linear infinite reverse}'''
    pal = dict(m1="#fff0b8", m2="#cf9f3e", m3="#6a4610", edge="#1a1030", f1="#6a4ad0", f2="#32248a", f3="#100a34",
               ring1="#120c30", ring2="#1f1658", gem="#7fe6ff", gemhi="#fff")
    return medal("m", pal, ring_meander("#e6c35a", "#0a0620"), icon, css, defs)

def icon_rogue():
    moon = '<path d="M262,84 A52,52 0 1 0 322,150 A42,42 0 1 1 262,84 Z" fill="url(#r-moon)" stroke="#2a3438" stroke-width="1.6"/><circle cx="268" cy="120" r="3" fill="#8da1a6" opacity=".6"/><circle cx="286" cy="136" r="2" fill="#8da1a6" opacity=".6"/>'
    hood = '''
<g>
 <path d="M200,66 C150,78 122,130 126,190 C128,226 140,252 156,270 L244,270 C260,252 272,226 274,190 C278,130 250,78 200,66 Z" fill="#0b1214" stroke="#050909" stroke-width="2"/>
 <path d="M200,66 C150,78 122,130 126,190 C128,226 140,252 156,270" fill="none" stroke="#3a5a60" stroke-width="2" opacity=".6"/>
 <path d="M200,66 C250,78 278,130 274,190 C272,226 260,252 244,270" fill="none" stroke="#3a5a60" stroke-width="1.4" opacity=".4"/>
 <path d="M200,66 C186,60 178,52 182,44 C190,52 196,56 200,66 Z" fill="#0b1214" stroke="#050909" stroke-width="1.6"/>
 <path d="M200,66 C214,60 222,52 218,44 C210,52 204,56 200,66 Z" fill="#0b1214" stroke="#050909" stroke-width="1.6"/>
 <path d="M200,86 C166,96 148,134 152,186 C154,216 164,238 176,252 L224,252 C236,238 246,216 248,186 C252,134 234,96 200,86 Z" fill="url(#r-face)"/>
 <path d="M160,176 L192,186 L188,198 L158,190 Z" fill="#0a0e10"/><path d="M240,176 L208,186 L212,198 L242,190 Z" fill="#0a0e10"/>
 <path d="M166,182 L190,190 L187,195 L165,187 Z" fill="#b8fff0" class="r-eye"/><path d="M234,182 L210,190 L213,195 L235,187 Z" fill="#b8fff0" class="r-eye"/>
 <path d="M150,214 C176,232 224,232 250,214 L254,262 C230,276 170,276 146,262 Z" fill="#1c3a40" stroke="#050909" stroke-width="1.8"/>
 <path d="M158,232 C180,246 220,246 242,232 M156,248 C180,262 220,262 244,248" fill="none" stroke="#0b1214" stroke-width="1.6" opacity=".7"/>
 <path d="M164,222 C180,232 200,236 222,232" fill="none" stroke="#7fb8b0" stroke-width="1.4" opacity=".4"/>
 <path d="M126,190 C112,210 108,240 120,276 L156,270 Z" fill="#0b1214" stroke="#050909" stroke-width="1.6"/><path d="M274,190 C288,210 292,240 280,276 L244,270 Z" fill="#0b1214" stroke="#050909" stroke-width="1.6"/>
</g>'''
    dagger = '''
<g>
 <path d="M200,208 L208,228 V292 H192 V228 Z" fill="url(#r-blade)" stroke="#0b1214" stroke-width="1.6" stroke-linejoin="round"/>
 <path d="M200,214 V288" stroke="#0b1214" stroke-width="1.6" opacity=".5"/>
 <rect x="178" y="292" width="44" height="8" rx="3" fill="url(#r-silver)" stroke="#0b1214" stroke-width="1.4"/>
 <rect x="195" y="300" width="10" height="26" fill="#12302a" stroke="#050909" stroke-width="1.2"/><path d="M195,306 H205 M195,312 H205 M195,318 H205" stroke="#7dffb0" stroke-width="1.2" opacity=".6"/>
 <circle cx="200" cy="332" r="6" fill="url(#r-silver)" stroke="#0b1214" stroke-width="1.2"/>
 <path d="M200,226 C196,236 204,242 200,252" fill="none" stroke="#7dffb0" stroke-width="2" opacity=".8" stroke-linecap="round"/>
 <circle cx="200" cy="262" r="2.6" fill="#7dffb0" class="r-dr"/>
</g>'''
    stars = "".join(f'<circle class="r-s" cx="{x}" cy="{y}" r="{r}" fill="#e8f6f4" style="animation-delay:{d}s"/>' for x, y, r, d in
                    [(88, 96, 1.6, 0), (110, 64, 1.2, -1), (330, 232, 1.6, -2), (74, 220, 1.4, -3), (320, 72, 1.2, -1.5), (96, 300, 1.4, -.5), (306, 300, 1.2, -2.5)])
    icon = f'<circle cx="200" cy="190" r="130" fill="url(#r-aura)"/>{moon}{stars}<g transform="translate(200 296) scale(1.42) translate(-200 -296)">{rot(dagger, 56, 200, 296)}{rot(dagger, -56, 200, 296)}</g>{hood}'
    defs = '''
<linearGradient id="r-silver" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f4fafa"/><stop offset=".5" stop-color="#9db0b4"/><stop offset="1" stop-color="#4a5c60"/></linearGradient>
<linearGradient id="r-blade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#9db0b4"/><stop offset=".5" stop-color="#f4fafa"/><stop offset="1" stop-color="#6a7c80"/></linearGradient>
<linearGradient id="r-moon" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f4fafa"/><stop offset="1" stop-color="#9db0b4"/></linearGradient>
<radialGradient id="r-face" cx="200" cy="190" r="70" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#0d1a1c"/><stop offset="1" stop-color="#020505"/></radialGradient>
<radialGradient id="r-aura"><stop offset="0" stop-color="#4fd0c0" stop-opacity=".28"/><stop offset="1" stop-color="#4fd0c0" stop-opacity="0"/></radialGradient>'''
    css = '''.r-eye{animation:r-eye 5s ease-in-out infinite}@keyframes r-eye{0%,88%,100%{opacity:1}92%{opacity:.1}}
.r-s{animation:r-s 3.6s ease-in-out infinite}@keyframes r-s{0%,100%{opacity:.2}50%{opacity:1}}
.r-dr{animation:r-dr 3s ease-in infinite}@keyframes r-dr{0%{transform:translateY(0);opacity:1}100%{transform:translateY(16px);opacity:0}}'''
    pal = dict(m1="#f4fafa", m2="#9db0b4", m3="#38464a", edge="#050a0b", f1="#2f6064", f2="#16363a", f3="#050e10",
               ring1="#0a1618", ring2="#142c30", gem="#7dffb0", gemhi="#eafff4")
    return medal("r", pal, ring_rope("#a9bcc0", "#5a6e72", "#050a0b"), icon, css, defs)

def icon_warlock():
    # grim: skull + chained tome + souls
    skull = '''
<g>
 <path d="M158,118 C128,114 106,86 112,50 C122,78 140,90 164,96 Z" fill="url(#k-bone)" stroke="#0a0806" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M242,118 C272,114 294,86 288,50 C278,78 260,90 236,96 Z" fill="url(#k-bone)" stroke="#0a0806" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M120,70 L134,72 M126,86 L142,86 M276,70 L262,72 M270,86 L254,86" stroke="#0a0806" stroke-width="1.4" opacity=".6"/>
 <path d="M114,56 C112,52 112,50 112,50" stroke="#c0392b" stroke-width="3" stroke-linecap="round"/><path d="M286,56 C288,52 288,50 288,50" stroke="#c0392b" stroke-width="3" stroke-linecap="round"/>
 <path d="M154,148 C154,108 178,90 200,90 C222,90 246,108 246,148 C246,166 240,176 232,184 L232,206 C232,214 226,218 220,218 L180,218 C174,218 168,214 168,206 L168,184 C160,176 154,166 154,148 Z" fill="url(#k-bone)" stroke="#0a0806" stroke-width="2.2" stroke-linejoin="round"/>
 <path d="M180,96 C170,108 166,120 168,134 M214,94 L208,112 L216,124 L210,140" fill="none" stroke="#0a0806" stroke-width="1.4" opacity=".55" stroke-linecap="round"/>
 <path d="M160,150 C164,132 176,118 190,112" fill="none" stroke="#fff" stroke-width="2.6" opacity=".25" stroke-linecap="round"/>
 <ellipse cx="177" cy="150" rx="16" ry="19" transform="rotate(-10 177 150)" fill="#05030a" stroke="#0a0806" stroke-width="2"/>
 <ellipse cx="223" cy="150" rx="16" ry="19" transform="rotate(10 223 150)" fill="#05030a" stroke="#0a0806" stroke-width="2"/>
 <ellipse cx="178" cy="152" rx="8" ry="10" fill="#8dff6a" class="k-eye" opacity=".95"/><ellipse cx="222" cy="152" rx="8" ry="10" fill="#8dff6a" class="k-eye" opacity=".95"/>
 <ellipse cx="178" cy="152" rx="3" ry="5" fill="#eaffd8"/><ellipse cx="222" cy="152" rx="3" ry="5" fill="#eaffd8"/>
 <path class="k-fl" d="M172,134 C164,122 174,116 172,104 C184,116 188,126 182,136 Z" fill="#8dff6a" opacity=".7"/>
 <path class="k-fl k-fl2" d="M228,134 C236,122 226,116 228,104 C216,116 212,126 218,136 Z" fill="#8dff6a" opacity=".7"/>
 <path d="M200,170 L192,188 L208,188 Z" fill="#05030a" stroke="#0a0806" stroke-width="1.2"/>
 <path d="M172,198 H228 V214 H172 Z" fill="#e9e2cc" stroke="#0a0806" stroke-width="1.6"/>
 <path d="M180,198 V214 M188,198 V214 M196,198 V214 M204,198 V214 M212,198 V214 M220,198 V214" stroke="#0a0806" stroke-width="1.4"/>
 <path d="M172,206 H228" stroke="#0a0806" stroke-width="1" opacity=".6"/>
</g>'''
    chain_link = lambda x, y, a: f'<g transform="translate({x:.1f} {y:.1f}) rotate({a})"><rect x="-7" y="-3.6" width="14" height="7.2" rx="3.6" fill="none" stroke="#6c7466" stroke-width="2.6"/><rect x="-7" y="-3.6" width="14" height="7.2" rx="3.6" fill="none" stroke="#c4cbb8" stroke-width=".9" opacity=".55"/></g>'
    def chain(x0, y0, x1, y1):
        n = 11
        s = ""
        ang = math.degrees(math.atan2(y1-y0, x1-x0))
        for i in range(n):
            t = i/(n-1)
            s += chain_link(x0+(x1-x0)*t, y0+(y1-y0)*t, ang + (90 if i % 2 else 0))
        return s
    book = f'''
<g>
 <rect x="116" y="228" width="168" height="100" rx="7" fill="url(#k-leather)" stroke="#050308" stroke-width="2.4"/>
 <rect x="116" y="228" width="22" height="100" rx="6" fill="#16101c" stroke="#050308" stroke-width="2"/>
 <path d="M124,232 V324 M130,232 V324" stroke="#3a2c44" stroke-width="1.2"/>
 <rect x="146" y="236" width="130" height="84" rx="3" fill="none" stroke="#7a3a8a" stroke-width="1.6" opacity=".7"/>
 <path d="M146,236 l12,12 M276,236 l-12,12 M146,320 l12,-12 M276,320 l-12,-12" stroke="#7a3a8a" stroke-width="1.2" opacity=".6"/>
 <g fill="#5a5f52" stroke="#050308" stroke-width="1.4"><path d="M116,228 h22 v8 h-14 v14 h-8 z"/><path d="M284,228 h-22 v8 h14 v14 h8 z"/><path d="M116,328 h22 v-8 h-14 v-14 h-8 z"/><path d="M284,328 h-22 v-8 h14 v-14 h8 z"/></g>
 {chain(126, 238, 274, 318)}{chain(274, 238, 126, 318)}
 <circle cx="206" cy="278" r="26" fill="#0d0814" stroke="#6c7466" stroke-width="3"/>
 <circle cx="206" cy="278" r="21" fill="none" stroke="#7a3a8a" stroke-width="1.2"/>
 <path d="M184,278 C194,262 218,262 228,278 C218,294 194,294 184,278 Z" fill="#e9e2cc" stroke="#050308" stroke-width="1.6"/>
 <circle cx="206" cy="278" r="8" fill="#8dff6a" class="k-eye"/><ellipse cx="206" cy="278" rx="2.2" ry="7" fill="#05030a"/>
 <path d="M206,250 V256 M206,300 V306 M178,278 H184 M228,278 H234" stroke="#7a3a8a" stroke-width="2" stroke-linecap="round"/>
</g>'''
    soul = lambda x, y, d, s: f'''<g class="k-soul" style="animation-delay:{d}s" transform="translate({x} {y}) scale({s})"><path d="M0,-22 C12,-22 16,-10 14,2 C13,12 18,22 8,30 C6,22 2,26 0,32 C-2,26 -6,22 -8,30 C-18,22 -13,12 -14,2 C-16,-10 -12,-22 0,-22 Z" fill="url(#k-soulg)" opacity=".85"/><ellipse cx="-5" cy="-6" rx="2.6" ry="3.6" fill="#05030a"/><ellipse cx="5" cy="-6" rx="2.6" ry="3.6" fill="#05030a"/><ellipse cx="0" cy="4" rx="2.4" ry="4" fill="#05030a"/></g>'''
    fog = '<ellipse cx="200" cy="330" rx="150" ry="40" fill="url(#k-fog)"/>'
    icon = f'<circle cx="200" cy="150" r="130" fill="url(#k-aura)"/>{fog}{soul(104, 214, 0, .9)}{soul(298, 206, -2.3, .8)}{soul(82, 120, -4.1, .6)}{soul(320, 112, -1.2, .6)}{book}<g transform="translate(0 7)">{skull}</g>'
    defs = '''
<linearGradient id="k-bone" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f1ecd9"/><stop offset=".55" stop-color="#bdb392"/><stop offset="1" stop-color="#6e664e"/></linearGradient>
<linearGradient id="k-leather" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3a2a44"/><stop offset=".5" stop-color="#22172c"/><stop offset="1" stop-color="#0e0914"/></linearGradient>
<radialGradient id="k-aura"><stop offset="0" stop-color="#8dff6a" stop-opacity=".30"/><stop offset=".5" stop-color="#7a3aff" stop-opacity=".16"/><stop offset="1" stop-color="#7a3aff" stop-opacity="0"/></radialGradient>
<radialGradient id="k-fog"><stop offset="0" stop-color="#8dff6a" stop-opacity=".45"/><stop offset="1" stop-color="#8dff6a" stop-opacity="0"/></radialGradient>
<linearGradient id="k-soulg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d8ffc8" stop-opacity=".95"/><stop offset="1" stop-color="#8dff6a" stop-opacity=".1"/></linearGradient>'''
    css = '''.k-eye{animation:k-eye 2.4s ease-in-out infinite alternate}@keyframes k-eye{from{opacity:.55}to{opacity:1}}
.k-fl{transform-box:fill-box;transform-origin:50% 100%;animation:k-fl 1.3s ease-in-out infinite alternate}.k-fl2{animation-delay:-.6s}@keyframes k-fl{from{transform:scale(1,.85) skewX(-5deg)}to{transform:scale(.9,1.15) skewX(5deg)}}
.k-soul{animation:k-soul 7s ease-in-out infinite}@keyframes k-soul{0%,100%{opacity:.25}50%{opacity:1}}
.th{animation:th 3s ease-in-out infinite}@keyframes th{0%,100%{opacity:.2}50%{opacity:1}}'''
    pal = dict(m1="#b8bfa6", m2="#59604f", m3="#1b1f17", edge="#050604", f1="#32163e", f2="#170a22", f3="#050208",
               ring1="#080a07", ring2="#12170f", gem="#8dff6a", gemhi="#eaffd8")
    return medal("k", pal, ring_thorns("#a9ad98", "#050604", "#8dff6a"), icon, css, defs)

def icon_dawn():
    sunrays = ""
    for i in range(28):
        th = 180 + i*180/27
        L = 150 if i % 2 == 0 else 122
        x0, y0 = polar(80, th - 2.4, 200, 262); x1, y1 = polar(80, th + 2.4, 200, 262); xt, yt = polar(L, th, 200, 262)
        sunrays += f'<path d="M{x0:.1f},{y0:.1f} L{xt:.1f},{yt:.1f} L{x1:.1f},{y1:.1f} Z" fill="url(#d-ray)"/>'
    sun = '<circle cx="200" cy="262" r="112" fill="url(#d-halo)"/><circle cx="200" cy="262" r="84" fill="url(#d-sun)"/><circle cx="200" cy="262" r="84" fill="none" stroke="#fff6d0" stroke-width="2.4" opacity=".8"/>'
    crown = '''
<g>
 <path d="M114,262 L104,170 L152,206 L172,140 L200,196 L228,140 L248,206 L296,170 L286,262 Z" fill="url(#d-gold)" stroke="#6a4308" stroke-width="2.4" stroke-linejoin="round"/>
 <path d="M114,262 H286 V286 C250,296 150,296 114,286 Z" fill="url(#d-gold)" stroke="#6a4308" stroke-width="2.4" stroke-linejoin="round"/>
 <path d="M122,274 C160,286 240,286 278,274" fill="none" stroke="#fff" stroke-width="1.8" opacity=".5"/>
 <path d="M122,262 C160,270 240,270 278,262" fill="none" stroke="#6a4308" stroke-width="1.4" opacity=".6"/>
 <path d="M152,206 L200,196 L248,206 M104,170 L114,262 M296,170 L286,262 M172,140 L180,262 M228,140 L220,262" fill="none" stroke="#6a4308" stroke-width="1.2" opacity=".45"/>
 <g fill="#fff8e0" stroke="#8a7a50" stroke-width="1.2"><circle cx="104" cy="164" r="8"/><circle cx="172" cy="134" r="8"/><circle cx="228" cy="134" r="8"/><circle cx="296" cy="164" r="8"/><circle cx="200" cy="108" r="10"/></g>
 <path d="M200,112 L200,196" stroke="#6a4308" stroke-width="1.2" opacity=".4"/>
 <path d="M200,116 L208,150 L200,196 L192,150 Z" fill="url(#d-gold)" stroke="#6a4308" stroke-width="1.8" stroke-linejoin="round"/>
 <path d="M200,206 L212,222 L200,238 L188,222 Z" fill="#2b6af0" stroke="#0d2a6a" stroke-width="1.8"/><path d="M200,210 L206,222 L200,230 L194,222 Z" fill="#9cc4ff"/>
 <circle cx="146" cy="276" r="7" fill="#e84a5a" stroke="#6a1a22" stroke-width="1.4"/><circle cx="254" cy="276" r="7" fill="#e84a5a" stroke="#6a1a22" stroke-width="1.4"/>
 <circle cx="200" cy="276" r="9" fill="#fff8e0" stroke="#8a7a50" stroke-width="1.4"/><circle cx="173" cy="279" r="5" fill="#2bc4a0" stroke="#0d4a3a" stroke-width="1.2"/><circle cx="227" cy="279" r="5" fill="#2bc4a0" stroke="#0d4a3a" stroke-width="1.2"/>
</g>'''
    rods = "".join(f'<path class="d-rod" d="M0,-6 L1.6,-1.6 L6,0 L1.6,1.6 L0,6 L-1.6,1.6 L-6,0 L-1.6,-1.6 Z" transform="translate({x} {y}) scale({s})" fill="#ffffff" style="animation-delay:{d}s"/>' for x, y, s, d in
                   [(76, 120, 1.2, 0), (324, 112, 1.4, -1.1), (92, 70, .9, -2.2), (306, 66, 1, -3.3), (60, 200, .9, -4.4), (340, 204, 1, -.7), (140, 84, .8, -1.6), (262, 80, .8, -2.7)])
    icon = f'<rect x="0" y="0" width="400" height="400" fill="url(#d-sky)"/>{sunrays}{sun}{crown}{rods}'
    defs = '''
<linearGradient id="d-gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff6c8"/><stop offset=".4" stop-color="#f0c458"/><stop offset=".75" stop-color="#c8902a"/><stop offset="1" stop-color="#8a5a12"/></linearGradient>
<radialGradient id="d-sun" cx="200" cy="262" r="84" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fffbe6"/><stop offset=".6" stop-color="#ffe08a"/><stop offset="1" stop-color="#f4b13a"/></radialGradient>
<radialGradient id="d-ray" cx="200" cy="262" r="150" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff6c8" stop-opacity=".9"/><stop offset="1" stop-color="#fff6c8" stop-opacity="0"/></radialGradient>
<radialGradient id="d-halo"><stop offset="0" stop-color="#fff6c8" stop-opacity=".8"/><stop offset="1" stop-color="#fff6c8" stop-opacity="0"/></radialGradient>
<linearGradient id="d-sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a2f7e"/><stop offset=".38" stop-color="#7a4a92"/><stop offset=".68" stop-color="#f08a5a"/><stop offset="1" stop-color="#ffd27a"/></linearGradient>'''
    css = '''.d-rod{animation:d-rod 3.6s ease-in-out infinite;transform-box:fill-box;transform-origin:center}@keyframes d-rod{0%,100%{opacity:.15;transform:scale(.5) rotate(0)}50%{opacity:1;transform:scale(1.2) rotate(45deg)}}'''
    pal = dict(m1="#fff6c8", m2="#e6b44c", m3="#7c5410", edge="#2a1c04", f1="#ffd27a", f2="#f08a5a", f3="#2a2f7e",
               ring1="#6a4410", ring2="#9a6a1a", gem="#fff6d0", gemhi="#fff")
    return medal("d", pal, ring_rays("#fff6c8", "#f0c458"), icon, css, defs)

def icon_valradis():
    wing = '''
<g>
 <path d="M168,214 L56,104 Q72,148 50,172 Q74,184 62,226 Q96,214 118,258 Q142,236 172,252 Z" fill="url(#v-mem)" stroke="#1a0408" stroke-width="2" stroke-linejoin="round"/>
 <path d="M168,214 L56,104 M168,214 L50,172 M168,214 L62,226 M168,214 L118,258" stroke="#3a0a10" stroke-width="3.2" stroke-linecap="round"/>
 <path d="M168,214 L56,104 M168,214 L50,172 M168,214 L62,226 M168,214 L118,258" stroke="#c0402a" stroke-width="1" opacity=".6" stroke-linecap="round"/>
 <path d="M60,108 l-8,-8 l14,2 Z" fill="#1a0408"/>
</g>'''
    crown = '''
<g>
 <path d="M126,262 L112,150 C122,162 132,168 142,170 L156,116 C164,138 176,150 188,154 L200,92 L212,154 C224,150 236,138 244,116 L258,170 C268,168 278,162 288,150 L274,262 Z" fill="url(#v-iron)" stroke="#0a0204" stroke-width="2.4" stroke-linejoin="round"/>
 <path d="M126,262 H274 V288 C240,298 160,298 126,288 Z" fill="url(#v-iron)" stroke="#0a0204" stroke-width="2.4" stroke-linejoin="round"/>
 <path d="M134,276 C170,288 230,288 266,276" fill="none" stroke="#c0402a" stroke-width="2" opacity=".8"/>
 <path d="M200,98 L206,150 L200,200 L194,150 Z" fill="#7a1218" stroke="#0a0204" stroke-width="1.4"/>
 <path d="M156,124 L162,168 M244,124 L238,168 M118,158 L126,230 M282,158 L274,230" stroke="#c0402a" stroke-width="1.6" opacity=".7"/>
 <path d="M200,210 L218,232 L200,254 L182,232 Z" fill="#d6261f" stroke="#0a0204" stroke-width="2"/><path d="M200,216 L210,232 L200,246 L190,232 Z" fill="#ff7a5a"/>
 <g fill="#ff6a3c" stroke="#0a0204" stroke-width="1.2"><circle cx="150" cy="276" r="6"/><circle cx="250" cy="276" r="6"/></g>
 <path d="M168,276 L176,270 L184,276 L176,282 Z M216,276 L224,270 L232,276 L224,282 Z" fill="#c0402a" stroke="#0a0204" stroke-width="1"/>
 <path d="M112,150 L110,140 M156,116 L154,104 M200,92 L200,80 M244,116 L246,104 M288,150 L290,140" stroke="#0a0204" stroke-width="3" stroke-linecap="round"/>
</g>'''
    flame = '''
<g class="v-flame" transform="translate(200 98)">
 <path d="M0,-34 C-14,-18 -12,-6 -6,2 C-14,0 -18,-8 -14,-18 C-24,-4 -20,14 0,18 C20,14 24,-4 14,-18 C18,-8 14,0 6,2 C12,-6 14,-18 0,-34 Z" fill="url(#v-soul)"/>
 <path d="M0,-18 C-6,-8 -4,0 0,4 C4,0 6,-8 0,-18 Z" fill="#eafcff"/>
</g>'''
    ash = "".join(f'<circle class="v-ash" cx="{x}" cy="{y}" r="{r}" fill="#ff8a4a" style="animation-delay:{d}s"/>' for x, y, r, d in
                  [(84, 300, 2, 0), (316, 296, 2.2, -1.3), (110, 330, 1.6, -2.6), (290, 332, 1.8, -3.9), (62, 250, 1.6, -5), (340, 254, 1.8, -.8), (146, 320, 1.4, -4.4), (258, 322, 1.4, -2)])
    glow = '<circle cx="200" cy="190" r="130" fill="url(#v-aura)"/>'
    icon = f'{glow}{wing}{mirror(wing)}<g transform="translate(0 16)">{crown}</g>{flame}{ash}'
    defs = '''
<linearGradient id="v-iron" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6a6068"/><stop offset=".4" stop-color="#2e2830"/><stop offset="1" stop-color="#0e0a10"/></linearGradient>
<linearGradient id="v-mem" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#d0343a"/><stop offset=".6" stop-color="#8a1620"/><stop offset="1" stop-color="#3a0710"/></linearGradient>
<radialGradient id="v-aura"><stop offset="0" stop-color="#ff4a2a" stop-opacity=".5"/><stop offset="1" stop-color="#ff4a2a" stop-opacity="0"/></radialGradient>
<linearGradient id="v-soul" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#1a8fb0"/><stop offset=".6" stop-color="#4fe0ff"/><stop offset="1" stop-color="#d8fbff"/></linearGradient>'''
    css = '''.v-flame{transform-box:fill-box;transform-origin:50% 100%;animation:v-fl 1.5s ease-in-out infinite alternate}@keyframes v-fl{from{transform:translate(200px,98px) scale(1,.9)}to{transform:translate(200px,98px) scale(.9,1.1)}}
.v-ash{opacity:0;animation:v-ash 6s ease-out infinite}@keyframes v-ash{0%{transform:translate(0,8px);opacity:0}25%{opacity:1}100%{transform:translate(-10px,-60px);opacity:0}}'''
    pal = dict(m1="#c9c0c8", m2="#5e5460", m3="#1a1218", edge="#060204", f1="#8a1a20", f2="#4a0a10", f3="#150206",
               ring1="#14040a", ring2="#2a080e", gem="#d6261f", gemhi="#ffb0a0")
    return medal("v", pal, ring_scales("#5a0f16", "#8a1a22", "#ff6a3c"), icon, css, defs)

ALL = {
    "warrior": ("Воин", icon_warrior), "hunter": ("Охотник", icon_hunter), "priest": ("Жрец", icon_priest),
    "mage": ("Маг", icon_mage), "rogue": ("Разбойник", icon_rogue), "warlock": ("Чернокнижник", icon_warlock),
    "dawn": ("Рассвет", icon_dawn), "valradis": ("Вальрадис", icon_valradis),
}

def standalone(key, size=400):
    name, fn = ALL[key]
    defs, body, css = fn()
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 400 400" role="img" aria-label="{name}">'
            f'<title>{name}</title><defs><style>{css}</style>{defs}</defs>{body}</svg>')

def group(key, cx, cy, size):
    """For embedding in other SVGs: returns (defs, css, body-with-transform)."""
    name, fn = ALL[key]
    defs, body, css = fn()
    s = size/400
    return defs, css, f'<g transform="translate({cx-size/2:.1f} {cy-size/2:.1f}) scale({s:.4f})">{body}</g>'
