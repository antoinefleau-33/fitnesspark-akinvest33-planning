#!/usr/bin/env python3
"""
Interface graphique du lanceur.

    python3 gui.py

Toute la logique (téléchargements, authentification, lancement) vit dans mclaunch.py ; ce fichier
ne fait que l'habiller. Les deux restent utilisables séparément : la ligne de commande continue de
fonctionner, et l'interface n'ajoute aucune dépendance.
"""

import os
import queue
import shutil
import subprocess
import sys
import threading
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import filedialog, messagebox

sys.path.insert(0, str(Path(__file__).resolve().parent))

import mclaunch as core
from ui import (Button, Field, NavItem, ProgressBar, ScrollFrame, Slider, T, Toggle,
                bind_all_children, font, make_icon, read_mod_info, round_rect)

WINDOW_W, WINDOW_H = 1060, 680


def center_on(window, parent):
    """Centre une fenêtre modale sur sa parente, légèrement au-dessus du milieu."""
    window.update_idletasks()
    x = parent.winfo_rootx() + (parent.winfo_width() - window.winfo_width()) // 2
    y = parent.winfo_rooty() + (parent.winfo_height() - window.winfo_height()) // 3
    window.geometry(f"+{max(0, x)}+{max(0, y)}")


class Launcher(tk.Tk):

    def __init__(self):
        super().__init__()
        self.title("Lanceur Minecraft")
        self.geometry(f"{WINDOW_W}x{WINDOW_H}")
        self.minsize(940, 600)
        self.configure(bg=T.BG_DEEP)

        self.config_root = Path(os.environ.get("MCLAUNCH_ROOT",
                                               Path.home() / f".{core.APP_NAME}"))
        self.cfg = core.Config(self.config_root)
        self.account = core.Account(self.config_root)

        self._queue = queue.Queue()
        self._busy = False
        self._icons = []          # références gardées : Tk libère les images non référencées
        self.pages = {}

        self._build()
        self.show_page("play")
        self.after(60, self._drain)

    # -- Fil d'exécution -------------------------------------------------------------------

    def post(self, fn):
        """Depuis un fil de travail : exécute `fn` sur le fil de l'interface."""
        self._queue.put(fn)

    def _drain(self):
        while True:
            try:
                self._queue.get_nowait()()
            except queue.Empty:
                break
            except Exception as e:
                print("erreur interface :", e)
        self.after(60, self._drain)

    def run_async(self, work, on_done=None, on_error=None):
        """
        Exécute une tâche longue hors du fil de l'interface.

        Sans ça, un téléchargement de 4000 fichiers fige la fenêtre et Windows affiche
        « ne répond pas » — le symptôme qui fait croire à un plantage alors que tout va bien.
        """
        if self._busy:
            return
        self._busy = True

        def runner():
            try:
                result = work()
                if on_done:
                    self.post(lambda: on_done(result))
            except Exception as e:
                message = str(e)
                if on_error:
                    self.post(lambda: on_error(message))
                else:
                    self.post(lambda: self.toast(message, error=True))
            finally:
                self.post(self._clear_busy)

        threading.Thread(target=runner, daemon=True).start()

    def _clear_busy(self):
        self._busy = False

    # -- Structure -------------------------------------------------------------------------

    def _build(self):
        self.sidebar = tk.Frame(self, bg=T.BG_SIDE, width=210)
        self.sidebar.pack(side="left", fill="y")
        self.sidebar.pack_propagate(False)
        self._build_sidebar()

        self.content = tk.Frame(self, bg=T.BG_DEEP)
        self.content.pack(side="left", fill="both", expand=True)

        for key, cls in (("play", PlayPage), ("mods", ModsPage),
                         ("settings", SettingsPage), ("account", AccountPage)):
            page = cls(self.content, self)
            self.pages[key] = page

        self.status = tk.Label(self, text="", bg=T.BG_DEEP, fg=T.TEXT_DIM, font=font(9))

    def _build_sidebar(self):
        header = tk.Frame(self.sidebar, bg=T.BG_SIDE)
        header.pack(fill="x", pady=(22, 26), padx=18)

        logo = tk.Canvas(header, width=34, height=34, bg=T.BG_SIDE, highlightthickness=0)
        logo.pack(side="left")
        round_rect(logo, 1, 1, 33, 33, 9, fill=T.ACCENT, outline="")
        logo.create_text(17, 17, text="M", fill="#FFFFFF", font=font(15, "bold"))

        titles = tk.Frame(header, bg=T.BG_SIDE)
        titles.pack(side="left", padx=(11, 0))
        tk.Label(titles, text="LANCEUR", bg=T.BG_SIDE, fg=T.TEXT,
                 font=font(11, "bold")).pack(anchor="w")
        tk.Label(titles, text=f"v{core.APP_VERSION}", bg=T.BG_SIDE, fg=T.TEXT_FAINT,
                 font=font(8)).pack(anchor="w")

        self.nav = {}
        for key, icon, label in (("play", "▶", "Jouer"),
                                 ("mods", "🧩", "Mods"),
                                 ("settings", "⚙", "Paramètres"),
                                 ("account", "👤", "Compte")):
            item = NavItem(self.sidebar, icon, label, lambda k=key: self.show_page(k))
            item.pack(fill="x", pady=1)
            self.nav[key] = item

        bottom = tk.Frame(self.sidebar, bg=T.BG_SIDE)
        bottom.pack(side="bottom", fill="x", padx=14, pady=14)

        self.account_chip = tk.Frame(bottom, bg=T.BG_PANEL, cursor="hand2")
        self.account_chip.pack(fill="x")
        inner = tk.Frame(self.account_chip, bg=T.BG_PANEL)
        inner.pack(fill="x", padx=10, pady=9)

        self.avatar = tk.Canvas(inner, width=26, height=26, bg=T.BG_PANEL, highlightthickness=0)
        self.avatar.pack(side="left")
        round_rect(self.avatar, 0, 0, 26, 26, 6, fill=T.BG_HOVER, outline="")
        self.avatar_letter = self.avatar.create_text(13, 13, text="?", fill=T.TEXT_DIM,
                                                     font=font(11, "bold"))

        texts = tk.Frame(inner, bg=T.BG_PANEL)
        texts.pack(side="left", padx=(9, 0), fill="x", expand=True)
        self.account_name = tk.Label(texts, text="Non connecté", bg=T.BG_PANEL, fg=T.TEXT,
                                     font=font(9, "bold"), anchor="w")
        self.account_name.pack(fill="x")
        self.account_state = tk.Label(texts, text="Se connecter", bg=T.BG_PANEL,
                                      fg=T.TEXT_FAINT, font=font(8), anchor="w")
        self.account_state.pack(fill="x")

        bind_all_children(self.account_chip, "<Button-1>",
                          lambda e: self.show_page("account"))

    def show_page(self, key):
        for page in self.pages.values():
            page.pack_forget()
        for name, item in self.nav.items():
            item.set_active(name == key)
        page = self.pages[key]
        page.pack(fill="both", expand=True)
        page.on_show()

    # -- État partagé ----------------------------------------------------------------------

    def refresh_account_chip(self):
        name = self.account.data.get("name")
        if name:
            self.account_name.configure(text=name)
            self.account_state.configure(
                text="Connecté" if self.account.valid else "Session à renouveler",
                fg=T.GREEN if self.account.valid else T.AMBER)
            self.avatar.itemconfig(self.avatar_letter, text=name[0].upper(), fill=T.TEXT)
        else:
            self.account_name.configure(text="Non connecté")
            self.account_state.configure(text="Se connecter", fg=T.TEXT_FAINT)
            self.avatar.itemconfig(self.avatar_letter, text="?", fill=T.TEXT_DIM)

    def toast(self, message, error=False):
        messagebox.showerror("Problème", message) if error \
            else messagebox.showinfo("Information", message)

    def gui_login_flow(self, client_id):
        """
        Version graphique du flux « code d'appareil » : affiche le code dans une fenêtre, ouvre
        le navigateur, et attend dans le fil courant (qui est déjà un fil de travail).
        """
        resp = core.device_code_request(client_id)
        cancelled = threading.Event()
        self.post(lambda: DeviceCodeDialog(self, resp, cancelled))
        try:
            return core.device_code_wait(client_id, resp,
                                         should_cancel=cancelled.is_set)
        finally:
            self.post(lambda: DeviceCodeDialog.close_current())


