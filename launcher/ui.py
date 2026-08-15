"""
Boîte à outils d'interface : thème et widgets personnalisés.

Tkinter est volontairement conservé plutôt que customtkinter ou PyQt : il est livré avec Python,
donc le lanceur reste utilisable sans aucun « pip install ». Le prix à payer est que les widgets
par défaut ont l'air datés — d'où ce fichier, qui redessine les éléments visibles sur des Canvas
plutôt que d'utiliser les boutons natifs.
"""

import base64
import json
import sys
import tkinter as tk
import zipfile


# ----------------------------------------------------------------------------------------------
# Thème
# ----------------------------------------------------------------------------------------------

class T:
    BG_DEEP = "#0D0F14"      # fond de fenêtre
    BG_SIDE = "#12161F"      # barre latérale
    BG_PANEL = "#181D28"     # panneaux
    BG_CARD = "#1F2634"      # cartes
    BG_HOVER = "#2A3344"     # survol
    BG_INPUT = "#0F131B"     # champs de saisie

    ACCENT = "#4C8DFF"
    ACCENT_HI = "#6BA3FF"
    ACCENT_DIM = "#2B4C8A"

    TEXT = "#E9ECF2"
    TEXT_DIM = "#8A93A6"
    TEXT_FAINT = "#5A6377"

    GREEN = "#4ADE80"
    AMBER = "#FBBF24"
    RED = "#F87171"

    BORDER = "#252C3B"


def font(size=10, weight="normal"):
    """
    Segoe UI sous Windows, DejaVu Sans ailleurs. Les deux ont un rendu propre à petite taille ;
    la police Tk par défaut (Helvetica bitmap) donne immédiatement un air de logiciel des années 90.
    """
    family = "Segoe UI" if sys.platform == "win32" else "DejaVu Sans"
    return (family, size, weight)


def rounded_points(x1, y1, x2, y2, r):
    """
    Points d'un rectangle à coins arrondis, dédoublés aux angles pour le lissage de Tk.

    Le rayon est borné à la moitié du plus petit côté. Sans cette borne, une barre fine et longue
    reçoit un rayon supérieur à sa demi-hauteur et le lissage produit une pointe triangulaire au
    lieu d'un bout arrondi.
    """
    r = max(0, min(r, abs(x2 - x1) / 2, abs(y2 - y1) / 2))
    return [
        x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r,
        x2, y2 - r, x2, y2, x2 - r, y2, x1 + r, y2,
        x1, y2, x1, y2 - r, x1, y1 + r, x1, y1,
    ]


def round_rect(canvas, x1, y1, x2, y2, r, **kwargs):
    """Rectangle à coins arrondis. Tk n'en propose pas nativement."""
    return canvas.create_polygon(rounded_points(x1, y1, x2, y2, r), smooth=True, **kwargs)


def bind_all_children(widget, sequence, handler):
    """
    Survol fiable : entrer dans un enfant déclenche un <Leave> sur le parent. Sans propager la
    liaison à toute la descendance, un bouton composé d'un cadre et d'un texte clignote au passage
    de la souris.
    """
    widget.bind(sequence, handler)
    for child in widget.winfo_children():
        bind_all_children(child, sequence, handler)


# ----------------------------------------------------------------------------------------------
# Widgets
# ----------------------------------------------------------------------------------------------

