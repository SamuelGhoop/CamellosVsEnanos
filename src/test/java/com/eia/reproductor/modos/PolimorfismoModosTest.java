package com.eia.reproductor.modos;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas que ejercitan los tres modos unicamente a traves de {@link ModoReproduccion}. */
@DisplayName("Polimorfismo de los modos de reproduccion")
class PolimorfismoModosTest {
    private List<Cancion> biblioteca;

    @BeforeEach
    void preparar() {
        biblioteca = new ArrayList<>();
        for (String titulo : List.of("Creep", "Africa", "Dream On", "Bohemian Rhapsody")) {
            biblioteca.add(new Cancion(titulo));
        }
    }

    /** Los tres modos, vistos solo como el contrato que cumplen. */
    private List<ModoReproduccion> todosLosModos() {
        return List.of(
                new ModoAleatorio(new Random(99)),
                new ModoOrdenLlegada(),
                new ModoAlfabetico());
    }

    @Test
    @DisplayName("los tres modos se cargan y reproducen con el mismo codigo")
    void mismaLlamadaParaLosTres() {
        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(biblioteca);

            assertNull(modo.actual(), modo.nombre() + ": no deberia sonar nada tras cargar");
            assertTrue(modo.hayMas(), modo.nombre() + ": deberia tener canciones");

            Cancion primera = modo.siguiente();
            assertNotNull(primera, modo.nombre() + ": siguiente() no puede devolver null");
            assertEquals(primera, modo.actual(), modo.nombre() + ": actual() debe reflejar lo ultimo");
        }
    }

    @Test
    @DisplayName("cada modo declara un nombre y una estructura distintos")
    void identidadesDistintas() {
        Set<String> nombres = new HashSet<>();
        Set<String> estructuras = new HashSet<>();

        for (ModoReproduccion modo : todosLosModos()) {
            nombres.add(modo.nombre());
            estructuras.add(modo.estructuraUsada());
        }

        assertEquals(3, nombres.size());
        assertEquals(3, estructuras.size(), "cada modo usa obligatoriamente una estructura distinta");
    }

    @Test
    @DisplayName("el retroceso se decide con permiteAnterior(), sin preguntar por el tipo")
    void retrocesoGobernadoPorLaBandera() {
        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(biblioteca);
            modo.siguiente();

            // Esto es exactamente lo que hara el controlador para habilitar el boton "Anterior":
            // consultar la bandera, nunca hacer 'instanceof'.
            if (modo.permiteAnterior()) {
                assertNotNull(modo.anterior(), modo.nombre() + ": deberia poder retroceder");
            } else {
                assertThrows(UnsupportedOperationException.class, modo::anterior,
                        modo.nombre() + ": deberia rechazar el retroceso");
            }
        }
    }

    @Test
    @DisplayName("cambiar de modo es reasignar la referencia y volver a cargar")
    void cambioDeModo() {
        // El controlador solo conoce este tipo.
        ModoReproduccion activo = new ModoOrdenLlegada();
        activo.cargar(biblioteca);
        assertEquals("Creep", activo.siguiente().getTitulo(), "la cola respeta el orden de llegada");

        activo = new ModoAlfabetico();
        activo.cargar(biblioteca);
        assertEquals("Africa", activo.siguiente().getTitulo(), "el arbol respeta el orden alfabetico");

        activo = new ModoAleatorio(new Random(5));
        activo.cargar(biblioteca);
        assertNotNull(activo.siguiente());

        // En todo el metodo no hubo un solo 'if' sobre el tipo concreto del modo.
    }

    @Test
    @DisplayName("los tres reproducen exactamente las mismas canciones, en distinto orden")
    void mismoContenidoDistintoOrden() {
        Set<Cancion> esperadas = new HashSet<>(biblioteca);

        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(biblioteca);

            Set<Cancion> obtenidas = new HashSet<>();
            for (int i = 0; i < biblioteca.size(); i++) {
                obtenidas.add(modo.siguiente());
            }

            assertEquals(esperadas, obtenidas,
                    modo.nombre() + ": debe recorrer la biblioteca completa");
        }
    }

    @Test
    @DisplayName("los tres sincronizan altas y bajas con la biblioteca")
    void sincronizacionUniforme() {
        Cancion nueva = new Cancion("Zombie");

        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(biblioteca);
            int inicial = modo.listaReproduccion().size();

            modo.agregar(nueva);
            assertEquals(inicial + 1, modo.listaReproduccion().size(),
                    modo.nombre() + ": agregar deberia sumar una cancion");

            modo.eliminar(nueva);
            assertEquals(inicial, modo.listaReproduccion().size(),
                    modo.nombre() + ": eliminar deberia restarla");
            assertFalse(modo.listaReproduccion().contains(nueva), modo.nombre());
        }
    }

    @Test
    @DisplayName("los tres manejan igual una biblioteca vacia")
    void bibliotecaVacia() {
        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(List.of());

            assertFalse(modo.hayMas(), modo.nombre());
            assertNull(modo.actual(), modo.nombre());
            assertTrue(modo.listaReproduccion().isEmpty(), modo.nombre());
        }
    }

    @Test
    @DisplayName("reiniciar deja los tres modos sin cancion en curso")
    void reinicioUniforme() {
        for (ModoReproduccion modo : todosLosModos()) {
            modo.cargar(biblioteca);
            modo.siguiente();
            assertNotNull(modo.actual(), modo.nombre());

            modo.reiniciar();

            assertNull(modo.actual(), modo.nombre() + ": reiniciar debe dejar sin cancion actual");
        }
    }

    @Test
    @DisplayName("los tres extienden ModoBase, que es donde vive la logica comun")
    void herenciaCompartida() {
        for (ModoReproduccion modo : todosLosModos()) {
            assertTrue(modo instanceof ModoBase,
                    modo.nombre() + " deberia heredar la logica comun de ModoBase");
        }
    }
}
