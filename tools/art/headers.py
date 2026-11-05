import random, math
import scene as S
S.T.clear(); S.T.update(S.THEMES["dark"])

T_, B_, M_ = 7, 77, 42
def g_U(w): L, R = 7, w-7; return f"M{L},{T_} V{B_-10} L{L+10},{B_} H{R-10} L{R},{B_-10} V{T_}"
def g_N(w): L, R = 7, w-7; return f"M{L},{B_} V{T_} L{R},{B_} V{T_}"
def g_B(w):
    L, R = 7, w-7
    return (f"M{L},{T_} V{B_} M{L},{T_} H{R-9} L{R},{T_+9} V{M_-9} L{R-9},{M_} H{L} "
            f"M{L},{M_} H{R-7} L{R+0},{M_+9} V{B_-9} L{R-9},{B_} H{L}")
def g_H(w): L, R = 7, w-7; return f"M{L},{T_} V{B_} M{R},{T_} V{B_} M{L},{M_} H{R}"
def g_G(w):
    L, R = 7, w-7
    return f"M{R},{T_+10} L{R-10},{T_} H{L+10} L{L},{T_+10} V{B_-10} L{L+10},{B_} H{R-10} L{R},{B_-10} V{M_+4} H{R-14}"
def g_I(w): L, R = 7, w-7; c = w/2; return f"M{L+4},{T_} H{R-4} M{c},{T_} V{B_} M{L+4},{B_} H{R-4}"
def g_T(w): L, R = 7, w-7; return f"M{L},{T_} H{R} M{w/2},{T_} V{B_}"
def g_Y(w): L, R = 7, w-7; return f"M{L},{T_} L{w/2},{M_} L{R},{T_} M{w/2},{M_} V{B_}"
GL = dict(S.GL, U=g_U, N=g_N, B=g_B, H=g_H, G=g_G, I=g_I, T=g_T, Y=g_Y)
WID = dict(R=58, A=64, S=54, K=58, O=60, L=50, C=50, E=44, U=56, N=58, B=56, H=58, G=56, I=40, T=52, Y=54)

def word_paths(word, x0=0):
    out = []; x = x0
    for ch in word:
        w = WID[ch]
        out.append(f'<path d="{GL[ch](w)}" transform="translate({x},0)"/>'); x += w + 14
    return "".join(out), x - 14 - x0

def title_block(left, right, sc=.82, ty=44):
    lp, lw = word_paths(left); rp, rw = word_paths(right)
    gap = 40
    xl = 600 - gap/2 - lw*sc; xr = 600 + gap/2
    def grp(p, x, extra): return f'<g transform="translate({x:.1f},{ty}) scale({sc})" fill="none" stroke-linejoin="miter" {extra}>{p}</g>'
    s = ""
    s += grp(lp + "", xl, 'stroke="#12081c" stroke-width="15" opacity=".7" filter="url(#b2)" style="transform:translateY(5px)"')
    s = ""
    # shadow
    s += f'<g transform="translate(0,5)" opacity=".7" filter="url(#b2)">{grp(lp, xl, "stroke=\"#12081c\" stroke-width=\"15\"")}{grp(rp, xr, "stroke=\"#12081c\" stroke-width=\"15\"")}</g>'
    s += f'<g filter="url(#b8)" opacity=".35">{grp(lp, xl, "stroke=\"#ffcf5a\" stroke-width=\"16\"")}</g><g filter="url(#b8)" opacity=".24">{grp(rp, xr, "stroke=\"#ff4b3a\" stroke-width=\"16\"")}</g>'
    s += grp(lp, xl, 'stroke="#2a1018" stroke-width="16"') + grp(rp, xr, 'stroke="#2a1018" stroke-width="16"')
    s += grp(lp, xl, 'stroke="url(#hL)" stroke-width="12"') + grp(rp, xr, 'stroke="url(#hR)" stroke-width="12"')
    s += grp(lp, xl, 'stroke="#6a3c0c" stroke-width="2" opacity=".45" transform="translate(0,1)"') if False else ""
    s += f'<g opacity=".45">{grp(lp, xl, "stroke=\"#6a3c0c\" stroke-width=\"2\"")}</g><g opacity=".5">{grp(rp, xr, "stroke=\"#3d0710\" stroke-width=\"2\"")}</g>'
    s += f'<g opacity=".38" transform="translate(-2.4,-2.4)">{grp(lp, xl, "stroke=\"#fffbe6\" stroke-width=\"1.4\"")}{grp(rp, xr, "stroke=\"#ffe3d8\" stroke-width=\"1.4\"")}</g>'
    return s, (xl, xr, lw*sc, rw*sc)

