package com.eia.reproductor.servicios.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cliente de la Web API de Spotify.
 *
 * <p>Concentra en un solo sitio el token, los tiempos limite y el manejo de los codigos de estado,
 * para que el resto del codigo no repita eso en cada llamada.</p>
 *
 * <p><b>Sobre el 401.</b> Un token puede dejar de valer antes de su fecha de vencimiento si el
 * usuario revoca el permiso desde su cuenta. En ese caso la fecha que guardamos miente, asi que
 * ante un {@code 401} se fuerza una renovacion y se reintenta <b>una sola vez</b>. Reintentar en
 * bucle contra un token muerto solo gastaria peticiones.</p>
 *
 * <p><b>Sobre el 204.</b> Varios endpoints del reproductor responden {@code 204 No Content} cuando
 * no hay nada sonando. No es un error: es la forma de decir "no hay reproduccion activa", y por eso
 * se traduce a un resultado vacio y no a un fallo.</p>
 */
public class ClienteWebApiSpotify {

    private static final String BASE = "https://api.spotify.com/v1";
    private static final Duration TIEMPO_LIMITE = Duration.ofSeconds(10);

    private static final int OK = 200;
    private static final int SIN_CONTENIDO = 204;
    private static final int NO_AUTORIZADO = 401;

    private final AutenticacionSpotify autenticacion;
    private final HttpClient http;

    private String ultimoAviso;

    /**
     * Crea el cliente.
     *
     * @param autenticacion de donde sale el token de cada peticion
     */
    public ClienteWebApiSpotify(AutenticacionSpotify autenticacion) {
        this.autenticacion = autenticacion;
        this.http = HttpClient.newBuilder().connectTimeout(TIEMPO_LIMITE).build();
    }

    /**
     * Lista los dispositivos visibles en la cuenta.
     *
     * <p>Es la consulta con la que se comprueba que librespot arranco de verdad: mientras no
     * aparezca en esta lista, el proceso todavia no se registro en Spotify Connect y transferirle
     * la reproduccion fallaria.</p>
     *
     * @return los dispositivos, o lista vacia si la consulta no se pudo hacer
     */
    public List<DispositivoSpotify> dispositivos() {
        Optional<JsonObject> respuesta = obtener("/me/player/devices");
        if (respuesta.isEmpty() || !respuesta.get().has("devices")) {
            return List.of();
        }
        JsonArray arreglo = respuesta.get().getAsJsonArray("devices");
        List<DispositivoSpotify> dispositivos = new ArrayList<>(arreglo.size());
        for (JsonElement elemento : arreglo) {
            dispositivos.add(DispositivoSpotify.desdeJson(elemento.getAsJsonObject()));
        }
        return dispositivos;
    }

    /**
     * Busca un dispositivo por su nombre visible.
     *
     * @param nombre nombre configurado en {@code device.name}
     * @return el dispositivo, o vacio si Spotify todavia no lo ve
     */
    public Optional<DispositivoSpotify> buscarDispositivo(String nombre) {
        return dispositivos().stream()
                .filter(dispositivo -> dispositivo.nombre().equalsIgnoreCase(nombre))
                .findFirst();
    }