class Button(tk.Canvas):
    """Bouton dessiné : coins arrondis, survol et état désactivé."""

    def __init__(self, parent, text, command=None, width=140, height=38,
                 style="primary", radius=8, icon=None, **kwargs):
        bg = kwargs.pop("bg", parent.cget("bg"))
        super().__init__(parent, width=width, height=height, bg=bg,
                         highlightthickness=0, bd=0, **kwargs)
        self.command = command
        self.style = style
        self._enabled = True
        # NE PAS nommer ces attributs _w/_h : Tkinter utilise déjà self._w pour le
        # chemin interne du widget, et l'écraser casse tous les appels Tk suivants.
        self._width, self._height, self._radius = width, height, radius
        self._text = text
        self._icon = icon

        self._shape = round_rect(self, 1, 1, width - 1, height - 1, radius,
                                 fill=self._fill(False), outline=self._outline())
        label = f"{icon}  {text}" if icon else text
        self._label = self.create_text(width / 2, height / 2, text=label,
                                       fill=self._fg(), font=font(10, "bold"))

        self.bind("<Enter>", self._on_enter)
        self.bind("<Leave>", self._on_leave)
        self.bind("<Button-1>", self._on_click)
        self.configure(cursor="hand2")

    def _fill(self, hover):
        if not self._enabled:
            return T.BG_CARD
        if self.style == "primary":
            return T.ACCENT_HI if hover else T.ACCENT
        if self.style == "ghost":
            return T.BG_HOVER if hover else T.BG_CARD
        if self.style == "danger":
            return "#B94A4A" if hover else "#8E3A3A"
        return T.BG_HOVER if hover else T.BG_CARD

    def _outline(self):
        return T.BORDER if self.style == "ghost" else ""

    def _fg(self):
        if not self._enabled:
            return T.TEXT_FAINT
        return "#FFFFFF" if self.style == "primary" else T.TEXT

    def _on_enter(self, _=None):
        if self._enabled:
            self.itemconfig(self._shape, fill=self._fill(True))

    def _on_leave(self, _=None):
        self.itemconfig(self._shape, fill=self._fill(False))

    def _on_click(self, _=None):
        if self._enabled and self.command:
            self.command()

    def set_enabled(self, enabled):
        self._enabled = enabled
        self.itemconfig(self._shape, fill=self._fill(False))
        self.itemconfig(self._label, fill=self._fg())
        self.configure(cursor="hand2" if enabled else "arrow")

    def set_text(self, text):
        self._text = text
        label = f"{self._icon}  {text}" if self._icon else text
        self.itemconfig(self._label, text=label)


class NavItem(tk.Frame):
    """Entrée de la barre latérale, avec un liseré d'accent quand elle est active."""

    def __init__(self, parent, icon, text, command):
        super().__init__(parent, bg=T.BG_SIDE, cursor="hand2")
        self.command = command
        self.active = False

        self.bar = tk.Frame(self, bg=T.BG_SIDE, width=3)
        self.bar.pack(side="left", fill="y")

        inner = tk.Frame(self, bg=T.BG_SIDE)
        inner.pack(side="left", fill="both", expand=True, padx=(11, 0), pady=9)

        self.icon = tk.Label(inner, text=icon, bg=T.BG_SIDE, fg=T.TEXT_DIM, font=font(12))
        self.icon.pack(side="left")
        self.label = tk.Label(inner, text=text, bg=T.BG_SIDE, fg=T.TEXT_DIM,
                              font=font(10, "bold"))
        self.label.pack(side="left", padx=(10, 0))

        bind_all_children(self, "<Button-1>", lambda e: self.command())
        bind_all_children(self, "<Enter>", self._enter)
        bind_all_children(self, "<Leave>", self._leave)

    def _paint(self, bg, fg):
        for w in (self, self.bar.master, self.icon.master, self.icon, self.label):
            if w is not self.bar:
                w.configure(bg=bg)
        self.icon.configure(fg=fg)
        self.label.configure(fg=fg)
        self.bar.configure(bg=T.ACCENT if self.active else bg)

    def _enter(self, _=None):
        if not self.active:
            self._paint(T.BG_PANEL, T.TEXT)

    def _leave(self, _=None):
        self._paint(T.BG_PANEL if self.active else T.BG_SIDE,
                    T.TEXT if self.active else T.TEXT_DIM)

    def set_active(self, active):
        self.active = active
        self._leave()


