import random, math
W, H = 1200, 440
CX = 600
T = {}
# ---------- helpers ----------
def cubic(p0, p1, p2, p3, t):
    u = 1 - t
    return (u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
            u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1])

def make_hill(start, segs):
    """start=(x,y); segs=[(c1,c2,end),...] -> (path d without closing, sampler)"""
    d = f"M{start[0]},{start[1]}"
    pts = []
    cur = start
    for c1, c2, e in segs:
        d += f" C{c1[0]},{c1[1]} {c2[0]},{c2[1]} {e[0]},{e[1]}"
        for i in range(0, 41):
            pts.append(cubic(cur, c1, c2, e, i/40))
        cur = e
    def y_at(x):
        best = min(pts, key=lambda p: abs(p[0]-x))
        return best[1]
    return d, y_at

# ---------- hills ----------
far_d, far_y = make_hill((0, 300), [
    ((60, 288), (120, 262), (170, 268)),
    ((230, 276), (280, 300), (360, 314)),
    ((450, 330), (540, 336), (600, 354)),
    ((660, 336), (750, 330), (840, 314)),
    ((920, 300), (970, 276), (1030, 268)),
    ((1080, 262), (1140, 288), (1200, 300)),
])
mid_d, mid_y = make_hill((0, 352), [
    ((90, 328), (170, 324), (250, 338)),
    ((340, 354), (450, 352), (520, 368)),
    ((560, 376), (585, 376), (600, 382)),
    ((615, 376), (640, 376), (680, 368)),
    ((750, 352), (860, 354), (950, 338)),
    ((1030, 324), (1110, 328), (1200, 352)),
])
nearL_d, nearL_y = make_hill((0, 392), [
    ((70, 376), (150, 372), (230, 384)),
    ((330, 400), (450, 398), (548, 418)),
    ((570, 424), (580, 432), (586, 440)),
])
nearR_d, nearR_y = make_hill((1200, 392), [
    ((1130, 376), (1050, 372), (970, 384)),
    ((870, 400), (750, 398), (652, 418)),
    ((630, 424), (620, 432), (614, 440)),
])

# ---------- title glyphs ----------
T_, B_, M_ = 7, 77, 42
def g_R(w):
    L, R = 7, w-7
    return f"M{L},{B_} V{T_} H{R-9} L{R},{T_+9} V{M_-9} L{R-9},{M_} H{L} M{L+16},{M_} L{R},{B_}"
def g_A(w):
    L, R = 7, w-7; ax = w/2; yb = 55
    k = (B_-yb)/(B_-T_)
    x1 = L + k*(ax-L); x2 = R - k*(R-ax)
    return f"M{L},{B_} L{ax},{T_} L{R},{B_} M{x1:.1f},{yb} H{x2:.1f}"
def g_S(w):
    L, R = 7, w-7
    return (f"M{R},{T_+9} L{R-9},{T_} H{L+9} L{L},{T_+9} V{M_-12} L{L+9},{M_} H{R-9} "
            f"L{R},{M_+9} V{B_-9} L{R-9},{B_} H{L+9} L{L},{B_-9}")
def g_K(w):
    L, R = 7, w-7
    arm_end_y = M_+4
    slope = (arm_end_y - T_)/(R-L)
    sx = L+14; sy = arm_end_y - slope*14
    return f"M{L},{T_} V{B_} M{R},{T_} L{L},{arm_end_y} M{sx},{sy:.1f} L{R},{B_}"
def g_O(w):
    L, R = 7, w-7
    return f"M{L+10},{T_} H{R-10} L{R},{T_+10} V{B_-10} L{R-10},{B_} H{L+10} L{L},{B_-10} V{T_+10} Z"
def g_L(w):
    L, R = 7, w-7
    return f"M{L},{T_} V{B_} H{R}"
def g_C(w):
    L, R = 7, w-7
    return f"M{R},{T_+10} L{R-10},{T_} H{L+10} L{L},{T_+10} V{B_-10} L{L+10},{B_} H{R-10} L{R},{B_-10}"
def g_E(w):
    L, R = 7, w-7
    return f"M{R},{T_} H{L} V{B_} H{R} M{L},{M_} H{R-8}"
GL = dict(R=g_R, A=g_A, S=g_S, K=g_K, O=g_O, L=g_L, C=g_C, E=g_E)
WID = dict(R=58, A=64, S=54, K=58, O=60, L=50)
WID2 = dict(C=50, L=44, A=60, S=50, E=44)
gap = 14; wordgap = 56; TY = 190
def layout(word, widths, x0):
    out = []; x = x0
    for ch in word:
        w = widths[ch]
        out.append((ch, x, w)); x += w + gap
    return out, x - gap
left_w = sum(WID[c] for c in "RASKOL") + gap*5
right_w = sum(WID2[c] for c in "CLASSES") + gap*6
x_left0 = CX - wordgap/2 - left_w
x_right0 = CX + wordgap/2
Lg, _ = layout("RASKOL", WID, x_left0)
Rg, _ = layout("CLASSES", WID2, x_right0)

def letters(group, extra=""):
    s = ""
    for ch, x, w in group:
        s += f'<path d="{GL[ch](w)}" transform="translate({x:.1f},{TY})" {extra}/>'
    return s

# ---------- scene pieces ----------
def stars():
    s = ""
    for i in range(T['stars_n']):
        x = random.uniform(24, 1176); y = random.uniform(22, 205) ** 1.0
        if random.random() < .5: y = random.uniform(22, 130)
        r = random.choice([.8, 1, 1.2, 1.6])
        col = "#fff1cf" if x < CX else "#ffd6d0"
        dur = random.uniform(2.6, 6.5); dl = -random.uniform(0, 6)
        s += f'<circle class="tw" cx="{x:.0f}" cy="{y:.0f}" r="{r}" fill="{col}" style="animation-duration:{dur:.1f}s;animation-delay:{dl:.1f}s"/>'
    return s

def crown(x, y, fill, stroke, cls="crown", dly=0, sc=1.5):
    return (f'<g transform="translate({x},{y}) scale({sc})"><g class="{cls}" style="animation-delay:{dly}s">'
            f'<path d="M-12,9 L-14,-5 L-7,2 L0,-9 L7,2 L14,-5 L12,9 Z" fill="{fill}" stroke="{stroke}" stroke-width="1.2" stroke-linejoin="round"/>'
            f'<rect x="-12.5" y="9" width="25" height="4" rx="1" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>'
            f'<circle cx="-14" cy="-5" r="1.7" fill="#fff6d8"/><circle cx="0" cy="-9" r="1.9" fill="#fff6d8"/><circle cx="14" cy="-5" r="1.7" fill="#fff6d8"/>'
            f'</g></g>')

