#!/usr/bin/env python3
"""
Lanceur Minecraft avec authentification Microsoft.

Un seul fichier, uniquement la bibliothèque standard Python : rien à installer avec pip.

    python3 mclaunch.py setup          # configurer (une fois)
    python3 mclaunch.py login          # se connecter à son compte Microsoft
    python3 mclaunch.py install 26.2   # télécharger le jeu (+ Fabric)
    python3 mclaunch.py play 26.2      # jouer

Pourquoi la connexion Microsoft est indispensable : un serveur en ligne demande à Mojang de
confirmer ton identité au moment où tu te connectes. Sans un vrai jeton d'authentification, le
serveur refuse avec « Failed to verify username » ou « Invalid session ». Ce n'est pas le lanceur
qui est refusé, c'est la session qui est invalide — d'où l'importance de faire la chaîne
d'authentification correctement plutôt que de la contourner.
"""

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

APP_NAME = "poclauncher"
APP_VERSION = "1.0.0"

MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_META = "https://meta.fabricmc.net/v2"
RESOURCES_URL = "https://resources.download.minecraft.net"

MS_DEVICE_CODE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
MS_TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
XBL_AUTH = "https://user.auth.xboxlive.com/user/authenticate"
XSTS_AUTH = "https://xsts.auth.xboxlive.com/xsts/authorize"
MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"
MC_ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore"
MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile"

UA = f"{APP_NAME}/{APP_VERSION}"


# ----------------------------------------------------------------------------------------------
# Utilitaires réseau et fichiers
# ----------------------------------------------------------------------------------------------

def http_json(url, data=None, headers=None, method=None):
    """Requête JSON. Renvoie (statut, corps décodé)."""
    body = None
    hdrs = {"User-Agent": UA, "Accept": "application/json"}
    if headers:
        hdrs.update(headers)
    if data is not None:
        if isinstance(data, dict) and hdrs.get("Content-Type") == "application/x-www-form-urlencoded":
            body = urllib.parse.urlencode(data).encode()
        else:
            body = json.dumps(data).encode()
            hdrs.setdefault("Content-Type", "application/json")

    req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode("utf-8")
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def download(url, target: Path, expected_sha1=None, expected_size=None):
    """
    Télécharge si absent ou corrompu.

    La vérification SHA-1 n'est pas du zèle : un téléchargement interrompu produit un jar
    tronqué qui fait planter le jeu avec une erreur incompréhensible plusieurs minutes plus tard.
    Vérifier ici transforme ça en un simple re-téléchargement.
    """
    if target.exists():
        if expected_size is not None and target.stat().st_size != expected_size:
            pass  # taille incorrecte -> on retélécharge
        elif expected_sha1:
            if sha1_of(target) == expected_sha1:
                return False
        else:
            return False

    target.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    tmp = target.with_suffix(target.suffix + ".part")
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f, length=1 << 20)
    tmp.replace(target)
    return True


