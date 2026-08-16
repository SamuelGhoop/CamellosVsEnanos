package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;
import com.eia.reproductor.modelo.ColeccionDeCanciones;
import com.eia.reproductor.modos.ModoReproduccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Lo ya reproducido, visto como una coleccion mas del selector. */
public class ColeccionHistorial implements ColeccionDeCanciones {
    /** Nombre con la flecha de vuelta, para distinguirlo de las listas hechas a mano. */
    public static final String NOMBRE = "↺ HISTORIAL";

    private final Supplier<ModoReproduccion> modoActivo;

    /** Crea la coleccion. */
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

    /** {@inheritDoc} Se llena solo al reproducir; no se le meten canciones a mano. */
    @Override
    public boolean admiteEdicion() {
        return false;
    }
}