def cottage(x, w, h, roof, side, flip=False, chimney=True, cat=False, lantern=False, delay=0, ground=None, sc=1.0):
    ground = ground or mid_y
    base = ground(x + w/2) + 4
    top = base - h
    win = T["win"][side]
    halo = "url(#haloL)" if side == "L" else "url(#haloR)"
    body = T["cot_body"][side]
    s = f'<g>'
    # chimney
    cxp = x + w*(0.7 if not flip else 0.3)
    if chimney:
        s += f'<rect x="{cxp-3:.1f}" y="{top-roof*0.9:.1f}" width="6" height="{roof*0.9+2:.1f}" fill="{T["chim"]}"/>'
        for k in range(3):
            s += (f'<circle class="smoke" cx="{cxp:.1f}" cy="{top-roof*0.9-2:.1f}" r="3" fill="{T["smoke"]}" filter="url(#b2)" '
                  f'style="animation-delay:{-(k*2.4)+delay:.1f}s"/>')
    # roof + body
    s += f'<polygon points="{x-5:.1f},{top+1:.1f} {x+w/2:.1f},{top-roof:.1f} {x+w+5:.1f},{top+1:.1f}" fill="{T["roof"][side]}"/>'
    s += f'<rect x="{x:.1f}" y="{top:.1f}" width="{w}" height="{h+3}" fill="{body}"/>'
    # roof rim light
    rim = "#e7b14a" if side == "L" else "#e0553f"
    s += f'<polyline points="{x-5:.1f},{top+1:.1f} {x+w/2:.1f},{top-roof:.1f} {x+w+5:.1f},{top+1:.1f}" fill="none" stroke="{rim}" stroke-width="1" opacity="{T["rim_op"]}"/>'
    # window(s)
    ww, wh = 7*sc, 9*sc
    wx = x + w*0.22; wy = top + h*0.3
    s += f'<circle cx="{wx+ww/2:.1f}" cy="{wy+wh/2:.1f}" r="{15*sc:.0f}" fill="{halo}" class="flick" style="animation-delay:{delay:.1f}s"/>'
    s += f'<rect class="flick" style="animation-delay:{delay:.1f}s" x="{wx:.1f}" y="{wy:.1f}" width="{ww}" height="{wh}" rx="1" fill="{win}"/>'
    s += f'<path d="M{wx+ww/2:.1f},{wy} v{wh} M{wx},{wy+wh/2:.1f} h{ww}" stroke="#2a1a24" stroke-width=".8"/>'
    if w > 36:
        wx2 = x + w*0.62
        s += f'<circle cx="{wx2+ww/2:.1f}" cy="{wy+wh/2:.1f}" r="{15*sc:.0f}" fill="{halo}" class="flick" style="animation-delay:{delay+1.3:.1f}s"/>'
        s += f'<rect class="flick" style="animation-delay:{delay+1.3:.1f}s" x="{wx2:.1f}" y="{wy:.1f}" width="{ww}" height="{wh}" rx="1" fill="{win}"/>'
    # door
    dx = x + w*0.42 if w <= 36 else x + w*0.42
    s += f'<rect x="{dx:.1f}" y="{base-h*0.4:.1f}" width="{6*sc:.1f}" height="{h*0.4+2:.1f}" rx="2" fill="{T["door"]}"/>'
    if lantern:
        lx = x + w + 2
        s += f'<line x1="{lx}" y1="{top+h*0.35:.1f}" x2="{lx}" y2="{top+h*0.5:.1f}" stroke="#0d0817" stroke-width="1"/>'
        s += f'<circle cx="{lx}" cy="{top+h*0.56:.1f}" r="11" fill="{halo}" class="flick" style="animation-delay:{delay+.6:.1f}s"/>'
        s += f'<rect x="{lx-1.8}" y="{top+h*0.5:.1f}" width="3.6" height="5" rx="1" fill="{win}"/>'
    if cat:
        ry = top - roof*0.28
        rx_ = x + w*0.34
        s += (f'<g transform="translate({rx_:.1f},{ry:.1f}) scale({1.25*sc:.2f})"><g fill="#0b0712">'
              f'<ellipse cx="0" cy="-3" rx="6.5" ry="4"/><circle cx="-5.8" cy="-6.2" r="3.2"/>'
              f'<polygon points="-8.4,-8 -7.6,-12 -5.8,-9"/><polygon points="-5.6,-9 -3.2,-12 -3,-7.6"/>'
              f'<path class="tail" d="M6,-2 C11,-2 12,-8 9,-9" stroke="#0b0712" stroke-width="2" fill="none" stroke-linecap="round"/>'
              f'</g><text class="zzz" x="-2" y="-14" font-family="Georgia,serif" font-style="italic" font-size="8" fill="#ffe6b0">z</text>'
              f'<text class="zzz z2" x="3" y="-19" font-family="Georgia,serif" font-style="italic" font-size="6" fill="#ffe6b0">z</text></g>')
    s += '</g>'
    return s

def pine(x, base, h, w, col="#0f0a1a"):
    s = f'<g fill="{col}">'
    s += f'<rect x="{x-1.5:.1f}" y="{base-5:.1f}" width="3" height="7"/>'
    for i in range(4):
        yb = base - 4 - i*h*0.22
        ww = w*(1 - i*0.2)
        s += f'<polygon points="{x-ww/2:.1f},{yb:.1f} {x:.1f},{yb-h*0.38:.1f} {x+ww/2:.1f},{yb:.1f}"/>'
    s += '</g>'
    return s

