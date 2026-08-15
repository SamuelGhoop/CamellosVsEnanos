package com.eia.reproductor.estructuras;

import com.eia.reproductor.modelo.Cancion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del {@link ArbolBinarioBusqueda}.
 *
 * <p>El foco esta en la navegacion por sucesor y predecesor inorden sobre el arbol vivo, y en que
 * la eliminacion en sus tres casos deje el arbol coherente.</p>
 */
@DisplayName("Arbol binario de busqueda")
class ArbolBinarioBusquedaTest {

    private ArbolBinarioBusqueda<Integer> arbol;

    @BeforeEach
    void prepararArbol() {
        arbol = new ArbolBinarioBusqueda<Integer>(Comparator.naturalOrder());
    }

    /**
     * Carga un arbol equilibrado a mano:
     * <pre>
     *            50
     *         /      \
     *       30        70
     *      /  \      /  \
     *    20    40  60    80
     * </pre>
     */
    private void cargarArbolEquilibrado() {
        for (int valor : new int[] {50, 30, 70, 20, 40, 60, 80}) {
            arbol.insertar(valor);
        }
    }

    @Nested
    @DisplayName("Estado inicial")
    class EstadoInicial {

        @Test
        @DisplayName("un arbol recien creado esta vacio")
        void arbolNuevoEstaVacio() {
            assertTrue(arbol.estaVacio());
            assertEquals(0, arbol.tamanio());
            assertEquals(ArbolBinarioBusqueda.ALTURA_ARBOL_VACIO, arbol.altura());
            assertTrue(arbol.recorridoInorden().isEmpty());
        }

        @Test
        @DisplayName("pedir extremos de un arbol vacio lanza excepcion")
        void extremosDeArbolVacio() {
            assertThrows(NoSuchElementException.class, () -> arbol.minimo());
            assertThrows(NoSuchElementException.class, () -> arbol.maximo());
        }

        @Test
        @DisplayName("el comparador es obligatorio")
        void comparadorObligatorio() {
            assertThrows(NullPointerException.class, () -> new ArbolBinarioBusqueda<Integer>(null));
        }

        @Test
        @DisplayName("no se admiten elementos nulos")
        void rechazaNulos() {
            assertThrows(NullPointerException.class, () -> arbol.insertar(null));
        }
    }

    @Nested
    @DisplayName("Insercion")
    class Insercion {

        @Test
        @DisplayName("insertar deja el recorrido inorden ordenado")
        void inordenOrdenado() {
            cargarArbolEquilibrado();

            assertEquals(7, arbol.tamanio());
            assertIterableEquals(List.of(20, 30, 40, 50, 60, 70, 80), arbol.recorridoInorden());
        }

        @Test
        @DisplayName("los duplicados se rechazan y no alteran el tamanio")
        void duplicadosRechazados() {
            assertTrue(arbol.insertar(50));
            assertFalse(arbol.insertar(50));

            assertEquals(1, arbol.tamanio());
        }

        @Test
        @DisplayName("la altura refleja la forma del arbol")
        void altura() {
            assertEquals(-1, arbol.altura());

            arbol.insertar(50);
            assertEquals(0, arbol.altura(), "un solo nodo tiene altura 0");

            cargarArbolEquilibrado();
            assertEquals(2, arbol.altura(), "tres niveles equivalen a altura 2");
        }
    }

    @Nested
    @DisplayName("Busqueda y extremos")
    class BusquedaYExtremos {

        @Test
        @DisplayName("buscar encuentra lo que esta y no lo que no esta")
        void buscar() {
            cargarArbolEquilibrado();

            assertTrue(arbol.buscar(20));
            assertTrue(arbol.buscar(50));
            assertTrue(arbol.buscar(80));
            assertFalse(arbol.buscar(99));
            assertFalse(arbol.buscar(null));
        }

        @Test
        @DisplayName("minimo y maximo son los extremos del recorrido inorden")
        void minimoYMaximo() {
            cargarArbolEquilibrado();

            assertEquals(20, arbol.minimo());
            assertEquals(80, arbol.maximo());
        }
    }

    @Nested
    @DisplayName("Navegacion inorden en arbol equilibrado")
    class NavegacionEquilibrado {

