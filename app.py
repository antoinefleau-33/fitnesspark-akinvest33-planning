import io
import os
import plistlib
import re
import shutil
import tempfile
import zipfile

from flask import Flask, render_template, request, send_file, flash, redirect, url_for

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "xcodeproj-to-ipa-dev-key")
app.config["MAX_CONTENT_LENGTH"] = 500 * 1024 * 1024  # 500 Mo


def safe_extract(zf: zipfile.ZipFile, dest: str) -> None:
    """Extrait un zip en refusant les chemins qui sortent du dossier cible (zip slip)."""
    dest_real = os.path.realpath(dest)
    for member in zf.infolist():
        name = member.filename
        if name.startswith("/") or ".." in name.split("/"):
            continue
        target = os.path.realpath(os.path.join(dest, name))
        if not target.startswith(dest_real + os.sep) and target != dest_real:
            continue
        zf.extract(member, dest)


def find_app_bundle(root: str):
    """Cherche un bundle .app déjà compilé (contenant un Info.plist)."""
    candidates = []
    for dirpath, dirnames, _ in os.walk(root):
        for d in list(dirnames):
            if d.endswith(".app"):
                app_dir = os.path.join(dirpath, d)
                if os.path.isfile(os.path.join(app_dir, "Info.plist")):
                    candidates.append(app_dir)
                dirnames.remove(d)  # ne pas descendre dans le bundle
    # Préférer un build iOS (iphoneos) si plusieurs candidats
    candidates.sort(key=lambda p: ("iphoneos" not in p.lower(), len(p)))
    return candidates[0] if candidates else None


def find_info_plist(root: str):
    """Cherche l'Info.plist principal d'un projet source (hors .xcodeproj, Pods, Tests)."""
    best = None
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [
            d for d in dirnames
            if not d.endswith((".xcodeproj", ".xcworkspace"))
            and d not in ("Pods", "Carthage", "DerivedData", "__MACOSX")
        ]
        for f in filenames:
            if f == "Info.plist":
                path = os.path.join(dirpath, f)
                if "test" in path.lower() and best:
                    continue
                if best is None or len(path) < len(best):
                    best = path
    return best


def read_plist(path: str):
    try:
        with open(path, "rb") as fh:
            return plistlib.load(fh)
    except Exception:
        return {}


def sanitize_name(name: str) -> str:
    name = re.sub(r"[^\w.\- ]", "", name).strip()
    return name or "App"


def build_ipa_from_app(app_dir: str) -> tuple[io.BytesIO, str]:
    """Empaquette un bundle .app compilé en IPA non signé (Payload/Name.app)."""
    app_name = os.path.basename(app_dir)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for dirpath, _, filenames in os.walk(app_dir):
            for f in filenames:
                full = os.path.join(dirpath, f)
                rel = os.path.relpath(full, app_dir)
                arcname = f"Payload/{app_name}/{rel}"
                zf.write(full, arcname)
        # Supprimer toute signature existante n'est pas nécessaire : on n'en ajoute pas.
    buf.seek(0)
    return buf, sanitize_name(app_name[: -len(".app")]) + "-unsigned.ipa"


def build_structural_ipa(root: str, plist_path: str) -> tuple[io.BytesIO, str, dict]:
    """Assemble un IPA structurel (sans binaire compilé) à partir des sources + Info.plist."""
    info = read_plist(plist_path)
    raw_name = info.get("CFBundleName") or info.get("CFBundleExecutable") or "App"
    if isinstance(raw_name, str) and raw_name.startswith("$("):
        raw_name = "App"
    app_name = sanitize_name(str(raw_name))
    src_dir = os.path.dirname(plist_path)

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        prefix = f"Payload/{app_name}.app/"
        zf.write(plist_path, prefix + "Info.plist")
        # Embarquer les ressources à côté de l'Info.plist (storyboards, assets, etc.)
        for dirpath, dirnames, filenames in os.walk(src_dir):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            for f in filenames:
                full = os.path.join(dirpath, f)
                if full == plist_path or f.startswith("."):
                    continue
                rel = os.path.relpath(full, src_dir)
                zf.write(full, prefix + rel)
        zf.writestr(
            prefix + "README-UNSIGNED.txt",
            "IPA structurel genere sans compilation : le binaire Mach-O est absent.\n"
            "Compilez le projet avec Xcode (Product > Archive) pour obtenir un .app "
            "executable, puis repassez-le dans ce convertisseur.\n",
        )
    buf.seek(0)
    return buf, app_name + "-unsigned.ipa", info


@app.route("/", methods=["GET"])
def index():
    return render_template("index.html")


@app.route("/convert", methods=["POST"])
def convert():
    file = request.files.get("project_zip")
    if not file or not file.filename:
        flash("Aucun fichier reçu. Choisissez un fichier .zip.", "error")
        return redirect(url_for("index"))
    if not file.filename.lower().endswith(".zip"):
        flash("Le fichier doit être un .zip contenant votre projet Xcode.", "error")
        return redirect(url_for("index"))

    workdir = tempfile.mkdtemp(prefix="xcodeipa_")
    try:
        try:
            with zipfile.ZipFile(file.stream) as zf:
                safe_extract(zf, workdir)
        except zipfile.BadZipFile:
            flash("Fichier zip invalide ou corrompu.", "error")
            return redirect(url_for("index"))

        app_bundle = find_app_bundle(workdir)
        if app_bundle:
            ipa, filename = build_ipa_from_app(app_bundle)
            return send_file(ipa, as_attachment=True, download_name=filename,
                             mimetype="application/octet-stream")

        plist_path = find_info_plist(workdir)
        if plist_path:
            ipa, filename, _ = build_structural_ipa(workdir, plist_path)
            resp = send_file(ipa, as_attachment=True, download_name=filename,
                             mimetype="application/octet-stream")
            resp.headers["X-Ipa-Mode"] = "structural"
            return resp

        flash("Impossible de trouver un bundle .app compilé ou un Info.plist dans le zip.",
              "error")
        return redirect(url_for("index"))
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5000)), debug=False)
