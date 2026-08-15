package com.eia.reproductor.modelo;

import java.util.List;

/**
 * Descripcion de la estructura de datos que esta usando el modo activo, lista para dibujar.
 *
 * <p><b>Para que sirve.</b> La sustentacion exige explicar como funciona por dentro cada
 * estructura. Contarlo es una cosa; verlo moverse es otra. Esta descripcion permite que la
 * interfaz dibuje la estructura <i>real</i> —el anillo con su cursor, la cola vaciandose, el arbol
 * con su forma— en vez de una ilustracion fija que podria no corresponder con el codigo.</p>
 *
 * <p><b>Por que un tipo aparte y no los nodos.</b> Si el dibujante recibiera los nodos, tendria
 * que conocer {@code NodoDoble}, {@code NodoCola} y {@code NodoArbol}, que son de visibilidad de
 * paquete a proposito. Aqui viaja una copia de la <i>forma</i>, con las canciones ya convertidas a
 * texto: las estructuras siguen sin exponer sus tripas y el paquete de animacion sigue sin saber
 * nada del dominio.</p>
 *
 * <p>Es {@code sealed}: solo hay tres estructuras y no habra una cuarta por sorpresa. Eso permite
 * que el dibujante use un {@code switch} exhaustivo, sin {@code default}, de modo que si algun dia
 * se agrega un modo el compilador obligue a decidir como se dibuja.</p>
 *
 * <p>El nombre lo aporta el modo desde su propia constante, en vez de repetirlo aqui: asi no
 * pueden acabar diciendo cosas distintas la pestania y el titulo del panel.</p>
 */
public sealed interface EstructuraVisual {

    /** @return el nombre de la estructura, para el titulo del panel */
    String nombre();

    /**
     * La lista circular doblemente enlazada del modo aleatorio.
     *
     * @param nombre        como la llama el modo
     * @param etiquetas     los elementos en el orden en que estan enlazados
     * @param indiceActual  posicion del cursor, o -1 si todavia no se ha reproducido nada
     */
    record Anillo(String nombre, List<String> etiquetas, int indiceActual)
            implements EstructuraVisual {

        public Anillo {
            etiquetas = List.copyOf(etiquetas);
        }
    }

    /**
     * La cola FIFO del modo de orden de llegada.
     *
     * @param nombre     como la llama el modo
     * @param etiquetas  lo que queda por reproducir; el frente es el primero
     * @param yaSalieron cuantas han salido de la cola, que es lo que demuestra que se vacia
     */
    record Cola(String nombre, List<String> etiquetas, int yaSalieron)
            implements EstructuraVisual {

        public Cola {
            etiquetas = List.copyOf(etiquetas);
        }
    }

    /**
     * El arbol binario de busqueda del modo alfabetico.
     *
     * @param nombre como lo llama el modo
     * @param raiz   la forma del arbol, o {@code null} si esta vacio
     * @param actual etiqueta de la cancion sonando, para resaltarla en el recorrido
     */
    record Arbol(String nombre, Rama raiz, String actual) implements EstructuraVisual { }

    /**
     * Un nodo del arbol, ya sin datos de dominio.
     *
     * @param etiqueta   texto a pintar
     * @param izquierdo  subarbol izquierdo, o {@code null}
     * @param derecho    subarbol derecho, o {@code null}
     */
    record Rama(String etiqueta, Rama izquierdo, Rama derecho) {

        /** @return la altura de este subarbol, para repartir el alto del dibujo */
        public int altura() {
            int izq = izquierdo == null ? 0 : izquierdo.altura();
            int der = derecho == null ? 0 : derecho.altura();
            return 1 + Math.max(izq, der);
        }
    }
}
