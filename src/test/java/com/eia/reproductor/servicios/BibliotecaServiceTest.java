package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modos.ModoAlfabetico;
import com.eia.reproductor.modos.ModoOrdenLlegada;
import com.eia.reproductor.modos.ModoReproduccion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del {@link BibliotecaService} como fuente unica de verdad. */
@DisplayName("Biblioteca (fuente unica de verdad)")
class BibliotecaServiceTest {

    @TempDir
    Path carpetaTemporal;

    private BibliotecaService biblioteca;
    private Cancion creep;
    private Cancion africa;
    private Cancion zombie;

    @BeforeEach
    void preparar() {
        biblioteca = new BibliotecaService(
                new PersistenciaService(carpetaTemporal.resolve("biblioteca.json")));
        creep = cancion("Creep", "Radiohead", "Rock");
        africa = cancion("Africa", "Toto", "Pop");
        zombie = cancion("Zombie", "The Cranberries", "Rock");
    }

    private static Cancion cancion(String titulo, String artista, String genero) {
        Cancion nueva = new Cancion(titulo);
        nueva.setArtista(artista);
        nueva.setGenero(genero);
        return nueva;
    }

    /** Observador de prueba que anota que eventos recibio y en que orden. */
    private static class ObservadorEspia implements ObservadorBiblioteca {
        final List<String> eventos = new ArrayList<>();

        @Override public void cancionAgregada(Cancion c) { eventos.add("agregada:" + c.getTitulo()); }
        @Override public void cancionEliminada(Cancion c) { eventos.add("eliminada:" + c.getTitulo()); }
        @Override public void antesDeEditar(Cancion c) { eventos.add("antes:" + c.getTitulo()); }
        @Override public void despuesDeEditar(Cancion c) { eventos.add("despues:" + c.getTitulo()); }
        @Override public void bibliotecaRecargada(Iterable<Cancion> cs) { eventos.add("recargada"); }
    }

    @Nested
    @DisplayName("Operaciones basicas")
    class OperacionesBasicas {

        @Test
        @DisplayName("una biblioteca nueva esta vacia")
        void bibliotecaNueva() {
            assertTrue(biblioteca.estaVacia());
            assertEquals(0, biblioteca.tamanio());
            assertTrue(biblioteca.todas().isEmpty());
        }

        @Test
        @DisplayName("agregar conserva el orden de insercion")
        void ordenDeInsercion() {
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);
            biblioteca.agregar(zombie);

            // Este orden es informacion del dominio: de el depende el modo de orden de llegada.
            assertEquals(List.of(creep, africa, zombie), biblioteca.todas());
        }

        @Test
        @DisplayName("no se agrega dos veces la misma cancion")
        void sinDuplicados() {
            assertTrue(biblioteca.agregar(creep));
            assertFalse(biblioteca.agregar(creep));

            assertEquals(1, biblioteca.tamanio());
        }

        @Test
        @DisplayName("dos canciones distintas con el mismo titulo si conviven")
        void homonimasConviven() {
            Cancion unaVersion = cancion("Hurt", "Johnny Cash", "Country");
            Cancion otraVersion = cancion("Hurt", "Nine Inch Nails", "Rock");

            assertTrue(biblioteca.agregar(unaVersion));
            assertTrue(biblioteca.agregar(otraVersion));
            assertEquals(2, biblioteca.tamanio());
        }

        @Test
        @DisplayName("eliminar saca la cancion y devuelve false si no estaba")
        void eliminar() {
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);