def castle_dawn(cx, by):
    # gold, orderly, round-topped towers with cone roofs
    c = T["castle_L"]; rim = "#f2c25a"; sp = T["spire_L"]
    s = f'<g transform="translate({cx},{by})">'
    # base keep
    s += f'<rect x="-30" y="-58" width="60" height="62" fill="{c}"/>'
    # battlements
    for i in range(6):
        s += f'<rect x="{-30+i*10.5:.1f}" y="-64" width="6" height="7" fill="{c}"/>'
    # side towers
    s += f'<rect x="-46" y="-80" width="18" height="86" fill="{c}"/><rect x="28" y="-76" width="18" height="82" fill="{c}"/>'
    s += f'<polygon points="-49,-80 -37,-108 -25,-80" fill="{sp}"/><polygon points="25,-76 37,-103 49,-76" fill="{sp}"/>'
    # central spire
    s += f'<rect x="-9" y="-98" width="18" height="44" fill="{c}"/><polygon points="-12,-98 0,-138 12,-98" fill="{sp}"/>'
    s += f'<line x1="0" y1="-138" x2="0" y2="-160" stroke="#0f0a1a" stroke-width="1.6"/>'
    s += f'<g class="flag"><path d="M0,-160 L24,-155 L18,-150 L24,-145 L0,-146 Z" fill="{T['flag'][0]}"/></g>'
    # rim light
    s += f'<polyline points="-49,-80 -37,-108 -25,-80" fill="none" stroke="{rim}" stroke-width="1" opacity=".55"/>'
    s += f'<polyline points="-12,-98 0,-138 12,-98" fill="none" stroke="{rim}" stroke-width="1" opacity=".55"/>'
    s += f'<polyline points="25,-76 37,-103 49,-76" fill="none" stroke="{rim}" stroke-width="1" opacity=".35"/>'
    # windows
    for (wx, wy, d) in [(-41, -62, 0), (33, -58, .8), (-4, -84, 1.7), (-20, -36, .4), (12, -36, 1.2)]:
        s += f'<circle cx="{wx+2}" cy="{wy+3}" r="10" fill="url(#haloL)" class="flick" style="animation-delay:{d}s"/>'
        s += f'<rect class="flick" style="animation-delay:{d}s" x="{wx}" y="{wy}" width="4" height="7" rx="2" fill="{T["cwin"]["L"]}"/>'
    # gate
    s += f'<path d="M-7,4 V-14 A7,7 0 0 1 7,-14 V4 Z" fill="#ffcf66" opacity=".55" class="flick"/>'
    s += '</g>'
    return s

def castle_val(cx, by):
    c = T["castle_R"]; rim = "#e5483a"; sp = T["spire_R"]
    s = f'<g transform="translate({cx},{by})">'
    s += f'<rect x="-34" y="-52" width="68" height="56" fill="{c}"/>'
    # jagged battlements
    for i in range(5):
        s += f'<polygon points="{-34+i*14:.1f},-52 {-30+i*14:.1f},-62 {-26+i*14:.1f},-52" fill="{c}"/>'
    # spiky towers
    s += f'<rect x="-52" y="-92" width="16" height="96" fill="{c}"/><polygon points="-54,-92 -44,-134 -34,-92" fill="{sp}"/>'
    s += f'<rect x="34" y="-84" width="16" height="88" fill="{c}"/><polygon points="32,-84 42,-122 52,-84" fill="{sp}"/>'
    s += f'<rect x="-10" y="-112" width="20" height="62" fill="{c}"/>'
    s += f'<polygon points="-14,-112 -6,-120 -9,-146 0,-128 9,-146 6,-120 14,-112" fill="{sp}"/>'
    s += f'<polygon points="-6,-120 0,-176 6,-120" fill="{sp}"/>'
    s += f'<line x1="0" y1="-176" x2="0" y2="-196" stroke="#0f0816" stroke-width="1.6"/>'
    s += f'<g class="flag f2"><path d="M0,-196 L-24,-191 L-18,-186 L-24,-181 L0,-182 Z" fill="{T['flag'][1]}"/></g>'
    s += f'<polyline points="-54,-92 -44,-134 -34,-92" fill="none" stroke="{rim}" stroke-width="1" opacity=".5"/>'
    s += f'<polyline points="-6,-120 0,-176 6,-120" fill="none" stroke="{rim}" stroke-width="1" opacity=".55"/>'
    s += f'<polyline points="32,-84 42,-122 52,-84" fill="none" stroke="{rim}" stroke-width="1" opacity=".5"/>'
    for (wx, wy, d) in [(-47, -70, .3), (39, -64, 1.1), (-3, -100, 1.9), (-22, -30, .7), (14, -30, .1)]:
        s += f'<circle cx="{wx+2}" cy="{wy+3}" r="10" fill="url(#haloR)" class="flick" style="animation-delay:{d}s"/>'
        s += f'<rect class="flick" style="animation-delay:{d}s" x="{wx}" y="{wy}" width="4" height="7" rx="1" fill="{T["cwin"]["R"]}"/>'
    s += f'<path d="M-7,4 V-14 A7,7 0 0 1 7,-14 V4 Z" fill="#ff8a4a" opacity=".5" class="flick"/>'
    s += '</g>'
    return s

def dragon():
    return ('<g class="dragon"><g class="bob"><g transform="scale(1.35)">'
            '<path d="M-14,2 C-8,-2 4,-2 12,-5 L19,-9 L17,-4 C21,-3 23,-1 24,1 L16,0 C8,3 -4,5 -14,2 Z M-14,2 L-24,6 L-19,1 Z" fill="#0c0612"/>'
            '<path class="wing" d="M-2,-2 C-4,-14 -12,-22 -22,-24 C-16,-17 -14,-11 -12,-3 Z" fill="#12081a"/>'
            '<path class="wing w2" d="M2,-2 C6,-13 14,-19 24,-19 C17,-14 14,-9 10,-3 Z" fill="#1a0b20"/>'
            '<circle cx="19" cy="-7" r="1" fill="#ff6a3c"/></g></g></g>')

def birds():
    s = f'<g class="birds" fill="none" stroke="{T["bird"]}" stroke-width="1.4" stroke-linecap="round">'
    for (x, y, sc) in [(0, 0, 1), (18, 9, .8), (-16, 12, .7), (34, -4, .6)]:
        s += f'<path class="bwing" transform="translate({x},{y}) scale({sc})" d="M-6,-2 Q-3,-5 0,0 Q3,-5 6,-2"/>'
    s += '</g>'
    return s

def fireflies(y0=230, y1=425, extra=0):
    s = ""
    for i in range(T['ff_n']+extra):
        x = random.uniform(24, 560); y = random.uniform(y0, y1)
        r = random.uniform(2.4, 4.2)
        dx = random.uniform(-26, 26); dy = random.uniform(-40, -8)
        dur = random.uniform(7, 13); dl = -random.uniform(0, 12)
        s += f'<circle class="ff" cx="{x:.0f}" cy="{y:.0f}" r="{r:.1f}" fill="url(#spG)" style="--dx:{dx:.0f}px;--dy:{dy:.0f}px;animation-duration:{dur:.1f}s;animation-delay:{dl:.1f}s"/>'
    for i in range(T['ff_n']+extra):
        x = random.uniform(640, 1176); y = random.uniform(y0, y1)
        r = random.uniform(2.2, 4)
        dx = random.uniform(-22, 22); dy = random.uniform(-70, -24)
        dur = random.uniform(6, 11); dl = -random.uniform(0, 11)
        s += f'<circle class="ff" cx="{x:.0f}" cy="{y:.0f}" r="{r:.1f}" fill="url(#spR)" style="--dx:{dx:.0f}px;--dy:{dy:.0f}px;animation-duration:{dur:.1f}s;animation-delay:{dl:.1f}s"/>'
    return s

