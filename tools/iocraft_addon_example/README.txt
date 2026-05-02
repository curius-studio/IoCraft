IoCraft Addon Example (Fase 5)
================================

Este ejemplo muestra un mod compatible que consume la API publica de IoCraft.
No es parte del runtime core: sirve como referencia tecnica para terceros.

Contenido principal:
- src\main\java\com\curius\iocraft\addonexample\IoCraftAddonExampleMod.java

Que demuestra:
1) Deteccion de disponibilidad y version de API:
   - IoCraftApiProvider.isAvailable()
   - IoCraftApiProvider.apiMajor()/apiVersion()
2) Degradacion segura:
   - Si API no esta disponible o major no compatible, no registra handlers.
3) Registro de handler externo:
   - type: "addon/ping"
   - priority: 200
   - ownerModId: "iocraft_addon_example"
   - respuesta tipada: "addon/pong"
4) Integracion por eventos Forge:
   - IoCraftMessagePreProcessEvent (cancelar "addon/blocked")
   - IoCraftMessagePostProcessEvent (telemetria basica)
   - IoCraftAuthResultEvent (registro de auth fallida)

Uso:
- Copiar/adaptar esta clase dentro de un mod Forge independiente.
- Asegurar que IoCraft este cargado en el mismo entorno.
- Ajustar MOD_ID y politica de version segun necesidad.
