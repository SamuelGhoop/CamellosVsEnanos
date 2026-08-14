package com.eia.reproductor.servicios.spotify;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.servicios.ReproductorAudio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del contrato de la fuente de Spotify.
 *
 * <p>No se prueba la reproduccion real —eso necesita cuenta, red y librespot, y se verifica con
 * {@link DiagnosticoSpotify}—. Lo que se prueba aqui es lo que el enrutador consulta para decidir:
 * si la fuente esta disponible y si sabe reproducir una cancion. Equivocarse en eso deja la
 * aplicacion muda o mandando canciones a una fuente que no puede con ellas.</p>
 */
class AudioSpotifyServiceTest {

    /** Proceso de mentira: permite fijar si esta vivo y con que dispositivo, sin lanzar nada. */
    private static final class LibrespotFalso extends ProcesoLibrespot {
        private boolean vivo;
        private DispositivoSpotify dispositivo;

        LibrespotFalso(ConfiguracionSpotify configuracion) {
            super(configuracion, null);
        }

        @Override public boolean activo() {
            return vivo;
        }

        @Override public Optional<DispositivoSpotify> dispositivo() {
            return Optional.ofNullable(dispositivo);
        }
    }

    private static ConfiguracionSpotify configuracion() {
        // Se apoya en la carga real desde un archivo para no duplicar el formato.
        return ConfiguracionSpotify.cargar(escribirConfiguracion()).orElseThrow();
    }

    private static Path escribirConfiguracion() {
        try {
            Path archivo = java.nio.file.Files.createTempFile("spotify", ".properties");
            java.nio.file.Files.writeString(archivo, """
                    client.id=abc123
                    redirect.uri=http://127.0.0.1:8888/callback
                    device.name=Camellos vs Enanos
                    """);
            archivo.toFile().deleteOnExit();
            return archivo;
        } catch (java.io.IOException fallo) {
            throw new IllegalStateException(fallo);
        }
    }

    private static AudioSpotifyService servicioCon(LibrespotFalso librespot) {
        ConfiguracionSpotify config = configuracion();
        AutenticacionSpotify autenticacion = new AutenticacionSpotify(
                config, new AlmacenTokenSpotify(Path.of("no-existe-a-proposito.json")));
        return new AudioSpotifyService(
                config, autenticacion, new ClienteWebApiSpotify(autenticacion), librespot);
    }

    private static Cancion conUri(String uri) {
        Cancion cancion = new Cancion("Una canción");
        cancion.setUriSpotify(uri);
        return cancion;
    }

    @Test
    @DisplayName("Sabe reproducir solo las canciones con URI de Spotify")
    void soloLasQueTienenUri() {
        AudioSpotifyService servicio = servicioCon(new LibrespotFalso(configuracion()));

        assertTrue(servicio.puedeReproducir(conUri("spotify:track:abc")));
        assertFalse(servicio.puedeReproducir(new Cancion("Solo metadata")));
        assertFalse(servicio.puedeReproducir(conUri("   ")));
        assertFalse(servicio.puedeReproducir(null));
    }

    @Test
    @DisplayName("No está disponible mientras el dispositivo no esté listo")
    void noDisponibleSinDispositivoListo() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = true;

        // Arrancar librespot no basta: falta transferirle la reproduccion.
        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("No está disponible si el proceso se murió")
    void noDisponibleSinProceso() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = false;

        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("Sin sesión guardada no está disponible aunque el proceso viva")
    void noDisponibleSinSesion() {
        LibrespotFalso librespot = new LibrespotFalso(configuracion());
        librespot.vivo = true;
        librespot.dispositivo = new DispositivoSpotify("id", "Camellos vs Enanos", true, "Speaker");

        // El almacen apunta a un archivo inexistente: no hay token ni forma de renovarlo.
        assertFalse(servicioCon(librespot).disponible());
    }

    @Test
    @DisplayName("Declara que necesita red, para que el interruptor FUENTE la descarte")
    void declaraQueNecesitaRed() {
        assertTrue(servicioCon(new LibrespotFalso(configuracion())).requiereRed());
    }

    @Test
    @DisplayName("Se identifica con un nombre para la interfaz")
    void seIdentifica() {
        assertEquals("Spotify", servicioCon(new LibrespotFalso(configuracion())).nombreFuente());
    }

    @Test
    @DisplayName("Cumple el contrato de ReproductorAudio")
    void cumpleElContrato() {
        ReproductorAudio comoInterfaz = servicioCon(new LibrespotFalso(configuracion()));

        // El controlador solo ve este tipo: si esto compila y responde, el polimorfismo se sostiene.
        assertEquals(0, comoInterfaz.posicionMsProperty().get());
        assertEquals(0, comoInterfaz.duracionMsProperty().get());
        assertFalse(comoInterfaz.reproduciendoProperty().get());
    }

    @Test
    @DisplayName("Las órdenes con la fuente no disponible no revientan")
    void lasOrdenesSonInocuasSinDispositivo() {
        AudioSpotifyService servicio = servicioCon(new LibrespotFalso(configuracion()));

        servicio.reproducir(conUri("spotify:track:abc"));
        servicio.pausar();
        servicio.detener();

        assertFalse(servicio.reproduciendoProperty().get());
    }

    // Nota: crearSiEstaConfigurado() no se prueba aqui a proposito. En esta maquina todos sus
    // requisitos se cumplen, asi que llamarlo lanzaria librespot de verdad en mitad de "mvn test".
    // Ese camino se verifica con DiagnosticoSpotify.
}