def clouds():
    s = ""
    for (x, y, rx, ry, col, op, dur, cls) in T["clouds"]:
        s += (f'<g class="{cls}" style="animation-duration:{dur}s"><ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{ry}" fill="{col}" opacity="{op}" filter="url(#b8)"/>'
              f'<ellipse cx="{x+rx*.35:.0f}" cy="{y-ry*.7:.0f}" rx="{rx*.55:.0f}" ry="{ry*.8:.0f}" fill="{col}" opacity="{op*.8:.2f}" filter="url(#b8)"/></g>')
    return s

# crack polyline (shared by moon halves, rift line, ribbon gap)
crack_pts = [(600, 40), (592, 70), (607, 92), (593, 116), (608, 138), (595, 160), (606, 182), (600, 200)]
def poly_left():
    pts = [(0, 0)] + [(600+ (x-600), y) for x, y in crack_pts] + [(600, 440), (0, 440)]
    return " ".join(f"{x},{y}" for x, y in [(0, 0)] + crack_pts + [(600, 440), (0, 440)])
def poly_right():
    return " ".join(f"{x},{y}" for x, y in [(W, 0)] + crack_pts + [(600, 440), (W, 440)])
rift_pts = crack_pts + [(595, 222), (606, 246), (596, 268), (604, 292), (600, 304)]
rift_d = "M" + " L".join(f"{x},{y}" for x, y in rift_pts)

# ---------- CSS ----------
CSS = """
.tw{animation:tw 4s ease-in-out infinite}
@keyframes tw{0%,100%{opacity:.18}50%{opacity:1}}
.ff{opacity:0;animation:ff 9s ease-in-out infinite}
@keyframes ff{0%{transform:translate(0,0);opacity:0}15%{opacity:1}50%{transform:translate(var(--dx),var(--dy));opacity:.5}80%{opacity:1}100%{transform:translate(calc(var(--dx)*2),calc(var(--dy)*1.6));opacity:0}}
.flick{animation:flick 4.8s ease-in-out infinite}
@keyframes flick{0%,100%{opacity:.95}20%{opacity:.7}35%{opacity:1}55%{opacity:.78}75%{opacity:1}88%{opacity:.8}}
.smoke{opacity:0;animation:smoke 7.2s ease-out infinite;transform-box:fill-box;transform-origin:center}
@keyframes smoke{0%{transform:translate(0,0) scale(.6);opacity:0}15%{opacity:.5}100%{transform:translate(-16px,-34px) scale(3.2);opacity:0}}
.flag{transform-box:fill-box;transform-origin:0% 50%;animation:flag 2.6s ease-in-out infinite alternate}
.flag.f2{transform-origin:100% 50%;animation-duration:3.1s}
@keyframes flag{from{transform:skewY(-9deg) scaleX(1)}to{transform:skewY(8deg) scaleX(.86)}}
.tail{transform-box:fill-box;transform-origin:0% 100%;animation:tail 3.4s ease-in-out infinite alternate}
@keyframes tail{from{transform:rotate(-10deg)}to{transform:rotate(14deg)}}
.zzz{opacity:0;animation:zzz 5s ease-in-out infinite}
.zzz.z2{animation-delay:1.2s}
@keyframes zzz{0%{opacity:0;transform:translate(0,2px)}30%{opacity:.9}100%{opacity:0;transform:translate(3px,-6px)}}
.crown{transform-box:fill-box;transform-origin:center;animation:crown 5.5s ease-in-out infinite alternate}
@keyframes crown{from{transform:translateY(-2.5px) rotate(-3deg)}to{transform:translateY(2.5px) rotate(3deg)}}
.rift{animation:rift 3.6s ease-in-out infinite alternate}
@keyframes rift{from{opacity:.65}to{opacity:1}}
.moonL{animation:mL 7s ease-in-out infinite alternate}
.moonR{animation:mR 7s ease-in-out infinite alternate}
@keyframes mL{from{transform:translate(-3px,0)}to{transform:translate(-6px,-1px)}}
@keyframes mR{from{transform:translate(3px,0)}to{transform:translate(6px,1px)}}
.drift{animation:drift 50s ease-in-out infinite alternate}
.driftb{animation:drift 64s ease-in-out infinite alternate-reverse}
@keyframes drift{from{transform:translateX(-34px)}to{transform:translateX(34px)}}
.glint{animation:glint 9s cubic-bezier(.45,.05,.35,1) infinite}
@keyframes glint{0%,55%{transform:translateX(0)}100%{transform:translateX(1180px)}}
.dragon{animation:fly 26s linear infinite}
@keyframes fly{0%{transform:translate(660px,150px);opacity:0}6%{opacity:.95}50%{transform:translate(900px,120px)}94%{opacity:.95}100%{transform:translate(1060px,170px);opacity:0}}
.bob{animation:bob 2.2s ease-in-out infinite alternate}
@keyframes bob{from{transform:translateY(-3px)}to{transform:translateY(3px)}}
.wing{transform-box:fill-box;transform-origin:50% 100%;animation:flap .55s ease-in-out infinite alternate}
.wing.w2{animation-delay:-.1s}
@keyframes flap{from{transform:scaleY(1) rotate(0deg)}to{transform:scaleY(.35) rotate(6deg)}}
.birds{animation:birds 40s linear infinite}
@keyframes birds{0%{transform:translate(120px,150px);opacity:0}8%{opacity:.8}92%{opacity:.8}100%{transform:translate(520px,92px);opacity:0}}
.bwing{animation:bw 1.1s ease-in-out infinite alternate;transform-box:fill-box;transform-origin:center}
@keyframes bw{from{transform:scaleY(1)}to{transform:scaleY(.4)}}
.river{stroke-dasharray:6 14;animation:river 3.2s linear infinite}
@keyframes river{to{stroke-dashoffset:-40}}
@media (prefers-reduced-motion:reduce){*{animation:none!important}.ff,.smoke,.zzz{opacity:.5}.dragon,.birds{opacity:0}}
"""



