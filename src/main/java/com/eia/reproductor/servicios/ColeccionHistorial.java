package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modos.ModoReproduccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lo ya reproducido, visto como una coleccion mas del selector.
 *
 * <p>Detras hay una {@code ColaSimple} acotada que vive en {@code ModoBase}: al reproducir se
 * encola la cancion y, si se pasa del tope, se descarta la mas antigua en O(1). Sacarla a la
 * interfaz convierte esa estructura en algo que se ve y se puede volver a reproducir.</p>
 *
 * <p><b>Se pregunta al modo activo en cada consulta</b>, no se guarda una referencia: el modo
 * cambia cuando el usuario pulsa otra pestania, y una referencia fija mostraria el historial del
 * modo anterior.</p>
 *
 * <p>Se muestra de la mas reciente a la mas antigua, que es como uno busca lo que acaba de sonar,
 * aunque la cola por dentro las guarde en el orden contrario.</p>
 */
public class ColeccionHistorial implements ColeccionDeCanciones {

    /** Nombre con la flecha de vuelta, para distinguirlo de las listas hechas a mano. */
    public static final String NOMBRE = "↺ HISTORIAL";

    private final Supplier<ModoReproduccion> modoActivo;

    /**
     * Crea la coleccion.
     *
     * @param modoActivo de donde sacar el modo que esta sonando ahora mismo
     */
    public ColeccionHistorial(Supplier<ModoReproduccion> modoActivo) {
        this.modoActivo = modoActivo;
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public List<Cancion> canciones() {
        ModoReproduccion modo = modoActivo.get();
        if (modo == null) {
            return List.of();
        }
        List<Cancion> alReves = new ArrayList<>(modo.historial());
        Collections.reverse(alReves);
        return alReves;
    }

    /** {@inheritDoc} <p>Se llena solo al reproducir; no se le meten canciones a mano.</p> */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
