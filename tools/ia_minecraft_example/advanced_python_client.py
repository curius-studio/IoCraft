#!/usr/bin/env python3
"""
advanced_python_client.py
──────────────────────────
Cliente Python para IoCraft — Constructor + Chat con agente IA via OpenCode.

Requisitos:
    pip install websocket-client requests

Uso:
    1. Ten Minecraft corriendo con el mod IoCraft.
    2. Ten OpenCode corriendo desde esta carpeta:
           opencode web
       (opencode.json carga el prompt del agente automáticamente)
    3. Ejecuta este script.
    4. Opción 1 → credenciales → opción 2 → autenticar.
    5. Opción 3 → construir casa manualmente.
       Opción 4 → chat con agente IA que construye en Minecraft.

Renovación de sesión:
    El SECRET es permanente. La SESIÓN expira cada 10 min.
    Este cliente la renueva automáticamente antes de cada envío.
"""

import hashlib
import hmac
import json
import os
import re
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

try:
    import websocket
except ImportError as exc:
    raise SystemExit("Falta dependencia: pip install websocket-client") from exc

try:
    import requests
except ImportError as exc:
    raise SystemExit("Falta dependencia: pip install requests") from exc


# ═══════════════════════════════════════════════════════════════
#  CONSTANTES
# ═══════════════════════════════════════════════════════════════

SESSION_RENEW_BEFORE_S = 60
OPENCODE_HOST          = "127.0.0.1"
OPENCODE_PORT          = 4096
CMD_DELAY              = 0.05

# Ruta al prompt — mismo directorio que este script
PROMPT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "iocraft_agent_prompt.md")


# ═══════════════════════════════════════════════════════════════
#  UTILIDADES WS
# ═══════════════════════════════════════════════════════════════

def sign(secret: str, device: str, ts: int, nonce: str) -> str:
    msg = f"{device}:{ts}:{nonce}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()


def make_url(host: str, port: int, name: Optional[str]) -> str:
    base = f"ws://{host}:{port}/"
    return f"{base}?name={name}" if name else base


def recv_json(ws: Any, timeout: float) -> Dict[str, Any]:
    ws.settimeout(timeout)
    raw = ws.recv()
    try:
        return json.loads(raw)
    except Exception:
        return {"raw": raw}


def send_json(ws: Any, payload: Dict[str, Any]) -> None:
    ws.send(json.dumps(payload, ensure_ascii=False))


# ═══════════════════════════════════════════════════════════════
#  SESIÓN ICRAFT
# ═══════════════════════════════════════════════════════════════

@dataclass
class Session:
    host: str
    port: int
    timeout: float
    name: Optional[str]
    device: Optional[str]
    secret: Optional[str]
    ws: Any                                     = None
    authenticated: bool                         = False
    session_expires_at: float                   = 0.0
    last_nonce: Optional[str]                   = None
    last_ts: Optional[int]                      = None
    monitor_enabled: bool                       = True
    stop_listener: bool                         = False
    listener_thread: Optional[threading.Thread] = None
    print_lock: Any                             = field(default_factory=threading.Lock)


# ═══════════════════════════════════════════════════════════════
#  I/O SEGURA
# ═══════════════════════════════════════════════════════════════

def safe_print(s: Session, *args, **kwargs) -> None:
    with s.print_lock:
        print(*args, **kwargs)


def ask(prompt: str, default: Optional[str] = None) -> str:
    suffix = f" [{default}]" if default is not None else ""
    v = input(f"  {prompt}{suffix}: ").strip()
    return default if (v == "" and default is not None) else v


def ask_int(prompt: str, default: int) -> int:
    while True:
        raw = ask(prompt, str(default))
        try:
            return int(raw)
        except ValueError:
            print(f"  ✗ '{raw}' no es un número entero. Intenta de nuevo.")


# ═══════════════════════════════════════════════════════════════
#  LISTENER (mensajes entrantes desde Minecraft)
# ═══════════════════════════════════════════════════════════════

def listener_loop(s: Session) -> None:
    while not s.stop_listener:
        if s.ws is None:
            break
        try:
            msg = recv_json(s.ws, 0.5)
            if s.monitor_enabled:
                safe_print(s, f"\n  [RX] {json.dumps(msg, ensure_ascii=False)}")
                safe_print(s, "  > ", end="", flush=True)
        except Exception:
            continue


