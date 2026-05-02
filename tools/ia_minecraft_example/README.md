# IoCraft + IA — Ejemplo de integración

Este ejemplo muestra cómo conectar un agente de IA (via **OpenCode Web**) con
el mod IoCraft para controlar Minecraft en lenguaje natural desde la terminal.

## Archivos

```
ia_minecraft_example/
  advanced_python_client.py   — cliente Python con menú + chat IA
  iocraft_agent_prompt.md     — prompt que le enseña al agente cómo usar IoCraft
  opencode.json               — configuración de OpenCode (carga el prompt automáticamente)
  README.md                   — este archivo
```

---

## Requisitos

- Python 3.10+
- Mod IoCraft instalado y Minecraft corriendo
- [OpenCode](https://opencode.ai) instalado (`npm i -g opencode-ai`)

Instalar dependencias Python:
```bash
pip install websocket-client requests
```

---

## Cómo usarlo

### 1. Registrar el dispositivo en Minecraft

```
/ioc menu  →  nombre: python-bot  →  rol: cmd  →  copiar SECRET
```

### 2. Iniciar OpenCode desde esta carpeta

```bash
cd tools/ia_minecraft_example
opencode web
```

El `opencode.json` carga `iocraft_agent_prompt.md` automáticamente — el agente
ya sabrá cómo formatear comandos para Minecraft sin necesidad de configuración extra.

### 3. Ejecutar el cliente

```bash
python advanced_python_client.py
```

### 4. Flujo dentro del cliente

```
[1] Configurar credenciales  →  pegar el SECRET del paso 1
[2] Autenticar               →  conecta con IoCraft
[3] Construir casa           →  construcción manual por coordenadas
[4] Chat con agente IA       →  lenguaje natural → comandos → Minecraft
[5] Reconectar
[0] Salir
```

---

## Cómo funciona el chat con IA

```
Tú › Construye una torre de piedra 5x5 y 15 bloques de alto en X=100 Y=64 Z=-50

  [IA pensando...]

  IA › Voy a construir una torre de piedra de 5×5 en la base...
       [comandos detectados — listos para ejecutar]
       Generé 5 comandos. Torre en X=100–104, Y=64–78, Z=-50–-46.

  [5 comandos para Minecraft]
  ¿Ejecutar en Minecraft? (s/N) › s
  Ejecutando 5 comandos en Minecraft...
  ✓ Completado — 5 comandos ejecutados.
```

El agente genera bloques ` ```minecraft-commands ` en su respuesta.
El cliente los detecta, los muestra y pide confirmación antes de ejecutarlos.

---

## Comandos especiales dentro del chat

| Comando      | Acción                              |
|--------------|-------------------------------------|
| `salir`      | Vuelve al menú principal            |
| `estado`     | Muestra el estado de la sesión      |
| `monitor on` | Activa logs de mensajes RX          |
| `monitor off`| Desactiva logs de mensajes RX       |

---

## Renovación de sesión automática

El SECRET del dispositivo es **permanente**.
La sesión (token de acceso) dura **10 minutos** — el cliente la renueva
automáticamente antes de cada envío sin intervención del administrador.

---

## Personalizar el agente

Edita `iocraft_agent_prompt.md` para cambiar el comportamiento del agente:
- Idioma de respuesta
- Materiales preferidos para construcción
- Restricciones o reglas específicas
- Nuevos tipos de comandos permitidos
