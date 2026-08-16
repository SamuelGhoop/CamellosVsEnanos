package com.eia.reproductor.estructuras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de la {@link ListaCircularDoble}. */
@DisplayName("Lista ligada circular doble")
class ListaCircularDobleTest {
    private ListaCircularDoble<String> lista;

    @BeforeEach
    void prepararLista() {
        lista = new ListaCircularDoble<>();
    }

    /** Carga la lista con los elementos indicados, en ese orden. */
    private void cargar(String... elementos) {
        for (String elemento : elementos) {
            lista.agregar(elemento);
        }
    }

    private List<String> aLista(ListaCircularDoble<String> origen) {
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
        @DisplayName("una lista recien creada esta vacia")
        void listaNuevaEstaVacia() {
            assertTrue(lista.estaVacia());
            assertEquals(0, lista.tamanio());
        }

        @Test
        @DisplayName("consultar los extremos de una lista vacia lanza excepcion")
        void extremosDeListaVaciaFallan() {
            assertThrows(NoSuchElementException.class, () -> lista.primero());
            assertThrows(NoSuchElementException.class, () -> lista.ultimo());
            assertThrows(NoSuchElementException.class, () -> lista.nuevoCursor());
        }

        @Test
        @DisplayName("no se admiten elementos nulos")
        void rechazaNulos() {
            assertThrows(NullPointerException.class, () -> lista.agregar(null));
        }
    }

    @Nested
    @DisplayName("Circularidad")
    class Circularidad {
        @Test
        @DisplayName("avanzando mas alla del ultimo se vuelve al primero")
        void avanzarDaLaVuelta() {
            cargar("A", "B", "C");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            assertEquals("A", cursor.actual());
            assertEquals("B", cursor.siguiente());
            assertEquals("C", cursor.siguiente());
            // Aqui esta el punto: despues de la ultima cancion viene la primera, no un final.
            assertEquals("A", cursor.siguiente());
            assertEquals("B", cursor.siguiente());
        }

        @Test
        @DisplayName("retrocediendo desde el primero se salta al ultimo")
        void retrocederDaLaVuelta() {
            cargar("A", "B", "C");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            assertEquals("A", cursor.actual());
            assertEquals("C", cursor.anterior());
            assertEquals("B", cursor.anterior());
            assertEquals("A", cursor.anterior());
            assertEquals("C", cursor.anterior());
        }

        @Test
        @DisplayName("se puede navegar indefinidamente en ambas direcciones")
        void navegacionIndefinida() {
            cargar("A", "B", "C", "D");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            // Diez vueltas completas hacia adelante deben dejar el cursor donde empezo.
            for (int i = 0; i < 4 * 10; i++) {
                cursor.siguiente();
            }
            assertEquals("A", cursor.actual());

            // Y lo mismo hacia atras.
            for (int i = 0; i < 4 * 10; i++) {
                cursor.anterior();
            }
            assertEquals("A", cursor.actual());
        }

        @Test
        @DisplayName("con un solo elemento el nodo se apunta a si mismo")
        void unSoloElementoSeCierraSobreSiMismo() {
            cargar("UNICA");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            assertEquals("UNICA", cursor.siguiente());
            assertEquals("UNICA", cursor.anterior());
            assertEquals("UNICA", cursor.actual());
        }

        @Test
        @DisplayName("avanzar y retroceder la misma cantidad devuelve al punto de partida")
        void avanzarYRetrocederEsSimetrico() {
            cargar("A", "B", "C", "D", "E");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            for (int i = 0; i < 7; i++) {
                cursor.siguiente();
            }
            for (int i = 0; i < 7; i++) {
                cursor.anterior();
            }
            assertEquals("A", cursor.actual());
        }
    }

    @Nested
    @DisplayName("Altas")
    class Altas {
        @Test
        @DisplayName("agregar deja los elementos en orden de llegada")
        void agregarMantieneOrden() {
            cargar("A", "B", "C");

            assertEquals(3, lista.tamanio());
            assertFalse(lista.estaVacia());
            assertEquals("A", lista.primero());
            assertEquals("C", lista.ultimo());
            assertIterableEquals(List.of("A", "B", "C"), aLista(lista));
        }

        @Test
        @DisplayName("agregarEnPosicion inserta al inicio, en medio y al final")
        void agregarEnPosicion() {
            cargar("A", "C");

            lista.agregarEnPosicion(1, "B");
            assertIterableEquals(List.of("A", "B", "C"), aLista(lista));

            lista.agregarEnPosicion(0, "INICIO");
            assertIterableEquals(List.of("INICIO", "A", "B", "C"), aLista(lista));
            assertEquals("INICIO", lista.primero());

            lista.agregarEnPosicion(4, "FINAL");
            assertIterableEquals(List.of("INICIO", "A", "B", "C", "FINAL"), aLista(lista));
            assertEquals("FINAL", lista.ultimo());
        }