def start_listener(s: Session) -> None:
    _stop_listener(s)
    s.stop_listener = False
    t = threading.Thread(target=listener_loop, args=(s,), daemon=True, name="iocraft-rx")
    s.listener_thread = t
    t.start()


def _stop_listener(s: Session) -> None:
    s.stop_listener = True
    t = s.listener_thread
    if t is not None and t.is_alive():
        t.join(timeout=1.2)
    s.listener_thread = None


# ═══════════════════════════════════════════════════════════════
#  CONEXIÓN / AUTENTICACIÓN ICRAFT
# ═══════════════════════════════════════════════════════════════

def connect_session(s: Session) -> bool:
    try:
        url = make_url(s.host, s.port, s.name)
        safe_print(s, f"\n  Conectando a {url} ...")
        s.ws = websocket.create_connection(url, timeout=s.timeout)
        hello_srv = recv_json(s.ws, s.timeout)
        safe_print(s, f"  Servidor: {hello_srv.get('server', '?')}  "
                      f"| ID: {hello_srv.get('id', '?')[:12]}...")
        s.authenticated      = False
        s.session_expires_at = 0.0
        start_listener(s)
        return True
    except Exception as exc:
        safe_print(s, f"  ✗ Error conectando: {exc}")
        s.ws = None
        return False


def close_session(s: Session) -> None:
    _stop_listener(s)
    if s.ws is not None:
        try:
            s.ws.close()
        except Exception:
            pass
    s.ws                 = None
    s.authenticated      = False
    s.session_expires_at = 0.0


def unwrap_ack(ack: Dict[str, Any]) -> Dict[str, Any]:
    data = ack.get("data")
    return data if isinstance(data, dict) else ack


def do_hello(s: Session, reuse_last: bool = False, silent: bool = False) -> Dict[str, Any]:
    if s.ws is None:
        if not silent:
            safe_print(s, "  ✗ Sin conexión activa.")
        return {"ok": False, "code": "not_connected"}
    if not s.device or not s.secret:
        if not silent:
            safe_print(s, "  ✗ Configura device/secret primero (opción 1).")
        return {"ok": False, "code": "missing_credentials"}

    ts    = s.last_ts    if (reuse_last and s.last_ts)    else int(time.time() * 1000)
    nonce = s.last_nonce if (reuse_last and s.last_nonce) else str(uuid.uuid4())

    payload = {
        "type":   "hello",
        "device": s.device,
        "ts":     ts,
        "nonce":  nonce,
        "sig":    sign(s.secret, s.device, ts, nonce),
    }

    _stop_listener(s)
    send_json(s.ws, payload)
    ack = recv_json(s.ws, s.timeout)
    start_listener(s)

    ack_data        = unwrap_ack(ack)
    s.last_ts       = ts
    s.last_nonce    = nonce
    s.authenticated = ack_data.get("ok") is True

    if s.authenticated:
        expires_ms           = ack_data.get("expiresAt", 0)
        s.session_expires_at = expires_ms / 1000.0
        if not silent:
            roles   = ack_data.get("roles", [])
            exp_str = time.strftime("%H:%M:%S", time.localtime(s.session_expires_at))
            safe_print(s, f"  ✓ Autenticado — roles: {roles} | sesión hasta: {exp_str}")
            if "cmd" not in roles:
                safe_print(s, "  ⚠  El dispositivo no tiene rol 'cmd'.")
    else:
        s.session_expires_at = 0.0
        if not silent:
            code = ack_data.get("code", "?")
            msg  = ack_data.get("message", "")
            safe_print(s, f"  ✗ Auth fallida [{code}]: {msg}")

    return ack_data


def session_time_remaining(s: Session) -> float:
    if not s.authenticated or s.session_expires_at == 0.0:
        return 0.0
    return max(0.0, s.session_expires_at - time.time())


def ensure_session(s: Session) -> bool:
    if not s.authenticated:
        return False
    restante = session_time_remaining(s)
    if restante < SESSION_RENEW_BEFORE_S:
        safe_print(s, f"\n  [sesión] Renovando automáticamente (quedan {restante:.0f}s)...")
        ack = do_hello(s, silent=True)
        if not ack.get("ok"):
            safe_print(s, f"  [sesión] ✗ Renovación fallida: {ack.get('code')}")
            return False
        nuevo_exp = time.strftime("%H:%M:%S", time.localtime(s.session_expires_at))
        safe_print(s, f"  [sesión] ✓ Sesión renovada hasta {nuevo_exp}")
    return s.authenticated


