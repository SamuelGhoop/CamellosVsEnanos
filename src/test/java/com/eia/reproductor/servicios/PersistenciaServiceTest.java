package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del {@link PersistenciaService}.
 *
 * <p>Todas trabajan sobre una carpeta temporal, asi que nunca tocan {@code data/biblioteca.json}.</p>
 */
@DisplayName("Persistencia de la biblioteca en JSON")
class PersistenciaServiceTest {

    @TempDir
    Path carpetaTemporal;

    private Path archivo() {
        return carpetaTemporal.resolve("biblioteca.json");
    }

    private Cancion cancionCompleta() {
        Cancion cancion = new Cancion("Bohemian Rhapsody");
        cancion.setArtista("Queen");
        cancion.setAlbum("A Night at the Opera");
        cancion.setDuracionSegundos(355);
        cancion.setGenero("Rock");
        cancion.setAnio(1975);
        cancion.setCalificacion(98);
        cancion.setRutaArchivo("data/musica/bohemian.mp3");
        cancion.setRutaPortada("data/covers/abc.jpg");
        cancion.setUrlPortadaRemota("https://ejemplo/600x600bb.jpg");
        cancion.setFavorita(true);
        cancion.setVecesReproducida(12);
        return cancion;
    }

    @Test
    @DisplayName("guardar y volver a cargar conserva todos los campos")
    void viajeDeIdaYVuelta() {
        PersistenciaService servicio = new PersistenciaService(archivo());
        Cancion original = cancionCompleta();

        assertTrue(servicio.guardar(List.of(original)));
        List<Cancion> recuperadas = servicio.cargar();

        assertEquals(1, recuperadas.size());
        Cancion copia = recuperadas.get(0);
        assertEquals(original.getId(), copia.getId(), "el id debe sobrevivir entre ejecuciones");
        assertEquals(original.getTitulo(), copia.getTitulo());
        assertEquals(original.getArtista(), copia.getArtista());
        assertEquals(original.getAlbum(), copia.getAlbum());
        assertEquals(original.getDuracionSegundos(), copia.getDuracionSegundos());
        assertEquals(original.getGenero(), copia.getGenero());
        assertEquals(original.getAnio(), copia.getAnio());
        assertEquals(original.getCalificacion(), copia.getCalificacion());
        assertEquals(original.getRutaArchivo(), copia.getRutaArchivo());
        assertEquals(original.getRutaPortada(), copia.getRutaPortada());
        assertEquals(original.getUrlPortadaRemota(), copia.getUrlPortadaRemota());
        assertEquals(original.isFavorita(), copia.isFavorita());
        assertEquals(original.getVecesReproducida(), copia.getVecesReproducida());
    }

    @Test
    @DisplayName("los acentos sobreviven al archivo")
    void acentosCorrectos() {
        PersistenciaService servicio = new PersistenciaService(archivo());
        Cancion cancion = new Cancion("Ángel de la Guarda");
        cancion.setArtista("Mägo de Oz");

        servicio.guardar(List.of(cancion));
        List<Cancion> recuperadas = servicio.cargar();

        assertEquals("Ángel de la Guarda", recuperadas.get(0).getTitulo());
        assertEquals("Mägo de Oz", recuperadas.get(0).getArtista());
    }

    @Test
    @DisplayName("si el archivo no existe se arranca con la biblioteca vacia")
    void archivoInexistente() {
        PersistenciaService servicio = new PersistenciaService(archivo());

        assertTrue(servicio.cargar().isEmpty());
        assertTrue(servicio.ultimoAviso().isEmpty(), "no existir todavia no es un problema");
    }

    @Test
    @DisplayName("guardar crea la carpeta si hace falta")
    void creaLaCarpeta() {
        Path anidado = carpetaTemporal.resolve("data").resolve("biblioteca.json");
        PersistenciaService servicio = new PersistenciaService(anidado);

        assertTrue(servicio.guardar(List.of(new Cancion("Prueba"))));
        assertTrue(Files.exists(anidado));
    }

    @Test
    @DisplayName("un archivo corrupto se respalda y la aplicacion arranca vacia")
    void archivoCorrupto() throws IOException {
        Files.writeString(archivo(), "{ esto no es JSON valido ][", StandardCharsets.UTF_8);
        PersistenciaService servicio = new PersistenciaService(archivo());

        List<Cancion> recuperadas = servicio.cargar();

        assertTrue(recuperadas.isEmpty());
        Path respaldo = carpetaTemporal.resolve("biblioteca.json.bak");
        assertTrue(Files.exists(respaldo), "debe conservarse una copia antes de descartar");
        assertTrue(Files.readString(respaldo).contains("esto no es JSON valido"),
                "el respaldo debe tener el contenido original intacto");
        assertTrue(servicio.ultimoAviso().isPresent(), "debe quedar un aviso para mostrar en pantalla");
    }

