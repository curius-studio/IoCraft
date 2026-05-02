#!/usr/bin/env python3
import hashlib
import hmac
import json
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any, Dict, Optional

try:
    import websocket
except ImportError as exc:
    raise SystemExit("Falta dependencia: pip install websocket-client") from exc


def sign(secret: str, device: str, ts: int, nonce: str) -> str:
    msg = f"{device}:{ts}:{nonce}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()


def make_url(host: str, port: int, name: Optional[str]) -> str:
    base = f"ws://{host}:{port}/"
    return f"{base}?name={name}" if name else base


def recv_json(ws, timeout: float) -> Dict[str, Any]:
    ws.settimeout(timeout)
    raw = ws.recv()
    try:
        return json.loads(raw)
    except Exception:
        return {"raw": raw}


def send_json(ws, payload: Dict[str, Any]) -> None:
    ws.send(json.dumps(payload, ensure_ascii=False))


SESSION_RENEW_BEFORE_S = 60


@dataclass
class Session:
    host: str
    port: int
    timeout: float
    name: Optional[str]
    device: Optional[str]
    secret: Optional[str]
    ws: Any = None
    authenticated: bool = False
    session_expires_at: float = 0.0
    last_nonce: Optional[str] = None
    last_ts: Optional[int] = None
    monitor_enabled: bool = True
    stop_listener: bool = False
    listener_thread: Optional[threading.Thread] = None
    print_lock: Any = None


def safe_print(s: Session, *args, **kwargs) -> None:
    if s.print_lock is None:
        print(*args, **kwargs)
        return
    with s.print_lock:
        print(*args, **kwargs)


def ask(prompt: str, default: Optional[str] = None) -> str:
    suffix = f" [{default}]" if default is not None else ""
    v = input(f"{prompt}{suffix}: ").strip()
    return default if v == "" and default is not None else v


def configure_session() -> Session:
    print("Configuración inicial")
    host = ask("Host", "127.0.0.1")
    port = int(ask("Port", "8765"))
    name = ask("Name URL (opcional)", "")
    timeout = float(ask("Timeout (segundos)", "4"))
    return Session(
        host=host,
        port=port,
        timeout=timeout,
        name=(None if not name else name),
        device=None,
        secret=None,
        print_lock=threading.Lock(),
    )


def listener_loop(s: Session) -> None:
    while not s.stop_listener:
        if s.ws is None:
            break
        try:
            msg = recv_json(s.ws, 0.5)
            if s.monitor_enabled:
                safe_print(s, "\n[RX]", json.dumps(msg, ensure_ascii=False))
                safe_print(s, "> ", end="", flush=True)
        except Exception:
            continue


def start_listener(s: Session) -> None:
    stop_listener(s)
    s.stop_listener = False
    t = threading.Thread(target=listener_loop, args=(s,), daemon=True, name="iocraft-rx-listener")
    s.listener_thread = t
    t.start()


def stop_listener(s: Session) -> None:
    s.stop_listener = True
    t = s.listener_thread
    if t is not None and t.is_alive():
        t.join(timeout=1.2)
    s.listener_thread = None


def unwrap_ack(ack: Dict[str, Any]) -> Dict[str, Any]:
    data = ack.get("data")
    if isinstance(data, dict):
        return data
    return ack


def connect_session(s: Session) -> bool:
    try:
        url = make_url(s.host, s.port, s.name)
        s.ws = websocket.create_connection(url, timeout=s.timeout)
        safe_print(s, f"\nConectado a {url}")
        server_hello = recv_json(s.ws, s.timeout)
        safe_print(s, "SERVER_HELLO:", json.dumps(server_hello, indent=2, ensure_ascii=False))
        s.authenticated = False
        start_listener(s)
        return True
    except Exception as exc:
        safe_print(s, f"Error conectando: {exc}")
        s.ws = None
        return False


def close_session(s: Session) -> None:
    stop_listener(s)
    if s.ws is not None:
        try:
            s.ws.close()
        except Exception:
            pass
    s.ws = None
    s.authenticated = False


def do_hello(s: Session, reuse_last: bool = False) -> Dict[str, Any]:
    if s.ws is None:
        return {"ok": False, "code": "not_connected", "message": "No hay conexión activa"}
    if not s.device or not s.secret:
        safe_print(s, "Credenciales no configuradas. Usa la opción 5 para configurar device/secret.")
        return {"ok": False, "code": "missing_credentials", "message": "Falta device/secret"}

    ts = s.last_ts if reuse_last and s.last_ts is not None else int(time.time() * 1000)
    nonce = s.last_nonce if reuse_last and s.last_nonce else str(uuid.uuid4())
    payload = {
        "type": "hello",
        "device": s.device,
        "ts": ts,
        "nonce": nonce,
        "sig": sign(s.secret, s.device, ts, nonce),
    }

    stop_listener(s)
    send_json(s.ws, payload)
    ack = recv_json(s.ws, s.timeout)
    start_listener(s)
    ack_data = unwrap_ack(ack)

    s.last_ts    = ts
    s.last_nonce = nonce
    s.authenticated = ack_data.get("ok") is True

    if s.authenticated:
        expires_ms = ack_data.get("expiresAt", 0)
        s.session_expires_at = expires_ms / 1000.0 if expires_ms else 0.0
    else:
        s.session_expires_at = 0.0

    safe_print(s, "HELLO_PAYLOAD:", json.dumps(payload, indent=2, ensure_ascii=False))
    safe_print(s, "HELLO_ACK:", json.dumps(ack, indent=2, ensure_ascii=False))
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
        safe_print(s, f"\n[sesión] Renovando automáticamente (quedan {restante:.0f}s)...")
        ack = do_hello(s, reuse_last=False)
        if not ack.get("ok"):
            safe_print(s, f"[sesión] ✗ Renovación fallida: {ack.get('code')}")
            return False
        nuevo_exp = time.strftime("%H:%M:%S", time.localtime(s.session_expires_at))
        safe_print(s, f"[sesión] ✓ Sesión renovada hasta {nuevo_exp}")
    return s.authenticated