DARK = dict(
    name="dark",
    sky=[(0,"#0a0720"),(.38,"#1f1336"),(.7,"#47233f"),(1,"#8f4a38")],
    glowL=("#ffd27a",.8,"#e29a45"), glowR=("#ff6a4a",.66,"#c8302f"),
    halo=("#fff4d6",.5,"#ffd9a0"),
    sunL=("#fff8e0","#f1c76a"), sunR=("#ffe3d6","#e0624d"), craters=True,
    stars_n=96,
    far=[(0,"#5a3a4a"),(.3,"#3a2744"),(.5,"#2d2040"),(.7,"#40243f"),(1,"#6a3040")],
    mid=[(0,"#3b2a3a"),(.35,"#241a38"),(.65,"#27172f"),(1,"#47202c")],
    nearL="#150e22", nearR="#150e22",
    pineF=("#241a38","#2b1832"), pineM=("#1a1028","#1a1028"), pineN=("#0c0715","#0c0715"),
    castle_L="#1f1633", castle_R="#1c1128", spire_L="#150e26", spire_R="#140b1e",
    cwin=dict(L="#ffd978", R="#ff9d58"), flag=("#f2c25a","#d6322f"),
    cot_body=dict(L="#1d1530", R="#1d1530"), roof=dict(L="#130d1f", R="#130d1f"),
    chim="#150f22", door="#0d0817", win=dict(L="#ffcf66", R="#ff9d58"), smoke="#d9cbe3", rim_op=".45",
    haloL=(.7,"#ffd978","#ffb43a"), haloR=(.7,"#ff9d58","#e0553f"),
    clouds=[(190,232,150,13,"#f6c978",.30,60,"drift"),(360,262,180,12,"#e9a35d",.26,80,"driftb"),(840,252,170,12,"#f09283",.26,70,"drift"),(1010,226,140,12,"#d96a63",.28,90,"driftb"),(600,300,220,10,"#ffcf9a",.12,50,"drift")],
    ff_n=26, spG=("#fff6c8","#ffd86a","#ffb43a"), spR=("#ffe0a8","#ff8a4a","#e03a2a"),
    bird="#0e0818", vig=("#0a0614",.62), grain=.3,
    riverL=("#ffe9b0","#e9a443"), riverR=("#ffc9a8","#d9433a"),
    riftcore="#fff3c8", riftglow="#fff3c8", shadow_op=.75,
    rimstroke=("#f3c26a","#e5584a"),
    ground="#150e22", ground2="#0a0614",
)
LIGHT = dict(DARK,
    name="light",
    sky=[(0,"#7f9fdc"),(.34,"#b9b4e6"),(.64,"#f7c3b4"),(1,"#ffe2a6")],
    glowL=("#fff0b0",.8,"#ffd27a"), glowR=("#ff9a8a",.6,"#e85a6a"),
    halo=("#ffffff",.8,"#ffe9b8"),
    sunL=("#fffdf2","#ffd45e"), sunR=("#fff2ea","#ff7a5c"), craters=False,
    stars_n=0,
    far=[(0,"#aebb92"),(.3,"#b9bcc6"),(.5,"#c9bfd8"),(.7,"#cba9bb"),(1,"#b98698")],
    mid=[(0,"#8fa27a"),(.35,"#9aa58e"),(.65,"#a58f9c"),(1,"#9a6676")],
    nearL="#5f7a52", nearR="#7a4a5c",
    pineF=("#6f8a62","#8a6274"), pineM=("#506d48","#6a4258"), pineN=("#304a35","#43283a"),
    castle_L="#efe2cc", castle_R="#6a4a68", spire_L="#d89a3a", spire_R="#a8303a",
    cwin=dict(L="#8fc0e8", R="#ffd9a0"), flag=("#f2c25a","#d6322f"),
    cot_body=dict(L="#f3e0c0", R="#ecd0c4"), roof=dict(L="#b0553f", R="#7a4660"),
    chim="#8a6a58", door="#6a3a2a", win=dict(L="#b8dcf4", R="#ffe3b0"), smoke="#ffffff", rim_op=".12",
    haloL=(.18,"#ffd978","#ffb43a"), haloR=(.18,"#ff9d58","#e0553f"),
    clouds=[(190,226,160,14,"#ffffff",.7,60,"drift"),(380,262,190,13,"#fff0e8",.55,80,"driftb"),(840,246,180,13,"#ffe0e0",.6,70,"drift"),(1020,222,150,13,"#ffffff",.65,90,"driftb"),(600,300,240,11,"#fff4d8",.45,50,"drift"),(700,150,120,9,"#ffffff",.4,70,"driftb")],
    ff_n=16, spG=("#ffffff","#fff0a0","#ffe070"), spR=("#ffffff","#ffd0b0","#ff9a8a"),
    bird="#4a3858", vig=("#4a2a58",.3), grain=.22,
    riverL=("#fff8dc","#f6c866"), riverR=("#ffe2d4","#f08a7a"),
    riftcore="#ffffff", riftglow="#ff9a5a", shadow_op=.5,
    rimstroke=("#ffffff","#ffe0d0"),
    ground="#4a6a46", ground2="#2c4430",
)
THEMES = dict(dark=DARK, light=LIGHT)
FONT = "'Palatino Linotype','Book Antiqua',Palatino,Georgia,'DejaVu Serif',serif"

def stops(lst):
    return "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in lst)