class DeviceCodeDialog(tk.Toplevel):
    """Fenêtre affichant le code à saisir chez Microsoft."""

    _current = None

    def __init__(self, app, resp, cancelled):
        super().__init__(app, bg=T.BG_PANEL)
        DeviceCodeDialog._current = self
        self.cancelled = cancelled
        self.title("Connexion Microsoft")
        self.configure(padx=32, pady=26)
        self.resizable(False, False)
        self.transient(app)
        self.protocol("WM_DELETE_WINDOW", self._cancel)

        url = resp["verification_uri"]
        code = resp["user_code"]

        tk.Label(self, text="Connexion à ton compte Microsoft", bg=T.BG_PANEL, fg=T.TEXT,
                 font=font(13, "bold")).pack()
        tk.Label(self, text="Ouvre la page ci-dessous et saisis ce code :", bg=T.BG_PANEL,
                 fg=T.TEXT_DIM, font=font(9)).pack(pady=(6, 18))

        box = tk.Canvas(self, width=300, height=62, bg=T.BG_PANEL, highlightthickness=0)
        box.pack()
        round_rect(box, 1, 1, 299, 61, 10, fill=T.BG_INPUT, outline=T.ACCENT_DIM)
        # Espacement des caractères : un code de 8 lettres se recopie beaucoup moins mal quand
        # les caractères sont détachés, surtout entre O et 0.
        box.create_text(150, 31, text="  ".join(code), fill=T.ACCENT, font=font(19, "bold"))

        row = tk.Frame(self, bg=T.BG_PANEL)
        row.pack(pady=(16, 0))
        Button(row, "Copier le code", command=lambda: self._copy(code),
               width=150, style="ghost", bg=T.BG_PANEL).pack(side="left", padx=4)
        Button(row, "Ouvrir la page", command=lambda: webbrowser.open(url),
               width=150, bg=T.BG_PANEL).pack(side="left", padx=4)

        tk.Label(self, text=url, bg=T.BG_PANEL, fg=T.TEXT_FAINT, font=font(8)).pack(pady=(14, 0))
        self.status = tk.Label(self, text="En attente de ta validation...", bg=T.BG_PANEL,
                               fg=T.TEXT_DIM, font=font(9))
        self.status.pack(pady=(10, 0))

        center_on(self, app)

    def _copy(self, code):
        self.clipboard_clear()
        self.clipboard_append(code)
        self.status.configure(text="Code copié.", fg=T.GREEN)

    def _cancel(self):
        self.cancelled.set()
        self.destroy()

    @classmethod
    def close_current(cls):
        if cls._current is not None:
            try:
                cls._current.destroy()
            except tk.TclError:
                pass
            cls._current = None