class ProgressBar(tk.Canvas):
    """Barre de progression au thème du lanceur, avec mode indéterminé."""

    def __init__(self, parent, width=520, height=6):
        super().__init__(parent, width=width, height=height,
                         bg=parent.cget("bg"), highlightthickness=0, bd=0)
        self._width, self._height = width, height   # jamais _w : réservé par Tkinter
        round_rect(self, 0, 0, width, height, height / 2, fill=T.BG_CARD, outline="")
        self._fill = round_rect(self, 0, 0, 1, height, height / 2, fill=T.ACCENT, outline="")
        self._pulse = None
        self._pos = 0
        self.itemconfig(self._fill, state="hidden")

    def set(self, fraction):
        self.stop_pulse()
        fraction = max(0.0, min(1.0, fraction))
        # A zero, on masque completement : sinon le rayon minimal des coins arrondis laisse une
        # pastille bleue visible, qu'on lit comme "quelque chose est deja en cours".
        self.itemconfig(self._fill, state="hidden" if fraction <= 0.001 else "normal")
        if fraction > 0.001:
            self.coords(self._fill, *self._points(0, self._width * fraction))

    def _points(self, x1, x2):
        r = self._height / 2
        return rounded_points(x1, 0, max(x2, x1 + 2 * r), self._height, r)

    def start_pulse(self):
        """Utilisé quand la durée est inconnue (vérification de milliers de fichiers)."""
        self.itemconfig(self._fill, state="normal")
        if self._pulse is None:
            self._animate()

    def _animate(self):
        span = self._width * 0.25
        self._pos = (self._pos + 8) % (self._width + span)
        x1 = max(0, self._pos - span)
        x2 = min(self._width, self._pos)
        if x2 > x1:
            self.coords(self._fill, *self._points(x1, x2))
        self._pulse = self.after(16, self._animate)

    def stop_pulse(self):
        if self._pulse is not None:
            self.after_cancel(self._pulse)
            self._pulse = None


class SlimScrollbar(tk.Canvas):
    """
    Ascenseur dessiné à la main.

    Le `tk.Scrollbar` natif impose deux boutons fléchés et un relief gravé, impossibles à
    désactiver — c'est l'élément qui trahit le plus l'âge de Tk dans une interface sombre. Ici :
    une simple pastille arrondie, sans flèche.
    """

    def __init__(self, parent, command, width=8, bg=None):
        super().__init__(parent, width=width, bg=bg or parent.cget("bg"),
                         highlightthickness=0, bd=0)
        self.command = command
        self._width = width
        self._first, self._last = 0.0, 1.0
        self._thumb = round_rect(self, 2, 0, width - 2, 10, (width - 4) / 2,
                                 fill=T.BG_HOVER, outline="")
        self._drag_origin = None

        self.bind("<Configure>", lambda e: self._redraw())
        self.bind("<Button-1>", self._press)
        self.bind("<B1-Motion>", self._drag)
        self.bind("<ButtonRelease-1>", lambda e: self.itemconfig(self._thumb, fill=T.BG_HOVER))
        self.bind("<Enter>", lambda e: self.itemconfig(self._thumb, fill=T.TEXT_FAINT))
        self.bind("<Leave>", lambda e: self.itemconfig(self._thumb, fill=T.BG_HOVER))

    def set(self, first, last):
        self._first, self._last = float(first), float(last)
        self._redraw()

    def _redraw(self):
        height = self.winfo_height()
        if height <= 1:
            return
        # Rien à faire défiler : pastille invisible plutôt que barre pleine.
        if self._first <= 0.0 and self._last >= 1.0:
            self.coords(self._thumb, *([0] * 24))
            return
        top = self._first * height
        bottom = max(self._last * height, top + 24)   # pastille jamais plus petite que 24 px
        w = self._width
        self.coords(self._thumb, *rounded_points(2, top, w - 2, bottom, (w - 4) / 2))

    def _press(self, event):
        self.itemconfig(self._thumb, fill=T.ACCENT)
        self._jump(event.y)

    def _drag(self, event):
        self._jump(event.y)

    def _jump(self, y):
        height = max(1, self.winfo_height())
        span = self._last - self._first
        # On centre la pastille sur le curseur plutôt que d'aligner son bord : le déplacement
        # suit alors la souris au lieu de sauter d'une demi-pastille au premier clic.
        self.command("moveto", max(0.0, min(1.0, y / height - span / 2)))