            assertTrue(biblioteca.eliminar(creep));
            assertEquals(List.of(africa), biblioteca.todas());
            assertFalse(biblioteca.eliminar(creep), "ya no esta, no se puede eliminar dos veces");
            assertFalse(biblioteca.eliminar(null));
        }

        @Test
        @DisplayName("no se admiten canciones nulas")
        void rechazaNulos() {
            assertThrows(NullPointerException.class, () -> biblioteca.agregar(null));
        }

        @Test
        @DisplayName("contiene y porId localizan las canciones")
        void localizar() {
            biblioteca.agregar(creep);

            assertTrue(biblioteca.contiene(creep));
            assertFalse(biblioteca.contiene(africa));
            assertEquals(creep, biblioteca.porId(creep.getId()));
            assertNull(biblioteca.porId("no-existe"));
        }
    }

    @Nested
    @DisplayName("Busquedas y filtros")
    class BusquedasYFiltros {

        @BeforeEach
        void cargarCanciones() {
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);
            biblioteca.agregar(zombie);
        }

        @Test
        @DisplayName("buscar encuentra por titulo, artista o album")
        void buscarEnVariosCampos() {
            assertEquals(List.of(creep), biblioteca.buscar("creep"));
            assertEquals(List.of(africa), biblioteca.buscar("toto"));
            assertEquals(List.of(zombie), biblioteca.buscar("cranberries"));
        }

        @Test
        @DisplayName("buscar ignora mayusculas y tildes")
        void buscarIgnoraTildes() {
            Cancion conTilde = cancion("Ángel", "Robbie Williams", "Pop");
            biblioteca.agregar(conTilde);

            assertEquals(List.of(conTilde), biblioteca.buscar("angel"));
            assertEquals(List.of(conTilde), biblioteca.buscar("ÁNGEL"));
        }

        @Test
        @DisplayName("buscar sin texto devuelve la biblioteca completa")
        void buscarVacio() {
            assertEquals(3, biblioteca.buscar("").size());
            assertEquals(3, biblioteca.buscar(null).size());
        }

        @Test
        @DisplayName("buscar algo inexistente devuelve una lista vacia")
        void buscarSinResultados() {
            assertTrue(biblioteca.buscar("reggaeton").isEmpty());
        }

        @Test
        @DisplayName("filtrar sirve de base para los filtros por genero y favoritas")
        void filtrar() {
            assertEquals(List.of(creep, zombie),
                    biblioteca.filtrar(c -> "Rock".equals(c.getGenero())));

            biblioteca.alternarFavorita(africa);
            assertEquals(List.of(africa), biblioteca.filtrar(Cancion::isFavorita));
        }
    }

    @Nested
    @DisplayName("Edicion")
    class Edicion {

        @Test
        @DisplayName("editar aplica los cambios sobre la cancion")
        void editarAplicaCambios() {
            biblioteca.agregar(creep);

            assertTrue(biblioteca.editar(creep, c -> c.setArtista("Radiohead (remaster)")));

            assertEquals("Radiohead (remaster)", creep.getArtista());
        }

        @Test
        @DisplayName("calificar valida el rango")
        void calificar() {
            biblioteca.agregar(creep);

            assertTrue(biblioteca.calificar(creep, 87));
            assertEquals(87, creep.getCalificacion());
            assertThrows(IllegalArgumentException.class, () -> biblioteca.calificar(creep, 150));
        }

        @Test
        @DisplayName("editar una cancion ajena a la biblioteca no hace nada")
        void editarCancionAjena() {
            assertFalse(biblioteca.editar(creep, c -> c.setArtista("X")));
            assertEquals("Radiohead", creep.getArtista());
        }
    }

    @Nested
    @DisplayName("Observadores")
    class Observadores {

        private ObservadorEspia espia;

        @BeforeEach
        void registrarEspia() {
            espia = new ObservadorEspia();
            biblioteca.registrarObservador(espia);
        }

        @Test
        @DisplayName("se avisa de altas y bajas")
        void avisaAltasYBajas() {
            biblioteca.agregar(creep);
            biblioteca.eliminar(creep);

            assertEquals(List.of("agregada:Creep", "eliminada:Creep"), espia.eventos);
        }

        @Test
        @DisplayName("la edicion avisa antes y despues, en ese orden")
        void ventanaDeEdicion() {
            biblioteca.agregar(creep);
            espia.eventos.clear();

            biblioteca.editar(creep, c -> c.setTitulo("Creep (Acoustic)"));

            // El 'antes' llega con el titulo viejo y el 'despues' con el nuevo: esa es la ventana
            // que aprovecha el arbol para reubicar el nodo.
            assertEquals(List.of("antes:Creep", "despues:Creep (Acoustic)"), espia.eventos);
        }

        @Test
        @DisplayName("no se avisa de altas rechazadas por duplicado")
        void sinAvisoSiNoHuboCambio() {
            biblioteca.agregar(creep);
            espia.eventos.clear();

            biblioteca.agregar(creep);
            biblioteca.eliminar(africa);

            assertTrue(espia.eventos.isEmpty());
        }

        @Test
        @DisplayName("un observador dado de baja deja de recibir avisos")
        void quitarObservador() {
            biblioteca.quitarObservador(espia);

            biblioteca.agregar(creep);

            assertTrue(espia.eventos.isEmpty());
        }

        @Test
        @DisplayName("registrar dos veces al mismo observador no duplica los avisos")
        void registroIdempotente() {
            biblioteca.registrarObservador(espia);

            biblioteca.agregar(creep);

            assertEquals(1, espia.eventos.size());
        }
    }

    @Nested
    @DisplayName("Sincronizacion con los modos")
    class SincronizacionConModos {

        /** Adaptador que conecta la biblioteca con el modo activo, como hara el controlador. */
        private ObservadorBiblioteca conectar(ModoReproduccion modo) {
            return new ObservadorBiblioteca() {
                @Override public void cancionAgregada(Cancion c) { modo.agregar(c); }
                @Override public void cancionEliminada(Cancion c) { modo.eliminar(c); }
                @Override public void antesDeEditar(Cancion c) { modo.prepararEdicion(c); }
                @Override public void despuesDeEditar(Cancion c) { modo.confirmarEdicion(c); }
                @Override public void bibliotecaRecargada(Iterable<Cancion> cs) { modo.cargar(cs); }
            };
        }

        @Test
        @DisplayName("agregar a la biblioteca se refleja en el modo activo")
        void altaSePropaga() {
            ModoReproduccion modo = new ModoOrdenLlegada();
            biblioteca.registrarObservador(conectar(modo));
            biblioteca.agregar(creep);
            modo.cargar(biblioteca.todas());

            biblioteca.agregar(africa);

            assertEquals(2, modo.listaReproduccion().size());
        }

        @Test
        @DisplayName("eliminar de la biblioteca se refleja en el modo activo")
        void bajaSePropaga() {
            ModoReproduccion modo = new ModoOrdenLlegada();
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);
            modo.cargar(biblioteca.todas());
            biblioteca.registrarObservador(conectar(modo));

            biblioteca.eliminar(creep);

            assertEquals(List.of(africa), modo.listaReproduccion());
        }

        @Test
        @DisplayName("renombrar una cancion la recoloca en el arbol alfabetico")
        void renombrarRecolocaEnElArbol() {
            ModoAlfabetico modo = new ModoAlfabetico();
            biblioteca.agregar(africa);
            biblioteca.agregar(creep);
            biblioteca.agregar(zombie);
            modo.cargar(biblioteca.todas());
            biblioteca.registrarObservador(conectar(modo));

            assertEquals(List.of("Africa", "Creep", "Zombie"), titulos(modo));

            // "Creep" pasa a empezar por B: debe moverse al medio... y sobre todo, el arbol tiene
            // que poder seguir encontrandola. Sin la ventana antes/despues, el nodo quedaria
            // colgado bajo la letra C y el arbol la perderia para siempre.
            biblioteca.editar(creep, c -> c.setTitulo("Bailando"));

            assertEquals(List.of("Africa", "Bailando", "Zombie"), titulos(modo));
            assertEquals(3, modo.listaReproduccion().size(), "no se puede perder ninguna cancion");
            // Y la navegacion sigue viva sobre la cancion renombrada.
            assertEquals("Africa", modo.siguiente().getTitulo());
            assertEquals("Bailando", modo.siguiente().getTitulo());
            assertEquals("Zombie", modo.siguiente().getTitulo());
        }

        @Test
        @DisplayName("renombrar la cancion que suena la conserva como actual")
        void renombrarLaQueSuena() {
            ModoAlfabetico modo = new ModoAlfabetico();
            biblioteca.agregar(africa);
            biblioteca.agregar(creep);
            modo.cargar(biblioteca.todas());
            biblioteca.registrarObservador(conectar(modo));
            modo.siguiente();
            assertEquals("Africa", modo.actual().getTitulo());

            biblioteca.editar(africa, c -> c.setTitulo("Zebra"));

            assertEquals("Zebra", modo.actual().getTitulo(), "debe seguir sonando la misma cancion");
            assertEquals(List.of("Creep", "Zebra"), titulos(modo));
            // Ahora es la ultima alfabeticamente, asi que avanzar da la vuelta al principio.
            assertEquals("Creep", modo.siguiente().getTitulo());
        }

        @Test
        @DisplayName("editar sin tocar el titulo no altera el orden")
        void editarSinRenombrar() {
            ModoAlfabetico modo = new ModoAlfabetico();
            biblioteca.agregar(africa);
            biblioteca.agregar(creep);
            modo.cargar(biblioteca.todas());
            biblioteca.registrarObservador(conectar(modo));

            biblioteca.calificar(creep, 90);

            assertEquals(List.of("Africa", "Creep"), titulos(modo));
            assertEquals(90, creep.getCalificacion());
        }

        private List<String> titulos(ModoReproduccion modo) {
            List<String> resultado = new ArrayList<>();
            for (Cancion cancion : modo.listaReproduccion()) {
                resultado.add(cancion.getTitulo());
            }
            return resultado;
        }
    }

    @Nested
    @DisplayName("Persistencia")
    class Persistencia {

        @Test
        @DisplayName("los cambios se guardan solos y sobreviven a reabrir la aplicacion")
        void guardadoAutomatico() {
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);
            biblioteca.calificar(creep, 75);

            // Se simula cerrar y volver a abrir: una instancia nueva sobre el mismo archivo.
            BibliotecaService recargada = new BibliotecaService(
                    new PersistenciaService(carpetaTemporal.resolve("biblioteca.json")));
            assertEquals(2, recargada.cargarDesdeDisco());

            List<Cancion> todas = recargada.todas();
            assertEquals("Creep", todas.get(0).getTitulo(), "debe conservarse el orden de insercion");
            assertEquals(75, todas.get(0).getCalificacion());
            assertEquals("Africa", todas.get(1).getTitulo());
        }

        @Test
        @DisplayName("eliminar tambien se persiste")
        void bajaPersistida() {
            biblioteca.agregar(creep);
            biblioteca.agregar(africa);
            biblioteca.eliminar(creep);

            BibliotecaService recargada = new BibliotecaService(
                    new PersistenciaService(carpetaTemporal.resolve("biblioteca.json")));
            recargada.cargarDesdeDisco();

            assertEquals(1, recargada.tamanio());
            assertEquals("Africa", recargada.todas().get(0).getTitulo());
        }

        @Test
        @DisplayName("cargar desde disco avisa a los observadores")
        void recargaAvisa() {
            biblioteca.agregar(creep);
            ObservadorEspia espia = new ObservadorEspia();
            biblioteca.registrarObservador(espia);

            biblioteca.cargarDesdeDisco();

            assertEquals(List.of("recargada"), espia.eventos);
        }

        @Test
        @DisplayName("arrancar sin archivo deja la biblioteca vacia y sin errores")
        void arranqueSinArchivo() {
            BibliotecaService nueva = new BibliotecaService(
                    new PersistenciaService(carpetaTemporal.resolve("no-existe.json")));

            assertEquals(0, nueva.cargarDesdeDisco());
            assertTrue(nueva.estaVacia());
            assertTrue(nueva.ultimoAviso().isEmpty());
        }

        @Test
        @DisplayName("cargar desde disco reemplaza lo que hubiera en memoria")
        void recargaReemplaza() {
            biblioteca.agregar(creep);
            biblioteca.guardar();
            biblioteca.agregar(africa);

            biblioteca.cargarDesdeDisco();

            // Africa se agrego despues del guardado explicito... pero agregar() ya persiste solo,
            // asi que las dos estan en disco. Lo que se comprueba es que no se dupliquen.
            assertEquals(2, biblioteca.tamanio());
            assertNotNull(biblioteca.porId(creep.getId()));
        }
    }
}