class Page(tk.Frame):
    def __init__(self, parent, app):
        super().__init__(parent, bg=T.BG_DEEP)
        self.app = app

    def header(self, title, subtitle):
        box = tk.Frame(self, bg=T.BG_DEEP)
        box.pack(fill="x", padx=34, pady=(28, 20))
        tk.Label(box, text=title, bg=T.BG_DEEP, fg=T.TEXT,
                 font=font(19, "bold"), anchor="w").pack(fill="x")
        tk.Label(box, text=subtitle, bg=T.BG_DEEP, fg=T.TEXT_DIM,
                 font=font(9), anchor="w").pack(fill="x", pady=(3, 0))
        return box

    def on_show(self):
        pass


# ----------------------------------------------------------------------------------------------
# Page « Jouer »
# ----------------------------------------------------------------------------------------------

class PlayPage(Page):

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self.selected = None
        self.version_rows = {}

        self.header("Jouer", "Choisis une version puis lance le jeu.")

        body = tk.Frame(self, bg=T.BG_DEEP)
        body.pack(fill="both", expand=True, padx=34)

        self.list_area = ScrollFrame(body, bg=T.BG_DEEP)
        self.list_area.pack(fill="both", expand=True)

        footer = tk.Frame(self, bg=T.BG_PANEL)
        footer.pack(fill="x", side="bottom")
        inner = tk.Frame(footer, bg=T.BG_PANEL)
        inner.pack(fill="x", padx=34, pady=18)

        left = tk.Frame(inner, bg=T.BG_PANEL)
        left.pack(side="left", fill="x", expand=True)
        self.status_label = tk.Label(left, text="", bg=T.BG_PANEL, fg=T.TEXT_DIM,
                                     font=font(9), anchor="w")
        self.status_label.pack(fill="x")
        self.progress = ProgressBar(left, width=330)
        self.progress.pack(anchor="w", pady=(8, 0))

        self.play_btn = Button(inner, "JOUER", command=self.play, width=180, height=52,
                               radius=10, bg=T.BG_PANEL)
        self.play_btn.pack(side="right")

        self.install_btn = Button(inner, "Installer", command=self.install_dialog,
                                  width=150, height=52, style="ghost", radius=10,
                                  icon="+", bg=T.BG_PANEL)
        self.install_btn.pack(side="right", padx=(0, 10))

    def on_show(self):
        self.refresh()

    def refresh(self):
        self.list_area.clear()
        self.version_rows.clear()
        versions = core.installed_versions(self.app.cfg.game_dir)

        if not versions:
            empty = tk.Frame(self.list_area.body, bg=T.BG_DEEP)
            empty.pack(fill="both", expand=True, pady=70)
            tk.Label(empty, text="Aucune version installée", bg=T.BG_DEEP, fg=T.TEXT,
                     font=font(13, "bold")).pack()
            tk.Label(empty, text="Clique sur « Installer une version » pour commencer.",
                     bg=T.BG_DEEP, fg=T.TEXT_DIM, font=font(9)).pack(pady=(6, 0))
            self.play_btn.set_enabled(False)
            return

        last = self.app.cfg.data.get("last_version")
        self.selected = last if last in versions else versions[-1]

        for version in versions:
            self.version_rows[version] = VersionRow(
                self.list_area.body, version, self.selected == version,
                lambda v=version: self.select(v))
            self.version_rows[version].pack(fill="x", pady=4)

        self.play_btn.set_enabled(True)
        self.status_label.configure(text=f"Prêt : {core.playable_name(self.selected)}")

    def select(self, version):
        self.selected = version
        for name, row in self.version_rows.items():
            row.set_selected(name == version)
        self.status_label.configure(text=f"Prêt : {core.playable_name(version)}")

    def install_dialog(self):
        InstallDialog(self.app, self)

    def play(self):
        if not self.selected:
            return
        if not self.app.cfg.client_id:
            self.app.toast("Configure d'abord ton identifiant Azure dans les Paramètres.",
                           error=True)
            self.app.show_page("settings")
            return

        self.app.cfg.data["last_version"] = self.selected
        self.app.cfg.save()
        self.play_btn.set_enabled(False)
        self.progress.start_pulse()
        self.status_label.configure(text="Connexion au compte...")

        version = self.selected

        def work():
            core.ensure_logged_in(self.app.cfg, self.app.account,
                                  login_flow=self.app.gui_login_flow)
            self.app.post(lambda: self.status_label.configure(text="Démarrage du jeu..."))

            game_dir = self.app.cfg.game_dir
            path = game_dir / "versions" / version / f"{version}.json"
            import json as _json
            version_json = core.merged_version(
                game_dir, _json.loads(path.read_text(encoding="utf-8")))
            command = core.build_command(self.app.cfg, self.app.account, game_dir, version_json)
            game_dir.mkdir(parents=True, exist_ok=True)
            return subprocess.Popen(command, cwd=str(game_dir))

        def done(_process):
            self.progress.stop_pulse()
            self.progress.set(1.0)
            self.play_btn.set_enabled(True)
            self.status_label.configure(text="Jeu lancé.", fg=T.GREEN)
            self.app.refresh_account_chip()

        def failed(message):
            self.progress.stop_pulse()
            self.progress.set(0)
            self.play_btn.set_enabled(True)
            self.status_label.configure(text="Échec du lancement", fg=T.RED)
            self.app.toast(message, error=True)

        self.app.run_async(work, done, failed)


