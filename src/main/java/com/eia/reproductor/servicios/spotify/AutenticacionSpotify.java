package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Autenticacion con Spotify mediante OAuth 2.0 con PKCE. */
public class AutenticacionSpotify {
    /** Permisos que se piden. */
    public static final String PERMISOS = "user-read-playback-state user-modify-playback-state";

    private static final String URL_AUTORIZACION = "https://accounts.spotify.com/authorize";
    private static final String URL_TOKEN = "https://accounts.spotify.com/api/token";
    private static final Duration TIEMPO_LIMITE_HTTP = Duration.ofSeconds(15);

    /** Cuanto se espera a que el usuario autorice en el navegador antes de rendirse. */
    private static final Duration ESPERA_DEL_USUARIO = Duration.ofMinutes(3);

    private final ConfiguracionSpotify configuracion;
    private final AlmacenTokenSpotify almacen;
    private final HttpClient http;

    /** A donde se piden los tokens; las pruebas lo apuntan a un servidor local. */
    private final String urlToken;

    /** Codigos con los que Spotify dice que la credencial ya no vale y no volvera a valer. */
    private static final int HTTP_PETICION_INVALIDA = 400;
    private static final int HTTP_NO_AUTORIZADO = 401;

    private TokenSpotify token;
    private String ultimoAviso;

    /**
     * Si el ultimo fallo al pedir token fue un rechazo definitivo del servidor.
     *
     * <p>Distingue "Spotify dice que esta credencial no sirve" de "no se pudo preguntar". Lo
     * primero se resuelve autorizando de nuevo; lo segundo se resuelve solo.</p>
     */
    private boolean ultimoFalloFueRechazo;

    /** Crea el autenticador. */
    public AutenticacionSpotify(ConfiguracionSpotify configuracion, AlmacenTokenSpotify almacen) {
        this(configuracion, almacen, URL_TOKEN);
    }

    /** Variante para pruebas: permite pedir los tokens a un servidor local. */
    AutenticacionSpotify(ConfiguracionSpotify configuracion, AlmacenTokenSpotify almacen,
                         String urlToken) {
        this.configuracion = configuracion;
        this.almacen = almacen;
        this.urlToken = urlToken;
        this.http = HttpClient.newBuilder().connectTimeout(TIEMPO_LIMITE_HTTP).build();
        this.token = almacen.cargar().orElse(null);
    }

    /** Fuerza una renovacion y devuelve si la sesion sobrevivio; existe para las pruebas. */
    boolean renovarParaPruebas() {
        return renovar();
    }

    /** Indica si hay una sesion utilizable sin intervencion del usuario. */
    public boolean haySesion() {
        return token != null && (token.vigente() || token.puedeRenovarse());
    }

    /** Devuelve un token de acceso utilizable. */
    public synchronized Optional<String> tokenDeAcceso() {
        if (token != null && token.vigente()) {
            return Optional.of(token.accessToken());
        }
        if (token != null && token.puedeRenovarse() && renovar()) {
            return Optional.of(token.accessToken());
        }
        if (autorizarEnNavegador()) {
            return Optional.of(token.accessToken());
        }
        return Optional.empty();
    }

    /** Devuelve un token sin abrir jamas el navegador. */
    public synchronized Optional<String> tokenSinInteraccion() {
        if (token != null && token.vigente()) {
            return Optional.of(token.accessToken());
        }
        if (token != null && token.puedeRenovarse() && renovar()) {
            return Optional.of(token.accessToken());
        }
        return Optional.empty();
    }

    /** Fuerza una renovacion aunque el token todavia se crea vigente. */
    public synchronized Optional<String> forzarRenovacion() {
        if (token == null || !token.puedeRenovarse()) {
            return Optional.empty();
        }
        return renovar() ? Optional.of(token.accessToken()) : Optional.empty();
    }

    /** @return el motivo del ultimo fallo de autenticacion, si lo hubo */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    /** Olvida la sesion guardada, obligando a autorizar de nuevo. */
    public synchronized void cerrarSesion() {
        token = null;
        almacen.borrar();
    }

    // --- Renovacion ---

    /** Pide un token nuevo usando el de refresco. */
    private boolean renovar() {
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("grant_type", "refresh_token");
        parametros.put("refresh_token", token.refreshToken());
        parametros.put("client_id", configuracion.clientId());

        Optional<JsonObject> respuesta = pedirToken(parametros);
        if (respuesta.isEmpty()) {
            // Un refresh token rechazado no se recupera: se borra para no reintentar en cada
            // arranque contra una credencial que el servidor ya repudio. Pero SOLO si hubo
            // rechazo: antes se borraba tambien cuando fallaba la red o se cerraba la aplicacion
            // a media renovacion, y una caida de un segundo obligaba a autorizar en el navegador
            // otra vez. Sin conexion el token no se toca; sigue ahi para el proximo arranque.
            if (ultimoFalloFueRechazo) {
                cerrarSesion();
            }
            return false;
        }
        return guardarDesde(respuesta.get(), token.refreshToken());
    }

    // --- Autorizacion inicial ---

