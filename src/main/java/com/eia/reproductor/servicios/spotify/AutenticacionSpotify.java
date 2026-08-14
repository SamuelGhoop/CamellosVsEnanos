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

/**
 * Autenticacion con Spotify mediante OAuth 2.0 con PKCE.
 *
 * <p><b>Por que PKCE y no usuario/contrasena.</b> El flujo de credenciales directas esta
 * descontinuado por Spotify y ya no funciona. Ademas, pedirle la contrasena al usuario dentro de la
 * aplicacion significaria que la aplicacion la ve; con PKCE nunca la ve: el usuario la escribe en
 * el navegador, en el sitio de Spotify, y la aplicacion solo recibe un codigo de un solo uso.</p>
 *
 * <p><b>Que aporta el "PK" de PKCE.</b> Al iniciar se genera un secreto al azar (el
 * <i>verifier</i>) y se envia solo su huella SHA-256 (el <i>challenge</i>). Al canjear el codigo se
 * presenta el verifier original. Asi, aunque alguien interceptara el codigo de retorno, no podria
 * canjearlo sin el verifier, que nunca viajo por la red. Es lo que permite que una aplicacion de
 * escritorio se autentique sin guardar ningun secreto.</p>
 *
 * <p><b>Uso.</b> {@link #tokenDeAcceso()} es el unico metodo que hace falta: devuelve un token
 * utilizable, renovandolo si esta por vencer, y abre el navegador solo la primera vez. Bloquea
 * mientras dura el intercambio, asi que nunca debe llamarse desde el hilo de la interfaz.</p>
 */
public class AutenticacionSpotify {

    /**
     * Permisos que se piden. Son los minimos para lo que hace la aplicacion.
     *
     * <p>{@code user-read-playback-state} para consultar el reproductor y la lista de dispositivos;
     * {@code user-modify-playback-state} para transferir la reproduccion y controlar play, pausa y
     * salto de posicion. No se pide leer la biblioteca, ni el correo, ni el perfil privado: cuantos
     * menos permisos, menos puede hacer el token si alguna vez se filtra.</p>
     */
    public static final String PERMISOS = "user-read-playback-state user-modify-playback-state";

    private static final String URL_AUTORIZACION = "https://accounts.spotify.com/authorize";
    private static final String URL_TOKEN = "https://accounts.spotify.com/api/token";
    private static final Duration TIEMPO_LIMITE_HTTP = Duration.ofSeconds(15);

    /** Cuanto se espera a que el usuario autorice en el navegador antes de rendirse. */
    private static final Duration ESPERA_DEL_USUARIO = Duration.ofMinutes(3);

    private final ConfiguracionSpotify configuracion;
    private final AlmacenTokenSpotify almacen;
    private final HttpClient http;

    private TokenSpotify token;
    private String ultimoAviso;

    /**
     * Crea el autenticador.
     *
     * @param configuracion datos del archivo {@code config/spotify.properties}
     * @param almacen       donde persistir el token entre ejecuciones
     */
    public AutenticacionSpotify(ConfiguracionSpotify configuracion, AlmacenTokenSpotify almacen) {
        this.configuracion = configuracion;
        this.almacen = almacen;
        this.http = HttpClient.newBuilder().connectTimeout(TIEMPO_LIMITE_HTTP).build();
        this.token = almacen.cargar().orElse(null);
    }

    /**
     * Indica si hay una sesion utilizable sin intervencion del usuario.
     *
     * <p>No abre el navegador ni hace peticiones: solo mira si hay un token vigente o uno que se
     * pueda renovar. Es lo que consulta la fuente de audio para declararse disponible al arrancar,
     * y por eso tiene que ser inmediato y silencioso.</p>
     *
     * @return {@code true} si hay token vigente o renovable
     */
    public boolean haySesion() {
        return token != null && (token.vigente() || token.puedeRenovarse());
    }

    /**
     * Devuelve un token de acceso utilizable.
     *
     * <p>Renueva por adelantado si esta por vencer, y solo abre el navegador si no hay forma de
     * renovar. Bloquea: no llamar desde el hilo de la interfaz.</p>
     *
     * @return el token, o vacio si no se pudo obtener
     */
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

