package com.eia.reproductor.servicios.spotify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Servidor minimo que recibe el retorno del navegador tras autorizar en Spotify.
 *
 * <p><b>Por que hace falta.</b> El flujo de OAuth termina con el navegador redirigiendo a una
 * direccion. Para que esa redireccion llegue a la aplicacion, tiene que haber algo escuchando en
 * esa direccion; de ahi el servidor. Se usa {@code com.sun.net.httpserver}, que ya viene en el JDK,
 * para no sumar ninguna dependencia por veinte lineas de HTTP.</p>
 *
 * <p><b>Vive lo minimo posible.</b> Se levanta justo antes de abrir el navegador y se apaga en
 * cuanto recibe el codigo, gracias a {@link AutoCloseable}. Escucha unicamente en
 * {@code 127.0.0.1}, nunca en todas las interfaces: asi el puerto no queda expuesto a la red del
 * salon ni un segundo.</p>
 *
 * <p><b>Sobre el parametro {@code state}.</b> Se genera al azar al iniciar y se compara al recibir
 * la respuesta. Si no coincide, la peticion se rechaza: es lo que impide que un tercero induzca al
 * navegador a entregar un codigo de otra sesion.</p>
 */
final class ServidorDeRetorno implements AutoCloseable {

    private static final int SIN_COLA_DE_ESPERA = 0;
    private static final int CIERRE_INMEDIATO_SEGUNDOS = 0;

    private final HttpServer servidor;
    private final CountDownLatch respuestaRecibida = new CountDownLatch(1);
    private final String estadoEsperado;

    private String codigo;
    private String motivoDelFallo;

    /**
     * Levanta el servidor y queda escuchando.
     *
     * @param puerto         puerto local, tomado del redirect uri configurado
     * @param ruta           ruta del callback, tomada del redirect uri configurado
     * @param estadoEsperado valor aleatorio que la respuesta debe traer de vuelta
     * @throws IOException si el puerto ya esta ocupado
     */
    ServidorDeRetorno(int puerto, String ruta, String estadoEsperado) throws IOException {
        this.estadoEsperado = estadoEsperado;
        this.servidor = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto),
                SIN_COLA_DE_ESPERA);
        this.servidor.createContext(ruta, this::atender);
        this.servidor.start();
    }

    /**
     * Espera a que el navegador entregue el codigo.
     *
     * @param tiempoMaximo cuanto esperar antes de rendirse
     * @return el codigo de autorizacion, o vacio si hubo fallo o se agoto la espera
     */
    Optional<String> esperarCodigo(Duration tiempoMaximo) {
        try {
            if (!respuestaRecibida.await(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                motivoDelFallo = "Se agotó el tiempo esperando la autorización en el navegador.";
                return Optional.empty();
            }
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            motivoDelFallo = "La espera de la autorización se interrumpió.";
            return Optional.empty();
        }
        return Optional.ofNullable(codigo);
    }

    /** @return el motivo por el que no se obtuvo el codigo, si lo hubo */
    Optional<String> motivoDelFallo() {
        return Optional.ofNullable(motivoDelFallo);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        Map<String, String> parametros = parametrosDe(intercambio.getRequestURI().getRawQuery());
        String titulo;
        String mensaje;

        if (parametros.containsKey("error")) {
            // Ocurre cuando el usuario pulsa "Cancelar" en la pantalla de Spotify.
            motivoDelFallo = "Spotify denegó la autorización: " + parametros.get("error");
            titulo = "Autorización cancelada";
            mensaje = "No se autorizó el acceso. Podés cerrar esta pestaña.";
        } else if (!estadoEsperado.equals(parametros.get("state"))) {
            motivoDelFallo = "La respuesta no corresponde a esta sesión de autorización.";
            titulo = "Respuesta inválida";
            mensaje = "La respuesta no corresponde a esta sesión. Intentá de nuevo.";
        } else if (parametros.get("code") == null) {
            motivoDelFallo = "Spotify no devolvió ningún código de autorización.";
            titulo = "Respuesta incompleta";
            mensaje = "Spotify no devolvió el código. Intentá de nuevo.";
        } else {
            codigo = parametros.get("code");
            titulo = "Listo";
            mensaje = "Autorización completada. Ya podés volver al reproductor.";
        }

        responder(intercambio, titulo, mensaje);
        respuestaRecibida.countDown();
    }

    private void responder(HttpExchange intercambio, String titulo, String mensaje)
            throws IOException {
        byte[] cuerpo = ("""
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <title>%s</title></head>
                <body style="background:#0b1020;color:#e8e8f0;font-family:monospace;
                             display:flex;align-items:center;justify-content:center;height:100vh">
                <div style="text-align:center">
                  <h1 style="color:#e63946">CAMELLOS VS ENANOS</h1>
                  <p>%s</p>
                </div></body></html>
                """).formatted(titulo, mensaje).getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private static Map<String, String> parametrosDe(String consulta) {
        Map<String, String> parametros = new HashMap<>();
        if (consulta == null || consulta.isBlank()) {
            return parametros;
        }
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                parametros.put(
                        URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return parametros;
    }

    @Override
    public void close() {
        servidor.stop(CIERRE_INMEDIATO_SEGUNDOS);
    }
}