        @Test
        @DisplayName("sucesor cuando el nodo tiene subarbol derecho")
        void sucesorConSubarbolDerecho() {
            cargarArbolEquilibrado();

            // 50 tiene subarbol derecho: el sucesor es el minimo de ese subarbol.
            assertEquals(60, arbol.sucesorInorden(50));
            assertEquals(40, arbol.sucesorInorden(30));
        }

        @Test
        @DisplayName("sucesor cuando hay que subir por los punteros al padre")
        void sucesorSubiendoPorElPadre() {
            cargarArbolEquilibrado();

            // 40 es hoja y es hijo derecho de 30: hay que subir hasta 50.
            assertEquals(50, arbol.sucesorInorden(40));
            // 20 es hoja e hijo izquierdo de 30: su sucesor es su padre directo.
            assertEquals(30, arbol.sucesorInorden(20));
            assertEquals(70, arbol.sucesorInorden(60));
        }

        @Test
        @DisplayName("predecesor cuando el nodo tiene subarbol izquierdo")
        void predecesorConSubarbolIzquierdo() {
            cargarArbolEquilibrado();

            assertEquals(40, arbol.predecesorInorden(50));
            assertEquals(60, arbol.predecesorInorden(70));
        }

        @Test
        @DisplayName("predecesor cuando hay que subir por los punteros al padre")
        void predecesorSubiendoPorElPadre() {
            cargarArbolEquilibrado();

            assertEquals(30, arbol.predecesorInorden(40));
            assertEquals(50, arbol.predecesorInorden(60));
            assertEquals(70, arbol.predecesorInorden(80));
        }

        @Test
        @DisplayName("el maximo no tiene sucesor y el minimo no tiene predecesor")
        void extremosSinVecino() {
            cargarArbolEquilibrado();

            assertNull(arbol.sucesorInorden(80));
            assertNull(arbol.predecesorInorden(20));
        }

        @Test
        @DisplayName("pedir el vecino de algo que no esta devuelve null")
        void vecinoDeInexistente() {
            cargarArbolEquilibrado();

            assertNull(arbol.sucesorInorden(99));
            assertNull(arbol.predecesorInorden(99));
        }

        @Test
        @DisplayName("encadenar sucesores recorre el arbol completo en orden")
        void cadenaDeSucesoresIgualaAlInorden() {
            cargarArbolEquilibrado();

            // Se navega como lo hace el modo alfabetico: sin volcar el arbol, solo saltando
            // de sucesor en sucesor. El resultado debe coincidir con el recorrido inorden.
            List<Integer> recorridoNavegando = new ArrayList<>();
            Integer actual = arbol.minimo();
            while (actual != null) {
                recorridoNavegando.add(actual);
                actual = arbol.sucesorInorden(actual);
            }

            assertIterableEquals(arbol.recorridoInorden(), recorridoNavegando);
        }

        @Test
        @DisplayName("encadenar predecesores recorre el arbol al reves")
        void cadenaDePredecesores() {
            cargarArbolEquilibrado();

            List<Integer> recorridoInverso = new ArrayList<>();
            Integer actual = arbol.maximo();
            while (actual != null) {
                recorridoInverso.add(actual);
                actual = arbol.predecesorInorden(actual);
            }

            List<Integer> esperado = new ArrayList<>(arbol.recorridoInorden());
            java.util.Collections.reverse(esperado);
            assertIterableEquals(esperado, recorridoInverso);
        }
    }

    @Nested
    @DisplayName("Navegacion inorden en arbol degenerado")
    class NavegacionDegenerado {

        @Test
        @DisplayName("insertar en orden ascendente degenera el arbol en una lista")
        void insercionOrdenadaDegenera() {
            for (int i = 1; i <= 6; i++) {
                arbol.insertar(i);
            }

            // Cada nodo se cuelga a la derecha del anterior: la altura es n-1 en vez de log2(n).
            // Es el peor caso del ABB y el motivo por el que existen los arboles autobalanceados.
            assertEquals(5, arbol.altura());
            assertEquals(6, arbol.tamanio());
            assertIterableEquals(List.of(1, 2, 3, 4, 5, 6), arbol.recorridoInorden());
        }