class Slider(tk.Canvas):
    """Curseur de valeur dessiné, pour remplacer tk.Scale dont le rendu est daté."""

    def __init__(self, parent, minimum, maximum, step, value, command=None,
                 width=460, height=26, bg=None):
        super().__init__(parent, width=width, height=height,
                         bg=bg or parent.cget("bg"), highlightthickness=0, bd=0,
                         cursor="hand2")
        self.min, self.max, self.step = minimum, maximum, step
        self.command = command
        self._width, self._height = width, height
        self._value = value

        cy = height / 2
        round_rect(self, 0, cy - 3, width, cy + 3, 3, fill=T.BG_INPUT, outline="")
        self._track = round_rect(self, 0, cy - 3, 10, cy + 3, 3, fill=T.ACCENT, outline="")
        self._knob = self.create_oval(0, 0, 0, 0, fill="#FFFFFF", outline=T.ACCENT, width=2)

        self.bind("<Button-1>", self._set_from_event)
        self.bind("<B1-Motion>", self._set_from_event)
        self.bind("<Configure>", lambda e: self._redraw())
        self._redraw()

    def get(self):
        return self._value

    def set(self, value):
        self._value = max(self.min, min(self.max, value))
        self._redraw()

    def _redraw(self):
        cy = self._height / 2
        span = self.max - self.min
        ratio = 0 if span == 0 else (self._value - self.min) / span
        x = 10 + ratio * (self._width - 20)
        self.coords(self._track, *rounded_points(0, cy - 3, x, cy + 3, 3))
        self.coords(self._knob, x - 9, cy - 9, x + 9, cy + 9)

    def _set_from_event(self, event):
        ratio = max(0.0, min(1.0, (event.x - 10) / max(1, self._width - 20)))
        raw = self.min + ratio * (self.max - self.min)
        self._value = int(round(raw / self.step) * self.step)
        self._redraw()
        if self.command:
            self.command(self._value)


class ScrollFrame(tk.Frame):
    """Zone défilante. `body` est le conteneur où placer les enfants."""

    def __init__(self, parent, bg=T.BG_DEEP):
        super().__init__(parent, bg=bg)
        self.canvas = tk.Canvas(self, bg=bg, highlightthickness=0, bd=0)
        self.canvas.pack(side="left", fill="both", expand=True)

        self.scrollbar = SlimScrollbar(self, self.canvas.yview, bg=bg)
        self.body = tk.Frame(self.canvas, bg=bg)
        self._window = self.canvas.create_window((0, 0), window=self.body, anchor="nw")

        self.body.bind("<Configure>", self._on_body_resize)
        self.canvas.bind("<Configure>", self._on_canvas_resize)
        self.canvas.configure(yscrollcommand=self._on_scroll)

        # La molette n'est liée qu'au survol : une liaison globale volerait le défilement aux
        # autres zones de la fenêtre.
        self.bind("<Enter>", lambda e: self._bind_wheel(True))
        self.bind("<Leave>", lambda e: self._bind_wheel(False))

    def _on_scroll(self, first, last):
        # L'ascenseur garde toujours sa place, même quand il n'y a rien à faire défiler : le
        # montrer et le cacher change la largeur disponible, et les enfants déjà positionnés se
        # retrouvent rognés — c'est ce qui coupait les boutons en bout de ligne.
        if not self.scrollbar.winfo_ismapped():
            self.scrollbar.pack(side="right", fill="y")
        self.scrollbar.set(first, last)

    def _on_body_resize(self, _):
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _on_canvas_resize(self, event):
        self.canvas.itemconfig(self._window, width=event.width)

    def _bind_wheel(self, active):
        if active:
            self.canvas.bind_all("<MouseWheel>", self._wheel)
            self.canvas.bind_all("<Button-4>", self._wheel)
            self.canvas.bind_all("<Button-5>", self._wheel)
        else:
            self.canvas.unbind_all("<MouseWheel>")
            self.canvas.unbind_all("<Button-4>")
            self.canvas.unbind_all("<Button-5>")

    def _wheel(self, event):
        if event.num == 4:
            delta = -1
        elif event.num == 5:
            delta = 1
        else:
            delta = -1 if event.delta > 0 else 1
        self.canvas.yview_scroll(delta, "units")

    def clear(self):
        for child in self.body.winfo_children():
            child.destroy()


class Toggle(tk.Canvas):
    """Interrupteur, plus lisible qu'une case à cocher pour activer/désactiver un mod."""

    def __init__(self, parent, value=True, command=None, bg=None):
        super().__init__(parent, width=38, height=22, bg=bg or parent.cget("bg"),
                         highlightthickness=0, bd=0, cursor="hand2")
        self.value = value
        self.command = command
        self._track = round_rect(self, 1, 3, 37, 19, 8, fill="", outline="")
        self._knob = self.create_oval(0, 0, 0, 0, fill="#FFFFFF", outline="")
        self._paint()
        self.bind("<Button-1>", self._toggle)

    def _paint(self):
        self.itemconfig(self._track, fill=T.ACCENT if self.value else "#39414F")
        x = 20 if self.value else 3
        self.coords(self._knob, x, 5, x + 14, 19)

    def _toggle(self, _=None):
        self.value = not self.value
        self._paint()
        if self.command:
            self.command(self.value)


