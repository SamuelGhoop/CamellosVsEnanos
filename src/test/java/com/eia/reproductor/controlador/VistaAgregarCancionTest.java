package com.eia.reproductor.controlador;

import com.eia.reproductor.App;
import com.eia.reproductor.servicios.EntornoJavaFx;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprueba que la vista del dialogo de canciones se carga de verdad.
 *
 * <p><b>Por que existe.</b> Este FXML solo se lee cuando el usuario pulsa AGREGAR, asi que un
 * error dentro —un atributo mal escrito, un {@code fx:id} que el controlador ya no tiene— no
 * aparece al compilar ni al arrancar la aplicacion: revienta en la cara del usuario al abrir el
 * dialogo. Ya paso una vez con un comentario XML invalido en la vista principal.</p>
 *
 * <p>Solo se prueba esta vista y no la principal: cargar {@code principal.fxml} construye el
 * controlador, que a su vez levanta el audio y lanzaria librespot en mitad de las pruebas.</p>
 */
class VistaAgregarCancionTest {

    private static final String RUTA_VISTA = "/vista/agregar-cancion.fxml";

    /** Campos que el controlador espera encontrar; si falta uno, el dialogo sale a medias. */
    private static final List<String> CAMPOS_ESPERADOS = List.of(
            "campoTitulo", "campoArtista", "campoAlbum", "campoGenero",
            "campoAnio", "campoDuracion", "campoUriSpotify", "campoConsulta");

    @BeforeAll
    static void prepararEntorno() {
        assumeTrue(EntornoJavaFx.disponible(), "No hay entorno grafico para levantar JavaFX.");
    }

    private static Parent cargarVista() {
        URL url = VistaAgregarCancionTest.class.getResource(RUTA_VISTA);
        assertNotNull(url, "No se encontró " + RUTA_VISTA + " en el classpath.");
        try {
            return new FXMLLoader(url).load();
        } catch (Exception fallo) {
            throw new AssertionError("La vista no se pudo cargar: " + fallo.getMessage(), fallo);
        }
    }

    @Test
    @DisplayName("La vista del diálogo se carga sin errores")
    void laVistaSeCarga() {
        EntornoJavaFx.enElHiloFx(() -> {
            App.cargarFuentePixel();
            assertDoesNotThrow(VistaAgregarCancionTest::cargarVista);
        });
    }

    @Test
    @DisplayName("Están todos los campos que el controlador necesita")
    void estanTodosLosCampos() {
        EntornoJavaFx.enElHiloFx(() -> {
            App.cargarFuentePixel();
            Parent raiz = cargarVista();

            for (String campo : CAMPOS_ESPERADOS) {
                assertNotNull(raiz.lookup("#" + campo), "falta el campo " + campo);
            }
        });
    }

    @Test
    @DisplayName("El campo de Spotify existe y anuncia que se rellena solo")
    void elCampoDeSpotifyAnunciaQueSeAutocompleta() {
        EntornoJavaFx.enElHiloFx(() -> {
            App.cargarFuentePixel();
            Parent raiz = cargarVista();

            TextField spotify = (TextField) raiz.lookup("#campoUriSpotify");
            assertNotNull(spotify);
            // Sin esta pista, el campo parece de rellenado obligatorio y nadie lo entiende.
            assertNotNull(spotify.getPromptText());
        });
    }
}
