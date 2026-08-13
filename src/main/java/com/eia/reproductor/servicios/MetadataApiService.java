package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.ResultadoBusquedaApi;
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

/**
 * Busca metadata de canciones en internet.
 *
 * <p><b>API principal: iTunes Search.</b> No pide registro ni clave, y en una sola llamada
 * devuelve titulo, artista, album, duracion, genero, anio y caratula, que es exactamente lo que
 * pide el enunciado.</p>
 *
 * <p><b>Respaldo: MusicBrainz + Cover Art Archive.</b> Solo se consulta si iTunes no devolvio
 * nada. MusicBrainz exige un {@code User-Agent} identificable (si no, responde 403) y admite como
 * maximo una peticion por segundo, asi que hay un limitador que espera lo que haga falta antes de
 * llamar.</p>
 *
 * <p><b>Esta clase bloquea.</b> Hace peticiones de red sincronas, asi que jamas debe invocarse
 * desde el hilo de la interfaz: el controlador la envuelve en un {@code Task} de JavaFX. A cambio,
 * no importa nada de JavaFX y se puede probar sin levantar la aplicacion.</p>
 *
 * <p>Ningun fallo de red se propaga: si no hay internet, si se agota el tiempo o si la respuesta
 * no se entiende, se devuelve una lista vacia y se deja el motivo en {@link #ultimoAviso()}. La
 * aplicacion tiene que seguir funcionando en modo manual.</p>
 */
public class MetadataApiService {

    /** Cuantos resultados se piden a iTunes. */
    public static final int LIMITE_RESULTADOS = 15;

    /** Tiempo maximo de espera por peticion. */
    public static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(8);

    /** Separacion minima entre peticiones a MusicBrainz, que lo exige en sus condiciones de uso. */
    public static final long MILIS_ENTRE_LLAMADAS_MUSICBRAINZ = 1000;

    /**
     * MusicBrainz rechaza con 403 a quien no se identifique con un contacto real.
     * Cambiar el correo por el institucional antes de entregar si hace falta.
     */
    private static final String USER_AGENT =
            "ReproductorEIA/1.0 ( samuelgiraldojimenez@gmail.com )";

    private static final String URL_ITUNES = "https://itunes.apple.com/search";
    private static final String URL_MUSICBRAINZ = "https://musicbrainz.org/ws/2/recording";
    private static final String URL_COVER_ART = "https://coverartarchive.org/release/";

    /** El pais afecta que catalogo devuelve iTunes. */
    private static final String PAIS = "CO";

    private static final String RESOLUCION_MINIATURA = "100x100bb";
    private static final String RESOLUCION_GRANDE = "600x600bb";

    private final HttpClient cliente;
    private long instanteUltimaLlamadaMusicBrainz;
    private String ultimoAviso;