def defs_block():
    t = T
    return f"""
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">{stops(t['sky'])}</linearGradient>
<radialGradient id="glowL" cx="170" cy="330" r="620" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{t['glowL'][0]}" stop-opacity="{t['glowL'][1]}"/><stop offset=".45" stop-color="{t['glowL'][2]}" stop-opacity=".22"/><stop offset="1" stop-color="{t['glowL'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="glowR" cx="1030" cy="330" r="620" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{t['glowR'][0]}" stop-opacity="{t['glowR'][1]}"/><stop offset=".45" stop-color="{t['glowR'][2]}" stop-opacity=".24"/><stop offset="1" stop-color="{t['glowR'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="moonHalo" cx="600" cy="112" r="190" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{t['halo'][0]}" stop-opacity="{t['halo'][1]}"/><stop offset=".4" stop-color="{t['halo'][2]}" stop-opacity=".16"/><stop offset="1" stop-color="{t['halo'][2]}" stop-opacity="0"/></radialGradient>
<linearGradient id="moonGL" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{t['sunL'][0]}"/><stop offset="1" stop-color="{t['sunL'][1]}"/></linearGradient>
<linearGradient id="moonGR" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{t['sunR'][0]}"/><stop offset="1" stop-color="{t['sunR'][1]}"/></linearGradient>
<linearGradient id="letL" gradientUnits="userSpaceOnUse" x1="0" y1="190" x2="0" y2="274"><stop offset="0" stop-color="#ffe58f"/><stop offset=".5" stop-color="#f3b535"/><stop offset="1" stop-color="#b8731a"/></linearGradient>
<linearGradient id="letR" gradientUnits="userSpaceOnUse" x1="0" y1="190" x2="0" y2="274"><stop offset="0" stop-color="#ff7a62"/><stop offset=".5" stop-color="#d42a2a"/><stop offset="1" stop-color="#820d16"/></linearGradient>
<linearGradient id="glintG" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".95"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<linearGradient id="ribL" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f8e3a2"/><stop offset="1" stop-color="#d5a74d"/></linearGradient>
<linearGradient id="ribR" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c8383a"/><stop offset="1" stop-color="#7a1218"/></linearGradient>
<linearGradient id="frameG" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#f6cf72"/><stop offset=".48" stop-color="#b98a3a"/><stop offset=".52" stop-color="#b24a3c"/><stop offset="1" stop-color="#e5584a"/></linearGradient>
<linearGradient id="riftG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t['riftcore']}"/><stop offset=".6" stop-color="#fff1c8"/><stop offset="1" stop-color="#ffd9a0"/></linearGradient>
<linearGradient id="riverL" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t['riverL'][0]}" stop-opacity=".95"/><stop offset="1" stop-color="{t['riverL'][1]}" stop-opacity=".65"/></linearGradient>
<linearGradient id="riverR" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t['riverR'][0]}" stop-opacity=".95"/><stop offset="1" stop-color="{t['riverR'][1]}" stop-opacity=".65"/></linearGradient>
<linearGradient id="farG" x1="0" y1="0" x2="1" y2="0">{stops(t['far'])}</linearGradient>
<linearGradient id="midG" x1="0" y1="0" x2="1" y2="0">{stops(t['mid'])}</linearGradient>
<linearGradient id="gndG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{t['ground']}"/><stop offset="1" stop-color="{t['ground2']}"/></linearGradient>
<radialGradient id="spG"><stop offset="0" stop-color="{t['spG'][0]}"/><stop offset=".3" stop-color="{t['spG'][1]}" stop-opacity=".85"/><stop offset="1" stop-color="{t['spG'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="spR"><stop offset="0" stop-color="{t['spR'][0]}"/><stop offset=".3" stop-color="{t['spR'][1]}" stop-opacity=".85"/><stop offset="1" stop-color="{t['spR'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="haloL"><stop offset="0" stop-color="{t['haloL'][1]}" stop-opacity="{t['haloL'][0]}"/><stop offset="1" stop-color="{t['haloL'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="haloR"><stop offset="0" stop-color="{t['haloR'][1]}" stop-opacity="{t['haloR'][0]}"/><stop offset="1" stop-color="{t['haloR'][2]}" stop-opacity="0"/></radialGradient>
<radialGradient id="vig" cx="600" cy="220" r="720" gradientUnits="userSpaceOnUse"><stop offset=".55" stop-color="{t['vig'][0]}" stop-opacity="0"/><stop offset="1" stop-color="{t['vig'][0]}" stop-opacity="{t['vig'][1]}"/></radialGradient>
<filter id="b2" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="2"/></filter>
<filter id="b4" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="4"/></filter>
<filter id="b8" x="-50%" y="-100%" width="200%" height="300%"><feGaussianBlur stdDeviation="9"/></filter>
<filter id="grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" seed="5" result="n"/><feColorMatrix in="n" type="matrix" values="0 0 0 0 1  0 0 0 0 .93  0 0 0 0 .78  0 0 0 .9 -.34"/></filter>
"""

def knot(x, y, col):
    return (f'<g transform="translate({x},{y}) rotate(45)"><rect x="-6" y="-6" width="12" height="12" fill="#1a1028" stroke="{col}" stroke-width="1.6"/>'
            f'<rect x="-2.4" y="-2.4" width="4.8" height="4.8" fill="{col}"/></g>')