    /**
     * Transfiere la reproduccion al dispositivo indicado.
     *
     * <p><b>Es el paso que se olvida.</b> Sin el, cualquier orden de reproduccion se va al ultimo
     * dispositivo que uso la cuenta —el telefono, el navegador, lo que sea— y por los parlantes de
     * esta maquina no suena nada, aunque la API responda que todo salio bien.</p>
     *
     * <p>Se transfiere con {@code play:false} a proposito: transferir y arrancar son dos cosas
     * distintas. Aqui solo se mueve el foco; que suene es decision del modo de reproduccion.</p>
     *
     * @param idDispositivo identificador que devuelve {@code GET /v1/me/player/devices}
     * @return {@code true} si Spotify acepto la transferencia
     */
    public boolean transferirA(String idDispositivo) {
        if (idDispositivo == null || idDispositivo.isBlank()) {
            ultimoAviso = "No hay dispositivo al que transferir la reproducción.";
            return false;
        }
        String cuerpo = "{\"device_ids\":[\"" + idDispositivo + "\"],\"play\":false}";
        return exito("/me/player",
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    /**
     * Apaga la repeticion y el modo aleatorio de la cuenta.
     *
     * <p><b>No es un detalle cosmetico.</b> Estos dos ajustes viven en la cuenta, no en el
     * dispositivo, y sobreviven a la transferencia: si el usuario dejo {@code repeat} encendido en
     * su telefono, Spotify repetiria la pista o el contexto y el aviso de fin de pista no llegaria
     * nunca, dejando colgada la cola. Aqui el orden lo mandan las estructuras de datos, asi que
     * Spotify tiene que limitarse a obedecer.</p>
     *
     * <p>Ojo: {@code --autoplay off} de librespot es otra cosa y no cubre esto.</p>
     *
     * @param idDispositivo dispositivo sobre el que aplicar los ajustes
     * @return {@code true} si ambos ajustes quedaron apagados
     */
    public boolean silenciarRepeticionYAleatorio(String idDispositivo) {
        String sufijo = (idDispositivo == null || idDispositivo.isBlank())
                ? ""
                : "&device_id=" + idDispositivo;

        // Solo se pide cambiar lo que hace falta. Pedirle a Spotify un estado que ya tiene
        // devuelve 403 "Restriction violated", que parece un fallo grave y no lo es.
        if (repeticionYAleatorioApagados()) {
            ultimoAviso = null;
            return true;
        }
        exito("/me/player/repeat?state=off" + sufijo,
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));
        exito("/me/player/shuffle?state=false" + sufijo,
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));

        // Lo que cuenta no es el codigo de respuesta sino como quedo la cuenta.
        boolean logrado = repeticionYAleatorioApagados();
        ultimoAviso = logrado
                ? null
                : "No se pudieron apagar la repetición y el modo aleatorio de la cuenta.";
        return logrado;
    }

    /** @return {@code true} si la cuenta ya tiene repeticion y aleatorio apagados */
    private boolean repeticionYAleatorioApagados() {
        Optional<JsonObject> estado = obtener("/me/player");
        if (estado.isEmpty()) {
            // Sin reproduccion activa no hay nada encendido que pueda pelear con la cola.
            return true;
        }
        JsonObject player = estado.get();
        boolean sinRepeticion = !player.has("repeat_state")
                || "off".equals(player.get("repeat_state").getAsString());
        boolean sinAleatorio = !player.has("shuffle_state")
                || !player.get("shuffle_state").getAsBoolean();
        return sinRepeticion && sinAleatorio;
    }

    /**
     * Reproduce una pista concreta desde el principio o desde donde se pida.
     *
     * <p>Se manda la pista en {@code uris} y no un contexto (album o lista): asi Spotify reproduce
     * exactamente esa y nada mas al terminar. El orden de las canciones lo deciden las estructuras
     * de datos de la aplicacion, no Spotify.</p>
     *
     * @param idDispositivo dispositivo donde reproducir
     * @param uriPista      identificador tipo {@code spotify:track:...}
     * @param posicionMs    desde donde empezar; 0 para el principio
     * @return {@code true} si Spotify acepto la orden
     */
    public boolean reproducir(String idDispositivo, String uriPista, long posicionMs) {
        if (uriPista == null || uriPista.isBlank()) {
            ultimoAviso = "La canción no tiene URI de Spotify.";
            return false;
        }
        String cuerpo = "{\"uris\":[\"" + uriPista + "\"],\"position_ms\":"
                + Math.max(0, posicionMs) + "}";
        return exito("/me/player/play" + porDispositivo(idDispositivo),
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    /**
     * Continua la pista actual sin cambiarla.
     *
     * @param idDispositivo dispositivo a reanudar
     * @return {@code true} si Spotify acepto la orden
     */
    public boolean reanudar(String idDispositivo) {
        return exito("/me/player/play" + porDispositivo(idDispositivo),
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));
    }

    /**
     * Salta a una posicion de la pista actual.
     *
     * @param idDispositivo dispositivo sobre el que saltar
     * @param posicionMs    posicion absoluta desde el inicio
     * @return {@code true} si Spotify acepto la orden
     */
    public boolean buscarPosicion(String idDispositivo, long posicionMs) {
        String ruta = "/me/player/seek?position_ms=" + Math.max(0, posicionMs)
                + (idDispositivo == null || idDispositivo.isBlank()
                        ? "" : "&device_id=" + idDispositivo);
        return exito(ruta, constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));
    }