# ═══════════════════════════════════════════════════════════════
#  ENVÍO DE COMANDOS A MINECRAFT
# ═══════════════════════════════════════════════════════════════

def enviar_cmd(s: Session, comando: str) -> bool:
    if s.ws is None:
        return False
    if not ensure_session(s):
        return False
    try:
        send_json(s.ws, {"type": "cmd", "device": s.device, "data": comando})
        return True
    except Exception as exc:
        safe_print(s, f"  ✗ Error enviando '{comando}': {exc}")
        s.authenticated = False
        return False


def ejecutar_lista_cmds(s: Session, comandos: List[str]) -> None:
    total   = len(comandos)
    errores = 0
    safe_print(s, f"\n  Ejecutando {total} comandos en Minecraft...\n")
    for i, cmd in enumerate(comandos, 1):
        if not enviar_cmd(s, cmd):
            errores += 1
        if i % 5 == 0 or i == total:
            with s.print_lock:
                print(f"    [{i:>3}/{total}]", end="\r", flush=True)
        time.sleep(CMD_DELAY)
    with s.print_lock:
        print()
    if errores == 0:
        safe_print(s, f"  ✓ Completado — {total} comandos ejecutados.")
    else:
        safe_print(s, f"  ⚠  {total - errores}/{total} OK, {errores} con error.")


# ═══════════════════════════════════════════════════════════════
#  ESTRUCTURAS PREDEFINIDAS
# ═══════════════════════════════════════════════════════════════

def cmds_casa(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int) -> List[str]:
    x2 = max(x2, x1 + 4)
    y2 = max(y2, y1 + 3)
    z2 = max(z2, z1 + 2)
    cx = (x1 + x2) // 2
    mz = (z1 + z2) // 2
    return [
        f"fill {x1-1} {y1} {z1-1} {x2+1} {y2+1} {z2+1} air",
        f"fill {x1} {y1} {z1} {x2} {y1} {z2} minecraft:oak_planks",
        f"fill {x1} {y1+1} {z1} {x2} {y2} {z2} minecraft:oak_planks",
        f"fill {x1+1} {y1+1} {z1+1} {x2-1} {y2-1} {z2-1} air",
        f"fill {x1} {y2} {z1} {x2} {y2} {z2} minecraft:birch_planks",
        f"setblock {cx} {y1+1} {z1} air",
        f"setblock {cx} {y1+2} {z1} air",
        f"setblock {x1+1} {y1+2} {z1} minecraft:glass_pane",
        f"setblock {x2-1} {y1+2} {z1} minecraft:glass_pane",
        f"setblock {x1} {y1+2} {mz} minecraft:glass_pane",
        f"setblock {x2} {y1+2} {mz} minecraft:glass_pane",
        f"setblock {cx} {y1+2} {z2} minecraft:glass_pane",
        f"setblock {cx} {y1+1} {mz} minecraft:torch",
    ]


# ═══════════════════════════════════════════════════════════════
#  CLIENTE OPENCODE
# ═══════════════════════════════════════════════════════════════

class OpenCodeChat:
    """
    Cliente para la API REST de OpenCode Web.
    Mantiene una sesión de conversación con contexto persistente.

    API resumida:
        POST /session                          — crear sesión
        POST /session/{id}/message             — enviar mensaje
        GET  /global/health                    — verificar servidor
    """

    def __init__(self, host: str = OPENCODE_HOST, port: int = OPENCODE_PORT):
        self.base_url   = f"http://{host}:{port}"
        self.session_id: Optional[str] = None

    def health_check(self) -> bool:
        try:
            r = requests.get(f"{self.base_url}/global/health", timeout=4)
            return r.status_code == 200
        except Exception:
            return False

    def crear_sesion(self, titulo: str = "IoCraft Chat") -> str:
        r = requests.post(
            f"{self.base_url}/session",
            json={"title": titulo},
            timeout=10,
        )
        r.raise_for_status()
        self.session_id = r.json()["id"]
        return self.session_id

    def enviar_mensaje(self, texto: str, timeout: int = 120) -> str:
        if not self.session_id:
            raise RuntimeError("Crea una sesión primero.")
        data = {"parts": [{"type": "text", "text": texto}]}
        r = requests.post(
            f"{self.base_url}/session/{self.session_id}/message",
            json=data,
            timeout=timeout,
        )
        r.raise_for_status()
        return self._extraer_texto(r.json())

    def _extraer_texto(self, mensaje: Dict[str, Any]) -> str:
        partes = mensaje.get("parts", [])
        textos = [p.get("text", "") for p in partes if p.get("type") == "text"]
        return "\n".join(textos).strip() or "(sin respuesta)"