    /** Abre el navegador, espera el retorno y canjea el codigo por un token. */
    private boolean autorizarEnNavegador() {
        String verificador = generarVerificador();
        String desafio = calcularDesafio(verificador);
        if (desafio == null) {
            ultimoAviso = "Esta máquina no soporta SHA-256, que es obligatorio para PKCE.";
            return false;
        }
        String estado = generarVerificador();

        try (ServidorDeRetorno servidor = new ServidorDeRetorno(
                configuracion.puertoDeRetorno(), configuracion.rutaDeRetorno(), estado)) {
            abrirNavegador(construirUrlDeAutorizacion(desafio, estado));

            Optional<String> codigo = servidor.esperarCodigo(ESPERA_DEL_USUARIO);
            if (codigo.isEmpty()) {
                ultimoAviso = servidor.motivoDelFallo()
                        .orElse("No se completó la autorización en el navegador.");
                return false;
            }

            Map<String, String> parametros = new LinkedHashMap<>();
            parametros.put("grant_type", "authorization_code");
            parametros.put("code", codigo.get());
            parametros.put("redirect_uri", configuracion.redirectUri());
            parametros.put("client_id", configuracion.clientId());
            parametros.put("code_verifier", verificador);

            Optional<JsonObject> respuesta = pedirToken(parametros);
            return respuesta.isPresent() && guardarDesde(respuesta.get(), null);

        } catch (IOException noSePudoAbrirElPuerto) {
            ultimoAviso = "No se pudo abrir el puerto " + configuracion.puertoDeRetorno()
                    + " para recibir la respuesta de Spotify: " + noSePudoAbrirElPuerto.getMessage();
            return false;
        }
    }

    private String construirUrlDeAutorizacion(String desafio, String estado) {
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("client_id", configuracion.clientId());
        parametros.put("response_type", "code");
        parametros.put("redirect_uri", configuracion.redirectUri());
        parametros.put("code_challenge_method", "S256");
        parametros.put("code_challenge", desafio);
        parametros.put("scope", PERMISOS);
        parametros.put("state", estado);
        return URL_AUTORIZACION + "?" + comoFormulario(parametros);
    }

    /** Abre la URL en el navegador del sistema. */
    private void abrirNavegador(String url) {
        try {
            String sistema = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            ProcessBuilder proceso = sistema.contains("win")
                    ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                    : new ProcessBuilder(sistema.contains("mac") ? "open" : "xdg-open", url);
            proceso.start();
        } catch (IOException noSePudoAbrir) {
            System.out.println("Abrí esta dirección en el navegador para autorizar:\n" + url);
        }
    }

    // --- Llamada al servidor de tokens ---

    private Optional<JsonObject> pedirToken(Map<String, String> parametros) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(urlToken))
                .timeout(TIEMPO_LIMITE_HTTP)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(comoFormulario(parametros)))
                .build();
        ultimoFalloFueRechazo = false;
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            JsonObject cuerpo = JsonParser.parseString(respuesta.body()).getAsJsonObject();
            if (respuesta.statusCode() != 200) {
                // Solo 400 y 401 significan "esta credencial no vale". Un 429 o un 5xx son la
                // nube teniendo un mal dia y se arreglan solos en el siguiente intento.
                ultimoFalloFueRechazo = respuesta.statusCode() == HTTP_PETICION_INVALIDA
                        || respuesta.statusCode() == HTTP_NO_AUTORIZADO;
                ultimoAviso = "Spotify rechazó la petición (HTTP " + respuesta.statusCode() + "): "
                        + (cuerpo.has("error_description")
                                ? cuerpo.get("error_description").getAsString()
                                : respuesta.body());
                return Optional.empty();
            }
            return Optional.of(cuerpo);
        } catch (IOException | RuntimeException fallo) {
            ultimoAviso = "No se pudo contactar a Spotify: " + fallo.getMessage();
            return Optional.empty();
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            ultimoAviso = "La autenticación se interrumpió.";
            return Optional.empty();
        }
    }

    private boolean guardarDesde(JsonObject respuesta, String refreshAnterior) {
        if (!respuesta.has("access_token")) {
            ultimoAviso = "La respuesta de Spotify no trae token de acceso.";
            return false;
        }
        token = TokenSpotify.desdeRespuesta(
                respuesta.get("access_token").getAsString(),
                respuesta.has("refresh_token") ? respuesta.get("refresh_token").getAsString() : null,
                respuesta.has("expires_in") ? respuesta.get("expires_in").getAsLong() : 3600,
                refreshAnterior);
        almacen.guardar(token);
        ultimoAviso = null;
        return true;
    }

    // --- Utilidades de PKCE ---

    /** Genera el secreto de un solo uso del flujo PKCE. */
    static String generarVerificador() {
        byte[] aleatorio = new byte[64];
        new SecureRandom().nextBytes(aleatorio);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
    }

    /** Calcula la huella que se envia en lugar del verificador. */
    static String calcularDesafio(String verificador) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256")
                    .digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(huella);
        } catch (NoSuchAlgorithmException sinSha256) {
            return null;
        }
    }

    static String comoFormulario(Map<String, String> parametros) {
        StringBuilder cuerpo = new StringBuilder();
        for (Map.Entry<String, String> par : parametros.entrySet()) {
            if (!cuerpo.isEmpty()) {
                cuerpo.append('&');
            }
            cuerpo.append(URLEncoder.encode(par.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(par.getValue(), StandardCharsets.UTF_8));
        }
        return cuerpo.toString();
    }
}