        @Test
        @DisplayName("insertar al inicio conserva la circularidad")
        void insertarAlInicioConservaCircularidad() {
            cargar("A", "B");
            lista.agregarEnPosicion(0, "NUEVO");

            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();
            assertEquals("NUEVO", cursor.actual());
            assertEquals("B", cursor.anterior());
            assertEquals("A", cursor.anterior());
            assertEquals("NUEVO", cursor.anterior());
        }

        @Test
        @DisplayName("una posicion fuera de rango lanza excepcion")
        void posicionInvalida() {
            cargar("A");
            assertThrows(IndexOutOfBoundsException.class, () -> lista.agregarEnPosicion(-1, "X"));
            assertThrows(IndexOutOfBoundsException.class, () -> lista.agregarEnPosicion(5, "X"));
        }
    }

    @Nested
    @DisplayName("Bajas")
    class Bajas {
        @Test
        @DisplayName("eliminar el primero, uno del medio y el ultimo")
        void eliminarEnCadaPosicion() {
            cargar("A", "B", "C", "D");

            assertTrue(lista.eliminar("A"));
            assertIterableEquals(List.of("B", "C", "D"), aLista(lista));
            assertEquals("B", lista.primero());

            assertTrue(lista.eliminar("C"));
            assertIterableEquals(List.of("B", "D"), aLista(lista));

            assertTrue(lista.eliminar("D"));
            assertIterableEquals(List.of("B"), aLista(lista));
            assertEquals("B", lista.primero());
            assertEquals("B", lista.ultimo());
        }

        @Test
        @DisplayName("eliminar deja la lista circular intacta")
        void eliminarConservaCircularidad() {
            cargar("A", "B", "C");
            lista.eliminar("B");

            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();
            assertEquals("A", cursor.actual());
            assertEquals("C", cursor.siguiente());
            assertEquals("A", cursor.siguiente());
            assertEquals("C", cursor.anterior());
        }

        @Test
        @DisplayName("eliminar el unico elemento deja la lista vacia")
        void eliminarUnicoElemento() {
            cargar("UNICA");

            assertTrue(lista.eliminar("UNICA"));
            assertTrue(lista.estaVacia());
            assertEquals(0, lista.tamanio());
        }

        @Test
        @DisplayName("eliminar algo que no esta devuelve false y no altera la lista")
        void eliminarInexistente() {
            cargar("A", "B");

            assertFalse(lista.eliminar("Z"));
            assertEquals(2, lista.tamanio());
        }

        @Test
        @DisplayName("limpiar vacia la lista")
        void limpiar() {
            cargar("A", "B", "C");
            lista.limpiar();

            assertTrue(lista.estaVacia());
            assertEquals(0, lista.tamanio());
        }
    }

    @Nested
    @DisplayName("Consultas")
    class Consultas {
        @Test
        @DisplayName("buscar encuentra lo que esta y no lo que no esta")
        void buscar() {
            cargar("A", "B", "C");

            assertTrue(lista.buscar("B"));
            assertFalse(lista.buscar("Z"));
            assertFalse(lista.buscar(null));
        }

        @Test
        @DisplayName("obtener devuelve el elemento de cada posicion")
        void obtener() {
            cargar("A", "B", "C", "D", "E");

            // Se piden posiciones de las dos mitades para ejercitar el recorrido hacia adelante
            // y el recorrido hacia atras.
            assertEquals("A", lista.obtener(0));
            assertEquals("B", lista.obtener(1));
            assertEquals("C", lista.obtener(2));
            assertEquals("D", lista.obtener(3));
            assertEquals("E", lista.obtener(4));
        }

        @Test
        @DisplayName("obtener con indice invalido lanza excepcion")
        void obtenerFueraDeRango() {
            cargar("A");
            assertThrows(IndexOutOfBoundsException.class, () -> lista.obtener(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> lista.obtener(1));
        }
    }

    @Nested
    @DisplayName("Iterador")
    class Iterador {
        @Test
        @DisplayName("recorre la lista una sola vuelta y se detiene")
        void iteradorNoSeCuelga() {
            cargar("A", "B", "C");

            int visitados = 0;
            for (String ignorado : lista) {
                visitados++;
                // Red de seguridad: si el iterador no estuviera acotado por tamanio, esta prueba
                // daria vueltas para siempre en vez de fallar.
                if (visitados > 100) {
                    break;
                }
            }
            assertEquals(3, visitados);
        }

