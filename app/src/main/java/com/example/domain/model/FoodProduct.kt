package com.example.domain.model

import java.text.Normalizer

data class FoodProduct(
    val id: String,
    val canonicalName: String,
    val aliases: List<String>,
    val category: FoodCategory,
    val isClimacteric: Boolean,
    val isSupportedForAdvancedEstimate: Boolean = true,
    val speciesRipenessCriteria: String
) {
    companion object {
        val SUPPORTED_CATALOG: List<FoodProduct> = listOf(
            FoodProduct(
                id = "banana",
                canonicalName = "Plátano / Banano",
                aliases = listOf("platano", "banano", "banana", "cambur", "guineo", "plantain"),
                category = FoodCategory.FRUIT,
                isClimacteric = true,
                speciesRipenessCriteria = "Escala visual de 1 a 7: verde firme (inmaduro), amarillo con puntas verdes (en maduración), amarillo uniforme con pecas finas marrones (maduro óptimo), marrón dominante o blando (sobremaduro)."
            ),
            FoodProduct(
                id = "avocado",
                canonicalName = "Aguacate / Palta",
                aliases = listOf("aguacate", "palta", "avocado", "hass"),
                category = FoodCategory.FRUIT,
                isClimacteric = true,
                speciesRipenessCriteria = "En variedad Hass: verde brillante y piel tensa (inmaduro), transición verde oscuro a púrpura (en maduración), oscuro casi negro rugoso sin hundimientos (maduro), hundimientos profundos o moho en pedúnculo (sobremaduro/deteriorado)."
            ),
            FoodProduct(
                id = "tomato",
                canonicalName = "Tomate / Jitomate",
                aliases = listOf("tomate", "jitomate", "tomato", "cherry"),
                category = FoodCategory.VEGETABLE,
                isClimacteric = true,
                speciesRipenessCriteria = "Verde o pintón rosado (inmaduro/en maduración), rojo uniforme brillante y piel tersa (maduro), piel arrugada, zonas translúcidas o grietas húmedas (sobremaduro/deteriorado)."
            ),
            FoodProduct(
                id = "apple",
                canonicalName = "Manzana",
                aliases = listOf("manzana", "apple", "fuji", "gala", "granny smith", "red delicious"),
                category = FoodCategory.FRUIT,
                isClimacteric = true,
                speciesRipenessCriteria = "Piel lisa, firme y color vivo según variedad (maduro), arrugamiento de cutícula, magulladuras marrones hundidas o textura harinosa visible en cortes (sobremaduro)."
            ),
            FoodProduct(
                id = "mango",
                canonicalName = "Mango",
                aliases = listOf("mango", "ataulfo", "tommy atkins", "kent", "keitt"),
                category = FoodCategory.FRUIT,
                isClimacteric = true,
                speciesRipenessCriteria = "Hombros planos y tono verde opaco (inmaduro), llenado de hombros con fondo amarillo/naranja y chapas rojizas (maduro), manchas negras coalescentes (antracnosis) o piel arrugada (sobremaduro)."
            ),
            FoodProduct(
                id = "papaya",
                canonicalName = "Papaya",
                aliases = listOf("papaya", "lechosa", "maradol"),
                category = FoodCategory.FRUIT,
                isClimacteric = true,
                speciesRipenessCriteria = "Verde con vetas amarillas incipientes (en maduración), 75-100% amarillo-anaranjado uniforme (maduro), depresiones circulares acuosas o micelio blanco (deteriorado)."
            ),
            FoodProduct(
                id = "strawberry",
                canonicalName = "Fresa / Frutilla",
                aliases = listOf("fresa", "frutilla", "fresas", "strawberry"),
                category = FoodCategory.FRUIT,
                isClimacteric = false,
                speciesRipenessCriteria = "No climatérica: puntas blancas o verdes (inmadura, no endulzará más), rojo brillante uniforme con cáliz verde fresco (madura), rojo oscuro opaco, exudado líquido o micelio gris Botrytis (sobremadura/deteriorada)."
            ),
            FoodProduct(
                id = "orange",
                canonicalName = "Naranja / Mandarina",
                aliases = listOf("naranja", "mandarina", "clementina", "orange", "tangerine", "citrico"),
                category = FoodCategory.FRUIT,
                isClimacteric = false,
                speciesRipenessCriteria = "No climatérica: piel turgente y brillante sin zonas blandas (madura), resequedad del flavedo, zonas acuosas blandas o moho verde/azul Penicillium (deteriorada)."
            ),
            FoodProduct(
                id = "lemon",
                canonicalName = "Limón / Lima",
                aliases = listOf("limon", "lima", "lemon", "lime", "sutil", "persa"),
                category = FoodCategory.FRUIT,
                isClimacteric = false,
                speciesRipenessCriteria = "Verde brillante o amarillo vivo según variedad con piel lisa (óptimo), amarillamiento seco o endurecimiento/pardeamiento de cáscara (envejecido)."
            ),
            FoodProduct(
                id = "potato",
                canonicalName = "Papa / Patata",
                aliases = listOf("papa", "patata", "potato", "papas"),
                category = FoodCategory.VEGETABLE,
                isClimacteric = false,
                speciesRipenessCriteria = "Piel firme sin brotes ni tonos verdes (óptimo). ¡Atención!: coloración verde subepidérmica indica solanina; brotes largos o arrugamiento indican envejecimiento; zonas húmedas oscuras indican pudrición."
            ),
            FoodProduct(
                id = "lettuce",
                canonicalName = "Lechuga / Hojas Verdes",
                aliases = listOf("lechuga", "espinaca", "acelga", "lettuce", "spinach", "romana", "iceberg"),
                category = FoodCategory.VEGETABLE,
                isClimacteric = false,
                speciesRipenessCriteria = "Hojas turgentes y crujientes (fresco), pérdida de turgencia/marchitez (en transición), bordes oxidados rojizos/marrones o descomposición mucosa (deteriorado)."
            ),
            FoodProduct(
                id = "bell_pepper",
                canonicalName = "Pimiento / Morrón",
                aliases = listOf("pimiento", "morron", "pimenton", "bell pepper", "capsicum", "chile dulce"),
                category = FoodCategory.VEGETABLE,
                isClimacteric = false,
                speciesRipenessCriteria = "Cutícula brillante, lisa y paredes firmes (fresco óptimo), arrugamiento en hombros o pedúnculo oscurecido y blando (sobremaduro/deteriorado)."
            )
        )

        fun findMatchingProduct(rawName: String?): FoodProduct? {
            if (rawName.isNullOrBlank()) return null
            val normalizedInput = normalize(rawName)
            if (normalizedInput.length < 3) return null

            return SUPPORTED_CATALOG.firstOrNull { product ->
                val canonicalNorm = normalize(product.canonicalName)
                canonicalNorm.contains(normalizedInput) ||
                    normalizedInput.contains(canonicalNorm) ||
                    product.aliases.any { alias ->
                        val aliasNorm = normalize(alias)
                        normalizedInput == aliasNorm ||
                            normalizedInput.contains(aliasNorm) ||
                            aliasNorm.contains(normalizedInput)
                    }
            }
        }

        private fun normalize(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase().trim(), Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }
    }
}