    /**
     * Devuelve un token sin abrir jamas el navegador.
     *
     * <p>Es la variante para el arranque automatico: si la sesion guardada ya no sirve, la
     * aplicacion sigue con audio local en vez de plantarle al usuario una ventana del navegador que
     * no pidio.</p>
     *
     * @return el token, o vacio si haria falta autorizar de nuevo
     */
    public synchronized Optional<String> tokenSinInteraccion() {
        if (token != null && token.vigente()) {
            return Optional.of(token.accessToken());
        }
        if (token != null && token.puedeRenovarse() && renovar()) {
            return Optional.of(token.accessToken());
        }
        return Optional.empty();
    }

    /**
     * Fuerza una renovacion aunque el token todavia se crea vigente.
     *
     * <p>Es la respuesta a un {@code 401} inesperado: el token puede haber sido revocado desde la
     * cuenta de Spotify, y entonces la fecha de vencimiento que guardamos miente. Renovar y
     * reintentar una vez distingue ese caso de una caida real.</p>
     *
     * @return el token nuevo, o vacio si tampoco se pudo renovar
     */
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

    // ------------------------------------------------------------------
    // Renovacion
    // ------------------------------------------------------------------

    /**
     * Pide un token nuevo usando el de refresco.
     *
     * @return {@code true} si la renovacion funciono
     */
    private boolean renovar() {
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("grant_type", "refresh_token");
        parametros.put("refresh_token", token.refreshToken());
        parametros.put("client_id", configuracion.clientId());

        Optional<JsonObject> respuesta = pedirToken(parametros);
        if (respuesta.isEmpty()) {
            // Un refresh token rechazado no se recupera: se borra para no reintentar en cada
            // arranque contra una credencial que el servidor ya repudio.
            cerrarSesion();
            return false;
        }
        return guardarDesde(respuesta.get(), token.refreshToken());
    }

    // ------------------------------------------------------------------
    // Autorizacion inicial
    // ------------------------------------------------------------------

    /**
     * Abre el navegador, espera el retorno y canjea el codigo por un token.
     *
     * @return {@code true} si se obtuvo un token
     */
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

    /**
     * Abre la URL en el navegador del sistema.
     *
     * <p>Se usa {@code rundll32} en Windows en vez de {@code java.awt.Desktop} a proposito:
     * {@code Desktop} arrastra el entorno grafico de AWT, y mezclar AWT con JavaFX en el mismo
     * proceso provoca bloqueos. Si el navegador no se puede abrir, se imprime la direccion para que
     * el usuario la pegue a mano en vez de quedarse sin salida.</p>
     */
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

    // ------------------------------------------------------------------
    // Llamada al servidor de tokens
    // ------------------------------------------------------------------

    private Optional<JsonObject> pedirToken(Map<String, String> parametros) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(URL_TOKEN))
                .timeout(TIEMPO_LIMITE_HTTP)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(comoFormulario(parametros)))
                .build();
        try {
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            JsonObject cuerpo = JsonParser.parseString(respuesta.body()).getAsJsonObject();
            if (respuesta.statusCode() != 200) {
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

    // ------------------------------------------------------------------
    // Utilidades de PKCE
    // ------------------------------------------------------------------

    /**
     * Genera el secreto de un solo uso del flujo PKCE.
     *
     * <p>64 bytes al azar en base64 sin relleno dan 86 caracteres, dentro del rango de 43 a 128 que
     * exige la especificacion, y usando solo caracteres que la URL admite sin escapar.</p>
     *
     * @return el verificador
     */
    static String generarVerificador() {
        byte[] aleatorio = new byte[64];
        new SecureRandom().nextBytes(aleatorio);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
    }

    /**
     * Calcula la huella que se envia en lugar del verificador.
     *
     * @param verificador secreto generado localmente
     * @return el desafio en base64url, o {@code null} si la maquina no tiene SHA-256
     */
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