def scene_layers(social=False):
    t = T
    L = []
    a = L.append
    a(f'<rect width="{W}" height="{H+ (200 if social else 0)}" fill="url(#sky)"/>')
    a(f'<rect width="{W}" height="{H}" fill="url(#glowL)"/><rect width="{W}" height="{H}" fill="url(#glowR)"/>')
    a(stars())
    a('<circle cx="600" cy="112" r="190" fill="url(#moonHalo)"/>')
    cr_l = ('<circle cx="574" cy="98" r="9" fill="#e9c27a" opacity=".45"/><circle cx="582" cy="132" r="6" fill="#e9c27a" opacity=".4"/><circle cx="560" cy="120" r="4" fill="#e9c27a" opacity=".4"/>') if t['craters'] else ''
    cr_r = ('<circle cx="624" cy="92" r="8" fill="#b83a34" opacity=".4"/><circle cx="632" cy="128" r="6" fill="#b83a34" opacity=".4"/><circle cx="618" cy="140" r="4" fill="#b83a34" opacity=".4"/>') if t['craters'] else ''
    a(f'<g class="moonL"><g clip-path="url(#cL)"><g clip-path="url(#moonClip)"><circle cx="600" cy="112" r="64" fill="url(#moonGL)"/>{cr_l}</g></g></g>')
    a(f'<g class="moonR"><g clip-path="url(#cR)"><g clip-path="url(#moonClip)"><circle cx="600" cy="112" r="64" fill="url(#moonGR)"/>{cr_r}</g></g></g>')
    a(f'<g class="rift"><path d="{rift_d}" fill="none" stroke="{t["riftglow"]}" stroke-width="9" opacity=".6" filter="url(#b4)"/>'
      f'<path d="{rift_d}" fill="none" stroke="url(#riftG)" stroke-width="2.4" stroke-linejoin="miter"/></g>')
    a(crown(488, 96, "#f3c458", "#8a5a14", dly=0))
    a(crown(712, 96, "#d6382f", "#4a0b10", dly=-2.7))
    a(clouds())
    a(birds())
    a(f'<path d="{far_d} V{H} H0 Z" fill="url(#farG)"/>')
    a(f'<path d="{far_d}" fill="none" stroke="{t["rimstroke"][0]}" stroke-width="1" opacity=".18"/>')
    a(castle_dawn(76, far_y(84)+4))
    a(castle_val(1124, far_y(1116)+4))
    for x in (210, 236, 262, 300): a(pine(x, far_y(x)+6, 34, 15, t['pineF'][0]))
    for x in (900, 936, 962, 990): a(pine(x, far_y(x)+6, 34, 15, t['pineF'][1]))
    a(dragon())
    a(f'<path d="{mid_d} V{H} H0 Z" fill="url(#midG)"/>')
    a(f'<path d="{mid_d}" fill="none" stroke="{t["rimstroke"][0]}" stroke-width="1" opacity=".22"/>')
    a(cottage(232, 22, 15, 11, "L", delay=0.4))
    a(cottage(270, 24, 16, 12, "L", flip=True, delay=1.5))
    a(cottage(944, 22, 15, 11, "R", delay=0.9))
    a(cottage(906, 24, 16, 12, "R", flip=True, delay=2.0))
    for x in (452, 478): a(pine(x, mid_y(x)+5, 30, 14, t['pineM'][0]))
    for x in (724, 750): a(pine(x, mid_y(x)+5, 30, 14, t['pineM'][1]))
    a(f'<g class="rift"><path d="M600,340 L600,{H}" stroke="{t["riftglow"]}" stroke-width="22" opacity=".28" filter="url(#b8)"/></g>')
    rv = f'M588,{mid_y(600)-4} Q600,{mid_y(600)-9} 612,{mid_y(600)-4} L640,{H} L560,{H} Z'
    a(f'<path d="{rv}" fill="url(#riverL)" clip-path="url(#cL)"/><path d="{rv}" fill="url(#riverR)" clip-path="url(#cR)"/>')
    a(f'<path class="river" d="M600,{mid_y(600)} L600,{H+(200 if social else 0)}" stroke="#fff7dc" stroke-width="2.4" opacity=".7"/>')
    a(f'<path d="{nearL_d} V{H} H0 Z" fill="{t["nearL"]}"/><path d="{nearR_d} V{H} H{W} Z" fill="{t["nearR"]}"/>')
    a(f'<path d="{nearL_d}" fill="none" stroke="{t["rimstroke"][0]}" stroke-width="1" opacity=".2"/><path d="{nearR_d}" fill="none" stroke="{t["rimstroke"][1]}" stroke-width="1" opacity=".22"/>')
    for (x, h, w) in [(34, 66, 28), (62, 52, 22), (92, 74, 30), (130, 48, 20), (170, 40, 18)]:
        a(pine(x, nearL_y(x)+8, h, w, t['pineN'][0]))
    for (x, h, w) in [(1166, 66, 28), (1138, 52, 22), (1108, 74, 30), (1070, 48, 20), (1030, 40, 18)]:
        a(pine(x, nearR_y(x)+8, h, w, t['pineN'][1]))
    a(cottage(212, 46, 30, 22, "L", cat=True, lantern=True, delay=0.0, ground=nearL_y, sc=1.35))
    a(cottage(318, 36, 24, 17, "L", flip=True, delay=1.2, ground=nearL_y, sc=1.15))
    a(cottage(942, 46, 30, 22, "R", lantern=True, flip=True, delay=0.7, ground=nearR_y, sc=1.35))
    a(cottage(846, 36, 24, 17, "R", delay=1.9, ground=nearR_y, sc=1.15))
    if social:
        a(f'<rect x="0" y="{H-3}" width="{W}" height="240" fill="url(#gndG)"/>')
        a(f'<path d="M0,{H-3} Q300,{H-9} 560,{H-3}" fill="none" stroke="{t["rimstroke"][0]}" stroke-width="1" opacity=".16"/><path d="M{W},{H-3} Q900,{H-9} 640,{H-3}" fill="none" stroke="{t["rimstroke"][1]}" stroke-width="1" opacity=".18"/>')
        a(f'<g class="rift"><path d="M600,{H-8} L600,{H+200}" stroke="{t["riftglow"]}" stroke-width="40" opacity=".3" filter="url(#b8)"/></g>')
        a(f'<path d="M562,{H-3} L600,{H-3} L600,{H+200} L536,{H+200} Z" fill="url(#riverL)"/><path d="M600,{H-3} L638,{H-3} L664,{H+200} L600,{H+200} Z" fill="url(#riverR)"/>')
        a(f'<path d="M562,{H-3} L536,{H+200} M638,{H-3} L664,{H+200}" stroke="#fff4d0" stroke-width="1.2" opacity=".35" fill="none"/>')
    a(fireflies())
    if social:
        a(fireflies(y0=450, y1=630, extra=-6))
    return "".join(L)