class VersionRow(tk.Frame):
    """Ligne sélectionnable d'une version installée."""

    def __init__(self, parent, version_id, selected, command):
        super().__init__(parent, bg=T.BG_CARD, cursor="hand2")
        self.command = command
        self.selected = selected

        self.bar = tk.Frame(self, bg=T.BG_CARD, width=3)
        self.bar.pack(side="left", fill="y")

        inner = tk.Frame(self, bg=T.BG_CARD)
        inner.pack(side="left", fill="both", expand=True, padx=16, pady=13)

        is_fabric = version_id.startswith("fabric-loader")
        badge = tk.Canvas(inner, width=38, height=38, bg=T.BG_CARD, highlightthickness=0)
        badge.pack(side="left")
        round_rect(badge, 0, 0, 38, 38, 8,
                   fill=T.ACCENT_DIM if is_fabric else T.BG_HOVER, outline="")
        badge.create_text(19, 19, text="F" if is_fabric else "MC",
                          fill=T.TEXT, font=font(11, "bold"))

        texts = tk.Frame(inner, bg=T.BG_CARD)
        texts.pack(side="left", padx=(14, 0), fill="x", expand=True)
        self.title = tk.Label(texts, text=core.playable_name(version_id), bg=T.BG_CARD,
                              fg=T.TEXT, font=font(11, "bold"), anchor="w")
        self.title.pack(fill="x")
        tk.Label(texts, text="Fabric — compatible avec les mods" if is_fabric else "Version vanilla",
                 bg=T.BG_CARD, fg=T.TEXT_DIM, font=font(8), anchor="w").pack(fill="x")

        self.check = tk.Label(inner, text="●" if selected else "", bg=T.BG_CARD,
                              fg=T.ACCENT, font=font(13))
        self.check.pack(side="right")

        bind_all_children(self, "<Button-1>", lambda e: self.command())
        bind_all_children(self, "<Enter>", lambda e: self._paint(True))
        bind_all_children(self, "<Leave>", lambda e: self._paint(False))
        self._paint(False)

    def _paint(self, hover):
        bg = T.BG_HOVER if (hover or self.selected) else T.BG_CARD
        for widget in self._all():
            if widget is not self.bar:
                try:
                    widget.configure(bg=bg)
                except tk.TclError:
                    pass
        self.bar.configure(bg=T.ACCENT if self.selected else bg)

    def _all(self):
        out = []
        def walk(w):
            out.append(w)
            for c in w.winfo_children():
                walk(c)
        walk(self)
        return out

    def set_selected(self, selected):
        self.selected = selected
        self.check.configure(text="●" if selected else "")
        self._paint(False)


class InstallDialog(tk.Toplevel):
    """Choix de la version à installer, avec suivi de progression."""

    def __init__(self, app, play_page):
        super().__init__(app, bg=T.BG_PANEL)
        self.app = app
        self.play_page = play_page
        self.title("Installer une version")
        self.configure(padx=30, pady=24)
        self.resizable(False, False)
        self.transient(app)

        tk.Label(self, text="Installer une version", bg=T.BG_PANEL, fg=T.TEXT,
                 font=font(13, "bold")).pack(anchor="w")
        tk.Label(self, text="Le jeu, ses ressources et Fabric seront téléchargés.",
                 bg=T.BG_PANEL, fg=T.TEXT_DIM, font=font(9)).pack(anchor="w", pady=(4, 16))

        self.version_field = Field(self, "VERSION", value="",
                                   hint="Numéro exact, par exemple 26.2. « latest » prend la "
                                        "dernière version stable.")
        self.version_field.pack(fill="x")

        self.fabric_row = tk.Frame(self, bg=T.BG_PANEL)
        self.fabric_row.pack(fill="x", pady=(16, 0))
        self.fabric_toggle = Toggle(self.fabric_row, True, bg=T.BG_PANEL)
        self.fabric_toggle.pack(side="left")
        tk.Label(self.fabric_row, text="Installer Fabric (nécessaire pour les mods)",
                 bg=T.BG_PANEL, fg=T.TEXT, font=font(9)).pack(side="left", padx=(10, 0))

        self.status = tk.Label(self, text="", bg=T.BG_PANEL, fg=T.TEXT_DIM,
                               font=font(9), anchor="w")
        self.status.pack(fill="x", pady=(18, 6))
        self.progress = ProgressBar(self, width=400)
        self.progress.pack(anchor="w")

        row = tk.Frame(self, bg=T.BG_PANEL)
        row.pack(fill="x", pady=(18, 0))
        self.start_btn = Button(row, "Installer", command=self.start, width=130, bg=T.BG_PANEL)
        self.start_btn.pack(side="right")
        Button(row, "Annuler", command=self.destroy, width=110, style="ghost",
               bg=T.BG_PANEL).pack(side="right", padx=(0, 8))

        center_on(self, app)
        self.app.run_async(core.fetch_manifest, self._fill_latest)

    def _fill_latest(self, manifest):
        latest = manifest["latest"]["release"]
        self.version_field.set(latest)
        self.status.configure(text=f"Dernière version stable : {latest}")

    def start(self):
        version = self.version_field.get()
        if not version:
            return
        with_fabric = self.fabric_toggle.value
        self.start_btn.set_enabled(False)

        def report(fraction, label):
            self.app.post(lambda: (self.progress.set(fraction),
                                   self.status.configure(text=label)))

        def work():
            game_dir = self.app.cfg.game_dir
            vid, version_json = core.resolve_version_json(game_dir, version)
            core.install_version(game_dir, version_json, progress=False, on_progress=report)
            if with_fabric:
                report(0.97, "Installation de Fabric")
                name, profile = core.install_fabric(game_dir, vid)
                merged = core.merged_version(game_dir, profile)
                core.install_version(game_dir, merged, progress=False)
                (game_dir / "mods").mkdir(exist_ok=True)
                return name
            return vid

        def done(name):
            self.progress.set(1.0)
            self.status.configure(text="Installation terminée.", fg=T.GREEN)
            self.app.cfg.data["last_version"] = name
            self.app.cfg.save()
            self.play_page.refresh()
            self.after(900, self.destroy)

        def failed(message):
            self.start_btn.set_enabled(True)
            self.status.configure(text="Échec", fg=T.RED)
            self.app.toast(message, error=True)

        self.app.run_async(work, done, failed)