def sha1_of(path: Path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ----------------------------------------------------------------------------------------------
# Plateforme et règles
# ----------------------------------------------------------------------------------------------

def os_name():
    return {"linux": "linux", "darwin": "osx", "win32": "windows"}.get(sys.platform, "linux")


def os_arch():
    machine = platform.machine().lower()
    if machine in ("x86_64", "amd64"):
        return "x64"
    if machine in ("arm64", "aarch64"):
        return "arm64"
    if machine in ("i386", "i686", "x86"):
        return "x86"
    return machine


def rules_allow(rules, features=None):
    """
    Évalue les règles Mojang : chaque entrée autorise ou interdit selon l'OS, l'architecture ou
    une fonctionnalité. La règle par défaut est « interdit » dès qu'une liste existe : c'est la
    subtilité qui fait qu'on se retrouve avec -XstartOnFirstThread sous Linux si on l'ignore.
    """
    if not rules:
        return True
    features = features or {}
    allowed = False
    for rule in rules:
        matches = True
        os_spec = rule.get("os", {})
        if "name" in os_spec and os_spec["name"] != os_name():
            matches = False
        if "arch" in os_spec and os_spec["arch"] != os_arch():
            matches = False
        if "version" in os_spec:
            if not re.search(os_spec["version"], platform.release()):
                matches = False
        if "versionRange" in os_spec:
            matches = matches  # plage de versions Windows : non filtrée ici, sans impact connu
        for key, wanted in rule.get("features", {}).items():
            if bool(features.get(key, False)) != bool(wanted):
                matches = False
        if matches:
            allowed = rule.get("action") == "allow"
    return allowed


def flatten_arguments(entries, features=None):
    """Aplatit une liste d'arguments Mojang (chaînes + objets conditionnels)."""
    out = []
    for entry in entries:
        if isinstance(entry, str):
            out.append(entry)
            continue
        if not rules_allow(entry.get("rules"), features):
            continue
        value = entry.get("value", [])
        out.extend([value] if isinstance(value, str) else value)
    return out


# ----------------------------------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------------------------------

class Config:
    def __init__(self, root: Path):
        self.root = root
        self.path = root / "config.json"
        self.data = {}
        if self.path.exists():
            self.data = json.loads(self.path.read_text(encoding="utf-8"))

    def save(self):
        self.root.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, indent=2), encoding="utf-8")
        try:
            os.chmod(self.path, 0o600)
        except OSError:
            pass

    @property
    def client_id(self):
        return self.data.get("azure_client_id", "")

    @property
    def game_dir(self):
        return Path(self.data.get("game_dir", str(self.root / "game"))).expanduser()

    @property
    def java(self):
        return self.data.get("java", "")

    @property
    def memory_mb(self):
        return int(self.data.get("memory_mb", 4096))


# ----------------------------------------------------------------------------------------------
# Authentification Microsoft
# ----------------------------------------------------------------------------------------------

class AuthError(Exception):
    pass


class Account:
    """Session de compte, mise en cache sur disque. Seul le refresh_token est durable."""

    def __init__(self, root: Path):
        self.path = root / "account.json"
        self.data = {}
        if self.path.exists():
            self.data = json.loads(self.path.read_text(encoding="utf-8"))

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, indent=2), encoding="utf-8")
        # Le fichier contient un jeton de rafraîchissement : il vaut un accès au compte.
        try:
            os.chmod(self.path, 0o600)
        except OSError:
            pass

    def clear(self):
        self.data = {}
        if self.path.exists():
            self.path.unlink()

    @property
    def valid(self):
        return bool(self.data.get("mc_token")) and time.time() < self.data.get("expires_at", 0)


