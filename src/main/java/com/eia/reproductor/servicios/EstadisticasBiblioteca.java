package com.eia.reproductor.servicios;

import com.eia.reproductor.modelo.Cancion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resumen de lo que mas se ha escuchado, calculado a partir del contador de cada cancion.
 *
 * <p>Es solo calculo: no sabe nada de ventanas ni de JavaFX. Asi la ventana de estadisticas se
 * limita a colocar textos, y estas cuentas —que son las que se pueden equivocar— se pueden probar
 * en un test normal.</p>
 *
 * @param totalReproducciones suma de todas las veces que ha sonado cualquier cancion
 * @param distintasSonadas    cuantas canciones han sonado al menos una vez
 * @param minutosEscuchados   duracion acumulada de todo lo reproducido, en minutos
 * @param masEscuchadas       las mas repetidas, de mayor a menor (como mucho cinco)
 * @param artistaTop          artista con mas reproducciones sumadas, o {@code null} si no hay
 * @param generoTop           genero con mas reproducciones sumadas, o {@code null} si no hay
 */
public record EstadisticasBiblioteca(
        int totalReproducciones,
        int distintasSonadas,
        long minutosEscuchados,
        List<Cancion> masEscuchadas,
        String artistaTop,
        String generoTop) {

    /** Cuantas entran en el podio. Mas de cinco no cabe en la ventana sin tener que desplazarse. */
    private static final int CUANTAS_EN_EL_PODIO = 5;

    /** Copia defensiva: el podio se entrega de solo lectura. */
    public EstadisticasBiblioteca {
        masEscuchadas = List.copyOf(masEscuchadas);
    }

    /**
     * Calcula el resumen de una biblioteca.
     *
     * @param canciones las canciones a resumir; puede venir vacia
     * @return el resumen, con ceros y {@code null} si todavia no ha sonado nada
     */
    public static EstadisticasBiblioteca de(List<Cancion> canciones) {
        int total = 0;
        int distintas = 0;
        long segundos = 0;
        Map<String, Integer> porArtista = new HashMap<>();
        Map<String, Integer> porGenero = new HashMap<>();

        for (Cancion cancion : canciones) {
            int veces = cancion.getVecesReproducida();
            if (veces <= 0) {
                continue;
            }
            total += veces;
            distintas++;
            segundos += (long) cancion.getDuracionSegundos() * veces;
            sumar(porArtista, cancion.getArtista(), veces);
            sumar(porGenero, cancion.getGenero(), veces);
        }

        List<Cancion> podio = new ArrayList<>();
        for (Cancion cancion : canciones) {
            if (cancion.getVecesReproducida() > 0) {
                podio.add(cancion);
            }
        }
        // Desempate por titulo para que dos ejecuciones den el mismo orden: si no, el podio
        // cambiaria de posiciones al reabrir la ventana sin que nadie haya escuchado nada.
        podio.sort(Comparator.comparingInt(Cancion::getVecesReproducida).reversed()
                .thenComparing(Cancion::getTitulo, String.CASE_INSENSITIVE_ORDER));
        if (podio.size() > CUANTAS_EN_EL_PODIO) {
            podio = podio.subList(0, CUANTAS_EN_EL_PODIO);
        }

        return new EstadisticasBiblioteca(total, distintas, segundos / 60, podio,
                elMasRepetido(porArtista), elMasRepetido(porGenero));
    }

    /**
     * Acumula, ignorando lo que venga en blanco.
     *
     * <p>No sobra aunque {@code Cancion} prometa que el artista nunca es nulo: al cargar de disco,
     * Gson escribe los campos por reflexion y se salta los setters, asi que un JSON con
     * {@code "artista": null} deja el campo nulo. Sin este filtro acabaria en las estadisticas un
     * artista llamado "null".</p>
     */
    private static void sumar(Map<String, Integer> cuenta, String clave, int veces) {
        if (clave == null || clave.isBlank()) {
            return;
        }
        cuenta.merge(clave, veces, Integer::sum);
    }

    /** @return la clave con mas reproducciones, o {@code null} si el mapa esta vacio */
    private static String elMasRepetido(Map<String, Integer> cuenta) {
        return cuenta.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(
                                Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** @return true si todavia no ha sonado nada y no hay nada que mostrar */
    public boolean vacias() {
        return totalReproducciones == 0;
    }
}