# ----------------------------------------------------------------------------------------------
# Page « Mods »
# ----------------------------------------------------------------------------------------------

class ModsPage(Page):

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self.header("Mods", "Ajoute tes propres mods, active-les ou désactive-les.")

        actions = tk.Frame(self, bg=T.BG_DEEP)
        actions.pack(fill="x", padx=34, pady=(0, 14))
        Button(actions, "Ajouter un mod", command=self.add_mods, width=160,
               icon="+", bg=T.BG_DEEP).pack(side="left")
        Button(actions, "Pack performance", command=self.install_perf_pack, width=170,
               style="ghost", bg=T.BG_DEEP).pack(side="left", padx=8)
        Button(actions, "Ouvrir le dossier", command=self.open_folder, width=150,
               style="ghost", bg=T.BG_DEEP).pack(side="left")

        self.count_label = tk.Label(self, text="", bg=T.BG_DEEP, fg=T.TEXT_DIM,
                                    font=font(9), anchor="w")
        self.count_label.pack(fill="x", padx=34, pady=(0, 8))

        self.list_area = ScrollFrame(self, bg=T.BG_DEEP)
        self.list_area.pack(fill="both", expand=True, padx=34, pady=(0, 20))

    @property
    def mods_dir(self):
        return self.app.cfg.game_dir / "mods"

    def on_show(self):
        self.refresh()

    def refresh(self):
        self.list_area.clear()
        self.app._icons.clear()

        if not self.mods_dir.is_dir():
            self._empty("Aucun dossier de mods",
                        "Installe d'abord une version avec Fabric.")
            self.count_label.configure(text="")
            return

        jars = sorted(list(self.mods_dir.glob("*.jar"))
                      + list(self.mods_dir.glob("*.jar.disabled")),
                      key=lambda p: p.name.lower())
        if not jars:
            self._empty("Aucun mod installé",
                        "Clique sur « Ajouter un mod », ou installe le pack performance.")
            self.count_label.configure(text="")
            return

        active = sum(1 for j in jars if j.suffix == ".jar")
        self.count_label.configure(text=f"{len(jars)} mods — {active} actifs")

        for jar in jars:
            ModRow(self.list_area.body, self.app, jar, self.refresh).pack(fill="x", pady=3)

    def _empty(self, title, hint):
        box = tk.Frame(self.list_area.body, bg=T.BG_DEEP)
        box.pack(fill="both", expand=True, pady=60)
        tk.Label(box, text=title, bg=T.BG_DEEP, fg=T.TEXT, font=font(13, "bold")).pack()
        tk.Label(box, text=hint, bg=T.BG_DEEP, fg=T.TEXT_DIM, font=font(9)).pack(pady=(6, 0))

    def add_mods(self):
        if not self.mods_dir.is_dir():
            self.app.toast("Installe d'abord une version avec Fabric.", error=True)
            return
        paths = filedialog.askopenfilenames(
            title="Choisir un ou plusieurs mods",
            filetypes=[("Mods Minecraft", "*.jar"), ("Tous les fichiers", "*.*")])
        for path in paths:
            source = Path(path)
            try:
                shutil.copy2(source, self.mods_dir / source.name)
            except Exception as e:
                self.app.toast(f"Impossible de copier {source.name} : {e}", error=True)
        if paths:
            self.refresh()

    def open_folder(self):
        if not self.mods_dir.is_dir():
            self.app.toast("Installe d'abord une version avec Fabric.", error=True)
            return
        if sys.platform == "win32":
            os.startfile(self.mods_dir)
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(self.mods_dir)])
        else:
            subprocess.Popen(["xdg-open", str(self.mods_dir)])

    def install_perf_pack(self):
        """Télécharge les mods de performance via le script voisin resolve_mods.py."""
        if not self.mods_dir.is_dir():
            self.app.toast("Installe d'abord une version avec Fabric.", error=True)
            return

        version = self.app.cfg.data.get("last_version", "")
        mc = version.split("-")[-1] if version else ""
        if not mc:
            self.app.toast("Installe d'abord une version.", error=True)
            return

        PerfPackDialog(self.app, self, mc)