        @Test
        @DisplayName("la navegacion sigue siendo correcta aunque el arbol degenere")
        void navegacionEnArbolDegenerado() {
            for (int i = 1; i <= 6; i++) {
                arbol.insertar(i);
            }

            assertEquals(2, arbol.sucesorInorden(1));
            assertEquals(4, arbol.sucesorInorden(3));
            assertNull(arbol.sucesorInorden(6));

            assertEquals(5, arbol.predecesorInorden(6));
            assertEquals(1, arbol.predecesorInorden(2));
            assertNull(arbol.predecesorInorden(1));
        }

        @Test
        @DisplayName("insertar en orden descendente tambien degenera, hacia el otro lado")
        void insercionDescendenteDegenera() {
            for (int i = 6; i >= 1; i--) {
                arbol.insertar(i);
            }

            assertEquals(5, arbol.altura());
            assertIterableEquals(List.of(1, 2, 3, 4, 5, 6), arbol.recorridoInorden());
            assertEquals(3, arbol.sucesorInorden(2));
            assertEquals(2, arbol.predecesorInorden(3));
        }
    }

    @Nested
    @DisplayName("Eliminacion")
    class Eliminacion {

        @Test
        @DisplayName("caso 1: eliminar una hoja")
        void eliminarHoja() {
            cargarArbolEquilibrado();

            assertTrue(arbol.eliminar(20));
            assertEquals(6, arbol.tamanio());
            assertFalse(arbol.buscar(20));
            assertIterableEquals(List.of(30, 40, 50, 60, 70, 80), arbol.recorridoInorden());
            // El padre de la hoja eliminada debe quedar coherente.
            assertEquals(40, arbol.sucesorInorden(30));
            assertEquals(30, arbol.minimo());
        }

        @Test
        @DisplayName("caso 2: eliminar un nodo con un solo hijo")
        void eliminarNodoConUnHijo() {
            cargarArbolEquilibrado();
            arbol.eliminar(20);      // 30 queda solo con el hijo derecho 40

            assertTrue(arbol.eliminar(30));
            assertEquals(5, arbol.tamanio());
            assertIterableEquals(List.of(40, 50, 60, 70, 80), arbol.recorridoInorden());
            // 40 subio a ocupar el lugar de 30: su sucesor sigue siendo la raiz.
            assertEquals(50, arbol.sucesorInorden(40));
            assertEquals(40, arbol.predecesorInorden(50));
        }

        @Test
        @DisplayName("caso 3: eliminar la raiz, que tiene dos hijos")
        void eliminarRaizConDosHijos() {
            cargarArbolEquilibrado();

            assertTrue(arbol.eliminar(50));

            assertEquals(6, arbol.tamanio());
            assertFalse(arbol.buscar(50));
            // El sucesor inorden de 50 era 60, que pasa a ser la nueva raiz.
            assertIterableEquals(List.of(20, 30, 40, 60, 70, 80), arbol.recorridoInorden());
            // Los enlaces al padre tienen que haber quedado bien o la navegacion se rompe.
            assertEquals(60, arbol.sucesorInorden(40));
            assertEquals(40, arbol.predecesorInorden(60));
            assertEquals(70, arbol.sucesorInorden(60));
            assertEquals(20, arbol.minimo());
            assertEquals(80, arbol.maximo());
        }

        @Test
        @DisplayName("caso 3 variante: el sucesor es hijo directo del nodo eliminado")
        void eliminarNodoCuyoSucesorEsSuHijo() {
            for (int valor : new int[] {50, 30, 70, 80}) {
                arbol.insertar(valor);
            }

            // 70 tiene dos... en realidad solo el derecho 80; se elimina 50, cuyo subarbol
            // derecho arranca en 70 y cuyo minimo es el propio 70 (hijo directo).
            assertTrue(arbol.eliminar(50));

            assertIterableEquals(List.of(30, 70, 80), arbol.recorridoInorden());
            assertEquals(70, arbol.sucesorInorden(30));
            assertEquals(30, arbol.predecesorInorden(70));
            assertEquals(80, arbol.sucesorInorden(70));
        }

