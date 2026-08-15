"""
Pont vers Spotify.

Trois façons de parler à Spotify, de la moins exigeante à la plus capable :

1. **Titre de fenêtre + touches multimédia** (Windows, ctypes, aucun compte requis)
   Spotify écrit « Artiste - Titre » dans le titre de sa fenêtre, et répond aux touches
   multimédia globales du clavier. Ça donne la lecture en cours, pause, suivant, précédent —
   y compris avec un compte **gratuit**.

2. **MPRIS** (Linux, via dbus) — l'équivalent, pour pouvoir développer et tester ailleurs.

3. **API Web Spotify** (tous systèmes, compte **Premium obligatoire**)
   Seule voie pour lister et changer de playlist, choisir un morceau précis, ou récupérer la
   pochette. Spotify réserve le contrôle de lecture aux comptes Premium : ce n'est pas une limite
   de ce code, c'est leur règle et elle renvoie une erreur 403 sur un compte gratuit.

Le lanceur choisit automatiquement le meilleur backend disponible.
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request

UA = "poclauncher-spotify/1.0"


class Track:
    """Ce qui joue actuellement. Champs vides plutôt que None : simplifie l'affichage."""

    def __init__(self, artist="", title="", album="", art_url="", playing=False,
                 progress_ms=0, duration_ms=0):
        self.artist = artist
        self.title = title
        self.album = album
        self.art_url = art_url
        self.playing = playing
        self.progress_ms = progress_ms
        self.duration_ms = duration_ms

    def as_dict(self):
        return {
            "artist": self.artist, "title": self.title, "album": self.album,
            "art_url": self.art_url, "playing": self.playing,
            "progress_ms": self.progress_ms, "duration_ms": self.duration_ms,
        }

    def __bool__(self):
        return bool(self.title or self.artist)


# ----------------------------------------------------------------------------------------------
# Backend Windows : titre de fenêtre + touches multimédia
# ----------------------------------------------------------------------------------------------