class ModRow(tk.Frame):
    """Une ligne de mod : icône, nom, version, interrupteur, suppression."""

    def __init__(self, parent, app, jar_path, on_change):
        super().__init__(parent, bg=T.BG_CARD)
        self.app = app
        self.jar = jar_path
        self.on_change = on_change
        enabled = jar_path.suffix == ".jar"

        inner = tk.Frame(self, bg=T.BG_CARD)
        inner.pack(fill="x", padx=16, pady=11)

        info = read_mod_info(jar_path)

        holder = tk.Frame(inner, bg=T.BG_CARD, width=36, height=36)
        holder.pack(side="left")
        holder.pack_propagate(False)
        icon_image = make_icon(info["icon"]) if info["icon"] else None
        if icon_image:
            app._icons.append(icon_image)   # sinon Tk libère l'image et rien ne s'affiche
            tk.Label(holder, image=icon_image, bg=T.BG_CARD).pack(expand=True)
        else:
            placeholder = tk.Canvas(holder, width=36, height=36, bg=T.BG_CARD,
                                    highlightthickness=0)
            placeholder.pack()
            round_rect(placeholder, 0, 0, 36, 36, 8, fill=T.BG_HOVER, outline="")
            placeholder.create_text(18, 18, text=info["name"][:1].upper(),
                                    fill=T.TEXT_DIM, font=font(12, "bold"))

        texts = tk.Frame(inner, bg=T.BG_CARD)
        texts.pack(side="left", padx=(14, 10), fill="x", expand=True)

        title_row = tk.Frame(texts, bg=T.BG_CARD)
        title_row.pack(fill="x")
        tk.Label(title_row, text=info["name"], bg=T.BG_CARD,
                 fg=T.TEXT if enabled else T.TEXT_FAINT,
                 font=font(10, "bold"), anchor="w").pack(side="left")
        if info["version"]:
            tk.Label(title_row, text=info["version"], bg=T.BG_CARD, fg=T.TEXT_FAINT,
                     font=font(8)).pack(side="left", padx=(8, 0))

        description = info["description"] or jar_path.name
        if len(description) > 96:
            description = description[:93] + "..."
        tk.Label(texts, text=description, bg=T.BG_CARD, fg=T.TEXT_DIM,
                 font=font(8), anchor="w").pack(fill="x")

        Button(inner, "✕", command=self.delete, width=34, height=30, style="ghost",
               radius=7, bg=T.BG_CARD).pack(side="right", padx=(10, 0))
        Toggle(inner, enabled, command=self.toggle, bg=T.BG_CARD).pack(side="right")

    def toggle(self, value):
        """
        Activation par renommage en .jar.disabled : c'est la convention que Fabric comprend
        nativement, et elle préserve le fichier — l'utilisateur peut réactiver sans retélécharger.
        """
        try:
            if value:
                self.jar.rename(self.jar.with_suffix(""))     # retire « .disabled »
            else:
                self.jar.rename(self.jar.with_suffix(self.jar.suffix + ".disabled"))
        except OSError as e:
            self.app.toast(f"Renommage impossible : {e}", error=True)
        self.on_change()

    def delete(self):
        if messagebox.askyesno("Supprimer", f"Supprimer {self.jar.name} ?"):
            try:
                self.jar.unlink()
            except OSError as e:
                self.app.toast(f"Suppression impossible : {e}", error=True)
            self.on_change()