def title_layers():
    t = T
    L = []
    a = L.append
    a(f'<g fill="none" stroke="#12081c" stroke-width="15" stroke-linejoin="miter" opacity="{t["shadow_op"]}" transform="translate(0,6)" filter="url(#b2)">{letters(Lg)}{letters(Rg)}</g>')
    a(f'<g fill="none" stroke="#ffcf5a" stroke-width="16" opacity=".4" filter="url(#b8)">{letters(Lg)}</g>')
    a(f'<g fill="none" stroke="#ff4b3a" stroke-width="16" opacity=".26" filter="url(#b8)">{letters(Rg)}</g>')
    a(f'<g fill="none" stroke="#2a1018" stroke-width="16" stroke-linejoin="miter">{letters(Lg)}{letters(Rg)}</g>')
    a(f'<g fill="none" stroke="url(#letL)" stroke-width="12" stroke-linejoin="miter">{letters(Lg)}</g>')
    a(f'<g fill="none" stroke="url(#letR)" stroke-width="12" stroke-linejoin="miter">{letters(Rg)}</g>')
    a(f'<g fill="none" stroke="#6a3c0c" stroke-width="2" opacity=".45" stroke-linejoin="miter" transform="translate(0,1)">{letters(Lg)}</g>')
    a(f'<g fill="none" stroke="#3d0710" stroke-width="2" opacity=".5" stroke-linejoin="miter" transform="translate(0,1)">{letters(Rg)}</g>')
    a(f'<g fill="none" stroke="#fffbe6" stroke-width="1.4" opacity=".4" stroke-linejoin="miter" transform="translate(-3.2,-3.2)">{letters(Lg)}</g>')
    a(f'<g fill="none" stroke="#ffe3d8" stroke-width="1.4" opacity=".38" stroke-linejoin="miter" transform="translate(-3.2,-3.2)">{letters(Rg)}</g>')
    a(f'<g mask="url(#lm)"><rect class="glint" x="-160" y="{TY-6}" width="110" height="{84+12}" fill="url(#glintG)" transform="skewX(-18)" style="transform-box:fill-box"/></g>')
    a('<ellipse cx="600" cy="344" rx="260" ry="9" fill="#0a0614" opacity=".3" filter="url(#b4)"/>')
    a('<path d="M352,300 H598 L594,310 L599,318 L593,327 L598,338 H352 L370,319 Z" fill="url(#ribL)" stroke="#7a4b12" stroke-width="1.6" stroke-linejoin="round"/>')
    a('<path d="M848,300 H602 L606,310 L601,318 L607,327 L602,338 H848 L830,319 Z" fill="url(#ribR)" stroke="#3a070b" stroke-width="1.6" stroke-linejoin="round"/>')
    a('<path d="M364,304 H590 M364,334 H590" stroke="#8a5a1c" stroke-width=".8" opacity=".5" fill="none"/><path d="M610,304 H836 M610,334 H836" stroke="#f0a090" stroke-width=".8" opacity=".4" fill="none"/>')
    a(f'<text x="480" y="324.5" text-anchor="middle" font-family="{FONT}" font-weight="700" font-size="15" fill="#3a210f" textLength="200" lengthAdjust="spacing">БОЕВОЙ СЛОЙ СЕРВЕРА</text>')
    a(f'<text x="722" y="324.5" text-anchor="middle" font-family="{FONT}" font-weight="700" font-size="15" fill="#ffe9c4" textLength="212" lengthAdjust="spacing">«РАСКОЛ | ДВЕ КОРОНЫ»</text>')
    a(f'<text x="600" y="371" text-anchor="middle" font-family="{FONT}" font-style="italic" font-size="15" fill="#fbe3b4" stroke="#1a0f1e" stroke-width="3.2" paint-order="stroke" stroke-linejoin="round" textLength="330" lengthAdjust="spacing">шесть путей · две короны · одна война</text>')
    return "".join(L)

def frame(Wc, Hc):
    s = (f'<rect x="10" y="10" width="{Wc-20}" height="{Hc-20}" rx="18" fill="none" stroke="url(#frameG)" stroke-width="2.6"/>'
         f'<rect x="19" y="19" width="{Wc-38}" height="{Hc-38}" rx="11" fill="none" stroke="url(#frameG)" stroke-width="1" opacity=".6" stroke-dasharray="1 5" stroke-linecap="round"/>')
    for (x, y) in [(10, 10), (10, Hc-10)]: s += knot(x, y, "#f2c25a")
    for (x, y) in [(Wc-10, 10), (Wc-10, Hc-10)]: s += knot(x, y, "#e5584a")
    for (x, y) in [(Wc/2, 10), (Wc/2, Hc-10)]:
        s += f'<g transform="translate({x},{y}) rotate(45)"><rect x="-5" y="-5" width="10" height="10" fill="#1a1028" stroke="#f6dfa0" stroke-width="1.4"/></g>'
    return s

def build(theme="dark", mode="banner"):
    random.seed(7)
    T.clear(); T.update(THEMES[theme])
    label = "RASKOL CLASSES — боевой слой сервера «РАСКОЛ | ДВЕ КОРОНЫ»"
    common_defs = (f'{defs_block()}'
        f'<clipPath id="cL"><polygon points="{poly_left()}"/></clipPath><clipPath id="cR"><polygon points="{poly_right()}"/></clipPath>'
        f'<clipPath id="moonClip"><circle cx="600" cy="112" r="64"/></clipPath>'
        f'<mask id="lm"><g fill="none" stroke="#fff" stroke-width="13" stroke-linejoin="miter">{letters(Lg)}{letters(Rg)}</g></mask>')
    if mode == "banner":
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="{label}"><title>{label}</title>',
               f'<defs><style>{CSS}</style>{common_defs}<clipPath id="scene"><rect x="10" y="10" width="{W-20}" height="{H-20}" rx="18"/></clipPath></defs>',
               f'<g clip-path="url(#scene)">{scene_layers()}</g>',
               f'<g clip-path="url(#scene)">{title_layers()}</g>',
               f'<g clip-path="url(#scene)"><rect width="{W}" height="{H}" fill="url(#vig)"/><rect width="{W}" height="{H}" filter="url(#grain)" opacity="{T["grain"]}" style="mix-blend-mode:soft-light"/></g>',
               frame(W, H), '</svg>']
        return "\n".join(out)
    # ---- social 1280x640
    import emblems
    SW, SH = 1280, 640
    s = SW / W
    em_defs = ""; em_css = ""; em_body = ""; em_labels = ""
    row = [("dawn", 116, 124), ("warrior", 250, 112), ("hunter", 380, 112), ("priest", 510, 112),
           ("mage", 770, 112), ("rogue", 900, 112), ("warlock", 1030, 112), ("valradis", 1164, 124)]
    for key, cx, size in row:
        d, c, b = emblems.group(key, cx, 526, size)
        em_defs += d; em_css += c
        name = emblems.ALL[key][0].upper()
        em_body += b
        fs = 15 if len(name) <= 7 else (13 if len(name) <= 9 else 11.5)
        ls = 1.2 if len(name) <= 9 else .5
        em_labels += (f'<text x="{cx}" y="{526+size/2+24}" text-anchor="middle" font-family="{FONT}" font-weight="700" font-size="{fs}" letter-spacing="{ls}" fill="#fff0c8" stroke="#120a1c" stroke-width="3.6" paint-order="stroke" stroke-linejoin="round">{name}</text>')
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SW}" height="{SH}" viewBox="0 0 {SW} {SH}" role="img" aria-label="{label}"><title>{label}</title>',
           f'<defs><style>{CSS}{em_css}</style>{common_defs}{em_defs}<clipPath id="scene"><rect x="10" y="10" width="{SW-20}" height="{SH-20}" rx="18"/></clipPath></defs>',
           f'<g clip-path="url(#scene)"><g transform="scale({s:.5f})">{scene_layers(True)}</g><g transform="scale({s:.5f})">{title_layers()}</g>{em_body}',
           f'<rect width="{SW}" height="{SH}" fill="url(#vig)"/><rect width="{SW}" height="{SH}" filter="url(#grain)" opacity="{T["grain"]}" style="mix-blend-mode:soft-light"/>{em_labels}</g>',
           frame(SW, SH), '</svg>']
    return "\n".join(out)
