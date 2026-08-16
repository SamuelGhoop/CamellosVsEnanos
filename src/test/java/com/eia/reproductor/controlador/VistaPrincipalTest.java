package com.eia.reproductor.controlador;

import com.eia.reproductor.App;
import com.eia.reproductor.servicios.EntornoJavaFx;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Comprueba que la vista principal y su controlador siguen encajando. */
class VistaPrincipalTest {
    private static final String RUTA_VISTA = "/vista/principal.fxml";

    /** Campos que el controlador rellena a mano y que por eso no llevan {@code fx:id}. */
    private static final Set<String> SIN_FX_ID = Set.of();

    private static Document vista() {
        try (InputStream entrada = VistaPrincipalTest.class.getResourceAsStream(RUTA_VISTA)) {
            assertNotNull(entrada, "No se encontró " + RUTA_VISTA + " en el classpath.");
            // Sin espacio de nombres: asi "fx:id" se lee tal cual, como atributo literal.
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entrada);
        } catch (Exception fallo) {
            throw new AssertionError("La vista no es XML válido: " + fallo.getMessage(), fallo);
        }
    }

    /** Recoge el valor de un atributo en todos los elementos del documento. */
    private static Set<String> atributos(String nombre) {
        NodeList todos = vista().getElementsByTagName("*");
        Set<String> valores = new HashSet<>();
        for (int i = 0; i < todos.getLength(); i++) {
            String valor = ((Element) todos.item(i)).getAttribute(nombre);
            if (!valor.isEmpty()) {
                valores.add(valor);
            }
        }
        return valores;
    }

    @Test
    @DisplayName("El FXML de la vista principal es XML bien formado")
    void laVistaEsXmlValido() {
        assertNotNull(vista().getDocumentElement());
    }

    @Test
    @DisplayName("La vista se construye entera: cada control acepta sus atributos")
    void laVistaSeConstruye(@TempDir Path carpeta) {
        // Comprobar el XML no basta: "bien formado" no quiere decir que un ComboBox acepte el
        // atributo que le pusimos, ni que falte un <?import?>. Eso solo lo dice el FXMLLoader.
        assumeTrue(EntornoJavaFx.disponible(), "No hay entorno gráfico para levantar JavaFX.");

        Path copia = sinControlador(carpeta);
        EntornoJavaFx.enElHiloFx(() -> {
            App.cargarFuentePixel();
            assertDoesNotThrow(() -> new FXMLLoader(copia.toUri().toURL()).load(),
                    "La vista principal no se pudo construir");
        });
    }

    /** Escribe una copia de la vista sin el controlador ni los manejadores. */
    private static Path sinControlador(Path carpeta) {
        try (InputStream entrada = VistaPrincipalTest.class.getResourceAsStream(RUTA_VISTA)) {
            assertNotNull(entrada, "No se encontró " + RUTA_VISTA + " en el classpath.");
            String fxml = new String(entrada.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s*fx:controller=\"[^\"]*\"", "")
                    .replaceAll("\\s*onAction=\"[^\"]*\"", "");
            Path copia = carpeta.resolve("principal-sin-controlador.fxml");
            Files.writeString(copia, fxml, StandardCharsets.UTF_8);
            return copia;
        } catch (Exception fallo) {
            throw new AssertionError("No se pudo preparar la copia: " + fallo.getMessage(), fallo);
        }
    }

    @Test
    @DisplayName("Cada campo @FXML del controlador tiene su fx:id en la vista")
    void ningunCampoSeQuedaSinInyectar() {
        Set<String> enLaVista = atributos("fx:id");
        List<String> huerfanos = new ArrayList<>();

        for (Field campo : PrincipalController.class.getDeclaredFields()) {
            if (campo.isAnnotationPresent(FXML.class)
                    && !SIN_FX_ID.contains(campo.getName())
                    && !enLaVista.contains(campo.getName())) {
                huerfanos.add(campo.getName());
            }
        }

        assertTrue(huerfanos.isEmpty(),
                "Estos campos se quedarían nulos al arrancar: " + huerfanos);
    }

    @Test
    @DisplayName("Cada onAction de la vista apunta a un método que existe")
    void ningunBotonApuntaAlVacio() {
        Set<String> metodos = new HashSet<>();
        for (Method metodo : PrincipalController.class.getDeclaredMethods()) {
            metodos.add(metodo.getName());
        }

        List<String> rotos = new ArrayList<>();
        for (String accion : atributos("onAction")) {
            // Los manejadores se escriben "#nombreDelMetodo".
            String nombre = accion.startsWith("#") ? accion.substring(1) : accion;
            if (!metodos.contains(nombre)) {
                rotos.add(accion);
            }
        }

        assertTrue(rotos.isEmpty(), "Estos botones fallarían al pulsarlos: " + rotos);
    }

    @Test
    @DisplayName("Están los controles de los que depende cada bono de la rúbrica")
    void estanLosControlesDeLosBonos() {
        Set<String> enLaVista = atributos("fx:id");

        // Si alguien quita uno de estos por hacer sitio en la fila, el bono se va con él y no hay
        // nada que avise: la aplicación seguiría arrancando igual.
        for (String control : List.of(
                "selectorCampo",            // filtros por género, artista y álbum
                "columnaReproducciones",    // estadísticas, canción por canción
                "botonEstadisticas",        // ventana de estadísticas
                "selectorColeccion",        // historial y listas
                "botonTema")) {             // modo oscuro
            assertTrue(enLaVista.contains(control), "falta el control " + control);
        }
    }
}