class PerfPackDialog(tk.Toplevel):
    """Téléchargement du pack de mods de performance depuis Modrinth."""

    MODS = ["fabric-api", "sodium", "lithium", "ferrite-core", "immediatelyfast",
            "entityculling", "moreculling", "sodium-extra", "krypton", "dynamic-fps",
            "lmd", "language-reload", "badoptimizations", "fastquit", "debugify"]

    def __init__(self, app, mods_page, mc_version):
        super().__init__(app, bg=T.BG_PANEL)
        self.app = app
        self.mods_page = mods_page
        self.mc = mc_version
        self.title("Pack performance")
        self.configure(padx=30, pady=24)
        self.resizable(False, False)
        self.transient(app)

        tk.Label(self, text="Pack performance", bg=T.BG_PANEL, fg=T.TEXT,
                 font=font(13, "bold")).pack(anchor="w")
        tk.Label(self, text=f"Les mods qui font gagner des FPS, pour Minecraft {mc_version}.\n"
                            "Sodium, Lithium, FerriteCore et une dizaine d'autres.",
                 bg=T.BG_PANEL, fg=T.TEXT_DIM, font=font(9),
                 justify="left").pack(anchor="w", pady=(4, 18))

        self.status = tk.Label(self, text="", bg=T.BG_PANEL, fg=T.TEXT_DIM,
                               font=font(9), anchor="w", width=52)
        self.status.pack(fill="x", pady=(0, 6))
        self.progress = ProgressBar(self, width=400)
        self.progress.pack(anchor="w")

        row = tk.Frame(self, bg=T.BG_PANEL)
        row.pack(fill="x", pady=(18, 0))
        self.start_btn = Button(row, "Télécharger", command=self.start, width=140, bg=T.BG_PANEL)
        self.start_btn.pack(side="right")
        Button(row, "Fermer", command=self.destroy, width=110, style="ghost",
               bg=T.BG_PANEL).pack(side="right", padx=(0, 8))

    def start(self):
        self.start_btn.set_enabled(False)
        target = self.mods_page.mods_dir

        def work():
            import json as _json
            import urllib.parse
            installed, missing = [], []
            for i, slug in enumerate(self.MODS, 1):
                self.app.post(lambda s=slug, i=i: (
                    self.progress.set(i / len(self.MODS)),
                    self.status.configure(text=f"{s} ({i}/{len(self.MODS)})")))
                query = urllib.parse.urlencode({
                    "game_versions": _json.dumps([self.mc]),
                    "loaders": _json.dumps(["fabric"]),
                })
                status, versions = core.http_json(
                    f"https://api.modrinth.com/v2/project/{slug}/version?{query}")
                if status != 200 or not versions:
                    missing.append(slug)
                    continue
                stable = [v for v in versions if v.get("version_type") == "release"] or versions
                best = stable[0]
                file = next((f for f in best["files"] if f.get("primary")), best["files"][0])
                core.download(file["url"], target / file["filename"])
                installed.append(slug)
            return installed, missing

        def done(result):
            installed, missing = result
            self.progress.set(1.0)
            text = f"{len(installed)} mods installés."
            if missing:
                text += f" Pas encore compatibles : {', '.join(missing)}."
            self.status.configure(text=text, fg=T.GREEN)
            self.mods_page.refresh()

        def failed(message):
            self.start_btn.set_enabled(True)
            self.status.configure(text=f"Échec : {message}", fg=T.RED)

        self.app.run_async(work, done, failed)


# ----------------------------------------------------------------------------------------------
# Page « Paramètres »
# ----------------------------------------------------------------------------------------------

class SettingsPage(Page):

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self.header("Paramètres", "Mémoire, dossier de jeu et identifiant Microsoft.")

        area = ScrollFrame(self, bg=T.BG_DEEP)
        area.pack(fill="both", expand=True, padx=34, pady=(0, 16))
        body = area.body

        panel = tk.Frame(body, bg=T.BG_PANEL)
        panel.pack(fill="x", pady=(0, 14))
        inner = tk.Frame(panel, bg=T.BG_PANEL)
        inner.pack(fill="x", padx=22, pady=18)

        tk.Label(inner, text="MÉMOIRE ALLOUÉE", bg=T.BG_PANEL, fg=T.TEXT_DIM,
                 font=font(9, "bold"), anchor="w").pack(fill="x")

        self.memory_value = tk.Label(inner, text="", bg=T.BG_PANEL, fg=T.ACCENT,
                                     font=font(15, "bold"), anchor="w")
        self.memory_value.pack(fill="x", pady=(6, 2))

        self.memory = Slider(inner, 2048, 12288, 512, 4096,
                             command=self._on_memory, width=560, bg=T.BG_PANEL)
        self.memory.pack(anchor="w")
        tk.Label(inner, text="4 à 6 Go suffisent largement. Au-delà, les pauses du ramasse-miettes "
                             "s'allongent et créent des micro-saccades — allouer 16 Go dégrade le "
                             "jeu au lieu de l'améliorer.",
                 bg=T.BG_PANEL, fg=T.TEXT_FAINT, font=font(8), anchor="w",
                 justify="left", wraplength=560).pack(fill="x", pady=(6, 0))

        panel2 = tk.Frame(body, bg=T.BG_PANEL)
        panel2.pack(fill="x", pady=(0, 14))
        inner2 = tk.Frame(panel2, bg=T.BG_PANEL)
        inner2.pack(fill="x", padx=22, pady=20)

        self.client_id = Field(
            inner2, "IDENTIFIANT D'APPLICATION AZURE",
            hint="Obligatoire pour la connexion Microsoft. Crée une application gratuite sur "
                 "portal.azure.com, active « Autoriser les flux clients publics », puis colle "
                 "ici l'ID d'application (client). Voir README.md.")
        self.client_id.pack(fill="x")

        self.game_dir = Field(inner2, "DOSSIER DE JEU")
        self.game_dir.pack(fill="x", pady=(18, 0))

        self.java = Field(inner2, "CHEMIN DE JAVA",
                          hint="Laisse vide pour la détection automatique. "
                               "Minecraft 26.2 exige Java 25.")
        self.java.pack(fill="x", pady=(18, 0))

        row = tk.Frame(body, bg=T.BG_DEEP)
        row.pack(fill="x", pady=(4, 34))
        Button(row, "Enregistrer", command=self.save, width=140, bg=T.BG_DEEP).pack(side="left")
        self.saved = tk.Label(row, text="", bg=T.BG_DEEP, fg=T.GREEN, font=font(9))
        self.saved.pack(side="left", padx=14)

    def _on_memory(self, value):
        self.memory_value.configure(text=f"{int(value) / 1024:.1f} Go")

    def on_show(self):
        cfg = self.app.cfg
        self.memory.set(cfg.memory_mb)
        self._on_memory(cfg.memory_mb)
        self.client_id.set(cfg.client_id)
        self.game_dir.set(str(cfg.game_dir))
        self.java.set(cfg.java)
        self.saved.configure(text="")

    def save(self):
        cfg = self.app.cfg
        cfg.data["memory_mb"] = int(self.memory.get())
        cfg.data["azure_client_id"] = self.client_id.get()
        cfg.data["game_dir"] = self.game_dir.get()
        cfg.data["java"] = self.java.get()
        cfg.save()
        self.saved.configure(text="Enregistré.")
        self.after(2200, lambda: self.saved.configure(text=""))