def device_code_login(client_id):
    """
    Flux « device code » : l'utilisateur ouvre une page et tape un code. On l'a préféré au flux
    par redirection parce qu'il ne demande ni serveur web local, ni navigateur intégré — et donc
    aucune manipulation d'URL de redirection, source classique d'échecs de configuration Azure.
    """
    status, resp = http_json(MS_DEVICE_CODE,
                             data={"client_id": client_id, "scope": "XboxLive.signin offline_access"},
                             headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        raise AuthError(f"Azure a refusé la demande ({status}) : {resp.get('error_description', resp)}")

    print()
    print("  ┌────────────────────────────────────────────────────┐")
    print(f"  │  Ouvre : {resp['verification_uri']:<41s}│")
    print(f"  │  Code  : {resp['user_code']:<41s}│")
    print("  └────────────────────────────────────────────────────┘")
    print()
    print("  En attente de la validation...", end="", flush=True)

    interval = int(resp.get("interval", 5))
    deadline = time.time() + int(resp.get("expires_in", 900))

    while time.time() < deadline:
        time.sleep(interval)
        status, token = http_json(
            MS_TOKEN,
            data={"grant_type": "urn:ietf:params:oauth:grant-type:device_code",
                  "client_id": client_id, "device_code": resp["device_code"]},
            headers={"Content-Type": "application/x-www-form-urlencoded"})
        error = token.get("error")
        if error == "authorization_pending":
            print(".", end="", flush=True)
            continue
        if error == "slow_down":
            interval += 5
            continue
        if error == "expired_token":
            raise AuthError("le code a expiré, relance la commande")
        if error == "access_denied":
            raise AuthError("connexion refusée dans le navigateur")
        if error:
            raise AuthError(f"{error} : {token.get('error_description', '')}")
        print(" OK")
        return token
    raise AuthError("délai dépassé")


def refresh_ms_token(client_id, refresh_token):
    status, token = http_json(
        MS_TOKEN,
        data={"grant_type": "refresh_token", "client_id": client_id,
              "refresh_token": refresh_token, "scope": "XboxLive.signin offline_access"},
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200 or "access_token" not in token:
        raise AuthError("session expirée, reconnecte-toi avec « login »")
    return token


def xbox_chain(ms_access_token):
    """Microsoft -> Xbox Live -> XSTS -> Minecraft. Chaque étape a ses propres erreurs typiques."""
    status, xbl = http_json(XBL_AUTH, data={
        "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com",
                       "RpsTicket": f"d={ms_access_token}"},
        "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT"})
    if status != 200:
        raise AuthError(f"Xbox Live a refusé la connexion ({status})")
    xbl_token = xbl["Token"]
    uhs = xbl["DisplayClaims"]["xui"][0]["uhs"]

    status, xsts = http_json(XSTS_AUTH, data={
        "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl_token]},
        "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT"})
    if status == 401:
        # Ces deux codes reviennent constamment et le message brut est incompréhensible.
        xerr = str(xsts.get("XErr", ""))
        if xerr == "2148916233":
            raise AuthError("ce compte Microsoft n'a pas de profil Xbox. "
                            "Crée-en un sur xbox.com puis réessaie.")
        if xerr == "2148916238":
            raise AuthError("compte enfant : il doit être rattaché à une famille Microsoft.")
        raise AuthError(f"XSTS a refusé la connexion (XErr {xerr})")
    if status != 200:
        raise AuthError(f"XSTS a échoué ({status})")

    xsts_token = xsts["Token"]
    xuid = xsts["DisplayClaims"]["xui"][0].get("xid", "")

    status, mc = http_json(MC_LOGIN, data={"identityToken": f"XBL3.0 x={uhs};{xsts_token}"})
    if status != 200:
        raise AuthError(f"Minecraft a refusé la connexion ({status})")
    return mc["access_token"], int(mc.get("expires_in", 86400)), xuid


def ensure_logged_in(config: Config, account: Account, interactive=True):
    if not config.client_id:
        raise AuthError("aucun identifiant d'application Azure configuré. "
                        "Lance d'abord : mclaunch.py setup")

    if account.valid:
        return account

    if account.data.get("ms_refresh_token"):
        try:
            token = refresh_ms_token(config.client_id, account.data["ms_refresh_token"])
        except AuthError:
            if not interactive:
                raise
            token = device_code_login(config.client_id)
    else:
        if not interactive:
            raise AuthError("non connecté")
        token = device_code_login(config.client_id)

    mc_token, expires_in, xuid = xbox_chain(token["access_token"])

    # Vérifier la possession du jeu avant d'aller plus loin : sinon l'erreur suivante est un 404
    # sur le profil, qu'on interprète naturellement — et à tort — comme un bug du lanceur.
    status, ent = http_json(MC_ENTITLEMENTS, headers={"Authorization": f"Bearer {mc_token}"})
    if status == 200 and not ent.get("items"):
        raise AuthError("ce compte ne possède pas Minecraft: Java Edition.")

    status, profile = http_json(MC_PROFILE, headers={"Authorization": f"Bearer {mc_token}"})
    if status != 200:
        raise AuthError("impossible de lire le profil : le compte n'a peut-être pas encore "
                        "de pseudo Java défini.")

    raw_uuid = profile["id"]
    account.data = {
        "ms_refresh_token": token.get("refresh_token", ""),
        "mc_token": mc_token,
        # Marge de 5 minutes : un jeton qui expire pendant le chargement du monde donne une
        # déconnexion au premier serveur rejoint, très difficile à relier à sa cause.
        "expires_at": time.time() + expires_in - 300,
        "uuid": f"{raw_uuid[0:8]}-{raw_uuid[8:12]}-{raw_uuid[12:16]}-{raw_uuid[16:20]}-{raw_uuid[20:]}",
        "name": profile["name"],
        "xuid": xuid,
    }
    account.save()
    return account


# ----------------------------------------------------------------------------------------------
# Installation
# ----------------------------------------------------------------------------------------------

def fetch_manifest():
    _, data = http_json(MANIFEST_URL)
    return data


def resolve_version_json(game_dir: Path, version_id):
    manifest = fetch_manifest()
    if version_id in ("latest", "release"):
        version_id = manifest["latest"]["release"]
    elif version_id == "snapshot":
        version_id = manifest["latest"]["snapshot"]

    entry = next((v for v in manifest["versions"] if v["id"] == version_id), None)
    if not entry:
        raise SystemExit(f"version inconnue : {version_id}")

    target = game_dir / "versions" / version_id / f"{version_id}.json"
    download(entry["url"], target, entry.get("sha1"))
    return version_id, json.loads(target.read_text(encoding="utf-8"))


def install_fabric(game_dir: Path, mc_version, loader_version=None):
    """
    Installe Fabric et fusionne son descripteur avec celui de vanilla.

    Fabric fournit un JSON « profil » qui ajoute ses librairies et remplace la classe principale.
    On le fusionne plutôt que de le traiter à part : ça garde une seule logique de lancement.
    """
    _, loaders = http_json(f"{FABRIC_META}/versions/loader/{mc_version}")
    if not loaders:
        raise SystemExit(f"Fabric ne supporte pas encore {mc_version}")
    loader_version = loader_version or loaders[0]["loader"]["version"]

    _, profile = http_json(
        f"{FABRIC_META}/versions/loader/{mc_version}/{loader_version}/profile/json")

    name = profile["id"]
    target = game_dir / "versions" / name / f"{name}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(profile, indent=2), encoding="utf-8")
    return name, profile


def merged_version(game_dir: Path, version_json):
    """Résout la chaîne inheritsFrom (Fabric hérite de vanilla)."""
    if "inheritsFrom" not in version_json:
        return version_json

    parent_id = version_json["inheritsFrom"]
    _, parent = resolve_version_json(game_dir, parent_id)
    parent = merged_version(game_dir, parent)

    merged = dict(parent)
    merged["id"] = version_json["id"]
    merged["mainClass"] = version_json.get("mainClass", parent.get("mainClass"))
    # Les librairies de Fabric passent AVANT celles de vanilla : l'ordre du classpath décide
    # quelle version d'une dépendance partagée gagne.
    merged["libraries"] = version_json.get("libraries", []) + parent.get("libraries", [])

    args = dict(parent.get("arguments", {}))
    for key, extra in version_json.get("arguments", {}).items():
        args[key] = args.get(key, []) + extra
    merged["arguments"] = args
    return merged


def library_path(game_dir: Path, lib):
    artifact = lib.get("downloads", {}).get("artifact")
    if artifact and artifact.get("path"):
        return game_dir / "libraries" / artifact["path"]
    # Fabric fournit des librairies sans bloc downloads : on reconstruit le chemin Maven.
    group, artifact_id, version = lib["name"].split(":")[:3]
    return (game_dir / "libraries" / Path(*group.split("."))
            / artifact_id / version / f"{artifact_id}-{version}.jar")


def library_url(lib):
    artifact = lib.get("downloads", {}).get("artifact")
    if artifact and artifact.get("url"):
        return artifact["url"]
    base = lib.get("url", "https://libraries.minecraft.net/")
    group, artifact_id, version = lib["name"].split(":")[:3]
    return f"{base.rstrip('/')}/{group.replace('.', '/')}/{artifact_id}/{version}/{artifact_id}-{version}.jar"


def install_version(game_dir: Path, version_json, progress=True):
    version_id = version_json["id"]
    natives_dir = game_dir / "versions" / version_id / "natives"
    # 26.2 attend ces sous-dossiers : LWJGL et JNA y extraient eux-mêmes leurs bibliothèques
    # natives depuis les jars du classpath. Plus besoin de les dépaqueter à la main comme avant.
    for sub in ("java", "jna", "lwjgl"):
        (natives_dir / sub).mkdir(parents=True, exist_ok=True)

    # Client jar
    client = version_json.get("downloads", {}).get("client")
    if client:
        jar = game_dir / "versions" / version_id / f"{version_id}.jar"
        if download(client["url"], jar, client.get("sha1"), client.get("size")) and progress:
            print(f"  client {version_id}.jar")

    # Librairies
    libs = [lib for lib in version_json["libraries"] if rules_allow(lib.get("rules"))]
    for i, lib in enumerate(libs, 1):
        target = library_path(game_dir, lib)
        artifact = lib.get("downloads", {}).get("artifact") or {}
        try:
            fresh = download(library_url(lib), target, artifact.get("sha1"), artifact.get("size"))
        except urllib.error.HTTPError as e:
            print(f"  ! librairie indisponible : {lib['name']} ({e.code})")
            continue
        if fresh and progress:
            print(f"  [{i}/{len(libs)}] {lib['name']}")

        # Ancien format (≤ 1.18) : natifs dans un jar séparé à dépaqueter.
        classifiers = lib.get("downloads", {}).get("classifiers")
        natives_key = (lib.get("natives") or {}).get(os_name())
        if classifiers and natives_key:
            key = natives_key.replace("${arch}", "64")
            native = classifiers.get(key)
            if native:
                native_jar = game_dir / "libraries" / native["path"]
                download(native["url"], native_jar, native.get("sha1"))
                with zipfile.ZipFile(native_jar) as z:
                    for member in z.namelist():
                        if member.startswith("META-INF/") or member.endswith("/"):
                            continue
                        z.extract(member, natives_dir / "java")

    # Configuration de journalisation
    logging_cfg = version_json.get("logging", {}).get("client", {}).get("file")
    if logging_cfg:
        download(logging_cfg["url"],
                 game_dir / "assets" / "log_configs" / logging_cfg["id"],
                 logging_cfg.get("sha1"))

    install_assets(game_dir, version_json, progress)


def install_assets(game_dir: Path, version_json, progress=True):
    asset_index = version_json.get("assetIndex")
    if not asset_index:
        return
    index_path = game_dir / "assets" / "indexes" / f"{asset_index['id']}.json"
    download(asset_index["url"], index_path, asset_index.get("sha1"))
    index = json.loads(index_path.read_text(encoding="utf-8"))
    objects = index.get("objects", {})

    print(f"  assets : {len(objects)} fichiers à vérifier")
    downloaded = 0
    for i, (name, obj) in enumerate(objects.items(), 1):
        h = obj["hash"]
        target = game_dir / "assets" / "objects" / h[:2] / h
        if download(f"{RESOURCES_URL}/{h[:2]}/{h}", target, None, obj.get("size")):
            downloaded += 1
        if progress and i % 500 == 0:
            print(f"    {i}/{len(objects)}...")

        # Versions ≤ 1.8 : arborescence à plat en plus du stockage par hash.
        if index.get("map_to_resources") or index.get("virtual"):
            legacy = game_dir / "assets" / "virtual" / "legacy" / name
            if not legacy.exists():
                legacy.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(target, legacy)

    print(f"  assets : {downloaded} téléchargés, {len(objects) - downloaded} déjà présents")


# ----------------------------------------------------------------------------------------------
# Lancement
# ----------------------------------------------------------------------------------------------

def find_java(config: Config, required_major):
    if config.java:
        return config.java
    candidate = shutil.which("java")
    if not candidate:
        raise SystemExit("Java introuvable. Installe Java "
                         f"{required_major} puis relance, ou renseigne-le via « setup ».")
    try:
        out = subprocess.run([candidate, "-version"], capture_output=True, text=True, timeout=20)
        text = out.stderr + out.stdout
        match = re.search(r'version "(\d+)', text)
        if match and int(match.group(1)) < required_major:
            print(f"  ! Java {match.group(1)} détecté, mais Minecraft {required_major} est requis.")
            print(f"    Installe un JDK {required_major} et indique-le avec « setup ».")
    except Exception:
        pass
    return candidate


def build_command(config: Config, account: Account, game_dir: Path, version_json):
    version_id = version_json["id"]
    natives_dir = game_dir / "versions" / version_id / "natives"

    classpath = []
    for lib in version_json["libraries"]:
        if not rules_allow(lib.get("rules")):
            continue
        path = library_path(game_dir, lib)
        if path.exists() and str(path) not in classpath:
            classpath.append(str(path))

    client_jar = game_dir / "versions" / version_id / f"{version_id}.jar"
    if not client_jar.exists():
        # Profil Fabric : le jar porte le nom de la version vanilla dont il hérite.
        parent = version_json.get("inheritsFrom")
        if parent:
            client_jar = game_dir / "versions" / parent / f"{parent}.jar"
    classpath.append(str(client_jar))

    assets_index = version_json.get("assetIndex", {}).get("id", "legacy")
    substitutions = {
        "auth_player_name": account.data["name"],
        "version_name": version_id,
        "game_directory": str(game_dir),
        "assets_root": str(game_dir / "assets"),
        "assets_index_name": assets_index,
        "auth_uuid": account.data["uuid"],
        "auth_access_token": account.data["mc_token"],
        "auth_xuid": account.data.get("xuid", ""),
        "clientid": config.client_id,
        # « msa » = compte Microsoft. Une valeur incorrecte ici fait échouer la vérification de
        # session côté serveur, avec le message « Failed to verify username ».
        "user_type": "msa",
        "version_type": version_json.get("type", "release"),
        "natives_directory": str(natives_dir),
        "launcher_name": APP_NAME,
        "launcher_version": APP_VERSION,
        "classpath": os.pathsep.join(classpath),
        "classpath_separator": os.pathsep,
        "library_directory": str(game_dir / "libraries"),
        "resolution_width": "1280",
        "resolution_height": "720",
    }

    def substitute(value):
        for key, replacement in substitutions.items():
            value = value.replace("${" + key + "}", str(replacement))
        return value

    arguments = version_json.get("arguments", {})
    jvm_args = flatten_arguments(arguments.get("jvm", []))
    if not jvm_args:
        jvm_args = [f"-Djava.library.path={natives_dir}", "-cp", "${classpath}"]

    memory = [f"-Xmx{config.memory_mb}M", f"-Xms{min(config.memory_mb, 2048)}M"]

    logging_arg = version_json.get("logging", {}).get("client", {})
    if logging_arg.get("argument") and logging_arg.get("file"):
        log_path = game_dir / "assets" / "log_configs" / logging_arg["file"]["id"]
        jvm_args.append(logging_arg["argument"].replace("${path}", str(log_path)))

    java = find_java(config, version_json.get("javaVersion", {}).get("majorVersion", 21))

    command = [java] + memory + [substitute(a) for a in jvm_args]
    command.append(version_json["mainClass"])
    command += [substitute(a) for a in flatten_arguments(arguments.get("game", []), features={})]
    return command


def redact(command):
    """Masque le jeton avant tout affichage : il donne un accès complet au compte."""
    out = []
    skip_next = False
    for arg in command:
        if skip_next:
            out.append("***JETON-MASQUÉ***")
            skip_next = False
            continue
        if arg == "--accessToken":
            out.append(arg)
            skip_next = True
            continue
        out.append(arg)
    return out


# ----------------------------------------------------------------------------------------------
# Commandes
# ----------------------------------------------------------------------------------------------

def cmd_setup(args, config: Config):
    print("Configuration du lanceur\n")
    print("Il te faut une application Azure (gratuite). Voir README.md, section « Azure ».")
    current = config.client_id
    prompt = f"Identifiant d'application Azure [{current or 'aucun'}] : "
    client_id = input(prompt).strip() or current
    if not client_id:
        print("Sans identifiant Azure, la connexion Microsoft ne peut pas fonctionner.")
        return 1
    config.data["azure_client_id"] = client_id

    default_dir = config.data.get("game_dir", str(config.root / "game"))
    game_dir = input(f"Dossier de jeu [{default_dir}] : ").strip() or default_dir
    config.data["game_dir"] = game_dir

    default_mem = config.data.get("memory_mb", 4096)
    memory = input(f"Mémoire en Mo [{default_mem}] : ").strip() or str(default_mem)
    config.data["memory_mb"] = int(memory)

    java = input(f"Chemin de Java [{config.java or 'détection auto'}] : ").strip()
    if java:
        config.data["java"] = java

    config.save()
    print(f"\nConfiguration enregistrée dans {config.path}")
    return 0


def cmd_login(args, config: Config):
    account = Account(config.root)
    if args.force:
        account.clear()
    try:
        ensure_logged_in(config, account)
    except AuthError as e:
        print(f"\nÉchec de la connexion : {e}")
        return 1
    print(f"\nConnecté en tant que {account.data['name']}")
    return 0


def cmd_logout(args, config: Config):
    Account(config.root).clear()
    print("Déconnecté.")
    return 0


def cmd_versions(args, config: Config):
    manifest = fetch_manifest()
    print(f"dernière version stable  : {manifest['latest']['release']}")
    print(f"dernier instantané       : {manifest['latest']['snapshot']}\n")
    shown = [v for v in manifest["versions"] if v["type"] == "release"][:args.limit]
    for v in shown:
        print(f"  {v['id']:<12s} {v['releaseTime'][:10]}")
    return 0


def cmd_install(args, config: Config):
    game_dir = config.game_dir
    version_id, version_json = resolve_version_json(game_dir, args.version)
    print(f"Installation de Minecraft {version_id}")

    required_java = version_json.get("javaVersion", {}).get("majorVersion")
    if required_java:
        print(f"  (cette version nécessite Java {required_java})")

    install_version(game_dir, version_json)

    if args.fabric:
        print("\nInstallation de Fabric")
        name, profile = install_fabric(game_dir, version_id, args.loader)
        merged = merged_version(game_dir, profile)
        install_version(game_dir, merged, progress=False)
        print(f"  profil créé : {name}")
        (game_dir / "mods").mkdir(exist_ok=True)
        print(f"  dossier de mods : {game_dir / 'mods'}")

    print("\nInstallation terminée.")
    return 0


def cmd_play(args, config: Config):
    game_dir = config.game_dir
    account = Account(config.root)

    try:
        ensure_logged_in(config, account, interactive=not args.dry_run)
    except AuthError as e:
        print(f"Connexion impossible : {e}")
        return 1

    version_id = args.version
    profile_path = game_dir / "versions" / version_id / f"{version_id}.json"
    if profile_path.exists():
        version_json = merged_version(game_dir, json.loads(profile_path.read_text(encoding="utf-8")))
    else:
        matches = sorted((game_dir / "versions").glob(f"fabric-loader-*-{version_id}"))
        if matches:
            name = matches[-1].name
            version_json = merged_version(
                game_dir,
                json.loads((matches[-1] / f"{name}.json").read_text(encoding="utf-8")))
            print(f"Profil Fabric détecté : {name}")
        else:
            print(f"Version {version_id} non installée. Lance d'abord :")
            print(f"  {sys.argv[0]} install {version_id} --fabric")
            return 1

    game_dir.mkdir(parents=True, exist_ok=True)
    command = build_command(config, account, game_dir, version_json)

    if args.dry_run:
        print("\nCommande qui serait exécutée :\n")
        print(" ".join(redact(command)))
        return 0

    print(f"Lancement de {version_json['id']} en tant que {account.data['name']}...")
    try:
        return subprocess.call(command, cwd=str(game_dir))
    except FileNotFoundError:
        print("Java est introuvable. Configure son chemin avec « setup ».")
        return 1


def main():
    parser = argparse.ArgumentParser(
        prog="mclaunch.py",
        description="Lanceur Minecraft avec authentification Microsoft.")
    parser.add_argument("--root", default=str(Path.home() / f".{APP_NAME}"),
                        help="dossier de configuration du lanceur")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("setup", help="configurer le lanceur")

    p = sub.add_parser("login", help="se connecter à un compte Microsoft")
    p.add_argument("--force", action="store_true", help="oublier la session et recommencer")

    sub.add_parser("logout", help="oublier la session enregistrée")

    p = sub.add_parser("versions", help="lister les versions disponibles")
    p.add_argument("--limit", type=int, default=15)

    p = sub.add_parser("install", help="télécharger une version")
    p.add_argument("version", help="numéro de version, ou « latest »")
    p.add_argument("--fabric", action="store_true", help="installer aussi Fabric")
    p.add_argument("--loader", help="version précise du chargeur Fabric")

    p = sub.add_parser("play", help="lancer le jeu")
    p.add_argument("version")
    p.add_argument("--dry-run", action="store_true",
                   help="afficher la commande sans lancer le jeu")

    args = parser.parse_args()
    config = Config(Path(args.root).expanduser())

    handlers = {
        "setup": cmd_setup, "login": cmd_login, "logout": cmd_logout,
        "versions": cmd_versions, "install": cmd_install, "play": cmd_play,
    }
    return handlers[args.command](args, config)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nInterrompu.")
        sys.exit(130)