    /** Crea el servicio con un cliente HTTP propio. */
    public MetadataApiService() {
        this.cliente = HttpClient.newBuilder()
                .connectTimeout(TIEMPO_MAXIMO)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Busca canciones que coincidan con la consulta.
     *
     * <p>Intenta primero con iTunes y, solo si no obtiene resultados, con MusicBrainz.</p>
     *
     * @param consulta texto libre, por ejemplo {@code "queen bohemian rhapsody"}
     * @return los resultados encontrados; lista vacia si no hay ninguno o si fallo la red
     */
    public List<ResultadoBusquedaApi> buscar(String consulta) {
        ultimoAviso = null;
        if (consulta == null || consulta.isBlank()) {
            return List.of();
        }

        List<ResultadoBusquedaApi> deItunes = buscarEnItunes(consulta.trim());
        if (!deItunes.isEmpty()) {
            return deItunes;
        }
        return buscarEnMusicBrainz(consulta.trim());
    }

    /**
     * @return el motivo del ultimo problema de red, si lo hubo
     */
    public java.util.Optional<String> ultimoAviso() {
        return java.util.Optional.ofNullable(ultimoAviso);
    }

    // ------------------------------------------------------------------
    // iTunes
    // ------------------------------------------------------------------

    private List<ResultadoBusquedaApi> buscarEnItunes(String consulta) {
        String url = URL_ITUNES
                + "?term=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)
                + "&entity=song&limit=" + LIMITE_RESULTADOS
                + "&country=" + PAIS;
        try {
            String cuerpo = pedir(url, false);
            return mapearItunes(cuerpo);
        } catch (Exception excepcion) {
            ultimoAviso = descripcionDelFallo("iTunes", excepcion);
            return List.of();
        }
    }

    /**
     * Traduce la respuesta de iTunes a resultados del dominio.
     *
     * <p>Es package-private y estatico a proposito: asi las pruebas pueden comprobar el mapeo con
     * una respuesta guardada, sin depender de que haya internet.</p>
     *
     * @param json cuerpo de la respuesta
     * @return los resultados contenidos en la respuesta
     */
    static List<ResultadoBusquedaApi> mapearItunes(String json) {
        List<ResultadoBusquedaApi> resultados = new ArrayList<>();
        JsonElement raiz = JsonParser.parseString(json);
        if (!raiz.isJsonObject()) {
            return resultados;
        }
        JsonElement lista = raiz.getAsJsonObject().get("results");
        if (lista == null || !lista.isJsonArray()) {
            return resultados;
        }

        for (JsonElement elemento : lista.getAsJsonArray()) {
            if (!elemento.isJsonObject()) {
                continue;
            }
            JsonObject pista = elemento.getAsJsonObject();
            String titulo = texto(pista, "trackName");
            if (titulo == null || titulo.isBlank()) {
                continue;
            }

            String miniatura = texto(pista, "artworkUrl100");
            resultados.add(new ResultadoBusquedaApi(
                    titulo,
                    texto(pista, "artistName"),
                    texto(pista, "collectionName"),
                    (int) Math.round(entero(pista, "trackTimeMillis") / 1000.0),
                    texto(pista, "primaryGenreName"),
                    anioDeFecha(texto(pista, "releaseDate")),
                    aAltaResolucion(miniatura),
                    miniatura,
                    ResultadoBusquedaApi.FUENTE_ITUNES));
        }
        return resultados;
    }

    /**
     * Sube la resolucion de la caratula de iTunes.
     *
     * <p>La URL que devuelve la API termina en {@code 100x100bb.jpg}. Cambiando esa parte por
     * {@code 600x600bb} el mismo servidor entrega la version grande, sin ninguna llamada extra.</p>
     *
     * @param urlMiniatura URL de la caratula pequenia
     * @return la URL en alta resolucion, o la original si no tiene el formato esperado
     */
    static String aAltaResolucion(String urlMiniatura) {
        if (urlMiniatura == null) {
            return null;
        }
        return urlMiniatura.replace(RESOLUCION_MINIATURA, RESOLUCION_GRANDE);
    }

    /** Extrae el anio de una fecha ISO como {@code "1975-10-31T12:00:00Z"}. */
    static int anioDeFecha(String fechaIso) {
        if (fechaIso == null || fechaIso.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(fechaIso.substring(0, 4));
        } catch (NumberFormatException excepcion) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // MusicBrainz
    // ------------------------------------------------------------------

    private List<ResultadoBusquedaApi> buscarEnMusicBrainz(String consulta) {
        String url = URL_MUSICBRAINZ
                + "?query=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)
                + "&fmt=json&limit=10";
        try {
            esperarTurnoDeMusicBrainz();
            String cuerpo = pedir(url, true);
            return mapearMusicBrainz(cuerpo);
        } catch (Exception excepcion) {
            ultimoAviso = descripcionDelFallo("MusicBrainz", excepcion);
            return List.of();
        }
    }

    /**
     * Traduce la respuesta de MusicBrainz a resultados del dominio.
     *
     * <p>MusicBrainz es un catalogo bibliografico, no una tienda: no trae genero y muchas veces
     * tampoco duracion. Se rellena lo que haya y el usuario completa el resto a mano.</p>
     *
     * @param json cuerpo de la respuesta
     * @return los resultados contenidos en la respuesta
     */
    static List<ResultadoBusquedaApi> mapearMusicBrainz(String json) {
        List<ResultadoBusquedaApi> resultados = new ArrayList<>();
        JsonElement raiz = JsonParser.parseString(json);
        if (!raiz.isJsonObject()) {
            return resultados;
        }
        JsonElement grabaciones = raiz.getAsJsonObject().get("recordings");
        if (grabaciones == null || !grabaciones.isJsonArray()) {
            return resultados;
        }

        for (JsonElement elemento : grabaciones.getAsJsonArray()) {
            if (!elemento.isJsonObject()) {
                continue;
            }
            JsonObject grabacion = elemento.getAsJsonObject();
            String titulo = texto(grabacion, "title");
            if (titulo == null || titulo.isBlank()) {
                continue;
            }

            String artista = primerNombreDeArtista(grabacion);
            JsonObject lanzamiento = primerLanzamiento(grabacion);
            String album = lanzamiento == null ? null : texto(lanzamiento, "title");
            String mbidLanzamiento = lanzamiento == null ? null : texto(lanzamiento, "id");
            int anio = anioDeFecha(lanzamiento == null ? null : texto(lanzamiento, "date"));

            String portada = mbidLanzamiento == null
                    ? null
                    : URL_COVER_ART + mbidLanzamiento + "/front-500";

            resultados.add(new ResultadoBusquedaApi(
                    titulo,
                    artista,
                    album,
                    (int) Math.round(entero(grabacion, "length") / 1000.0),
                    null,
                    anio,
                    portada,
                    portada,
                    ResultadoBusquedaApi.FUENTE_MUSICBRAINZ));
        }
        return resultados;
    }

    private static String primerNombreDeArtista(JsonObject grabacion) {
        JsonElement creditos = grabacion.get("artist-credit");
        if (creditos == null || !creditos.isJsonArray() || creditos.getAsJsonArray().isEmpty()) {
            return null;
        }
        JsonElement primero = creditos.getAsJsonArray().get(0);
        if (!primero.isJsonObject()) {
            return null;
        }
        JsonObject credito = primero.getAsJsonObject();
        String nombre = texto(credito, "name");
        if (nombre != null) {
            return nombre;
        }
        JsonElement artista = credito.get("artist");
        return (artista != null && artista.isJsonObject())
                ? texto(artista.getAsJsonObject(), "name") : null;
    }

    private static JsonObject primerLanzamiento(JsonObject grabacion) {
        JsonElement lanzamientos = grabacion.get("releases");
        if (lanzamientos == null || !lanzamientos.isJsonArray()) {
            return null;
        }
        JsonArray arreglo = lanzamientos.getAsJsonArray();
        if (arreglo.isEmpty() || !arreglo.get(0).isJsonObject()) {
            return null;
        }
        return arreglo.get(0).getAsJsonObject();
    }

    /**
     * Respeta el limite de una peticion por segundo que impone MusicBrainz.
     *
     * <p>Superarlo hace que el servicio empiece a rechazar las peticiones, asi que se espera lo que
     * falte desde la llamada anterior antes de lanzar la siguiente.</p>
     */
    private void esperarTurnoDeMusicBrainz() throws InterruptedException {
        long transcurrido = System.currentTimeMillis() - instanteUltimaLlamadaMusicBrainz;
        long pendiente = MILIS_ENTRE_LLAMADAS_MUSICBRAINZ - transcurrido;
        if (pendiente > 0) {
            Thread.sleep(pendiente);
        }
        instanteUltimaLlamadaMusicBrainz = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private String pedir(String url, boolean conUserAgent) throws IOException, InterruptedException {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIEMPO_MAXIMO)
                .header("Accept", "application/json")
                .GET();
        if (conUserAgent) {
            constructor.header("User-Agent", USER_AGENT);
        }

        HttpResponse<String> respuesta =
                cliente.send(constructor.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (respuesta.statusCode() / 100 != 2) {
            throw new IOException("el servidor respondio " + respuesta.statusCode());
        }
        return respuesta.body();
    }

    private static String descripcionDelFallo(String servicio, Exception excepcion) {
        if (excepcion instanceof java.net.http.HttpTimeoutException) {
            return "La consulta a " + servicio + " tardó demasiado. Podés completar los datos a mano.";
        }
        if (excepcion instanceof java.net.UnknownHostException
                || excepcion instanceof java.net.ConnectException) {
            return "No hay conexión a internet. Podés agregar la canción a mano.";
        }
        if (excepcion instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "La búsqueda en " + servicio + " se interrumpió.";
        }
        return "No se pudo consultar " + servicio + ": " + excepcion.getMessage()
                + ". Podés completar los datos a mano.";
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull() || !valor.isJsonPrimitive()) {
            return null;
        }
        return valor.getAsString();
    }

    private static long entero(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || valor.isJsonNull() || !valor.isJsonPrimitive()
                || !valor.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return valor.getAsLong();
    }
}