        @Test
        @DisplayName("eliminar el unico nodo deja el arbol vacio")
        void eliminarUnicoNodo() {
            arbol.insertar(50);

            assertTrue(arbol.eliminar(50));
            assertTrue(arbol.estaVacio());
            assertEquals(ArbolBinarioBusqueda.ALTURA_ARBOL_VACIO, arbol.altura());
        }

        @Test
        @DisplayName("eliminar algo que no esta devuelve false")
        void eliminarInexistente() {
            cargarArbolEquilibrado();

            assertFalse(arbol.eliminar(99));
            assertEquals(7, arbol.tamanio());
        }

        @Test
        @DisplayName("vaciar el arbol nodo por nodo mantiene el orden en todo momento")
        void vaciadoProgresivo() {
            for (int valor : new int[] {50, 30, 70, 20, 40, 60, 80, 10, 25, 35, 45}) {
                arbol.insertar(valor);
            }

            List<Integer> pendientes = new ArrayList<>(arbol.recorridoInorden());
            // Se elimina en un orden arbitrario y tras cada baja se verifica que lo que queda
            // sigue estando ordenado y completo.
            for (int valor : new int[] {50, 10, 70, 35, 20, 80, 25, 60, 45, 30, 40}) {
                assertTrue(arbol.eliminar(valor), "deberia poder eliminar " + valor);
                pendientes.remove(Integer.valueOf(valor));

                assertEquals(pendientes.size(), arbol.tamanio());
                assertIterableEquals(pendientes, arbol.recorridoInorden(),
                        "el inorden se desordeno tras eliminar " + valor);
            }
            assertTrue(arbol.estaVacio());
        }

        @Test
        @DisplayName("tras eliminar, insertar de nuevo funciona")
        void reinsertarTrasEliminar() {
            cargarArbolEquilibrado();
            arbol.eliminar(50);

            assertTrue(arbol.insertar(50));
            assertIterableEquals(List.of(20, 30, 40, 50, 60, 70, 80), arbol.recorridoInorden());
        }

        @Test
        @DisplayName("limpiar vacia el arbol")
        void limpiar() {
            cargarArbolEquilibrado();
            arbol.limpiar();

            assertTrue(arbol.estaVacio());
            assertEquals(0, arbol.tamanio());
        }
    }

    @Nested
    @DisplayName("Uso real con canciones")
    class ConCanciones {

        private Cancion crear(String titulo, String artista) {
            Cancion cancion = new Cancion(titulo);
            cancion.setArtista(artista);
            return cancion;
        }

        @Test
        @DisplayName("las canciones quedan en orden alfabetico ignorando tildes y mayusculas")
        void ordenAlfabeticoEspanol() {
            ArbolBinarioBusqueda<Cancion> biblioteca =
                    new ArbolBinarioBusqueda<>(Cancion.POR_TITULO);

            biblioteca.insertar(crear("Zombie", "The Cranberries"));
            biblioteca.insertar(crear("Ángel", "Robbie Williams"));
            biblioteca.insertar(crear("bohemian rhapsody", "Queen"));
            biblioteca.insertar(crear("Angie", "The Rolling Stones"));

            List<String> titulos = new ArrayList<>();
            for (Cancion cancion : biblioteca.recorridoInorden()) {
                titulos.add(cancion.getTitulo());
            }

            // "Ángel" va junto a las palabras que empiezan por A, no al final del alfabeto,
            // y una minuscula inicial no manda la cancion a otra parte.
            assertIterableEquals(
                    List.of("Ángel", "Angie", "bohemian rhapsody", "Zombie"),
                    titulos);
        }

        @Test
        @DisplayName("dos canciones con el mismo titulo no se pierden")
        void cancionesHomonimasSobreviven() {
            ArbolBinarioBusqueda<Cancion> biblioteca =
                    new ArbolBinarioBusqueda<>(Cancion.POR_TITULO);

            Cancion original = crear("Hurt", "Nine Inch Nails");
            Cancion version = crear("Hurt", "Johnny Cash");

            assertTrue(biblioteca.insertar(original));
            assertTrue(biblioteca.insertar(version));

            // Si compareTo devolviera 0 ante titulos iguales, el arbol habria descartado una.
            // El desempate por artista (y por id) es lo que las salva a las dos.
            assertEquals(2, biblioteca.tamanio());
            assertTrue(biblioteca.buscar(original));
            assertTrue(biblioteca.buscar(version));
        }

