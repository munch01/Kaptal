package com.Muncho.kaptal.model

data class CategoryFamily(
    val name: String = "",
    val subCategories: List<String> = emptyList()
)

fun getDefaultCategories() = listOf(
    CategoryFamily(
        name = "Vital / Incompressible",
        subCategories = listOf(
            "Logement",
            "Énergie & Eau",
            "Alimentation",
            "Santé",
            "Assurances & Impôts",
            "Transport & Mobilité",
            "Communication",
            "Crédits"
        )
    ),
    CategoryFamily(
        name = "Confort / Vie courante",
        subCategories = listOf(
            "Scolarité & Enfants",
            "Hygiène & Beauté",
            "Vêtements & Équipement",
            "Animaux",
            "Cadeaux & Événements",
            "Frais divers du quotidien"
        )
    ),
    CategoryFamily(
        name = "Superficiel / Plaisir",
        subCategories = listOf(
            "Abonnements & Divertissement",
            "Sorties & Restauration",
            "Vacances & Loisirs",
            "Shopping & Achats plaisir"
        )
    )
)

// Garder pour compatibilité temporaire durant la migration
val transactionCategories = getDefaultCategories()
