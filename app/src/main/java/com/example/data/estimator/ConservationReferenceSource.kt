package com.example.data.estimator

import com.example.domain.model.FoodCategory
import com.example.domain.model.FoodProduct

data class ConservationReferenceEntry(
    val productId: String,
    val productName: String,
    val category: FoodCategory,
    val optimalTempRangeCelsius: IntRange,
    val chillingSensitiveBelowCelsius: Int? = null,
    val typicalRipeningDaysRoomTemp: IntRange? = null,
    val ripeRoomTempDays: IntRange,
    val ripeRefrigeratedDays: IntRange,
    val cutRefrigeratedDays: IntRange,
    val idealStorageLocation: String,
    val ethyleneNotes: String,
    val generalReferenceSummary: String,
    val qualityCheckTips: List<String>,
    val referenceCitation: String
)

object ConservationReferenceSource {
    private val entries: List<ConservationReferenceEntry> = listOf(
        ConservationReferenceEntry(
            productId = "banana",
            productName = "Plátano / Banano",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 13..15,
            chillingSensitiveBelowCelsius = 12,
            typicalRipeningDaysRoomTemp = 2..5,
            ripeRoomTempDays = 2..4,
            ripeRefrigeratedDays = 4..7,
            cutRefrigeratedDays = 1..2,
            idealStorageLocation = "Temperatura ambiente fresca (15–20 °C) lejos del sol directo; refrigerar solo cuando ya alcanzó madurez óptima.",
            ethyleneNotes = "Alto productor y sensible al etileno. Mantener separado de manzanas o aguacates si desea retrasar su maduración.",
            generalReferenceSummary = "A temp. ambiente (20 °C) madura en 2–5 días y dura 2–4 días una vez amarillo. Bajo 12 °C sufre daño por frío (piel gris/marrón), aunque la pulpa se conserva 4–7 días si se refrigera ya maduro.",
            qualityCheckTips = listOf(
                "Pecas marrones finas ('sugar spots') indican dulzor máximo, no deterioro.",
                "Descartar si presenta olor fermentado alcohólico, exudado líquido o moho en el corona/pedúnculo."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Musa spp., T° crítica 13 °C)"
        ),
        ConservationReferenceEntry(
            productId = "avocado",
            productName = "Aguacate / Palta",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 5..12,
            chillingSensitiveBelowCelsius = 5,
            typicalRipeningDaysRoomTemp = 3..6,
            ripeRoomTempDays = 1..3,
            ripeRefrigeratedDays = 3..6,
            cutRefrigeratedDays = 1..2,
            idealStorageLocation = "Madurar a temperatura ambiente (18–22 °C); transferir a refrigeración (5–7 °C) únicamente cuando ceda suavemente al tacto.",
            ethyleneNotes = "Climatérico sensible al etileno. Envolver junto a un plátano o manzana acelera su ablandamiento en 24–48 h.",
            generalReferenceSummary = "Verde firme tarda 3–6 días en madurar a temperatura ambiente. Una vez maduro dura 1–3 días en mesón o 3–6 días en el cajón de verduras del refrigerador.",
            qualityCheckTips = listOf(
                "Retirar el botón peduncular: verde/amarillo indica pulpa sana; negro o con micelio indica pardeamiento vascular.",
                "Si está cortado, aplicar gotas de limón y cubrir en contacto directo con film para frenar la polifenoloxidasa."
            ),
            referenceCitation = "UC Davis Postharvest Technology Center & USDA FoodKeeper (Persea americana)"
        ),
        ConservationReferenceEntry(
            productId = "tomato",
            productName = "Tomate / Jitomate",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 12..20,
            chillingSensitiveBelowCelsius = 10,
            typicalRipeningDaysRoomTemp = 2..4,
            ripeRoomTempDays = 3..5,
            ripeRefrigeratedDays = 4..7,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Conservar fuera del refrigerador con la cicatriz del tallo hacia abajo hasta alcanzar rojo uniforme; refrigerar solo si está muy maduro.",
            ethyleneNotes = "Productor moderado de etileno. No almacenar junto a pepinos o lechugas (les provoca amarillamiento).",
            generalReferenceSummary = "A temperatura ambiente conserva compuestos aromáticos durante 3–5 días. Bajo 10 °C pierde sabor y adquiere textura harinosa, aunque el frío extiende la firmeza 4–7 días en tomates ya muy maduros.",
            qualityCheckTips = listOf(
                "La cicatriz peduncular es el primer punto de entrada de Alternaria (moho negro aterciopelado).",
                "Descartar si la piel cede con exudado ácido o grietas húmedas."
            ),
            referenceCitation = "USDA FoodKeeper & FAO Postharvest Manual (Solanum lycopersicum)"
        ),
        ConservationReferenceEntry(
            productId = "apple",
            productName = "Manzana",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 1..4,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = 2..4,
            ripeRoomTempDays = 5..8,
            ripeRefrigeratedDays = 21..35,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Cajón de alta humedad del refrigerador (1–4 °C). A temperatura ambiente se vuelve harinosa 6 veces más rápido.",
            ethyleneNotes = "Muy alto emisor de etileno. Aislar de hojas verdes, brócoli, zanahorias (causa amargor) y plátanos.",
            generalReferenceSummary = "En mesón dura 5–8 días antes de perder crocancia. En refrigeración (1–4 °C) mantiene su turgencia de 3 a 5 semanas.",
            qualityCheckTips = listOf(
                "Piel ligeramente arrugada indica pérdida de humedad pero sigue siendo apta para cocción o compota.",
                "Separar inmediatamente cualquier pieza con magulladuras blandas marrones (Penicillium expansum)."
            ),
            referenceCitation = "USDA FoodKeeper & Washington State Tree Fruit Extension (Malus domestica)"
        ),
        ConservationReferenceEntry(
            productId = "mango",
            productName = "Mango",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 12..14,
            chillingSensitiveBelowCelsius = 10,
            typicalRipeningDaysRoomTemp = 3..6,
            ripeRoomTempDays = 2..4,
            ripeRefrigeratedDays = 4..7,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Madurar a 18–22 °C; una vez fragante y ligeramente flexible al tacto, refrigerar entero hasta por una semana.",
            ethyleneNotes = "Fruta climatérica tropical. No refrigerar en estado verde porque se bloquea su síntesis de azúcares y carotenoides.",
            generalReferenceSummary = "Madura en 3–6 días a temp. ambiente. Maduro dura 2–4 días fuera del frío o 4–7 días refrigerado.",
            qualityCheckTips = listOf(
                "Pequeñas pecas negras aisladas en piel amarilla son normales en mangos dulces.",
                "Manchas negras hundidas coalescentes cerca del pedúnculo indican antracnosis avanzada."
            ),
            referenceCitation = "National Mango Board & UC Davis Postharvest (Mangifera indica)"
        ),
        ConservationReferenceEntry(
            productId = "papaya",
            productName = "Papaya",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 7..13,
            chillingSensitiveBelowCelsius = 7,
            typicalRipeningDaysRoomTemp = 2..5,
            ripeRoomTempDays = 2..3,
            ripeRefrigeratedDays = 4..6,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Ambiente fresco hasta que el 75% de la piel sea amarilla; luego refrigerar para frenar el ablandamiento.",
            ethyleneNotes = "Climatérica rápida. Girar ocasionalmente para evitar puntos planos por presión.",
            generalReferenceSummary = "Pasa de pintona a madura en 2–5 días a 20 °C. Una vez amarilla dura 2–3 días a temp. ambiente o 4–6 días en refrigeración.",
            qualityCheckTips = listOf(
                "Ceder levemente a la presión digital indica punto óptimo de consumo.",
                "Descartar zonas con depresiones circulares acuosas o micelio blanco."
            ),
            referenceCitation = "FAO & UC Davis Postharvest Fact Sheet (Carica papaya)"
        ),
        ConservationReferenceEntry(
            productId = "strawberry",
            productName = "Fresa / Frutilla",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 0..2,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 1..2,
            ripeRefrigeratedDays = 3..6,
            cutRefrigeratedDays = 1..2,
            idealStorageLocation = "Refrigerar inmediatamente (0–4 °C) sin lavar en envase ventilado con papel absorbente seco.",
            ethyleneNotes = "No climatérica: no se vuelve más dulce tras la cosecha. Lavar únicamente justo antes de consumir.",
            generalReferenceSummary = "Altamente perecedera: a temp. ambiente se deteriora en 24–36 h. En refrigeración seca con papel absorbente dura 3–6 días.",
            qualityCheckTips = listOf(
                "La humedad superficial libre dispara el moho gris (Botrytis cinerea) en pocas horas.",
                "Retirar cualquier fresa blanda o con pelusa grisácea para no contaminar el resto del envase."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Berry Postharvest Guide (Fragaria × ananassa)"
        ),
        ConservationReferenceEntry(
            productId = "orange",
            productName = "Naranja / Mandarina",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 3..8,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 5..9,
            ripeRefrigeratedDays = 14..28,
            cutRefrigeratedDays = 2..4,
            idealStorageLocation = "Lugar fresco ventilado para consumo en la semana, o refrigeración (3–8 °C) con circulación de aire hasta por 3–4 semanas.",
            ethyleneNotes = "No climatérica. No guardar en bolsas plásticas cerradas sin perforaciones porque condensa humedad.",
            generalReferenceSummary = "A temp. ambiente dura 5–9 días; en refrigeración conserva jugosidad de 2 a 4 semanas.",
            qualityCheckTips = listOf(
                "Cáscara ligeramente seca o arrugada no afecta la inocuidad si el interior sigue jugoso.",
                "Descartar inmediatamente si observa polvo verde/azulado o zonas acuosas blandas."
            ),
            referenceCitation = "USDA FoodKeeper & Citrus Postharvest Extension (Citrus sinensis)"
        ),
        ConservationReferenceEntry(
            productId = "lemon",
            productName = "Limón / Lima",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 7..12,
            chillingSensitiveBelowCelsius = 6,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 5..8,
            ripeRefrigeratedDays = 14..28,
            cutRefrigeratedDays = 2..4,
            idealStorageLocation = "Refrigeración en compartimento de verduras (7–10 °C) dentro de bolsa perforada para evitar deshidratación de la piel.",
            ethyleneNotes = "No climatérico. El amarillamiento de limas verdes indica envejecimiento de clorofila, no mayor acidez.",
            generalReferenceSummary = "A temp. ambiente se deshidrata y endurece en 5–8 días; en refrigeración se mantiene jugoso de 2 a 4 semanas.",
            qualityCheckTips = listOf(
                "Un limón pesado para su tamaño indica alto contenido de jugo.",
                "Si está cortado, conservar boca abajo en plato pequeño o recipiente hermético."
            ),
            referenceCitation = "UC Davis Postharvest Center & USDA FoodKeeper (Citrus limon / aurantiifolia)"
        ),
        ConservationReferenceEntry(
            productId = "potato",
            productName = "Papa / Patata",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 7..10,
            chillingSensitiveBelowCelsius = 4,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 14..28,
            ripeRefrigeratedDays = 7..14,
            cutRefrigeratedDays = 1..2,
            idealStorageLocation = "Despensa oscura, seca y fresca (7–12 °C) en bolsa de papel o malla transpirable. No refrigerar crudas.",
            ethyleneNotes = "Almacenar lejos de cebollas (los gases mutuos aceleran la brotación) y en total oscuridad.",
            generalReferenceSummary = "En despensa oscura dura 2–4 semanas. Bajo 4 °C convierte su almidón en azúcares reductores (endulzamiento por frío).",
            qualityCheckTips = listOf(
                "La luz induce síntesis de clorofila y glicoalcaloides tóxicos (solanina): pele profundamente o descarte zonas verdes.",
                "Si está pelada o cortada, cubrir con agua fría en refrigeración máximo 24 horas."
            ),
            referenceCitation = "USDA FoodKeeper & University of Idaho Potato Storage Guide (Solanum tuberosum)"
        ),
        ConservationReferenceEntry(
            productId = "lettuce",
            productName = "Lechuga / Hojas Verdes",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 0..4,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 1..1,
            ripeRefrigeratedDays = 4..8,
            cutRefrigeratedDays = 2..4,
            idealStorageLocation = "Refrigeración (0–4 °C) en recipiente ventilado con toalla de papel absorbente que controle la condensación.",
            ethyleneNotes = "Extremadamente sensible al etileno (provoca manchas rojizas 'russet spotting'). Mantener lejos de manzanas, plátanos y tomates.",
            generalReferenceSummary = "Fuera del frío se marchita en menos de 24 h. En refrigeración con control de humedad dura 4–8 días (entera) o 2–4 días (en hojas cortadas).",
            qualityCheckTips = listOf(
                "Hojas ligeramente lacias pero sin olor ni mucosidad pueden rehidratarse 10 minutos en agua helada.",
                "Descartar hojas con bordes negros viscosos o descomposición blanda húmeda."
            ),
            referenceCitation = "UC Davis Postharvest & USDA FoodKeeper (Lactuca sativa)"
        ),
        ConservationReferenceEntry(
            productId = "bell_pepper",
            productName = "Pimiento / Morrón",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 7..10,
            chillingSensitiveBelowCelsius = 7,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 3..5,
            ripeRefrigeratedDays = 7..14,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Cajón de verduras del refrigerador (7–10 °C) completamente seco.",
            ethyleneNotes = "No climatérico. Los pimientos verdes suelen durar un poco más que los rojos o amarillos ya maduros en planta.",
            generalReferenceSummary = "En mesón fresco dura 3–5 días; en el cajón del refrigerador mantiene firmeza de 1 a 2 semanas.",
            qualityCheckTips = listOf(
                "Revisar el cáliz/tallo: es la primera zona donde aparece moho o ablandamiento.",
                "Una vez cortado, retirar semillas y nervaduras blancas para prolongar su frescura 2–3 días."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Capsicum annuum)"
        ),
        ConservationReferenceEntry(
            productId = "passion_fruit",
            productName = "Maracuyá / Granadilla",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 7..10,
            chillingSensitiveBelowCelsius = 6,
            typicalRipeningDaysRoomTemp = 3..6,
            ripeRoomTempDays = 3..6,
            ripeRefrigeratedDays = 10..21,
            cutRefrigeratedDays = 2..4,
            idealStorageLocation = "Madurar a temperatura ambiente (18–22 °C) hasta que sea amarillo intenso; luego refrigerar (7–10 °C) para conservar su pulpa jugosa por 2 a 3 semanas.",
            ethyleneNotes = "Fruta climatérica con alta producción de etileno. Las ligeras arrugas en la cáscara amarilla indican madurez y dulzor óptimos, no deterioro.",
            generalReferenceSummary = "A temp. ambiente dura 3–6 días una vez amarillo. En refrigeración (7–10 °C) mantiene la calidad de su pulpa de 10 a 21 días.",
            qualityCheckTips = listOf(
                "Una cáscara ligeramente arrugada pero pesada al tacto indica máxima concentración de jugo aromático.",
                "Descartar únicamente si presenta manchas fúngicas algodonosas, grietas con exudado fermentado o pérdida total de peso."
            ),
            referenceCitation = "UC Davis Postharvest Technology Center & FAO Tropical Fruits (Passiflora edulis)"
        ),
        ConservationReferenceEntry(
            productId = "pineapple",
            productName = "Piña / Ananá",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 7..12,
            chillingSensitiveBelowCelsius = 7,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 2..4,
            ripeRefrigeratedDays = 5..8,
            cutRefrigeratedDays = 3..5,
            idealStorageLocation = "A temperatura ambiente fresca por 2–4 días o en refrigeración (7–10 °C) una vez lista para consumir.",
            ethyleneNotes = "No climatérica: no acumula más azúcares después de cosechada, aunque la cáscara puede cambiar de verde a amarillo.",
            generalReferenceSummary = "Entera dura 2–4 días en mesón o 5–8 días refrigerada. Cortada en trozos en envase hermético dura 3–5 días en frío.",
            qualityCheckTips = listOf(
                "Aroma dulce en la base e interior dorado indican buen punto de cosecha.",
                "Descartar si percibe burbujeo, olor alcohólico fermentado o manchas marrones acuosas en la base."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Ananas comosus)"
        ),
        ConservationReferenceEntry(
            productId = "grape",
            productName = "Uva",
            category = FoodCategory.FRUIT,
            optimalTempRangeCelsius = 0..2,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 2..4,
            ripeRefrigeratedDays = 7..14,
            cutRefrigeratedDays = 2..3,
            idealStorageLocation = "Refrigerar inmediatamente sin lavar en bolsa o envase ventilado; lavar solo justo antes de consumir.",
            ethyleneNotes = "No climatérica. Sensible a la deshidratación del escobajo (tallo) y al moho gris (Botrytis).",
            generalReferenceSummary = "En mesón dura 2–4 días; en refrigeración seca y ventilada conserva su firmeza de 1 a 2 semanas.",
            qualityCheckTips = listOf(
                "El escobajo verde y flexible es el mejor indicador de cosecha reciente.",
                "Retirar cualquier uva partida o con moho para evitar que contamine el racimo."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Vitis vinifera)"
        ),
        ConservationReferenceEntry(
            productId = "carrot",
            productName = "Zanahoria",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 0..4,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 4..7,
            ripeRefrigeratedDays = 14..28,
            cutRefrigeratedDays = 5..10,
            idealStorageLocation = "Cajón de verduras del refrigerador dentro de bolsa perforada, retirando las hojas verdes superiores si las tuviera.",
            ethyleneNotes = "Sensible al etileno: almacenarla junto a manzanas o plátanos genera compuestos amargos (isocumarina).",
            generalReferenceSummary = "A temp. ambiente se deshidrata y ablanda en 4–7 días. En refrigeración se conserva crujiente de 2 a 4 semanas.",
            qualityCheckTips = listOf(
                "Si perdió rigidez por deshidratación leve pero no tiene manchas, puede rehidratarse en agua fría.",
                "Descartar si presenta zonas blandas acuosas o pardeamiento viscoso."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Daucus carota)"
        ),
        ConservationReferenceEntry(
            productId = "broccoli",
            productName = "Brócoli",
            category = FoodCategory.VEGETABLE,
            optimalTempRangeCelsius = 0..2,
            chillingSensitiveBelowCelsius = null,
            typicalRipeningDaysRoomTemp = null,
            ripeRoomTempDays = 1..2,
            ripeRefrigeratedDays = 4..7,
            cutRefrigeratedDays = 2..4,
            idealStorageLocation = "Refrigeración inmediata (0–4 °C) con circulación de aire moderada y sin humedad libre acumulada.",
            ethyleneNotes = "Muy sensible al etileno: amarillea rápidamente si se guarda cerca de manzanas, plátanos o tomates.",
            generalReferenceSummary = "A temp. ambiente amarillea en 1–2 días. En refrigeración mantiene sus floretes verdes durante 4–7 días.",
            qualityCheckTips = listOf(
                "Floretes amarillos pero firmes y sin mal olor aún son seguros para consumo inmediato cocido.",
                "Descartar si los floretes están negros, viscosos o desprenden olor fuerte desagradable."
            ),
            referenceCitation = "USDA FoodKeeper & UC Davis Postharvest Center (Brassica oleracea)"
        )
    )

    fun getAllReferences(): List<ConservationReferenceEntry> = entries

    fun findByProduct(product: FoodProduct?): ConservationReferenceEntry? {
        if (product == null) return null
        return entries.firstOrNull { it.productId == product.id }
    }

    fun findByRawName(rawName: String?): ConservationReferenceEntry? {
        val matched = FoodProduct.findMatchingProduct(rawName)
        return findByProduct(matched)
    }
}