# ----------------------------------------------------------------------------------------------
# Page « Compte »
# ----------------------------------------------------------------------------------------------

class AccountPage(Page):

    def __init__(self, parent, app):
        super().__init__(parent, app)
        self.header("Compte", "Connexion à ton compte Microsoft.")

        self.panel = tk.Frame(self, bg=T.BG_PANEL)
        self.panel.pack(fill="x", padx=34)
        self.inner = tk.Frame(self.panel, bg=T.BG_PANEL)
        self.inner.pack(fill="x", padx=26, pady=26)

        note = tk.Frame(self, bg=T.BG_DEEP)
        note.pack(fill="x", padx=34, pady=20)
        tk.Label(note, text="Pourquoi la connexion est indispensable", bg=T.BG_DEEP,
                 fg=T.TEXT, font=font(10, "bold"), anchor="w").pack(fill="x")
        tk.Label(note,
                 text="Quand tu rejoins un serveur, celui-ci demande à Mojang de confirmer ton "
                      "identité. Sans jeton valide, il refuse avec « Failed to verify username ». "
                      "Ce n'est jamais le lanceur qui est rejeté, c'est la session.",
                 bg=T.BG_DEEP, fg=T.TEXT_DIM, font=font(9), anchor="w",
                 justify="left", wraplength=680).pack(fill="x", pady=(6, 0))

    def on_show(self):
        for child in self.inner.winfo_children():
            child.destroy()

        account = self.app.account
        name = account.data.get("name")

        if name:
            top = tk.Frame(self.inner, bg=T.BG_PANEL)
            top.pack(fill="x")
            avatar = tk.Canvas(top, width=54, height=54, bg=T.BG_PANEL, highlightthickness=0)
            avatar.pack(side="left")
            round_rect(avatar, 0, 0, 54, 54, 12, fill=T.ACCENT, outline="")
            avatar.create_text(27, 27, text=name[0].upper(), fill="#FFFFFF", font=font(20, "bold"))

            texts = tk.Frame(top, bg=T.BG_PANEL)
            texts.pack(side="left", padx=(16, 0))
            tk.Label(texts, text=name, bg=T.BG_PANEL, fg=T.TEXT,
                     font=font(15, "bold"), anchor="w").pack(fill="x")
            state = "Session active" if account.valid else "Session expirée, renouvelée au lancement"
            tk.Label(texts, text=state, bg=T.BG_PANEL,
                     fg=T.GREEN if account.valid else T.AMBER,
                     font=font(9), anchor="w").pack(fill="x", pady=(2, 0))
            tk.Label(texts, text=account.data.get("uuid", ""), bg=T.BG_PANEL, fg=T.TEXT_FAINT,
                     font=font(8), anchor="w").pack(fill="x", pady=(4, 0))

            Button(self.inner, "Se déconnecter", command=self.logout, width=150,
                   style="danger", bg=T.BG_PANEL).pack(anchor="w", pady=(22, 0))
        else:
            tk.Label(self.inner, text="Aucun compte connecté", bg=T.BG_PANEL, fg=T.TEXT,
                     font=font(13, "bold"), anchor="w").pack(fill="x")
            tk.Label(self.inner,
                     text="Un code s'affichera, à saisir sur la page Microsoft.",
                     bg=T.BG_PANEL, fg=T.TEXT_DIM, font=font(9), anchor="w").pack(fill="x",
                                                                                 pady=(6, 18))
            self.login_btn = Button(self.inner, "Se connecter avec Microsoft",
                                    command=self.login, width=250, height=44, bg=T.BG_PANEL)
            self.login_btn.pack(anchor="w")

        self.app.refresh_account_chip()

    def login(self):
        if not self.app.cfg.client_id:
            self.app.toast("Renseigne d'abord ton identifiant Azure dans les Paramètres.",
                           error=True)
            self.app.show_page("settings")
            return
        self.login_btn.set_enabled(False)

        def work():
            core.ensure_logged_in(self.app.cfg, self.app.account,
                                  login_flow=self.app.gui_login_flow)

        def done(_):
            self.on_show()
            self.app.refresh_account_chip()

        def failed(message):
            try:
                self.login_btn.set_enabled(True)
            except tk.TclError:
                pass
            self.app.toast(message, error=True)

        self.app.run_async(work, done, failed)

    def logout(self):
        self.app.account.clear()
        self.on_show()
        self.app.refresh_account_chip()


def main():
    app = Launcher()
    app.refresh_account_chip()
    app.mainloop()


if __name__ == "__main__":
    main()