# ---------------- motifs (local 100x100) ----------------
def m_lantern():
    return '''
<circle cx="50" cy="50" r="46" fill="url(#hGl)"/>
<path d="M38,14 Q50,-2 62,14" fill="none" stroke="#1a1018" stroke-width="3"/>
<circle cx="50" cy="8" r="4.5" fill="none" stroke="#1a1018" stroke-width="2.4"/>
<path d="M34,22 L66,22 L60,14 L40,14 Z" fill="#2a1e26" stroke="#0e0a10" stroke-width="1.6" stroke-linejoin="round"/>
<path d="M36,22 H64 L62,30 H38 Z" fill="#3a2a30" stroke="#0e0a10" stroke-width="1.4"/>
<rect x="38" y="30" width="24" height="40" fill="url(#hGlass)" stroke="#0e0a10" stroke-width="2"/>
<path d="M50,30 V70 M38,50 H62" stroke="#0e0a10" stroke-width="1.6" opacity=".7"/>
<path class="hfl" d="M50,64 C42,56 47,50 50,40 C53,50 58,56 50,64 Z" fill="#ffd45a"/><path class="hfl" d="M50,62 C46,57 49,54 50,48 C51,54 54,57 50,62 Z" fill="#fff7d0"/>
<path d="M34,70 H66 L62,78 H38 Z" fill="#2a1e26" stroke="#0e0a10" stroke-width="1.6" stroke-linejoin="round"/>
<g transform="translate(76 62) rotate(28)"><circle cx="0" cy="0" r="7" fill="none" stroke="#e8c24a" stroke-width="3"/><path d="M0,7 V32 M0,22 h7 M0,28 h5" stroke="#e8c24a" stroke-width="3" fill="none" stroke-linecap="round"/></g>
'''
def m_book():
    return '''
<circle cx="46" cy="58" r="44" fill="url(#hGl)"/>
<path d="M8,62 L48,54 L48,90 L8,96 Z" fill="#3a1a22" stroke="#120a0e" stroke-width="1.6"/>
<path d="M92,62 L52,54 L52,90 L92,96 Z" fill="#3a1a22" stroke="#120a0e" stroke-width="1.6"/>
<path d="M10,58 L48,50 L48,86 L10,92 Z" fill="#f6e8c2" stroke="#7a5a2a" stroke-width="1.4"/>
<path d="M90,58 L52,50 L52,86 L90,92 Z" fill="#efdcae" stroke="#7a5a2a" stroke-width="1.4"/>
<path d="M16,64 L42,58.5 M16,72 L42,66.5 M16,80 L42,74.5 M58,58.5 L84,64 M58,66.5 L84,72 M58,74.5 L84,80" stroke="#7a5a2a" stroke-width="1.6" opacity=".6" stroke-linecap="round"/>
<path d="M48,50 V86 M52,50 V86" stroke="#7a5a2a" stroke-width="1.2"/>
<path d="M60,50 V70 L63,66 L66,70 V52" fill="#c0302a" stroke="#4a0a0a" stroke-width="1"/>
<rect x="14" y="10" width="12" height="36" rx="2" fill="#f4ecd2" stroke="#6a5a30" stroke-width="1.4"/>
<path class="hfl" d="M20,10 C13,0 18,-6 20,-16 C22,-6 27,0 20,10 Z" fill="#ffd45a" transform="translate(0 8)"/>
<path class="hfl" d="M20,10 C17,4 19,1 20,-4 C21,1 23,4 20,10 Z" fill="#fff7d0" transform="translate(0 8)"/>
'''
def m_scroll():
    return '''
<circle cx="50" cy="52" r="46" fill="url(#hGl)"/>
<path d="M18,22 H80 Q90,22 90,32 V70 Q90,80 80,80 H26 Q16,80 16,70 V30 Q16,22 26,22" fill="#f4e4b6" stroke="#7a5a2a" stroke-width="1.6"/>
<ellipse cx="22" cy="26" rx="8" ry="9" fill="#e6cf92" stroke="#7a5a2a" stroke-width="1.6"/><ellipse cx="84" cy="76" rx="8" ry="9" fill="#e6cf92" stroke="#7a5a2a" stroke-width="1.6"/>
<path d="M32,36 H72 M32,44 H78 M32,52 H66 M32,60 H74" stroke="#7a5a2a" stroke-width="2" opacity=".55" stroke-linecap="round"/>
<circle cx="70" cy="66" r="8" fill="#b02a2a" stroke="#4a0808" stroke-width="1.4"/><path d="M66,66 l3,3 l5,-6" stroke="#ffd0c0" stroke-width="1.6" fill="none"/>
<g transform="translate(80 18) rotate(35)"><path d="M0,0 C6,-18 4,-36 -2,-52 C-10,-36 -12,-18 0,0 Z" fill="#f2ecdc" stroke="#5a4a30" stroke-width="1.2" transform="scale(.8)"/><path d="M-1,0 V-38" stroke="#5a4a30" stroke-width="1.2" transform="scale(.8)"/></g>
<path d="M8,92 h18 v-8 q0,-6 -6,-6 h-6 q-6,0 -6,6 z" fill="#1a1428" stroke="#0a0810" stroke-width="1.4"/>
'''
def m_books():
    return '''
<circle cx="50" cy="56" r="46" fill="url(#hGl)"/>
<rect x="14" y="70" width="72" height="16" rx="2" fill="#5a2a1a" stroke="#1a0c08" stroke-width="1.6"/><rect x="20" y="74" width="60" height="8" fill="#e8c24a" opacity=".25"/>
<rect x="20" y="54" width="62" height="16" rx="2" fill="#2a3a6a" stroke="#0a1020" stroke-width="1.6"/><path d="M26,58 h50 M26,66 h50" stroke="#e8c24a" stroke-width="1.4" opacity=".7"/>
<rect x="12" y="38" width="66" height="16" rx="2" fill="#7a1a22" stroke="#2a0608" stroke-width="1.6"/><rect x="38" y="42" width="14" height="8" rx="1" fill="#f4e4b6" stroke="#4a2a10" stroke-width="1"/>
<rect x="24" y="22" width="56" height="16" rx="2" fill="#3a5a3a" stroke="#0a200a" stroke-width="1.6"/><path d="M30,26 h44" stroke="#e8c24a" stroke-width="1.4" opacity=".7"/>
<rect x="66" y="6" width="10" height="16" rx="2" fill="#f4ecd2" stroke="#6a5a30" stroke-width="1.4"/>
<path class="hfl" d="M71,6 C64,-2 69,-8 71,-16 C73,-8 78,-2 71,6 Z" fill="#ffd45a" transform="translate(0 5)"/><path class="hfl" d="M71,6 C68,0 70,-3 71,-8 C72,-3 74,0 71,6 Z" fill="#fff7d0" transform="translate(0 5)"/>
'''
def m_shield(gold=True):
    c1, c2, c3 = ("#fff0b0", "#e0b040", "#7a5212") if gold else ("#ff9a8a", "#c0302f", "#4a0a10")
    return f'''
<circle cx="50" cy="52" r="46" fill="url(#hGl{'' if gold else 'R'})"/>
<path d="M50,6 L90,18 V50 C90,76 70,90 50,98 C30,90 10,76 10,50 V18 Z" fill="{c3}" stroke="#0a0608" stroke-width="2.4" stroke-linejoin="round"/>
<path d="M50,12 L84,22 V50 C84,72 67,84 50,92 C33,84 16,72 16,50 V22 Z" fill="url(#sh{'G' if gold else 'R'})" stroke="#0a0608" stroke-width="1.6" stroke-linejoin="round"/>
<path d="M50,12 V92" stroke="#0a0608" stroke-width="1.2" opacity=".35"/>
<path d="M22,30 L50,20 L78,30" fill="none" stroke="{c1}" stroke-width="2" opacity=".55"/>
<circle cx="50" cy="46" r="11" fill="#140a10" stroke="{c1}" stroke-width="2"/><path d="M45,70 L50,52 L55,70 Z" fill="#140a10" stroke="{c1}" stroke-width="1.6" stroke-linejoin="round"/>
<circle cx="50" cy="45" r="4" fill="{c1}" class="hfl"/>
<g fill="{c1}"><circle cx="22" cy="22" r="2.2"/><circle cx="78" cy="22" r="2.2"/><circle cx="50" cy="16" r="2.2"/></g>
'''

