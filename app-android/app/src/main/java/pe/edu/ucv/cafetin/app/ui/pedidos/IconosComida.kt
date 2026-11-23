package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.annotation.DrawableRes
import pe.edu.ucv.cafetin.app.R
import java.text.Normalizer

/**
 * Ícono de cada comida del menú. La API no trae imágenes, así que se elige por palabras clave del nombre
 * del producto y, si ninguna coincide, de su categoría. Sin coincidencia, el avatar muestra la inicial.
 *
 * El orden importa: gana la primera regla que coincide ("Pan con chicharrón" es sándwich, no pan).
 */
enum class Comida(@DrawableRes val icono: Int, vararg val claves: String) {
    SANDWICH(R.drawable.ic_comida_sandwich, "sandwich", "sanguche", "hamburguesa", "butifarra", "chicharron", "triple", "mixto", "hot dog", "pan con"),
    PIZZA(R.drawable.ic_comida_pizza, "pizza"),
    TORTA(R.drawable.ic_comida_torta, "torta", "queque", "keke", "pastel", "postre", "alfajor", "brownie", "pie", "cheesecake", "gelatina", "mazamorra", "arroz con leche", "pionono", "muffin"),
    CAFE(R.drawable.ic_comida_cafe, "cafe", "capuchino", "cappuccino", "espresso", "expreso", "latte", "mocaccino", "te", "infusion", "anis", "manzanilla", "emoliente", "chocolate caliente", "mate"),
    BEBIDA(R.drawable.ic_comida_bebida, "chicha", "jugo", "refresco", "limonada", "gaseosa", "agua", "bebida", "maracuya", "inca kola", "coca cola", "yogurt", "batido", "frappe", "smoothie", "leche"),
    HELADO(R.drawable.ic_comida_helado, "helado", "paleta", "raspadilla"),
    GALLETA(R.drawable.ic_comida_galleta, "galleta", "cookie", "chocolate", "chocolatina"),
    SNACK(R.drawable.ic_comida_snack, "papas", "papitas", "chifle", "frituras", "snack", "salchipapa", "tequeno", "nachos", "canchita", "piqueo", "chizito", "doritos"),
    FRUTA(R.drawable.ic_comida_fruta, "fruta", "ensalada", "manzana", "platano", "mandarina", "naranja", "uva", "fresa", "papaya"),
    PAN(R.drawable.ic_comida_pan, "pan", "empanada", "croissant", "cachito", "enrollado", "humita", "tamal"),
    PLATO(R.drawable.ic_comida_plato, "menu", "almuerzo", "arroz", "sopa", "caldo", "pollo", "lomo", "tallarin", "ceviche", "causa", "seco", "aji de gallina", "estofado", "guiso", "plato", "combo"),
    DESAYUNO(R.drawable.ic_comida_desayuno, "desayuno", "tostada", "huevo", "avena", "quinua"),
    ;

    companion object {
        /** Categorías habituales, por si el nombre del producto no dice nada. */
        private val POR_CATEGORIA = listOf(
            "bebida" to BEBIDA, "jugo" to BEBIDA, "cafe" to CAFE, "infusion" to CAFE,
            "desayuno" to DESAYUNO, "snack" to SNACK, "golosina" to GALLETA, "postre" to TORTA,
            "menu" to PLATO, "almuerzo" to PLATO, "plato" to PLATO, "panaderia" to PAN, "fruta" to FRUTA,
        )

        fun de(nombre: String, categoria: String?): Comida? =
            entries.firstOrNull { c -> c.claves.any { contiene(nombre, it) } }
                ?: categoria?.let { cat -> POR_CATEGORIA.firstOrNull { contiene(cat, it.first) }?.second }

        /** Coincide por palabra completa (o el plural con "s"/"es"), sin tildes ni mayúsculas: "te" no casa con "tequeño". */
        private fun contiene(texto: String, clave: String): Boolean {
            val t = " " + normalizar(texto) + " "
            return listOf("", "s", "es").any { t.contains(" $clave$it ") }
        }

        private fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
    }
}