        @Test
        @DisplayName("se puede avanzar y retroceder por la biblioteca en orden alfabetico")
        void navegacionAlfabetica() {
            ArbolBinarioBusqueda<Cancion> biblioteca =
                    new ArbolBinarioBusqueda<>(Cancion.POR_TITULO);

            Cancion creep = crear("Creep", "Radiohead");
            Cancion bohemian = crear("Bohemian Rhapsody", "Queen");
            Cancion enterSandman = crear("Enter Sandman", "Metallica");
            biblioteca.insertar(creep);
            biblioteca.insertar(bohemian);
            biblioteca.insertar(enterSandman);

            Cancion primera = biblioteca.minimo();
            assertEquals("Bohemian Rhapsody", primera.getTitulo());

            Cancion segunda = biblioteca.sucesorInorden(primera);
            assertNotNull(segunda);
            assertEquals("Creep", segunda.getTitulo());

            Cancion tercera = biblioteca.sucesorInorden(segunda);
            assertNotNull(tercera);
            assertEquals("Enter Sandman", tercera.getTitulo());

            assertNull(biblioteca.sucesorInorden(tercera), "despues de la ultima no hay sucesor");
            assertEquals("Creep", biblioteca.predecesorInorden(tercera).getTitulo());
        }
    }

    @Nested
    @DisplayName("Copia de la forma")
    class Forma {

        /** Copia minima para inspeccionar la silueta en los tests. */
        private record Rama(int valor, Rama izquierdo, Rama derecho) { }

        private Rama formaDelArbol() {
            return arbol.forma(Rama::new);
        }

        @Test
        @DisplayName("un arbol vacio no tiene forma")
        void arbolVacioSinForma() {
            assertNull(formaDelArbol());
        }

        @Test
        @DisplayName("reproduce la silueta del arbol equilibrado")
        void siluetaDelEquilibrado() {
            cargarArbolEquilibrado();

            Rama raiz = formaDelArbol();

            assertEquals(50, raiz.valor());
            assertEquals(30, raiz.izquierdo().valor());
            assertEquals(70, raiz.derecho().valor());
            assertEquals(20, raiz.izquierdo().izquierdo().valor());
            assertEquals(40, raiz.izquierdo().derecho().valor());
            assertEquals(60, raiz.derecho().izquierdo().valor());
            assertEquals(80, raiz.derecho().derecho().valor());
            assertNull(raiz.izquierdo().izquierdo().izquierdo(), "20 es hoja");
        }

        @Test
        @DisplayName("un arbol degenerado se ve como una sola rama")
        void siluetaDelDegenerado() {
            // Insertar ya ordenado es el peor caso: el arbol se convierte en una lista y todo pasa
            // a O(n). La forma tiene que ensenarlo, que es justo para lo que existe.
            for (int valor : new int[] {10, 20, 30, 40}) {
                arbol.insertar(valor);
            }

            Rama raiz = formaDelArbol();

            assertNull(raiz.izquierdo(), "nada a la izquierda: todos son mayores");
            assertEquals(20, raiz.derecho().valor());
            assertEquals(30, raiz.derecho().derecho().valor());
            assertEquals(40, raiz.derecho().derecho().derecho().valor());
            assertNull(raiz.derecho().derecho().derecho().derecho());
        }

        @Test
        @DisplayName("la copia no se entera de lo que pase despues en el arbol")
        void laCopiaEsIndependiente() {
            cargarArbolEquilibrado();
            Rama antes = formaDelArbol();

            arbol.eliminar(70);

            // Es una copia, no una vista: quien la tenga puede dibujarla tranquilo aunque el
            // arbol cambie mientras tanto.
            assertEquals(70, antes.derecho().valor());
            assertEquals(80, formaDelArbol().derecho().valor(), "el arbol si cambio");
        }

        @Test
        @DisplayName("refleja el arbol despues de eliminar, no el de antes")
        void siguePorDondeVaElArbol() {
            cargarArbolEquilibrado();

            arbol.eliminar(50);

            // Al borrar la raiz, el sucesor inorden (60) ocupa su lugar.
            assertEquals(60, formaDelArbol().valor());
        }
    }
}
