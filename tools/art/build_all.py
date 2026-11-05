"""Генератор оформления RaskolClasses. Запуск: python3 build_all.py <папка репозитория>
Создаёт banner-dark.svg, banner-light.svg, docs/assets/** (эмблемы, шапки, разделитель, social preview)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scene, emblems, headers

root = sys.argv[1] if len(sys.argv) > 1 else "."
def w(path, text):
    p = os.path.join(root, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf8").write(text); print("  +", path)

w("banner-dark.svg", scene.build("dark"))
w("banner-light.svg", scene.build("light"))
w("docs/assets/social/social-preview.svg", scene.build("dark", "social"))
for k in emblems.ALL: w(f"docs/assets/emblems/{k}.svg", emblems.standalone(k))
for k in headers.HEADS: w(f"docs/assets/headers/{k}.svg", headers.header(k))
w("docs/assets/divider.svg", headers.divider())

# social preview -> JPG (<1 MB для GitHub Settings → Social preview)
try:
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch(); pg = b.new_page(viewport={"width": 1280, "height": 640})
        pg.goto("file://" + os.path.abspath(os.path.join(root, "docs/assets/social/social-preview.svg"))); pg.wait_for_timeout(3500)
        pg.screenshot(path=os.path.join(root, "docs/assets/social/social-preview.jpg"), type="jpeg", quality=90); b.close()
    print("  + docs/assets/social/social-preview.jpg")
except Exception as e:
    print("  ! JPG не создан (нужен playwright):", e)
