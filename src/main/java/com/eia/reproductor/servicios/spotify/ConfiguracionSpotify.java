package com.eia.reproductor.servicios.spotify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Lee {@code config/spotify.properties}, el archivo que queda fuera del repositorio.
 *
 * <p><b>Por que un archivo y no constantes en el codigo.</b> El repositorio se entrega y se
 * comparte con el grupo; cualquier identificador que viva en un {@code .java} termina en el
 * historial de git para siempre, y de ahi no se borra. Manteniendolo en un archivo ignorado, el
 * codigo se puede publicar sin arrastrar nada de la cuenta de nadie.</p>
 *
 * <p>Si el archivo no existe, {@link #cargar()} devuelve vacio y la fuente de Spotify se declara no
 * disponible. La aplicacion arranca igual, con audio local y simulado, sin avisos ni ventanas
 * emergentes: para quien no configuro Spotify, la funcionalidad sencillamente no existe.</p>
 *
 * <p>No se guarda ningun <i>client secret</i>. El flujo PKCE existe justamente para no necesitarlo
 * en aplicaciones de escritorio, donde no hay forma de esconder un secreto del usuario.</p>
 */
public final class ConfiguracionSpotify {

    /** Ubicacion del archivo, relativa a la carpeta desde donde se ejecuta la aplicacion. */
    public static final Path RUTA_POR_DEFECTO = Path.of("config", "spotify.properties");

    /** Calidad por defecto del flujo de librespot. 320 exige cuenta Premium. */
    private static final int BITRATE_POR_DEFECTO = 160;

    /**
     * Puerto donde librespot recibe su propia autorizacion.
     *
     * <p>Se fija explicitamente porque, sin el, librespot arma la direccion de retorno sin puerto y
     * termina intentando escuchar en el 80, que en Windows suele estar reservado.</p>
     */
    private static final int PUERTO_OAUTH_LIBRESPOT_POR_DEFECTO = 5588;

    /**
     * Volumen con el que arranca librespot, de 0 a 100.
     *
     * <p>Al maximo a proposito: librespot arranca al 50 % con curva logaritmica, que suena a
     * susurro. El volumen audible lo sigue controlando el del sistema operativo.</p>
     */
    private static final int VOLUMEN_POR_DEFECTO = 100;

    private final String clientId;
    private final String redirectUri;
    private final String nombreDispositivo;
    private final int bitrate;
    private final int puertoOauthLibrespot;
    private final int volumenInicial;

    private ConfiguracionSpotify(String clientId, String redirectUri, String nombreDispositivo,
                                 int bitrate, int puertoOauthLibrespot, int volumenInicial) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.nombreDispositivo = nombreDispositivo;
        this.bitrate = bitrate;
        this.puertoOauthLibrespot = puertoOauthLibrespot;
        this.volumenInicial = volumenInicial;
    }

    /**
     * Carga la configuracion desde la ubicacion por defecto.
     *
     * @return la configuracion, o vacio si el archivo falta o esta incompleto
     */
    public static Optional<ConfiguracionSpotify> cargar() {
        return cargar(RUTA_POR_DEFECTO);
    }

    /**
     * Carga la configuracion desde una ruta concreta.
     *
     * @param ruta archivo de propiedades a leer
     * @return la configuracion, o vacio si el archivo falta o esta incompleto
     */
    public static Optional<ConfiguracionSpotify> cargar(Path ruta) {
        if (ruta == null || !Files.isRegularFile(ruta)) {
            return Optional.empty();
        }
        Properties propiedades = new Properties();
        try (InputStream flujo = Files.newInputStream(ruta)) {
            propiedades.load(flujo);
        } catch (IOException excepcion) {
            return Optional.empty();
        }

        String clientId = limpio(propiedades.getProperty("client.id"));
        String redirectUri = limpio(propiedades.getProperty("redirect.uri"));
        if (clientId == null || redirectUri == null) {
            // Un archivo copiado del .example y sin rellenar cuenta como "no configurado".
            return Optional.empty();
        }
        String dispositivo = limpio(propiedades.getProperty("device.name"));
        return Optional.of(new ConfiguracionSpotify(
                clientId,
                redirectUri,
                dispositivo == null ? "Camellos vs Enanos" : dispositivo,
                entero(propiedades, "librespot.bitrate", BITRATE_POR_DEFECTO),
                entero(propiedades, "librespot.oauth.port",
                        PUERTO_OAUTH_LIBRESPOT_POR_DEFECTO),
                Math.max(0, Math.min(100,
                        entero(propiedades, "librespot.volumen", VOLUMEN_POR_DEFECTO)))));
    }

    /** @return el identificador de la aplicacion registrada en el panel de Spotify */
    public String clientId() {
        return clientId;
    }

    /** @return la direccion de retorno; debe coincidir con la registrada en el panel */
    public String redirectUri() {
        return redirectUri;
    }

    /** @return el nombre con el que librespot debe aparecer en la lista de dispositivos */
    public String nombreDispositivo() {
        return nombreDispositivo;
    }

    /**
     * Extrae el puerto del {@code redirectUri} para levantar ahi el servidor del callback.
     *
     * @return el puerto declarado, o 8888 si la direccion no lo dice
     */
    public int puertoDeRetorno() {
        try {
            int puerto = java.net.URI.create(redirectUri).getPort();
            return puerto == -1 ? 8888 : puerto;
        } catch (IllegalArgumentException direccionInvalida) {
            return 8888;
        }
    }

    /**
     * Extrae la ruta del {@code redirectUri} para registrar ahi el manejador del callback.
     *
     * @return la ruta declarada, o {@code /callback} si la direccion no la dice
     */
    public String rutaDeRetorno() {
        try {
            String ruta = java.net.URI.create(redirectUri).getPath();
            return (ruta == null || ruta.isBlank()) ? "/callback" : ruta;
        } catch (IllegalArgumentException direccionInvalida) {
            return "/callback";
        }
    }

    /** @return la calidad del flujo de librespot, en kbps */
    public int bitrate() {
        return bitrate;
    }

    /** @return el puerto donde librespot recibe su propia autorizacion */
    public int puertoOauthLibrespot() {
        return puertoOauthLibrespot;
    }

    /** @return el volumen con el que arranca librespot, de 0 a 100 */
    public int volumenInicial() {
        return volumenInicial;
    }

    /**
     * Lee un entero opcional del archivo.
     *
     * <p>Un valor mal escrito no tumba la aplicacion: se usa el valor por defecto, porque una
     * errata en un ajuste secundario no justifica dejar sin audio al usuario.</p>
     */
    private static int entero(Properties propiedades, String clave, int porDefecto) {
        String valor = limpio(propiedades.getProperty(clave));
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException valorMalEscrito) {
            return porDefecto;
        }
    }

    private static String limpio(String valor) {
        if (valor == null) {
            return null;
        }
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
