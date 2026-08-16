package com.eia.reproductor.modelo;

import java.util.List;

/** Descripcion de la estructura de datos que esta usando el modo activo, lista para dibujar. */
public sealed interface EstructuraVisual {
    /** @return el nombre de la estructura, para el titulo del panel */
    String nombre();

    /** La lista circular doblemente enlazada del modo aleatorio. */
    record Anillo(String nombre, List<String> etiquetas, int indiceActual)
            implements EstructuraVisual {
        public Anillo {
            etiquetas = List.copyOf(etiquetas);
        }
    }

    /** La cola FIFO del modo de orden de llegada. */
    record Cola(String nombre, List<String> etiquetas, int yaSalieron)
            implements EstructuraVisual {
        public Cola {
            etiquetas = List.copyOf(etiquetas);
        }
    }

    /** El arbol binario de busqueda del modo alfabetico. */
    record Arbol(String nombre, Rama raiz, String actual) implements EstructuraVisual { }

    /** Un nodo del arbol, ya sin datos de dominio. */
    record Rama(String etiqueta, Rama izquierdo, Rama derecho) {
        /** @return la altura de este subarbol, para repartir el alto del dibujo */
        public int altura() {
            int izq = izquierdo == null ? 0 : izquierdo.altura();
            int der = derecho == null ? 0 : derecho.altura();
            return 1 + Math.max(izq, der);
        }
    }
}