class WindowsBackend:
    """
    Ne demande aucun compte, aucune autorisation, aucune bibliothèque : uniquement ctypes, qui
    fait partie de Python. C'est le backend qui marche pour tout le monde.
    """

    # Codes des touches multimédia. Windows les diffuse à l'application qui a le focus média,
    # c'est-à-dire Spotify dès qu'il joue — même si la fenêtre est réduite.
    VK_MEDIA_NEXT = 0xB0
    VK_MEDIA_PREV = 0xB1
    VK_MEDIA_PLAY_PAUSE = 0xB3
    KEYEVENTF_KEYUP = 0x0002

    name = "windows"

    def __init__(self):
        import ctypes
        self.ctypes = ctypes
        self.user32 = ctypes.windll.user32

    @staticmethod
    def available():
        return sys.platform == "win32"

    def _spotify_window_title(self):
        """Parcourt les fenêtres et renvoie le titre de celle de Spotify."""
        import ctypes
        from ctypes import wintypes

        found = []

        @ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)
        def callback(hwnd, _):
            length = self.user32.GetWindowTextLengthW(hwnd)
            if length == 0:
                return True
            buffer = ctypes.create_unicode_buffer(length + 1)
            self.user32.GetWindowTextW(hwnd, buffer, length + 1)

            cls = ctypes.create_unicode_buffer(256)
            self.user32.GetClassNameW(hwnd, cls, 256)
            # Spotify utilise cette classe de fenêtre ; s'y fier évite de confondre avec un
            # onglet de navigateur qui aurait « Spotify » dans son titre.
            if "Chrome_WidgetWin" in cls.value or cls.value == "SpotifyMainWindow":
                if buffer.value:
                    found.append(buffer.value)
            return True

        self.user32.EnumWindows(callback, 0)

        for title in found:
            if " - " in title or title.startswith("Spotify"):
                return title
        return ""

    def is_running(self):
        try:
            out = subprocess.run(
                ["tasklist", "/FI", "IMAGENAME eq Spotify.exe", "/NH"],
                capture_output=True, text=True, timeout=8,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            return "Spotify.exe" in out.stdout
        except Exception:
            return False

    def now_playing(self):
        title = self._spotify_window_title()
        if not title:
            return Track()
        # « Spotify » ou « Spotify Premium » seul = rien en lecture (ou en pause).
        if title.strip() in ("Spotify", "Spotify Premium", "Spotify Free"):
            return Track(playing=False)
        if " - " in title:
            artist, song = title.split(" - ", 1)
            return Track(artist=artist.strip(), title=song.strip(), playing=True)
        return Track(title=title.strip(), playing=True)

    def _tap(self, vk):
        self.user32.keybd_event(vk, 0, 0, 0)
        self.user32.keybd_event(vk, 0, self.KEYEVENTF_KEYUP, 0)
        return True

    def play_pause(self):
        return self._tap(self.VK_MEDIA_PLAY_PAUSE)

    def next_track(self):
        return self._tap(self.VK_MEDIA_NEXT)

    def previous_track(self):
        return self._tap(self.VK_MEDIA_PREV)

    def playlists(self):
        return []          # hors de portée sans l'API Web

    def play_playlist(self, uri):
        raise NotImplementedError("changer de playlist demande l'API Web et un compte Premium")


# ----------------------------------------------------------------------------------------------
# Backend Linux : MPRIS
# ----------------------------------------------------------------------------------------------

class MprisBackend:
    """Équivalent Linux, via l'interface MPRIS standard. Permet de développer hors Windows."""

    name = "mpris"
    SERVICE = "org.mpris.MediaPlayer2.spotify"

    @staticmethod
    def available():
        if sys.platform == "win32":
            return False
        from shutil import which
        return which("dbus-send") is not None

    def _call(self, method, path="/org/mpris/MediaPlayer2"):
        try:
            out = subprocess.run(
                ["dbus-send", "--print-reply", f"--dest={self.SERVICE}", path, method],
                capture_output=True, text=True, timeout=6)
            return out.stdout if out.returncode == 0 else ""
        except Exception:
            return ""

    def is_running(self):
        return bool(self._call("org.freedesktop.DBus.Peer.Ping"))

    def now_playing(self):
        out = subprocess.run(
            ["dbus-send", "--print-reply", f"--dest={self.SERVICE}",
             "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties.Get",
             "string:org.mpris.MediaPlayer2.Player", "string:Metadata"],
            capture_output=True, text=True, timeout=6).stdout

        def field(key):
            match = re.search(rf'"{key}"\s*variant\s+(?:array \[\s*)?string "([^"]*)"', out)
            return match.group(1) if match else ""

        status = subprocess.run(
            ["dbus-send", "--print-reply", f"--dest={self.SERVICE}",
             "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties.Get",
             "string:org.mpris.MediaPlayer2.Player", "string:PlaybackStatus"],
            capture_output=True, text=True, timeout=6).stdout

        return Track(artist=field("xesam:artist"), title=field("xesam:title"),
                     album=field("xesam:album"), art_url=field("mpris:artUrl"),
                     playing="Playing" in status)

    def play_pause(self):
        return bool(self._call("org.mpris.MediaPlayer2.Player.PlayPause"))

    def next_track(self):
        return bool(self._call("org.mpris.MediaPlayer2.Player.Next"))

    def previous_track(self):
        return bool(self._call("org.mpris.MediaPlayer2.Player.Previous"))

    def playlists(self):
        return []

    def play_playlist(self, uri):
        raise NotImplementedError("changer de playlist demande l'API Web et un compte Premium")


# ----------------------------------------------------------------------------------------------
# Backend API Web (Premium)
# ----------------------------------------------------------------------------------------------

class WebApiBackend:
    """
    Contrôle complet : playlists, pochettes, choix du morceau, volume.

    Exige un compte **Premium**. Spotify renvoie 403 « Player command failed: Premium required »
    sur un compte gratuit — c'est leur politique, pas une limite de ce code. La lecture de l'état
    (ce qui joue) fonctionne en revanche avec un compte gratuit.

    Authentification par code d'autorisation avec PKCE : pas de secret client à stocker, ce qui
    convient à une application installée chez l'utilisateur.
    """

    name = "web"
    API = "https://api.spotify.com/v1"
    AUTH = "https://accounts.spotify.com/authorize"
    TOKEN = "https://accounts.spotify.com/api/token"
    SCOPES = ("user-read-playback-state user-modify-playback-state "
              "user-read-currently-playing playlist-read-private playlist-read-collaborative")

    def __init__(self, client_id, token_store):
        self.client_id = client_id
        self.store = token_store
        self.data = {}
        if token_store.exists():
            self.data = json.loads(token_store.read_text(encoding="utf-8"))

    @staticmethod
    def available():
        return True

    def _save(self):
        self.store.parent.mkdir(parents=True, exist_ok=True)
        self.store.write_text(json.dumps(self.data, indent=2), encoding="utf-8")
        try:
            os.chmod(self.store, 0o600)
        except OSError:
            pass

    # -- Authentification ------------------------------------------------------------------

    @staticmethod
    def make_verifier():
        import base64
        import secrets
        return base64.urlsafe_b64encode(secrets.token_bytes(48)).decode().rstrip("=")

    @staticmethod
    def challenge_for(verifier):
        import base64
        import hashlib
        digest = hashlib.sha256(verifier.encode()).digest()
        return base64.urlsafe_b64encode(digest).decode().rstrip("=")

    def authorize_url(self, verifier, redirect_uri):
        params = urllib.parse.urlencode({
            "client_id": self.client_id,
            "response_type": "code",
            "redirect_uri": redirect_uri,
            "code_challenge_method": "S256",
            "code_challenge": self.challenge_for(verifier),
            "scope": self.SCOPES,
        })
        return f"{self.AUTH}?{params}"

    def exchange_code(self, code, verifier, redirect_uri):
        body = urllib.parse.urlencode({
            "client_id": self.client_id,
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": redirect_uri,
            "code_verifier": verifier,
        }).encode()
        req = urllib.request.Request(self.TOKEN, data=body,
                                     headers={"Content-Type": "application/x-www-form-urlencoded",
                                              "User-Agent": UA})
        with urllib.request.urlopen(req, timeout=30) as r:
            token = json.loads(r.read())
        self.data = {
            "access_token": token["access_token"],
            "refresh_token": token.get("refresh_token", ""),
            "expires_at": time.time() + token.get("expires_in", 3600) - 60,
        }
        self._save()
        return True

    def _token(self):
        if not self.data.get("access_token"):
            raise PermissionError("non connecté à Spotify")
        if time.time() >= self.data.get("expires_at", 0):
            body = urllib.parse.urlencode({
                "client_id": self.client_id,
                "grant_type": "refresh_token",
                "refresh_token": self.data["refresh_token"],
            }).encode()
            req = urllib.request.Request(
                self.TOKEN, data=body,
                headers={"Content-Type": "application/x-www-form-urlencoded", "User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                token = json.loads(r.read())
            self.data["access_token"] = token["access_token"]
            self.data["expires_at"] = time.time() + token.get("expires_in", 3600) - 60
            if token.get("refresh_token"):
                self.data["refresh_token"] = token["refresh_token"]
            self._save()
        return self.data["access_token"]

    def connected(self):
        return bool(self.data.get("access_token"))

    # -- Appels ----------------------------------------------------------------------------

    def _request(self, method, path, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(
            f"{self.API}{path}", data=data, method=method,
            headers={"Authorization": f"Bearer {self._token()}",
                     "Content-Type": "application/json", "User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                raw = r.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            if e.code == 403:
                raise PermissionError(
                    "Spotify a refusé : le contrôle de lecture demande un compte Premium.")
            if e.code == 404:
                raise OSError("Aucun appareil Spotify actif. Lance la lecture sur ton PC "
                              "d'abord, le lanceur pourra ensuite la piloter.")
            raise

    def is_running(self):
        return self.connected()

    def now_playing(self):
        state = self._request("GET", "/me/player")
        if not state or not state.get("item"):
            return Track()
        item = state["item"]
        images = item.get("album", {}).get("images", [])
        return Track(
            artist=", ".join(a["name"] for a in item.get("artists", [])),
            title=item.get("name", ""),
            album=item.get("album", {}).get("name", ""),
            art_url=images[0]["url"] if images else "",
            playing=state.get("is_playing", False),
            progress_ms=state.get("progress_ms", 0),
            duration_ms=item.get("duration_ms", 0))

    def play_pause(self):
        state = self._request("GET", "/me/player")
        if state and state.get("is_playing"):
            self._request("PUT", "/me/player/pause")
        else:
            self._request("PUT", "/me/player/play")
        return True

    def next_track(self):
        self._request("POST", "/me/player/next")
        return True

    def previous_track(self):
        self._request("POST", "/me/player/previous")
        return True

    def playlists(self):
        data = self._request("GET", "/me/playlists?limit=50")
        return [{"name": p["name"], "uri": p["uri"],
                 "tracks": p.get("tracks", {}).get("total", 0),
                 "image": p["images"][0]["url"] if p.get("images") else ""}
                for p in data.get("items", [])]

    def play_playlist(self, uri):
        self._request("PUT", "/me/player/play", {"context_uri": uri})
        return True


# ----------------------------------------------------------------------------------------------
# Sélection automatique
# ----------------------------------------------------------------------------------------------

class BridgeServer:
    """
    Petit serveur HTTP local que le mod Minecraft interroge.

    Le mod tourne dans la JVM du jeu et ne peut pas parler à Spotify directement — il n'a ni les
    jetons, ni le droit d'ouvrir un navigateur pour l'autorisation. Le lanceur, lui, a déjà tout
    ça. Il sert donc de relais.

    Deux précautions qui ne sont pas optionnelles :

    - **Écoute sur 127.0.0.1 uniquement.** Sur 0.0.0.0, n'importe qui sur le même réseau Wi-Fi
      pourrait piloter la musique.
    - **Jeton obligatoire.** Sans lui, n'importe quelle page web ouverte dans le navigateur
      pourrait envoyer des requêtes à ce serveur depuis du JavaScript et prendre le contrôle.
      Le jeton est écrit dans un fichier que seul le mod, local lui aussi, sait lire.
    """

    def __init__(self, backend_provider, token_file, port=25577):
        self.backend_provider = backend_provider
        self.token_file = token_file
        self.port = port
        self.httpd = None
        self.token = ""
        self._thread = None

    def start(self):
        import http.server
        import secrets
        import threading

        self.token = secrets.token_urlsafe(24)
        self.token_file.parent.mkdir(parents=True, exist_ok=True)
        self.token_file.write_text(json.dumps({"port": self.port, "token": self.token}),
                                   encoding="utf-8")
        try:
            os.chmod(self.token_file, 0o600)
        except OSError:
            pass

        bridge = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass          # sinon chaque requête pollue la console du jeu

            def _authorized(self):
                return self.headers.get("X-Token") == bridge.token

            def _reply(self, code, payload):
                body = json.dumps(payload).encode()
                self.send_response(code)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def _handle(self, path, payload=None):
                if not self._authorized():
                    return self._reply(401, {"error": "jeton invalide"})
                backend = bridge.backend_provider()
                if backend is None:
                    return self._reply(503, {"error": "Spotify indisponible"})
                try:
                    if path == "/status":
                        track = backend.now_playing()
                        return self._reply(200, {"backend": backend.name, **track.as_dict()})
                    if path == "/playlists":
                        return self._reply(200, {"playlists": backend.playlists()})
                    if path == "/playpause":
                        backend.play_pause()
                    elif path == "/next":
                        backend.next_track()
                    elif path == "/previous":
                        backend.previous_track()
                    elif path == "/play":
                        backend.play_playlist((payload or {}).get("uri", ""))
                    else:
                        return self._reply(404, {"error": "route inconnue"})
                    return self._reply(200, {"ok": True})
                except PermissionError as e:
                    return self._reply(402, {"error": str(e)})
                except NotImplementedError as e:
                    return self._reply(501, {"error": str(e)})
                except Exception as e:
                    return self._reply(500, {"error": str(e)})

            def do_GET(self):
                self._handle(urllib.parse.urlparse(self.path).path)

            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                raw = self.rfile.read(length) if length else b""
                try:
                    payload = json.loads(raw) if raw else {}
                except json.JSONDecodeError:
                    payload = {}
                self._handle(urllib.parse.urlparse(self.path).path, payload)

        self.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", self.port), Handler)
        self.port = self.httpd.server_address[1]
        self._thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self._thread.start()
        return self.port

    def stop(self):
        if self.httpd:
            self.httpd.shutdown()
            self.httpd.server_close()
            self.httpd = None
        if self.token_file.exists():
            self.token_file.unlink(missing_ok=True)


def pick_backend(client_id="", token_store=None):
    """
    Choisit le meilleur backend disponible.

    L'API Web passe en premier quand elle est connectée : elle seule donne les playlists et la
    pochette. Sinon on retombe sur le contrôle local, qui fonctionne sans compte Premium.
    """
    if client_id and token_store is not None:
        web = WebApiBackend(client_id, token_store)
        if web.connected():
            return web
    if WindowsBackend.available():
        return WindowsBackend()
    if MprisBackend.available():
        return MprisBackend()
    return None