        @Test
        @DisplayName("pedir un elemento de mas lanza NoSuchElementException")
        void iteradorAgotado() {
            cargar("A");
            Iterator<String> iterador = lista.iterator();

            assertEquals("A", iterador.next());
            assertFalse(iterador.hasNext());
            assertThrows(NoSuchElementException.class, iterador::next);
        }

        @Test
        @DisplayName("iterar una lista vacia no entrega nada")
        void iteradorDeListaVacia() {
            assertFalse(lista.iterator().hasNext());
        }
    }

    @Nested
    @DisplayName("Mezcla aleatoria")
    class Mezcla {
        @Test
        @DisplayName("mezclar conserva todos los elementos y el tamanio")
        void mezclarNoPierdeElementos() {
            cargar("A", "B", "C", "D", "E", "F", "G", "H");
            lista.mezclar(new Random(42));

            assertEquals(8, lista.tamanio());
            List<String> mezclada = aLista(lista);
            assertEquals(8, mezclada.size());
            assertTrue(mezclada.containsAll(List.of("A", "B", "C", "D", "E", "F", "G", "H")));
        }

        @Test
        @DisplayName("mezclar deja la lista circular consistente en ambos sentidos")
        void mezclarConservaCircularidad() {
            cargar("A", "B", "C", "D", "E");
            lista.mezclar(new Random(7));

            List<String> ordenTrasMezcla = aLista(lista);
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            // Una vuelta completa hacia adelante reproduce el mismo orden que el iterador.
            for (String esperado : ordenTrasMezcla) {
                assertEquals(esperado, cursor.actual());
                cursor.siguiente();
            }
            assertEquals(ordenTrasMezcla.get(0), cursor.actual());

            // Y hacia atras se recorre el orden inverso.
            for (int i = ordenTrasMezcla.size() - 1; i >= 0; i--) {
                assertEquals(ordenTrasMezcla.get(i), cursor.anterior());
            }
        }

        @Test
        @DisplayName("con la misma semilla la mezcla es reproducible")
        void mezclaReproducible() {
            cargar("A", "B", "C", "D", "E", "F");
            lista.mezclar(new Random(123));
            List<String> primera = aLista(lista);

            ListaCircularDoble<String> otra = new ListaCircularDoble<>();
            for (String elemento : List.of("A", "B", "C", "D", "E", "F")) {
                otra.agregar(elemento);
            }
            otra.mezclar(new Random(123));

            assertIterableEquals(primera, aLista(otra));
        }

        @Test
        @DisplayName("mezclar una lista de 0 o 1 elementos no rompe nada")
        void mezclarListasMinimas() {
            lista.mezclar(new Random(1));
            assertTrue(lista.estaVacia());

            cargar("UNICA");
            lista.mezclar(new Random(1));
            assertEquals(1, lista.tamanio());
            assertEquals("UNICA", lista.primero());
        }

        @Test
        @DisplayName("mezclar realmente cambia el orden de una lista grande")
        void mezclarCambiaElOrden() {
            for (int i = 0; i < 50; i++) {
                lista.agregar("cancion-" + i);
            }
            List<String> ordenOriginal = aLista(lista);
            lista.mezclar(new Random(2026));

            // Con 50 elementos, la probabilidad de que Fisher-Yates devuelva el orden identico
            // es 1/50!, asi que si coinciden es que mezclar() no esta haciendo nada.
            assertFalse(ordenOriginal.equals(aLista(lista)));
        }
    }

    @Nested
    @DisplayName("Cursor")
    class CursorLista {
        @Test
        @DisplayName("posicionarEn mueve el cursor a un elemento concreto")
        void posicionarEn() {
            cargar("A", "B", "C");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            assertTrue(cursor.posicionarEn("C"));
            assertEquals("C", cursor.actual());
            assertEquals("A", cursor.siguiente());
        }

        @Test
        @DisplayName("posicionarEn sobre un elemento inexistente no mueve el cursor")
        void posicionarEnInexistente() {
            cargar("A", "B");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            assertFalse(cursor.posicionarEn("Z"));
            assertEquals("A", cursor.actual());
        }

        @Test
        @DisplayName("reiniciar devuelve el cursor al primer elemento")
        void reiniciar() {
            cargar("A", "B", "C");
            ListaCircularDoble.Cursor<String> cursor = lista.nuevoCursor();

            cursor.siguiente();
            cursor.siguiente();
            cursor.reiniciar();
            assertEquals("A", cursor.actual());
        }

        @Test
        @DisplayName("dos cursores sobre la misma lista son independientes")
        void cursoresIndependientes() {
            cargar("A", "B", "C");
            ListaCircularDoble.Cursor<String> uno = lista.nuevoCursor();
            ListaCircularDoble.Cursor<String> otro = lista.nuevoCursor();

            uno.siguiente();
            assertEquals("B", uno.actual());
            assertEquals("A", otro.actual());
        }
    }
}