def do_replay_test(s: Session) -> None:
    safe_print(s, "\nReplay test: se envían 2 hello con mismo nonce/ts")
    ack1 = do_hello(s, reuse_last=False)
    ack2 = do_hello(s, reuse_last=True)
    ok1 = ack1.get("ok") is True
    blocked = ack2.get("code") == "replay_nonce" or (ack2.get("ok") is False and ack2.get("code") == "replay_nonce")
    safe_print(s, "RESULTADO:", "OK" if (ok1 and blocked) else "FALLÓ")


def send_message(s: Session, msg_type: str) -> None:
    if s.ws is None:
        safe_print(s, "No hay conexión activa.")
        return
    if not s.authenticated:
        safe_print(s, "Aviso: enviarás sin auth (útil para probar errores del servidor).")
    else:
        ensure_session(s)

    data = ask("data")
    x = int(ask("x", "0"))
    y = int(ask("y", "64"))
    z = int(ask("z", "0"))
    mundo = ask("mundo (vacío para omitir)", "")
    device_in_payload = ask("device en payload", s.device or "")

    payload = {
        "type": msg_type,
        "x": x,
        "y": y,
        "z": z,
        "data": data,
    }
    if device_in_payload:
        payload["device"] = device_in_payload
    if mundo:
        payload["mundo"] = mundo

    send_json(s.ws, payload)
    safe_print(s, "ENVIADO:", json.dumps(payload, indent=2, ensure_ascii=False))


def reconnect_flow(s: Session) -> None:
    close_session(s)
    safe_print(s, "")
    s.host = ask("Host", s.host)
    s.port = int(ask("Port", str(s.port)))
    s.name = ask("Name URL (opcional)", s.name or "")
    s.name = None if not s.name else s.name
    s.timeout = float(ask("Timeout (segundos)", str(s.timeout)))
    connect_session(s)


def configure_credentials(s: Session) -> None:
    s.device = ask("Device ID", s.device or "")
    s.secret = ask("Secret", s.secret or "")
    if not s.device or not s.secret:
        safe_print(s, "Credenciales incompletas.")
        return
    safe_print(s, "Credenciales guardadas.")


def credentials_status(s: Session) -> str:
    return "OK" if (s.device and s.secret) else "NO"


def ensure_credentials(s: Session) -> bool:
    if s.device and s.secret:
        return True
    safe_print(s, "No hay credenciales configuradas.")
    auto = ask("¿Configurar ahora? (y/n)", "y").lower().startswith("y")
    if not auto:
        return False
    configure_credentials(s)
    return bool(s.device and s.secret)


def menu_loop(s: Session) -> int:
    while True:
        safe_print(s, "")
        safe_print(
            s,
            f"Conexión: {'ACTIVA' if s.ws else 'CERRADA'} | "
            f"Credenciales: {credentials_status(s)} | "
            f"Auth: {'OK' if s.authenticated else 'NO'}"
            + (f" | Sesión: {int(session_time_remaining(s) // 60)}m{int(session_time_remaining(s) % 60):02d}s"
               if s.authenticated and s.session_expires_at > 0 else "") +
            f" | Monitor RX: {'ACTIVO' if s.monitor_enabled else 'PAUSADO'}"
        )
        safe_print(s, "1) Hello autenticado")
        safe_print(s, "2) Probar anti-replay (nonce duplicado)")
        safe_print(s, "3) Enviar mensaje tipo sensor")
        safe_print(s, "4) Enviar mensaje tipo cmd")
        safe_print(s, "5) Configurar credenciales (device/secret)")
        safe_print(s, "6) Reconfigurar y reconectar")
        safe_print(s, "7) Ver config actual")
        safe_print(s, "8) Activar/Desactivar monitor RX en vivo")
        safe_print(s, "0) Salir")
        opt = input("Opción: ").strip()

        try:
            if opt == "1":
                if not ensure_credentials(s):
                    continue
                do_hello(s, reuse_last=False)
            elif opt == "2":
                if not ensure_credentials(s):
                    continue
                do_replay_test(s)
            elif opt == "3":
                send_message(s, "sensor")
            elif opt == "4":
                send_message(s, "cmd")
            elif opt == "5":
                configure_credentials(s)
            elif opt == "6":
                reconnect_flow(s)
            elif opt == "7":
                safe_print(s, json.dumps({
                    "host": s.host,
                    "port": s.port,
                    "name": s.name,
                    "device": s.device,
                    "secret_configured": bool(s.secret),
                    "timeout": s.timeout,
                    "connected": s.ws is not None,
                    "authenticated": s.authenticated,
                    "monitor_enabled": s.monitor_enabled
                }, indent=2, ensure_ascii=False))
            elif opt == "8":
                s.monitor_enabled = not s.monitor_enabled
                safe_print(s, f"Monitor RX en vivo: {'ACTIVO' if s.monitor_enabled else 'PAUSADO'}")
            elif opt == "0":
                close_session(s)
                return 0
            else:
                safe_print(s, "Opción inválida.")
        except Exception as exc:
            safe_print(s, f"Error: {exc}")


def main() -> int:
    session = configure_session()
    if not connect_session(session):
        return 1
    return menu_loop(session)


if __name__ == "__main__":
    raise SystemExit(main())