    /** @return el parametro de dispositivo como primera cadena de consulta, o vacio */
    private static String porDispositivo(String idDispositivo) {
        return (idDispositivo == null || idDispositivo.isBlank())
                ? ""
                : "?device_id=" + idDispositivo;
    }

    /**
     * Fija el volumen del dispositivo.
     *
     * <p>Hace falta ademas de {@code --initial-volume}: librespot guarda el ultimo volumen en su
     * carpeta de cache y ese valor guardado le gana al de arranque. Fijarlo por la API es lo unico
     * que garantiza el nivel en cada sesion.</p>
     *
     * @param idDispositivo dispositivo a ajustar
     * @param porcentaje    volumen de 0 a 100
     * @return {@code true} si Spotify acepto la orden
     */
    public boolean ajustarVolumen(String idDispositivo, int porcentaje) {
        int limitado = Math.max(0, Math.min(100, porcentaje));
        String ruta = "/me/player/volume?volume_percent=" + limitado
                + (idDispositivo == null || idDispositivo.isBlank()
                        ? "" : "&device_id=" + idDispositivo);
        return exito(ruta, constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));
    }

    /**
     * Pausa la reproduccion.
     *
     * <p>Hace falta justo despues de transferir: {@code play:false} significa "no cambies el estado
     * de reproduccion", no "quedate en silencio". Si la cuenta venia reproduciendo, al transferir
     * la musica sigue sonando en el dispositivo nuevo.</p>
     *
     * @param idDispositivo dispositivo a pausar
     * @return {@code true} si Spotify acepto la orden
     */
    public boolean pausar(String idDispositivo) {
        return exito("/me/player/pause" + porDispositivo(idDispositivo),
                constructor -> constructor.PUT(HttpRequest.BodyPublishers.noBody()));
    }

    /**
     * Busca una cancion en el catalogo de Spotify.
     *
     * <p>Se piden varios resultados y no uno solo: Spotify ordena por relevancia, pero el primero
     * puede ser una version en directo o de otro interprete. Se devuelve el primero que de verdad
     * concuerde en titulo e interprete, y si ninguno concuerda no se devuelve nada — es preferible
     * dejar la cancion sin URI que asociarla a la equivocada.</p>
     *
     * @param titulo  titulo de la cancion de la biblioteca
     * @param artista interprete de la cancion de la biblioteca
     * @return la pista que concuerda, o vacio
     */
    public Optional<PistaSpotify> buscarPista(String titulo, String artista) {
        if (titulo == null || titulo.isBlank()) {
            return Optional.empty();
        }
        for (String consulta : consultasPara(titulo, artista)) {
            Optional<PistaSpotify> hallada = primeraQueConcuerde(consulta, titulo, artista);
            if (hallada.isPresent()) {
                return hallada;
            }
        }
        return Optional.empty();
    }

    /**
     * Arma las consultas a probar, de la mas precisa a la mas laxa.
     *
     * <p>La busqueda por campos ({@code track:} y {@code artist:}) es la que menos falsos positivos
     * da, pero se atraganta con titulos como "Cancion (feat. Alguien)", donde el catalogo guarda
     * el invitado como segundo interprete y no dentro del titulo. Por eso se reintenta sin la
     * coletilla y, al final, con texto libre, que es mas tolerante. El filtro de concordancia se
     * aplica igual en todos los casos, asi que aflojar la consulta no relaja el criterio.</p>
     */
    private static List<String> consultasPara(String titulo, String artista) {
        String porArtista = (artista == null || artista.isBlank()) ? "" : " artist:" + artista;
        String desnudo = sinColetillas(titulo);

        List<String> consultas = new ArrayList<>();
        consultas.add("track:" + titulo + porArtista);
        if (!desnudo.equalsIgnoreCase(titulo)) {
            consultas.add("track:" + desnudo + porArtista);
        }
        consultas.add(desnudo + " " + (artista == null ? "" : artista));
        return consultas;
    }

    /**
     * Quita del titulo lo que suele estorbar en la busqueda.
     *
     * @param titulo titulo tal como esta en la biblioteca
     * @return el titulo sin parentesis, corchetes ni sufijos de edicion
     */
    static String sinColetillas(String titulo) {
        String limpio = titulo
                .replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "")
                .replaceAll("(?i)\\s+-\\s+(remaster|remastered|radio edit|single version).*$", "");
        return limpio.isBlank() ? titulo : limpio.trim();
    }

    /**
     * Lanza una consulta y devuelve el primer resultado que concuerde de verdad.
     *
     * @param consulta       texto de busqueda
     * @param tituloBuscado  titulo con el que comparar
     * @param artistaBuscado interprete con el que comparar
     * @return la pista que concuerda, o vacio
     */
    private Optional<PistaSpotify> primeraQueConcuerde(String consulta, String tituloBuscado,
                                                       String artistaBuscado) {
        String ruta = "/search?type=track&limit=10&q="
                + URLEncoder.encode(consulta, StandardCharsets.UTF_8);

        Optional<JsonObject> respuesta = obtener(ruta);
        if (respuesta.isEmpty() || !respuesta.get().has("tracks")) {
            return Optional.empty();
        }
        JsonObject pistas = respuesta.get().getAsJsonObject("tracks");
        if (!pistas.has("items")) {
            return Optional.empty();
        }
        for (JsonElement elemento : pistas.getAsJsonArray("items")) {
            PistaSpotify candidata = PistaSpotify.desdeJson(elemento.getAsJsonObject());
            if (candidata.concuerdaCon(tituloBuscado, artistaBuscado)) {
                return Optional.of(candidata);
            }
        }
        return Optional.empty();
    }

    /**
     * Consulta el estado del reproductor.
     *
     * <p>Es la unica forma de saber la posicion: Spotify no empuja avisos, hay que preguntarle.</p>
     *
     * @return el estado, o vacio si no hay reproduccion activa
     */
    public Optional<EstadoReproductorSpotify> estadoDelReproductor() {
        return obtener("/me/player").map(EstadoReproductorSpotify::desdeJson);
    }

    /**
     * Hace una peticion GET a la API.
     *
     * @param ruta ruta relativa a {@code /v1}, empezando por barra
     * @return el cuerpo de la respuesta, o vacio si no hubo contenido o hubo error
     */
    public Optional<JsonObject> obtener(String ruta) {
        return enviar(ruta, constructor -> constructor.GET());
    }

    /** @return el motivo del ultimo fallo de la API, si lo hubo */
    public Optional<String> ultimoAviso() {
        return Optional.ofNullable(ultimoAviso);
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    /** Aplica el metodo y el cuerpo a la peticion en construccion. */
    interface Metodo {
        HttpRequest.Builder aplicar(HttpRequest.Builder constructor);
    }

    /**
     * Envia una peticion y devuelve su cuerpo como JSON.
     *
     * @param ruta   ruta relativa a {@code /v1}
     * @param metodo verbo y cuerpo de la peticion
     * @return el cuerpo de la respuesta, o vacio
     */
    Optional<JsonObject> enviar(String ruta, Metodo metodo) {
        return interpretar(enviarConReintento(ruta, metodo).orElse(null));
    }

    /**
     * Envia una peticion cuyo exito no depende del cuerpo sino del codigo de estado.
     *
     * <p>Las ordenes del reproductor (transferir, reproducir, pausar, saltar) responden
     * {@code 204 No Content}. Con {@link #enviar} no se podrian distinguir del fallo, porque ambos
     * casos dan un resultado vacio; por eso existe este metodo aparte.</p>
     *
     * @param ruta   ruta relativa a {@code /v1}
     * @param metodo verbo y cuerpo de la peticion
     * @return {@code true} si Spotify acepto la orden
     */
    boolean exito(String ruta, Metodo metodo) {
        Optional<HttpResponse<String>> respuesta = enviarConReintento(ruta, metodo);
        if (respuesta.isEmpty()) {
            return false;
        }
        int estado = respuesta.get().statusCode();
        if (estado >= 200 && estado < 300) {
            ultimoAviso = null;
            return true;
        }
        ultimoAviso = mensajeDeError(estado, respuesta.get().body());
        return false;
    }

    /**
     * Envia una peticion, renovando el token y reintentando una vez ante un 401.
     *
     * @return la respuesta cruda, o vacio si ni siquiera se pudo enviar
     */
    private Optional<HttpResponse<String>> enviarConReintento(String ruta, Metodo metodo) {
        Optional<String> token = autenticacion.tokenSinInteraccion();
        if (token.isEmpty()) {
            ultimoAviso = "No hay sesión de Spotify válida.";
            return Optional.empty();
        }

        Optional<HttpResponse<String>> respuesta = intentar(ruta, metodo, token.get());
        if (respuesta.isPresent() && respuesta.get().statusCode() == NO_AUTORIZADO) {
            // El token murio antes de tiempo: se renueva y se reintenta una sola vez.
            Optional<String> renovado = autenticacion.forzarRenovacion();
            if (renovado.isEmpty()) {
                ultimoAviso = "Spotify rechazó la sesión y no se pudo renovar.";
                return Optional.empty();
            }
            respuesta = intentar(ruta, metodo, renovado.get());
        }
        return respuesta;
    }

    private Optional<HttpResponse<String>> intentar(String ruta, Metodo metodo, String token) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(BASE + ruta))
                .timeout(TIEMPO_LIMITE)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        try {
            return Optional.of(http.send(
                    metodo.aplicar(constructor).build(), HttpResponse.BodyHandlers.ofString()));
        } catch (IOException fallo) {
            ultimoAviso = "No se pudo contactar a Spotify: " + fallo.getMessage();
            return Optional.empty();
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            ultimoAviso = "La petición a Spotify se interrumpió.";
            return Optional.empty();
        }
    }

    private Optional<JsonObject> interpretar(HttpResponse<String> respuesta) {
        if (respuesta == null) {
            return Optional.empty();
        }
        int estado = respuesta.statusCode();

        // 204 es la forma de decir "no hay reproduccion activa". No es un fallo.
        if (estado == SIN_CONTENIDO || respuesta.body() == null || respuesta.body().isBlank()) {
            ultimoAviso = estado == OK || estado == SIN_CONTENIDO ? null : mensajeDeError(estado, "");
            return Optional.empty();
        }
        if (estado != OK) {
            ultimoAviso = mensajeDeError(estado, respuesta.body());
            return Optional.empty();
        }
        try {
            ultimoAviso = null;
            return Optional.of(JsonParser.parseString(respuesta.body()).getAsJsonObject());
        } catch (RuntimeException cuerpoInesperado) {
            ultimoAviso = "Spotify devolvió una respuesta que no se pudo leer.";
            return Optional.empty();
        }
    }

    private static String mensajeDeError(int estado, String cuerpo) {
        String detalle = detalleDe(cuerpo);
        return switch (estado) {
            // Hay dos 403 muy distintos y confundirlos hace perder mucho tiempo: uno es no tener
            // Premium, y el otro es pedir algo que el estado actual no permite (por ejemplo poner
            // un ajuste en el valor que ya tiene). Solo el primero es un problema de verdad.
            case 403 -> detalle.contains("Restriction violated")
                    ? "Spotify no permitió la operación en el estado actual (403). " + detalle
                    : "Spotify rechazó la operación (403). "
                            + "Los controles de reproducción requieren cuenta Premium. " + detalle;
            case 404 -> "Spotify no encontró un dispositivo activo (404). " + detalle;
            case 429 -> "Spotify pidió esperar por exceso de peticiones (429). " + detalle;
            default -> "Spotify respondió HTTP " + estado + ". " + detalle;
        };
    }

    private static String detalleDe(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "";
        }
        try {
            JsonObject objeto = JsonParser.parseString(cuerpo).getAsJsonObject();
            if (objeto.has("error") && objeto.get("error").isJsonObject()) {
                JsonObject error = objeto.getAsJsonObject("error");
                if (error.has("message")) {
                    return error.get("message").getAsString();
                }
            }
        } catch (RuntimeException noEsJson) {
            // Se devuelve el cuerpo crudo mas abajo.
        }
        return cuerpo.length() > 200 ? cuerpo.substring(0, 200) : cuerpo;
    }
}