# ═══════════════════════════════════════════════════════════════
#  EXTRACTOR DE COMANDOS MINECRAFT DESDE RESPUESTA DE LA IA
# ═══════════════════════════════════════════════════════════════

_RE_CMD_BLOCK = re.compile(
    r"```minecraft-commands\s*\n(.*?)```",
    re.DOTALL | re.IGNORECASE,
)

CMDS_VALIDOS = {
    "fill", "setblock", "say", "weather", "time", "give",
    "tp", "gamemode", "effect", "summon", "kill", "clear",
    "title", "tellraw",
}

def extraer_comandos_minecraft(respuesta: str) -> List[str]:
    """
    Detecta bloques ```minecraft-commands en la respuesta de la IA
    y retorna la lista de comandos listos para enviar a IoCraft.
    """
    comandos: List[str] = []
    for bloque in _RE_CMD_BLOCK.findall(respuesta):
        for linea in bloque.splitlines():
            linea = linea.strip()
            if not linea or linea.startswith("#"):
                continue
            if linea.startswith("/"):
                linea = linea[1:]
            if linea.split()[0] in CMDS_VALIDOS:
                comandos.append(linea)
    return comandos


# ═══════════════════════════════════════════════════════════════
#  FLUJOS DE MENÚ
# ═══════════════════════════════════════════════════════════════

def sep(s: Session) -> None:
    safe_print(s, "  " + "─" * 52)