HEADS = {
    "runbook": dict(left="RUN", right="BOOK", ml=m_lantern, mr=m_book, rl="КНИГА ХРАНИТЕЛЯ ОЧАГА", rr="АВАРИИ · ГЕЙТЫ · ТЮНИНГ",
                    tag="тюнинг без пересборки · контроль и DR · миграции · troubleshooting", alt="RUNBOOK — Книга хранителя очага"),
    "changelog": dict(left="CHAN", right="GELOG", ml=m_scroll, mr=m_books, rl="ЛЕТОПИСЬ ВЕРСИЙ", rr="НЕ ПЕРЕПИСЫВАЕТСЯ",
                      tag="Keep a Changelog · SemVer · история не переписывается задним числом", alt="CHANGELOG — Летопись версий"),
    "security": dict(left="SECU", right="RITY", ml=lambda: m_shield(True), mr=lambda: m_shield(False), rl="СТРАЖА ОЧАГА", rr="СООБЩИТЬ ОБ УЯЗВИМОСТИ",
                     tag="поддерживаемые версии · ответственное раскрытие · тихо и вовремя", alt="SECURITY — Стража очага"),
}

def header(key):
    h = HEADS[key]
    random.seed(3)
    W, H = 1200, 240
    S.T.clear(); S.T.update(S.THEMES["dark"])
    t = S.T
    title, (xl, xr, lw, rw) = title_block(h["left"], h["right"])
    stars = "".join(f'<circle class="tw" cx="{random.uniform(24,1176):.0f}" cy="{random.uniform(22,150):.0f}" r="{random.choice([.8,1,1.3])}" fill="{"#fff1cf" if random.random()<.5 else "#ffd6d0"}" style="animation-duration:{random.uniform(2.6,6):.1f}s;animation-delay:-{random.uniform(0,6):.1f}s"/>' for _ in range(60))
    hills = ('<path d="M0,176 C160,150 300,168 440,186 C520,196 570,204 600,214 C630,204 680,196 760,186 C900,168 1040,150 1200,176 V240 H0 Z" fill="url(#hFar)"/>'
             '<path d="M0,200 C140,182 260,190 380,206 C480,220 560,224 600,232 C640,224 720,220 820,206 C940,190 1060,182 1200,200 V240 H0 Z" fill="#150e22"/>'
             '<path d="M0,176 C160,150 300,168 440,186" fill="none" stroke="#f3c26a" stroke-width="1" opacity=".2"/><path d="M1200,176 C1040,150 900,168 760,186" fill="none" stroke="#e5584a" stroke-width="1" opacity=".22"/>')
    pines = "".join(S.pine(x, y, hh, ww, "#0c0715") for x, y, hh, ww in [(40,224,52,22),(70,228,40,18),(108,226,60,24),(1160,224,52,22),(1130,228,40,18),(1092,226,60,24)])
    ff = ""
    for i in range(22):
        left = i < 11
        x = random.uniform(30, 560) if left else random.uniform(640, 1170); y = random.uniform(120, 225)
        ff += (f'<circle class="ff" cx="{x:.0f}" cy="{y:.0f}" r="{random.uniform(2.2,3.8):.1f}" fill="url(#{"spG" if left else "spR"})" style="--dx:{random.uniform(-22,22):.0f}px;--dy:{random.uniform(-48,-10):.0f}px;animation-duration:{random.uniform(7,12):.1f}s;animation-delay:-{random.uniform(0,11):.1f}s"/>')
    # rift
    rift = "M600,0 L594,34 L606,58 L596,84 L605,110 L598,138"
    # ribbon
    rb_y = 150; rbh = 30
    lw_ = 250
    ribL = f'M{600-lw_},{rb_y} H598 L594,{rb_y+8} L599,{rb_y+15} L593,{rb_y+22} L598,{rb_y+rbh} H{600-lw_} L{600-lw_+16},{rb_y+rbh/2} Z'
    ribR = f'M{600+lw_},{rb_y} H602 L606,{rb_y+8} L601,{rb_y+15} L607,{rb_y+22} L602,{rb_y+rbh} H{600+lw_} L{600+lw_-16},{rb_y+rbh/2} Z'
    ribbon = (f'<ellipse cx="600" cy="{rb_y+rbh+5}" rx="240" ry="6" fill="#0a0614" opacity=".3" filter="url(#b4)"/>'
              f'<path d="{ribL}" fill="url(#ribL)" stroke="#7a4b12" stroke-width="1.5" stroke-linejoin="round"/><path d="{ribR}" fill="url(#ribR)" stroke="#3a070b" stroke-width="1.5" stroke-linejoin="round"/>'
              f'<text x="{600-lw_/2-2}" y="{rb_y+19.5}" text-anchor="middle" font-family="{S.FONT}" font-weight="700" font-size="13" fill="#3a210f" textLength="{min(len(h["rl"])*9.4,208):.0f}" lengthAdjust="spacing">{h["rl"]}</text>'
              f'<text x="{600+lw_/2+2}" y="{rb_y+19.5}" text-anchor="middle" font-family="{S.FONT}" font-weight="700" font-size="13" fill="#ffe9c4" textLength="{min(len(h["rr"])*9.4,208):.0f}" lengthAdjust="spacing">{h["rr"]}</text>')
    tagline = f'<text x="600" y="{rb_y+rbh+30}" text-anchor="middle" font-family="{S.FONT}" font-style="italic" font-size="13" fill="#fbe3b4" stroke="#1a0f1e" stroke-width="3" paint-order="stroke" stroke-linejoin="round">{h["tag"]}</text>'
    motifs = (f'<g transform="translate(150 28) scale(1.15)">{h["ml"]()}</g><g transform="translate(940 28) scale(1.15)">{h["mr"]()}</g>')
    css = S.CSS + ".hfl{transform-box:fill-box;transform-origin:50% 100%;animation:hfl 1.5s ease-in-out infinite alternate}@keyframes hfl{from{transform:scale(1,.9) rotate(-3deg)}to{transform:scale(.92,1.1) rotate(3deg)}}"
    defs = f'''{S.defs_block()}
<linearGradient id="hL" gradientUnits="userSpaceOnUse" x1="0" y1="44" x2="0" y2="112"><stop offset="0" stop-color="#ffe58f"/><stop offset=".5" stop-color="#f3b535"/><stop offset="1" stop-color="#b8731a"/></linearGradient>
<linearGradient id="hR" gradientUnits="userSpaceOnUse" x1="0" y1="44" x2="0" y2="112"><stop offset="0" stop-color="#ff7a62"/><stop offset=".5" stop-color="#d42a2a"/><stop offset="1" stop-color="#820d16"/></linearGradient>
<linearGradient id="hFar" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#4a3248"/><stop offset=".5" stop-color="#2d2040"/><stop offset="1" stop-color="#5a2a3a"/></linearGradient>
<linearGradient id="hGlass" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffe9a0" stop-opacity=".95"/><stop offset="1" stop-color="#ff9d3a" stop-opacity=".85"/></linearGradient>
<radialGradient id="hGl"><stop offset="0" stop-color="#ffd978" stop-opacity=".5"/><stop offset="1" stop-color="#ffb43a" stop-opacity="0"/></radialGradient>
<radialGradient id="hGlR"><stop offset="0" stop-color="#ff7a5a" stop-opacity=".5"/><stop offset="1" stop-color="#e0553f" stop-opacity="0"/></radialGradient>
<linearGradient id="shG" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff0b0"/><stop offset=".5" stop-color="#e0b040"/><stop offset="1" stop-color="#8a5f12"/></linearGradient>
<linearGradient id="shR" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e0504a"/><stop offset=".5" stop-color="#a01a22"/><stop offset="1" stop-color="#3a0710"/></linearGradient>
<clipPath id="hscene"><rect x="10" y="10" width="{W-20}" height="{H-20}" rx="18"/></clipPath>'''
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="{h["alt"]}"><title>{h["alt"]}</title>'
           f'<defs><style>{css}</style>{defs}</defs><g clip-path="url(#hscene)">'
           f'<rect width="{W}" height="{H}" fill="url(#sky)"/><rect width="{W}" height="{H}" fill="url(#glowL)" opacity=".8"/><rect width="{W}" height="{H}" fill="url(#glowR)" opacity=".8"/>'
           f'{stars}{hills}{pines}'
           f'<g class="rift"><path d="{rift}" fill="none" stroke="#fff3c8" stroke-width="8" opacity=".5" filter="url(#b4)"/><path d="{rift}" fill="none" stroke="url(#riftG)" stroke-width="2.2"/></g>'
           f'{motifs}{title}{ribbon}{tagline}{ff}'
           f'<rect width="{W}" height="{H}" fill="url(#vig)"/><rect width="{W}" height="{H}" filter="url(#grain)" opacity=".3" style="mix-blend-mode:soft-light"/></g>'
           f'{S.frame(W, H)}</svg>')
    return svg

