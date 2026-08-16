package com.eia.reproductor.estructuras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la {@link ColaSimple}. */
@DisplayName("Cola simple FIFO")
class ColaSimpleTest {
    private ColaSimple<String> cola;

    @BeforeEach
    void prepararCola() {
        cola = new ColaSimple<>();
    }

    private void cargar(String... elementos) {
        for (String elemento : elementos) {
            cola.encolar(elemento);
        }
    }

    private List<String> aLista(ColaSimple<String> origen) {
        List<String> copia = new ArrayList<>();
        for (String elemento : origen) {
            copia.add(elemento);
        }
        return copia;
    }

    @Nested
    @DisplayName("Estado inicial")
    class EstadoInicial {
        @Test
        @DisplayName("una cola recien creada esta vacia")
        void colaNuevaEstaVacia() {
            assertTrue(cola.estaVacia());
            assertEquals(0, cola.tamanio());
        }

        @Test
        @DisplayName("desencolar o mirar una cola vacia lanza excepcion")
        void operacionesSobreColaVacia() {
            assertThrows(NoSuchElementException.class, () -> cola.desencolar());
            assertThrows(NoSuchElementException.class, () -> cola.verFrente());
            assertThrows(NoSuchElementException.class, () -> cola.verFin());
        }

        @Test
        @DisplayName("no se admiten elementos nulos")
        void rechazaNulos() {
            assertThrows(NullPointerException.class, () -> cola.encolar(null));
        }
    }

    @Nested
    @DisplayName("Principio FIFO")
    class PrincipioFifo {
        @Test
        @DisplayName("se desencola exactamente en el orden en que se encolo")
        void ordenEstrictamenteFifo() {
            cargar("PRIMERA", "SEGUNDA", "TERCERA");

            assertEquals("PRIMERA", cola.desencolar());
            assertEquals("SEGUNDA", cola.desencolar());
            assertEquals("TERCERA", cola.desencolar());
            assertTrue(cola.estaVacia());
        }

        @Test
        @DisplayName("el frente siempre es el elemento mas antiguo y el fin el mas reciente")
        void frenteYFin() {
            cargar("A", "B", "C");

            assertEquals("A", cola.verFrente());
            assertEquals("C", cola.verFin());

            cola.desencolar();
            assertEquals("B", cola.verFrente());
            assertEquals("C", cola.verFin());
        }

        @Test
        @DisplayName("intercalar altas y bajas mantiene el orden de llegada")
        void altasYBajasIntercaladas() {
            cargar("A", "B");
            assertEquals("A", cola.desencolar());

            cargar("C");
            assertEquals("B", cola.desencolar());
            assertEquals("C", cola.desencolar());
            assertTrue(cola.estaVacia());
        }
    }

    @Nested
    @DisplayName("Desencolar retira de verdad")
    class DesencolarRetira {
        @Test
        @DisplayName("desencolar reduce el tamanio")
        void desencolarReduceTamanio() {
            cargar("A", "B", "C");
            assertEquals(3, cola.tamanio());

            cola.desencolar();
            assertEquals(2, cola.tamanio());

            cola.desencolar();
            assertEquals(1, cola.tamanio());
        }

        @Test
        @DisplayName("un elemento desencolado ya no esta en la cola")
        void elementoDesencoladoDesaparece() {
            cargar("A", "B");
            assertTrue(cola.buscar("A"));

            cola.desencolar();

            // Este es el requisito del enunciado: una cancion reproducida sale de la cola.
            assertFalse(cola.buscar("A"));
            assertTrue(cola.buscar("B"));
            assertIterableEquals(List.of("B"), aLista(cola));
        }

        @Test
        @DisplayName("verFrente no retira el elemento")
        void verFrenteNoRetira() {
            cargar("A", "B");

            assertEquals("A", cola.verFrente());
            assertEquals("A", cola.verFrente());
            assertEquals(2, cola.tamanio());
        }

        @Test
        @DisplayName("vaciar la cola y volver a encolar funciona correctamente")
        void reutilizarColaTrasVaciarla() {
            // Este caso caza el error clasico: al vaciar la cola, si no se pone 'fin' en null,
            // el proximo encolar se engancha detras de un nodo que ya salio y el elemento
            cargar("A");
            cola.desencolar();
            assertTrue(cola.estaVacia());

            cola.encolar("B");
            assertEquals(1, cola.tamanio());
            assertEquals("B", cola.verFrente());
            assertEquals("B", cola.verFin());
            assertEquals("B", cola.desencolar());
            assertTrue(cola.estaVacia());
        }

        @Test
        @DisplayName("vaciar por completo y recargar varias veces mantiene la coherencia")
        void ciclosDeVaciadoYRecarga() {
            for (int vuelta = 0; vuelta < 3; vuelta++) {
                cargar("X" + vuelta, "Y" + vuelta);
                assertEquals("X" + vuelta, cola.desencolar());
                assertEquals("Y" + vuelta, cola.desencolar());
                assertTrue(cola.estaVacia());
            }
        }
    }

    @Nested
    @DisplayName("Consultas y limpieza")
    class ConsultasYLimpieza {
        @Test
        @DisplayName("buscar encuentra lo que esta y no lo que no esta")
        void buscar() {
            cargar("A", "B", "C");

            assertTrue(cola.buscar("B"));
            assertFalse(cola.buscar("Z"));
            assertFalse(cola.buscar(null));
        }

        @Test
        @DisplayName("el iterador recorre la cola sin consumirla")
        void iteradorNoConsume() {
            cargar("A", "B", "C");

            assertIterableEquals(List.of("A", "B", "C"), aLista(cola));
            assertEquals(3, cola.tamanio());
            assertEquals("A", cola.verFrente());
        }

        @Test
        @DisplayName("limpiar vacia la cola y la deja reutilizable")
        void limpiar() {
            cargar("A", "B", "C");
            cola.limpiar();

            assertTrue(cola.estaVacia());
            assertEquals(0, cola.tamanio());

            cola.encolar("NUEVA");
            assertEquals("NUEVA", cola.verFrente());
            assertEquals("NUEVA", cola.verFin());
        }
    }

    @Nested
    @DisplayName("Genericos")
    class Genericos {
        @Test
        @DisplayName("la misma cola sirve para cualquier tipo")
        void colaDeEnteros() {
            ColaSimple<Integer> numeros = new ColaSimple<>();
            numeros.encolar(10);
            numeros.encolar(20);

            assertEquals(10, numeros.desencolar().intValue());
            assertEquals(20, numeros.desencolar().intValue());
        }
    }
}
