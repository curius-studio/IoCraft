package com.curius.iocraft.ws;

import com.curius.iocraft.blocks.computer.ComputerSocketBridge;
import com.curius.iocraft.iot.MensajeIoTParser;
import com.curius.iocraft.iot.NotificadorIoT;
import com.curius.iocraft.mensajeria.MensajeriaBus;
import com.curius.iocraft.security.AuthManager;
import com.curius.iocraft.security.BlacklistManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanWebSocketServer {
    private static final Logger LOGGER = LogManager.getLogger("WS-NETTY");
    private static final Gson GSON = new Gson();

    private final String host;
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    // canal -> id
    private final Map<ChannelId, UUID> channelToId = new ConcurrentHashMap<>();
    // id -> canal (para send/close directo)
    private final Map<UUID, Channel> idToChannel = new ConcurrentHashMap<>();

    public LanWebSocketServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpServerCodec());
                        p.addLast(new HttpObjectAggregator(65536));
                        p.addLast(new ChunkedWriteHandler());
                        // Acepta cualquier path; parseamos ?name= a mano
                        p.addLast(new WebSocketServerProtocolHandler("/", null, true, 65536, false, true));
                        p.addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete hc) {
                                    try {
                                        InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
                                        String ip = (addr != null) ? addr.getHostString() : null;
                                        if (BlacklistManager.isIpBlocked(ip)) {
                                            LOGGER.warn("WS: Bloqueado por IP {}", ip);
                                            ctx.close();
                                            return;
                                        }

                                        // nombre desde query (?name=)
                                        URI uri = new URI(hc.requestUri());
                                        String nombre = null;
                                        String q = uri.getQuery();
                                        if (q != null) {
                                            for (String part : q.split("&")) {
                                                if (part.startsWith("name=")) {
                                                    nombre = part.substring(5);
                                                    break;
                                                }
                                            }
                                        }

                                        UUID id = UUID.randomUUID();
                                        channelToId.put(ctx.channel().id(), id);
                                        idToChannel.put(id, ctx.channel());

                                        DeviceInfo info = new DeviceInfo(id, nombre, addr);
                                        DeviceRegistry.add(info);

                                        LOGGER.info("WS: Conectado {} ({})", info.nombre, info.ip);

                                        // hello inicial (solo informativo)
                                        JsonObject hello = new JsonObject();
                                        hello.addProperty("type", "hello");
                                        hello.addProperty("id", id.toString());
                                        hello.addProperty("server", "minecraft-1.18.2");
                                        ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(hello)));

                                        pokeUi();
                                    } catch (Exception e) {
                                        LOGGER.error("Handshake parse error: {}", e.toString(), e);
                                    }
                                } else {
                                    ctx.fireUserEventTriggered(evt);
                                }
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                                UUID id = channelToId.get(ctx.channel().id());
                                if (id == null) return;

                                final String raw = frame.text();
                                final JsonObject obj = GSON.fromJson(raw, JsonObject.class);
                                final String tipo = (obj != null && obj.has("type") && !obj.get("type").isJsonNull())
                                        ? obj.get("type").getAsString() : null;

                                // 1) Handshake siempre permitido
                                if ("hello".equalsIgnoreCase(tipo)) {
                                    String deviceRaw = (obj != null && obj.has("device") && !obj.get("device").isJsonNull())
                                            ? obj.get("device").getAsString() : null;
                                    if (BlacklistManager.isDeviceBlocked(deviceRaw)) {
                                        JsonObject err = new JsonObject();
                                        err.addProperty("type", "error");
                                        err.addProperty("code", "blocked_device");
                                        err.addProperty("message", "Dispositivo bloqueado.");
                                        ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(err)));
                                        LOGGER.warn("WS: Bloqueado device='{}' ip={}", deviceRaw, ctx.channel().remoteAddress());
                                        UUID closeId = channelToId.remove(ctx.channel().id());
                                        if (closeId != null) {
                                            idToChannel.remove(closeId);
                                            DeviceRegistry.remove(closeId);
                                        }
                                        ctx.close();
                                        return;
                                    }
                                    MensajeriaBus.onReceive(id, raw); // sin contexto
                                    return;
                                }

                                // 2) Autenticación básica
                                if (!AuthManager.isMessageAllowed(tipo, id)) {
                                    JsonObject err = new JsonObject();
                                    err.addProperty("type", "error");
                                    err.addProperty("code", "unauthenticated");
                                    err.addProperty("message", "Debes enviar 'hello' primero.");
                                    ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(err)));
                                    return;
                                }

                                // 3) Chequeo de ROLES a nivel WS (corta temprano por tipo)
                                if ("sensor".equalsIgnoreCase(tipo) && !AuthManager.hasRole(id, "sensor")) {
                                    JsonObject err = new JsonObject();
                                    err.addProperty("type", "error");
                                    err.addProperty("code", "forbidden");
                                    err.addProperty("message", "No tienes rol 'sensor'.");
                                    ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(err)));
                                    return;
                                }
                                if ("comando".equalsIgnoreCase(tipo) && !AuthManager.hasRole(id, "cmd")) {
                                    JsonObject err = new JsonObject();
                                    err.addProperty("type", "error");
                                    err.addProperty("code", "forbidden");
                                    err.addProperty("message", "No tienes rol 'cmd'.");
                                    ctx.writeAndFlush(new TextWebSocketFrame(GSON.toJson(err)));
                                    return;
                                }

                                // 4) Construir Contexto (mundo/pos/device) desde el JSON para que el handler sepa a qué bloque va
                                MensajeriaBus.Contexto ctxMsg = MensajeriaBus.Contexto.vacio();
                                try {
                                    String mundo = obj.has("mundo") && !obj.get("mundo").isJsonNull()
                                            ? obj.get("mundo").getAsString() : "overworld";
                                    int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
                                    int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
                                    int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
                                    String device = obj.has("device") && !obj.get("device").isJsonNull()
                                            ? obj.get("device").getAsString() : null;

                                    var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                                    ServerLevel level = levelFromString(server, mundo);
                                    BlockPos pos = new BlockPos(x, y, z);

                                    ctxMsg = MensajeriaBus.Contexto.of(level, pos, device);
                                } catch (Throwable ignore) {}

                                // 5) Pasar SIEMPRE por el Bus con contexto (aquí ya decide RegistroManejadores)
                                MensajeriaBus.onReceive(id, raw, ctxMsg);

                                // 6) (Opcional) solo espejo visual al cliente local si el emisor tiene rol 'sensor'
                                if (AuthManager.hasRole(id, "sensor")) {
                                    try {
                                        var parsed = MensajeIoTParser.parse(raw);
                                        if (parsed != null) {
                                            NotificadorIoT.mostrarAlJugador(parsed);
                                            // Si además quieres monitor del “Computer” solo bajo auth:
                                            Minecraft mc = Minecraft.getInstance();
                                            if (mc != null && mc.level != null && AuthManager.isAuthenticated(id)) {
                                                mc.execute(() -> ComputerSocketBridge.onIncoming(parsed, mc.level));
                                            }
                                        }
                                    } catch (Throwable t) {
                                        LOGGER.debug("Mirror UI error: {}", t.toString());
                                    }
                                }
                            }

                            // Helper: mapea string de mundo a ServerLevel (1.18.2)
                            private ServerLevel levelFromString(net.minecraft.server.MinecraftServer server, String mundo) {
                                if (server == null) return null;
                                String m = (mundo == null ? "" : mundo.toLowerCase()).replace("minecraft:", "");
                                switch (m) {
                                    case "the_nether":
                                    case "nether":
                                        return server.getLevel(net.minecraft.world.level.Level.NETHER);
                                    case "the_end":
                                    case "end":
                                        return server.getLevel(net.minecraft.world.level.Level.END);
                                    default:
                                        return server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                UUID id = channelToId.remove(ctx.channel().id());
                                if (id != null) {
                                    idToChannel.remove(id);
                                    DeviceRegistry.remove(id);
                                }
                                LOGGER.info("WS: Cerrado {}", ctx.channel().remoteAddress());
                                pokeUi();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                LOGGER.error("WS error {}: {}", ctx.channel().remoteAddress(), cause.toString(), cause);
                                ctx.close();
                            }
                        });
                    }
                });

        ChannelFuture f = b.bind(host, port).sync();
        serverChannel = f.channel();
        LOGGER.info("WS Netty iniciado en ws://{}:{}/", host, port);
    }

    public void stop() {
        try {
            if (serverChannel != null) serverChannel.close().syncUninterruptibly();
        } catch (Exception ignored) {}
        try {
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
        } catch (Exception ignored) {}
        channelToId.clear();
        idToChannel.clear();
        LOGGER.info("WS Netty detenido");
        pokeUi();
    }

    /** Envía texto a un dispositivo por UUID. */
    public boolean sendTo(UUID id, String text) {
        Channel ch = idToChannel.get(id);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(new TextWebSocketFrame(text));
            return true;
        }
        return false;
    }

    /** Cierra (expulsa) el dispositivo por UUID. */
    public void close(UUID id) {
        Channel ch = idToChannel.get(id);
        if (ch != null) {
            ch.close().addListener(f -> pokeUi());
        }
    }

    /** “Pica” el hilo de render para refrescar UI. */
    private void pokeUi() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(() -> {});
        } catch (Throwable ignored) {}
    }
}