    @Test
    @DisplayName("un archivo vacio no rompe la carga")
    void archivoVacio() throws IOException {
        Files.writeString(archivo(), "", StandardCharsets.UTF_8);
        PersistenciaService servicio = new PersistenciaService(archivo());

        assertTrue(servicio.cargar().isEmpty());
    }

    @Test
    @DisplayName("un JSON editado a mano sin campos opcionales toma los valores por defecto")
    void jsonIncompleto() throws IOException {
        // Esto es lo que pasaria si alguien abre el archivo y escribe una cancion a mano.
        Files.writeString(archivo(), """
                [
                  { "id": "abc-123", "titulo": "Cancion Minima" }
                ]
                """, StandardCharsets.UTF_8);
        PersistenciaService servicio = new PersistenciaService(archivo());

        List<Cancion> recuperadas = servicio.cargar();

        assertEquals(1, recuperadas.size());
        Cancion cancion = recuperadas.get(0);
        assertEquals("abc-123", cancion.getId());
        assertEquals("Cancion Minima", cancion.getTitulo());
        // Sin el deserializador propio, estos campos llegarian en null y reventarian la interfaz.
        assertEquals(Cancion.TEXTO_DESCONOCIDO, cancion.getArtista());
        assertEquals(Cancion.TEXTO_DESCONOCIDO, cancion.getAlbum());
        assertEquals(Cancion.TEXTO_DESCONOCIDO, cancion.getGenero());
        assertEquals(0, cancion.getCalificacion());
        assertEquals(0, cancion.getDuracionSegundos());
        assertFalse(cancion.isFavorita());
        assertNull(cancion.getRutaArchivo());
    }

    @Test
    @DisplayName("una calificacion fuera de rango se recorta en vez de tumbar la carga")
    void calificacionFueraDeRango() throws IOException {
        Files.writeString(archivo(), """
                [
                  { "id": "a", "titulo": "Pasada",  "calificacion": 500 },
                  { "id": "b", "titulo": "Negativa", "calificacion": -30 }
                ]
                """, StandardCharsets.UTF_8);
        PersistenciaService servicio = new PersistenciaService(archivo());

        List<Cancion> recuperadas = servicio.cargar();

        assertEquals(2, recuperadas.size());
        assertEquals(Cancion.CALIFICACION_MAX, recuperadas.get(0).getCalificacion());
        assertEquals(Cancion.CALIFICACION_MIN, recuperadas.get(1).getCalificacion());
    }

    @Test
    @DisplayName("una cancion sin id recibe uno nuevo")
    void cancionSinId() throws IOException {
        Files.writeString(archivo(), """
                [ { "titulo": "Sin identificador" } ]
                """, StandardCharsets.UTF_8);
        PersistenciaService servicio = new PersistenciaService(archivo());

        List<Cancion> recuperadas = servicio.cargar();

        assertEquals(1, recuperadas.size());
        assertFalse(recuperadas.get(0).getId().isBlank());
    }

    @Test
    @DisplayName("guardar no deja archivos temporales tirados")
    void sinTemporalesHuerfanos() throws IOException {
        PersistenciaService servicio = new PersistenciaService(archivo());
        servicio.guardar(List.of(new Cancion("Prueba")));

        try (var contenido = Files.list(carpetaTemporal)) {
            assertTrue(contenido.noneMatch(ruta -> ruta.toString().endsWith(".tmp")),
                    "el archivo temporal debe haberse renombrado al definitivo");
        }
    }

    @Test
    @DisplayName("guardar dos veces reemplaza el contenido, no lo acumula")
    void guardadosSucesivos() {
        PersistenciaService servicio = new PersistenciaService(archivo());

        servicio.guardar(List.of(new Cancion("Primera"), new Cancion("Segunda")));
        servicio.guardar(List.of(new Cancion("Unica")));

        List<Cancion> recuperadas = servicio.cargar();
        assertEquals(1, recuperadas.size());
        assertEquals("Unica", recuperadas.get(0).getTitulo());
    }

    @Test
    @DisplayName("guardar una biblioteca vacia deja un arreglo vacio valido")
    void guardarBibliotecaVacia() {
        PersistenciaService servicio = new PersistenciaService(archivo());

        assertTrue(servicio.guardar(List.of()));
        assertTrue(servicio.cargar().isEmpty());
        assertTrue(servicio.ultimoAviso().isEmpty(), "no deberia reportarse como corrupto");
    }
}