def divider():
    return '''<svg xmlns="http://www.w3.org/2000/svg" width="960" height="44" viewBox="0 0 960 44" role="img" aria-label="разделитель">
<defs><style>.s{animation:s 4s ease-in-out infinite;transform-box:fill-box;transform-origin:center}@keyframes s{0%,100%{opacity:.5;transform:scale(.8) rotate(0)}50%{opacity:1;transform:scale(1.15) rotate(45deg)}}@media (prefers-reduced-motion:reduce){.s{animation:none}}</style>
<linearGradient id="gl" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#c99a2e" stop-opacity=".12"/><stop offset="1" stop-color="#c99a2e"/></linearGradient>
<linearGradient id="gr" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#b8322e"/><stop offset="1" stop-color="#b8322e" stop-opacity=".12"/></linearGradient></defs>
<path d="M60,22 H420" stroke="url(#gl)" stroke-width="2.6" stroke-linecap="round"/><path d="M540,22 H900" stroke="url(#gr)" stroke-width="2.6" stroke-linecap="round"/>
<path d="M120,22 H300 M660,22 H840" stroke="#8a6a20" stroke-width="1" opacity=".0"/>
<g fill="#c99a2e"><circle cx="300" cy="22" r="2"/><circle cx="360" cy="22" r="2.4"/><circle cx="400" cy="22" r="2"/></g>
<g fill="#b8322e"><circle cx="560" cy="22" r="2"/><circle cx="600" cy="22" r="2.4"/><circle cx="660" cy="22" r="2"/></g>
<g transform="translate(446 22)"><path d="M-12,8 L-14,-4 L-7,2 L0,-8 L7,2 L14,-4 L12,8 Z" fill="#e0b040" stroke="#7a5212" stroke-width="1.2" stroke-linejoin="round"/><rect x="-12.5" y="8" width="25" height="3.4" rx="1" fill="#e0b040" stroke="#7a5212" stroke-width="1.2"/></g>
<g transform="translate(514 22)"><path d="M-12,8 L-14,-4 L-7,2 L0,-8 L7,2 L14,-4 L12,8 Z" fill="#c8302f" stroke="#4a0b10" stroke-width="1.2" stroke-linejoin="round"/><rect x="-12.5" y="8" width="25" height="3.4" rx="1" fill="#c8302f" stroke="#4a0b10" stroke-width="1.2"/></g>
<path d="M480,2 L477,12 L483,20 L478,30 L480,42" fill="none" stroke="#e8b84a" stroke-width="1.6" stroke-linejoin="miter"/>
<path class="s" d="M480,14 L483,22 L480,30 L477,22 Z" fill="#fff3c8" stroke="#e8b84a" stroke-width="1"/>
</svg>'''

if __name__ == "__main__":
    import os
    os.makedirs('/home/claude/art/out', exist_ok=True)
    for k in HEADS: open(f'/home/claude/art/out/h_{k}.svg','w',encoding='utf8').write(header(k))
    open('/home/claude/art/out/divider.svg','w',encoding='utf8').write(divider())
    print("headers ok")