class Field(tk.Frame):
    """Champ de saisie avec libellé et cadre discret."""

    def __init__(self, parent, label, value="", hint=None, show=None, bg=T.BG_PANEL):
        super().__init__(parent, bg=bg)
        tk.Label(self, text=label, bg=bg, fg=T.TEXT_DIM,
                 font=font(9, "bold"), anchor="w").pack(fill="x")

        box = tk.Frame(self, bg=T.BG_INPUT, highlightthickness=1,
                       highlightbackground=T.BORDER, highlightcolor=T.ACCENT)
        box.pack(fill="x", pady=(5, 0))

        self.var = tk.StringVar(value=value)
        self.entry = tk.Entry(box, textvariable=self.var, bg=T.BG_INPUT, fg=T.TEXT,
                              insertbackground=T.ACCENT, relief="flat", font=font(10),
                              show=show, bd=0)
        self.entry.pack(fill="x", padx=10, pady=8)

        if hint:
            tk.Label(self, text=hint, bg=bg, fg=T.TEXT_FAINT, font=font(8),
                     anchor="w", justify="left", wraplength=460).pack(fill="x", pady=(4, 0))

    def get(self):
        return self.var.get().strip()

    def set(self, value):
        self.var.set(value)


# ----------------------------------------------------------------------------------------------
# Lecture des mods
# ----------------------------------------------------------------------------------------------

def read_mod_info(jar_path):
    """
    Extrait nom, version, description et icône depuis fabric.mod.json.

    Afficher « Sodium 0.9.1 » plutôt que « sodium-fabric-mc26.2-0.9.1.jar » change complètement la
    lisibilité de la liste. Tout est enveloppé : un jar corrompu ou un mod Forge posé là par erreur
    ne doit pas faire disparaître toute la liste.
    """
    info = {"name": jar_path.name, "version": "", "description": "", "icon": None}
    try:
        with zipfile.ZipFile(jar_path) as z:
            names = z.namelist()
            if "fabric.mod.json" not in names:
                return info
            meta = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"))
            info["name"] = meta.get("name") or meta.get("id") or jar_path.name
            info["version"] = str(meta.get("version", ""))
            info["description"] = (meta.get("description") or "").strip().replace("\n", " ")

            icon = meta.get("icon")
            if isinstance(icon, dict):
                icon = icon.get(sorted(icon, key=lambda k: -int(k) if k.isdigit() else 0)[0])
            if isinstance(icon, str) and icon in names:
                info["icon"] = z.read(icon)
    except Exception:
        pass
    return info


def make_head_icon(skin_png, scale=4):
    """
    Découpe la tête dans une peau Minecraft et l'agrandit.

    Une peau est une planche de textures : la face avant de la tête occupe le carré (8,8)-(16,16),
    et le « second calque » (chapeau, cheveux) le carré (40,8)-(48,16). On superpose les deux, sinon
    beaucoup de joueurs apparaissent chauves.

    Agrandissement par `zoom`, qui duplique les pixels sans les lisser — c'est exactement le rendu
    voulu pour du pixel art ; un lissage donnerait une bouillie floue.
    """
    try:
        skin = tk.PhotoImage(data=base64.b64encode(skin_png))
        head = tk.PhotoImage(width=8, height=8)
        head.tk.call(head, "copy", skin, "-from", 8, 8, 16, 16, "-to", 0, 0)
        # Le calque supérieur n'existe pas sur toutes les peaux : on l'ajoute au mieux.
        try:
            head.tk.call(head, "copy", skin, "-from", 40, 8, 48, 16, "-to", 0, 0,
                         "-compositingrule", "overlay")
        except tk.TclError:
            pass
        return head.zoom(scale, scale)
    except Exception:
        return None


def make_icon(png_bytes, target=34):
    """
    Convertit un PNG en image Tk réduite. `subsample` ne divise que par des entiers, donc la
    taille finale est approximative — suffisant pour une vignette, et ça évite d'imposer Pillow.
    """
    try:
        image = tk.PhotoImage(data=base64.b64encode(png_bytes))
        factor = max(1, round(image.width() / target))
        return image.subsample(factor, factor) if factor > 1 else image
    except Exception:
        return None
