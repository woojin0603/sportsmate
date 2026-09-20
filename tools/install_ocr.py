"""Install a project-local Tesseract OCR runtime for scanned PDFs on Windows."""

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
from urllib.request import urlretrieve


ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / ".local-tools" / "tesseract"
INSTALLER_URL = (
    "https://github.com/tesseract-ocr/tesseract/releases/download/5.5.3/"
    "tesseract-ocr-w64-setup-5.5.3.20260724.exe"
)
MODEL_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata/main/"
HASHES = {
    "installer": "bee9e3434bd94fd65387d9be28cd467a41f61b1275383b55b0f59a1331270ae4",
    "kor": "9520bfe9e3cfc38d4a808e036b0287c88a1d37fb80b9a0a23928ddccdd20595b",
    "eng": "daa0c97d651c19fba3b25e81317cd697e9908c8208090c94c3905381c23fc047",
}


def download_verified(url: str, destination: Path, expected_hash: str) -> None:
    """Download a pinned official artifact and reject unexpected content."""
    urlretrieve(url, destination)
    actual = hashlib.sha256(destination.read_bytes()).hexdigest()
    if actual != expected_hash:
        destination.unlink(missing_ok=True)
        raise RuntimeError(f"SHA-256 mismatch for {destination.name}")


def configure_spring() -> None:
    """Point only the local Spring profile to the private, ignored OCR folder."""
    settings = ROOT / "config" / "local-secrets.properties"
    settings.parent.mkdir(parents=True, exist_ok=True)
    existing = settings.read_text(encoding="utf-8") if settings.exists() else ""
    line = "app.fitness.tesseract-command=./.local-tools/tesseract/tesseract.exe"
    if "app.fitness.tesseract-command=" not in existing:
        settings.write_text(existing.rstrip("\n") + "\n" + line + "\n", encoding="utf-8")


def main() -> None:
    """Install Tesseract and kor/eng models, then verify the executable."""
    if os.name != "nt":
        raise SystemExit("This installer is for Windows. Install Tesseract via your OS package manager.")
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    executable = TARGET / "tesseract.exe"
    if not executable.exists():
        with tempfile.TemporaryDirectory(prefix="sportmap-ocr-") as folder:
            installer = Path(folder) / "tesseract-setup.exe"
            download_verified(INSTALLER_URL, installer, HASHES["installer"])
            subprocess.run([str(installer), "/S", f"/D={TARGET}"], check=True)
    data_dir = TARGET / "tessdata"
    data_dir.mkdir(parents=True, exist_ok=True)
    for language in ("kor", "eng"):
        model = data_dir / f"{language}.traineddata"
        if not model.exists():
            download_verified(MODEL_URL + model.name, model, HASHES[language])
    configure_spring()
    subprocess.run([str(executable), "--list-langs"], check=True)
    print("OCR is ready. Restart Spring Boot from the sportmap project directory.")


if __name__ == "__main__":
    main()