def estado_str(s: Session) -> str:
    conn  = "CONECTADO"  if s.ws            else "SIN CONEXIÓN"
    auth  = "AUTH ✓"     if s.authenticated else "AUTH ✗"
    creds = "CREDS ✓"    if (s.device and s.secret) else "CREDS ✗"
    if s.authenticated and s.session_expires_at > 0:
        r    = session_time_remaining(s)
        mins = int(r // 60)
        segs = int(r % 60)
        ses  = f"SESIÓN {mins}m{segs:02d}s"
    else:
        ses = "SESIÓN —"
    return f"{conn} | {auth} | {creds} | {ses}"


def pedir_punto(s: Session, etiqueta: str, dx: int = 0, dy: int = 64, dz: int = 0) -> Optional[Tuple[int, int, int]]:
    safe_print(s, f"\n  Punto {etiqueta} — pulsa F3 en Minecraft.")
    safe_print(s,  "  Deja X vacío para cancelar.\n")
    xs = ask("X (este/oeste)", str(dx) if dx else None)
    if not xs:
        return None
    try:
        x = int(xs)
        y = ask_int("Y (altura)   ", dy)
        z = ask_int("Z (norte/sur)", dz)
        return x, y, z
    except ValueError:
        safe_print(s, "  ✗ Valor no válido.")
        return None


def ordenar_esquinas(ax, ay, az, bx, by, bz):
    return min(ax,bx), min(ay,by), min(az,bz), max(ax,bx), max(ay,by), max(az,bz)


# ── Credenciales ──────────────────────────────────────────────

def accion_credenciales(s: Session) -> None:
    sep(s)
    safe_print(s, "  CONFIGURAR CREDENCIALES\n")
    safe_print(s, "  En Minecraft: /ioc menu → registra 'python-bot' con rol cmd → copia SECRET\n")
    s.device = ask("Device ID", s.device or "python-bot")
    s.secret = ask("Secret   ", s.secret or "")
    if s.device and s.secret:
        safe_print(s, f"\n  ✓ Credenciales guardadas para '{s.device}'.")
    else:
        safe_print(s, "\n  ⚠  Credenciales incompletas.")


# ── Autenticar ────────────────────────────────────────────────

def accion_autenticar(s: Session) -> None:
    sep(s)
    safe_print(s, "  AUTENTICAR\n")
    if not (s.device and s.secret):
        safe_print(s, "  ✗ Configura credenciales primero (opción 1).")
        return
    if s.ws is None:
        safe_print(s, "  ✗ Sin conexión. Usa la opción 5 para reconectar.")
        return
    do_hello(s)


# ── Construir casa (manual) ───────────────────────────────────

def accion_construir_casa(s: Session) -> None:
    sep(s)
    safe_print(s, "  CONSTRUIR CASA\n")

    if not s.authenticated:
        safe_print(s, "  ✗ Autentícate primero (opción 2).")
        return

    safe_print(s, "  Indica las dos esquinas opuestas del área.\n")

    p1 = pedir_punto(s, "Inicial (esquina 1)")
    if p1 is None:
        safe_print(s, "  Cancelado.")
        return
    ax, ay, az = p1

    p2 = pedir_punto(s, "Final   (esquina 2)", dx=ax+8, dy=ay+4, dz=az+6)
    if p2 is None:
        safe_print(s, "  Cancelado.")
        return
    bx, by, bz = p2

    x1, y1, z1, x2, y2, z2 = ordenar_esquinas(ax, ay, az, bx, by, bz)

    ancho_r    = max(x2-x1+1, 5)
    alto_r     = max(y2-y1+1, 4)
    profundo_r = max(z2-z1+1, 3)

    safe_print(s, f"\n  Esquina 1 : X={x1}  Y={y1}  Z={z1}")
    safe_print(s, f"  Esquina 2 : X={x2}  Y={y2}  Z={z2}")
    safe_print(s, f"  Dimensiones: {ancho_r} × {alto_r} × {profundo_r}")
    safe_print(s,  "  ⚠  El área se limpiará antes de construir.\n")

    if ask("¿Construir aquí? (s/N)", "N").lower() != "s":
        safe_print(s, "  Cancelado.")
        return

    ejecutar_lista_cmds(s, cmds_casa(x1, y1, z1, x2, y2, z2))
    safe_print(s, "\n  ¡Busca la casa en el juego!")


# ── Chat con agente IA ────────────────────────────────────────

def accion_chat_ia(s: Session) -> None:
    sep(s)
    safe_print(s, "  CHAT CON AGENTE IA\n")

    if not s.authenticated:
        safe_print(s, "  ✗ Autentícate con IoCraft primero (opción 2).")
        return

    # Verificar OpenCode
    oc = OpenCodeChat(OPENCODE_HOST, OPENCODE_PORT)
    safe_print(s, f"  Verificando OpenCode en {oc.base_url} ...")

    if not oc.health_check():
        safe_print(s, "  ✗ OpenCode no está corriendo.")
        safe_print(s, "    Inicia desde esta carpeta:  opencode web")
        safe_print(s, "    (opencode.json carga el prompt automáticamente)\n")
        return

    safe_print(s, "  ✓ OpenCode activo.\n")

    # Crear sesión
    try:
        session_id = oc.crear_sesion("IoCraft Builder Chat")
        safe_print(s, f"  Sesión IA: {session_id[:24]}...\n")
    except Exception as exc:
        safe_print(s, f"  ✗ Error creando sesión OpenCode: {exc}\n")
        return

    # Cargar prompt desde archivo (solo si OpenCode no lo cargó via opencode.json)
    try:
        with open(PROMPT_PATH, encoding="utf-8") as f:
            prompt_sistema = f.read()
        safe_print(s, "  Enviando contexto al agente...")
        oc.enviar_mensaje(
            f"[CONTEXTO DE SISTEMA]\n\n{prompt_sistema}\n\nConfirma con: Listo."
        )
        safe_print(s, "  ✓ Agente listo.\n")
    except FileNotFoundError:
        # Si opencode.json ya cargó el prompt, esto no es necesario
        safe_print(s, "  ✓ Prompt cargado via opencode.json.\n")
    except Exception as exc:
        safe_print(s, f"  ✗ Error inicializando agente: {exc}\n")
        return

    # Loop de chat
    sep(s)
    safe_print(s, "  Chat activo. Escribe tu mensaje o 'salir' para volver al menú.")
    safe_print(s, "  Comandos especiales: salir | estado | monitor on | monitor off")
    sep(s)
    print()

    while True:
        try:
            with s.print_lock:
                user_input = input("  Tú › ").strip()
        except (EOFError, KeyboardInterrupt):
            safe_print(s, "\n  Saliendo del chat...")
            break

        if not user_input:
            continue

        if user_input.lower() == "salir":
            break
        if user_input.lower() == "estado":
            safe_print(s, f"\n  {estado_str(s)}\n")
            continue
        if user_input.lower() == "monitor on":
            s.monitor_enabled = True
            safe_print(s, "  Monitor RX activado.\n")
            continue
        if user_input.lower() == "monitor off":
            s.monitor_enabled = False
            safe_print(s, "  Monitor RX desactivado.\n")
            continue

        # Enviar a OpenCode
        try:
            safe_print(s, "\n  [IA pensando...]\n")
            respuesta = oc.enviar_mensaje(user_input)
        except Exception as exc:
            safe_print(s, f"\n  ✗ Error consultando OpenCode: {exc}\n")
            continue

        # Mostrar respuesta (reemplaza el bloque de comandos por un aviso)
        texto_limpio = _RE_CMD_BLOCK.sub(
            "[comandos detectados — listos para ejecutar]", respuesta
        )
        safe_print(s, f"  IA › {texto_limpio}\n")

        # Extraer y ejecutar comandos si los hay
        comandos = extraer_comandos_minecraft(respuesta)
        if comandos:
            safe_print(s, f"  [{len(comandos)} comandos para Minecraft]\n")
            with s.print_lock:
                confirmar = input("  ¿Ejecutar en Minecraft? (s/N) › ").strip().lower()
            if confirmar == "s":
                ejecutar_lista_cmds(s, comandos)
            else:
                safe_print(s, "  Comandos descartados.\n")

    safe_print(s, "\n  Chat cerrado. Volviendo al menú...\n")


# ── Reconectar ────────────────────────────────────────────────

def accion_reconectar(s: Session) -> None:
    sep(s)
    safe_print(s, "  RECONFIGURAR CONEXIÓN\n")
    close_session(s)
    s.host    = ask("Host   ", s.host)
    s.port    = int(ask("Puerto ", str(s.port)))
    name_str  = ask("Name URL (vacío para omitir)", s.name or "")
    s.name    = name_str if name_str else None
    s.timeout = float(ask("Timeout (s)", str(s.timeout)))
    connect_session(s)


# ═══════════════════════════════════════════════════════════════
#  MENÚ PRINCIPAL
# ═══════════════════════════════════════════════════════════════

BANNER = r"""
  ___    ___                     ___   _
 |_ _|  / _ \   ___  _ __  __ _|  __|| |_
  | |  | | | | / __|| '__|/ _` | |__ | __|
  | |  | |_| || (__ | |  | (_| |  __|| |_
 |___|  \___/  \___||_|   \__,_|_|    \__|
       Builder + IA Chat — IoCraft Mod
"""


def menu_loop(s: Session) -> None:
    while True:
        safe_print(s, "\n")
        sep(s)
        safe_print(s, f"  {estado_str(s)}")
        sep(s)
        safe_print(s, "  [1] Configurar credenciales (device / secret)")
        safe_print(s, "  [2] Autenticar con Minecraft (hello)")
        safe_print(s, "  [3] Construir casa")
        safe_print(s, "  [4] Chat con agente IA")
        safe_print(s, "  [5] Reconectar / cambiar servidor")
        safe_print(s, "  [0] Salir")
        sep(s)

        opcion = input("  Opción › ").strip()

        if   opcion == "1": accion_credenciales(s)
        elif opcion == "2": accion_autenticar(s)
        elif opcion == "3": accion_construir_casa(s)
        elif opcion == "4": accion_chat_ia(s)
        elif opcion == "5": accion_reconectar(s)
        elif opcion == "0":
            close_session(s)
            safe_print(s, "\n  ¡Hasta luego!\n")
            break
        else:
            safe_print(s, "  ⚠  Opción no válida.")


# ═══════════════════════════════════════════════════════════════
#  ARRANQUE
# ═══════════════════════════════════════════════════════════════

def main() -> int:
    print(BANNER)
    print("  Configuración inicial de conexión IoCraft")
    print("  " + "─" * 52)

    host    = ask("Host   ", "127.0.0.1")
    port    = int(ask("Puerto ", "8765"))
    name    = ask("Name URL (vacío para omitir)", "")
    timeout = float(ask("Timeout (s)", "5"))

    s = Session(
        host    = host,
        port    = port,
        timeout = timeout,
        name    = name if name else None,
        device  = None,
        secret  = None,
    )

    if not connect_session(s):
        print("\n  Comprueba que Minecraft está corriendo con IoCraft.")
        print("  Puedes reconectar desde el menú (opción 5).\n")
    else:
        print("\n  ✓ Conexión IoCraft establecida.")
        print("  Usa la opción 1 para configurar credenciales.")
        print("  Para el chat IA inicia OpenCode desde esta carpeta:\n")
        print("    opencode web\n")

    menu_loop(s)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
