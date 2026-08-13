package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del {@link ModoOrdenLlegada}: FIFO estricto y sin marcha atras. */
@DisplayName("Modo 2 - Orden de llegada (cola simple)")
class ModoOrdenLlegadaTest {

    private ModoOrdenLlegada modo;
    private List<Cancion> biblioteca;
    private Cancion primera;
    private Cancion segunda;
    private Cancion tercera;

    @BeforeEach
    void preparar() {
        modo = new ModoOrdenLlegada();
        primera = new Cancion("Primera en llegar");
        segunda = new Cancion("Segunda en llegar");
        tercera = new Cancion("Tercera en llegar");
        biblioteca = new ArrayList<>(List.of(primera, segunda, tercera));
    }

    @Test
    @DisplayName("identifica su nombre y su estructura")
    void identidad() {
        assertEquals("Orden de llegada", modo.nombre());
        assertEquals("Cola Simple (FIFO)", modo.estructuraUsada());
    }

    @Test
    @DisplayName("reproduce exactamente en el orden en que se agregaron")
    void ordenFifo() {
        modo.cargar(biblioteca);

        assertEquals(primera, modo.siguiente());
        assertEquals(segunda, modo.siguiente());
        assertEquals(tercera, modo.siguiente());
    }

    @Test
    @DisplayName("no permite retroceder")
    void noPermiteRetroceder() {
        assertFalse(modo.permiteAnterior(),
                "la interfaz usa esta bandera para deshabilitar el boton Anterior");
    }

    @Test
    @DisplayName("llamar anterior() lanza UnsupportedOperationException")
    void anteriorNoSoportado() {
        modo.cargar(biblioteca);
        modo.siguiente();

        UnsupportedOperationException error =
                assertThrows(UnsupportedOperationException.class, () -> modo.anterior());
        assertTrue(error.getMessage().contains("Orden de llegada"));
    }

    @Test
    @DisplayName("anterior() falla incluso con la cola vacia, por no estar soportado")
    void anteriorFallaSiempre() {
        modo.cargar(List.of());
        assertThrows(UnsupportedOperationException.class, () -> modo.anterior());
    }

    @Test
    @DisplayName("una cancion reproducida sale de la cola")
    void loReproducidoSale() {
        modo.cargar(biblioteca);
        assertEquals(3, modo.listaReproduccion().size());

        modo.siguiente();

        assertEquals(2, modo.listaReproduccion().size());
        assertFalse(modo.listaReproduccion().contains(primera),
                "la cancion ya reproducida no puede seguir en la cola");
    }

    @Test
    @DisplayName("la cola se agota y no se puede seguir reproduciendo")
    void colaSeAgota() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        modo.siguiente();

        assertFalse(modo.hayMas(), "la interfaz debe mostrar 'Cola vacia'");
        assertTrue(modo.listaReproduccion().isEmpty());
        assertThrows(NoSuchElementException.class, () -> modo.siguiente());
    }

    @Test
    @DisplayName("recargar desde la biblioteca vuelve a llenar la cola")
    void recargarDesdeLaBiblioteca() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();
        modo.siguiente();
        assertFalse(modo.hayMas());

        // Es lo que hace el boton "Recargar cola desde la biblioteca".
        modo.cargar(biblioteca);

        assertTrue(modo.hayMas());
        assertEquals(3, modo.listaReproduccion().size());
        assertEquals(primera, modo.siguiente());
    }

    @Test
    @DisplayName("mostrar la lista de espera no la consume")
    void listaReproduccionNoConsume() {
        modo.cargar(biblioteca);

        assertEquals(List.of(primera, segunda, tercera), modo.listaReproduccion());
        assertEquals(List.of(primera, segunda, tercera), modo.listaReproduccion());
        assertEquals(3, modo.listaReproduccion().size());
    }

    @Test
    @DisplayName("agregar encola al final, respetando el orden de llegada")
    void agregarVaAlFinal() {
        modo.cargar(biblioteca);
        Cancion cuarta = new Cancion("Cuarta en llegar");

        modo.agregar(cuarta);

        assertEquals(List.of(primera, segunda, tercera, cuarta), modo.listaReproduccion());
    }

    @Test
    @DisplayName("agregar sobre una cola agotada la reactiva")
    void agregarSobreColaVacia() {
        modo.cargar(List.of());
        assertFalse(modo.hayMas());

        Cancion nueva = new Cancion("Nueva");
        modo.agregar(nueva);

        assertTrue(modo.hayMas());
        assertEquals(nueva, modo.siguiente());
    }

    @Test
    @DisplayName("eliminar saca la cancion sin romper el orden del resto")
    void eliminarConservaElOrden() {
        modo.cargar(biblioteca);

        modo.eliminar(segunda);

        assertEquals(List.of(primera, tercera), modo.listaReproduccion());
        assertEquals(primera, modo.siguiente());
        assertEquals(tercera, modo.siguiente());
    }

    @Test
    @DisplayName("eliminar la primera de la cola tambien funciona")
    void eliminarLaPrimera() {
        modo.cargar(biblioteca);

        modo.eliminar(primera);

        assertEquals(List.of(segunda, tercera), modo.listaReproduccion());
        assertEquals(segunda, modo.siguiente());
    }

    @Test
    @DisplayName("eliminar la ultima deja la cola coherente para seguir encolando")
    void eliminarLaUltima() {
        modo.cargar(biblioteca);

        modo.eliminar(tercera);
        Cancion nueva = new Cancion("Agregada despues");
        modo.agregar(nueva);

        assertEquals(List.of(primera, segunda, nueva), modo.listaReproduccion());
    }

    @Test
    @DisplayName("eliminar algo que no esta no altera la cola")
    void eliminarInexistente() {
        modo.cargar(biblioteca);

        modo.eliminar(new Cancion("No existe"));

        assertEquals(3, modo.listaReproduccion().size());
    }

    @Test
    @DisplayName("al cargar no hay nada sonando todavia")
    void cargaSinReproducir() {
        modo.cargar(biblioteca);
        assertNull(modo.actual());
    }

    @Test
    @DisplayName("el historial conserva lo reproducido aunque haya salido de la cola")
    void historialConservaLoQueYaSalio() {
        modo.cargar(biblioteca);
        modo.siguiente();
        modo.siguiente();

        assertEquals(List.of(primera, segunda), modo.historial());
        assertFalse(modo.listaReproduccion().contains(primera));
    }
}
