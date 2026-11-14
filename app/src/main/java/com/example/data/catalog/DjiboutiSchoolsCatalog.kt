package com.example.data.catalog

enum class SchoolLevelFilter(val typeKey: String?, val labelFr: String) {
    ALL(null, "Tous"),
    PRIMAIRE("primaire", "Primaire"),
    COLLEGE("college", "Collège"),
    LYCEE("lycee", "Lycée")
}

data class OfficialSchoolEntry(
    val id: String,
    val code: String,
    val name: String,
    val schoolType: String, // "primaire", "college", "lycee"
    val sector: String,
    val address: String,
    val description: String = ""
) {
    val levelLabelFr: String
        get() = when (schoolType) {
            "primaire" -> "École primaire"
            "college" -> "Collège"
            "lycee" -> "Lycée"
            else -> schoolType.replaceFirstChar { it.uppercase() }
        }
}

object DjiboutiSchoolsCatalog {

    const val REGION_ID = "reg_djibouti_ville"
    const val REGION_NAME = "Djibouti-Ville"
    const val REGION_CODE = "DJV"

    val allSchools: List<OfficialSchoolEntry> = listOf(
        // --- Écoles Primaires : Centre-ville et Plateaux ---
        OfficialSchoolEntry(
            id = "dj_ep_republique",
            code = "EP-REP",
            name = "École de la République",
            schoolType = "primaire",
            sector = "Centre-ville et Plateaux",
            address = "Boulevard de la République / Plateau",
            description = "Plus ancienne école publique de Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_q6",
            code = "EP-Q6",
            name = "École Primaire Quartier 6",
            schoolType = "primaire",
            sector = "Centre-ville et Plateaux",
            address = "Rue Gamal Abder Nasser / Q6"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_q6_chateau_eau",
            code = "EP-Q6CE",
            name = "École Q6 Château d'Eau",
            schoolType = "primaire",
            sector = "Centre-ville et Plateaux",
            address = "Quartier 6 — Château d'Eau"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_complex",
            code = "EP-COMP",
            name = "École du Complex",
            schoolType = "primaire",
            sector = "Centre-ville et Plateaux",
            address = "Centre-ville et Plateaux"
        ),

        // --- Écoles Primaires : Quartiers Sud et Ambouli ---
        OfficialSchoolEntry(
            id = "dj_ep_ambouli_2",
            code = "EP-AMB2",
            name = "École Primaire d'Ambouli II",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Ambouli"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_guelleh_battal",
            code = "EP-GBAT",
            name = "École Primaire Publique Guelleh Battal",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Ambouli"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_dogley",
            code = "EP-DOG",
            name = "École Dogley",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Quartiers Sud et Ambouli"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_al_houriya",
            code = "EP-HOUR",
            name = "École Al Houriya",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Quartiers Sud et Ambouli"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_einguella",
            code = "EP-EING",
            name = "École Einguella",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Einguella — Quartiers Sud"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_boulaos",
            code = "EP-BOUL",
            name = "École Boulaos",
            schoolType = "primaire",
            sector = "Quartiers Sud et Ambouli",
            address = "Route de la Siesta, Boulaos"
        ),

        // --- Écoles Primaires : Balbala et secteurs périphériques ---
        OfficialSchoolEntry(
            id = "dj_ep_balbala_1",
            code = "EP-BAL1",
            name = "École de Balbala 1",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Balbala 1"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_balbala_3",
            code = "EP-BAL3",
            name = "École Balbala 3",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Balbala 3"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_balbala_3bis",
            code = "EP-BAL3B",
            name = "École Balbala 3 bis",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Balbala 3 bis"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_balbala_6",
            code = "EP-BAL6",
            name = "École Balbala VI",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Balbala VI"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_hodan_1",
            code = "EP-HOD1",
            name = "École Hodan 1",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Hodan 1, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_hodan_2",
            code = "EP-HOD2",
            name = "École Hodan 2",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "Hodan 2, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_pk12",
            code = "EP-PK12",
            name = "École PK12",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "PK12, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_ep_pk13",
            code = "EP-PK13",
            name = "École PK13",
            schoolType = "primaire",
            sector = "Balbala et secteurs périphériques",
            address = "PK13, Balbala"
        ),

        // --- Collèges Publics (Enseignement Moyen) ---
        OfficialSchoolEntry(
            id = "dj_col_boulaos",
            code = "COL-BOUL",
            name = "Collège de Boulaos",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Boulaos, Djibouti-Ville",
            description = "L'un des établissements historiques de la ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_cdf",
            code = "COL-CDF",
            name = "Collège Charles de Foucauld (CDF)",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_ambouli",
            code = "COL-AMB",
            name = "Collège d'Ambouli",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Ambouli, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_gabode_1",
            code = "COL-GAB1",
            name = "Collège de Gabode 1",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Gabode 1, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_stade",
            code = "COL-STAD",
            name = "Collège du Stade",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Secteur du Stade, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_fukuzawa",
            code = "COL-FUKU",
            name = "Collège de Fukuzawa",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_palmeraie_1",
            code = "COL-PAL1",
            name = "Collège de la Palmeraie 1",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "La Palmeraie, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_palmeraie_2",
            code = "COL-PAL2",
            name = "Collège de la Palmeraie 2",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "La Palmeraie, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_hayel_said",
            code = "COL-HAY",
            name = "Collège Hayel Said",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_barwaqo_1",
            code = "COL-BAR1",
            name = "Collège de Barwaqo 1",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Barwaqo 1, Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_col_dogley",
            code = "COL-DOG",
            name = "Collège Dogley",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Quartiers Sud / Dogley"
        ),
        OfficialSchoolEntry(
            id = "dj_col_dogley_bis",
            code = "COL-DOGB",
            name = "Collège Dogley Bis",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Quartiers Sud / Dogley"
        ),
        OfficialSchoolEntry(
            id = "dj_col_hodan_1",
            code = "COL-HOD1",
            name = "Collège Hodan 1",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Hodan 1, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_col_hodan_2",
            code = "COL-HOD2",
            name = "Collège Hodan 2",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Hodan 2, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_col_hodan_3",
            code = "COL-HOD3",
            name = "Collège Hodan 3",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Hodan 3, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_col_biokeyd_3",
            code = "COL-BIO3",
            name = "Collège de Biokeyd 3",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Biokeyd 3, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_col_sans_fil",
            code = "COL-SFIL",
            name = "Collège de Sans Fil",
            schoolType = "college",
            sector = "Collèges Publics (Enseignement Moyen)",
            address = "Quartier Sans Fil, Djibouti-Ville"
        ),

        // --- Lycées Publics Nationaux ---
        OfficialSchoolEntry(
            id = "dj_lyc_led",
            code = "LYC-LED",
            name = "Lycée d'État de Djibouti (LED)",
            schoolType = "lycee",
            sector = "Lycées Publics Nationaux",
            address = "Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_lyc_hodan_4",
            code = "LYC-HOD4",
            name = "Lycée de Hodan 4",
            schoolType = "lycee",
            sector = "Lycées Publics Nationaux",
            address = "Hodan 4, Balbala"
        ),
        OfficialSchoolEntry(
            id = "dj_lyc_guelleh_batal",
            code = "LYC-GBAT",
            name = "Lycée Guelleh Batal",
            schoolType = "lycee",
            sector = "Lycées Publics Nationaux",
            address = "Djibouti-Ville"
        ),
        OfficialSchoolEntry(
            id = "dj_lyc_lic",
            code = "LYC-LIC",
            name = "Lycée Technique Industriel et Commercial El-Hadj Omar Kamil Warsama",
            schoolType = "lycee",
            sector = "Lycées Publics Nationaux",
            address = "Djibouti-Ville"
        )
    )

    val primarySchools: List<OfficialSchoolEntry>
        get() = allSchools.filter { it.schoolType == "primaire" }

    val middleSchools: List<OfficialSchoolEntry>
        get() = allSchools.filter { it.schoolType == "college" }

    val highSchools: List<OfficialSchoolEntry>
        get() = allSchools.filter { it.schoolType == "lycee" }

    fun filter(
        level: SchoolLevelFilter,
        query: String = ""
    ): List<OfficialSchoolEntry> {
        val cleanQuery = query.trim().lowercase()
        return allSchools.filter { entry ->
            val levelMatches = level.typeKey == null || entry.schoolType == level.typeKey
            val queryMatches = cleanQuery.isBlank() ||
                entry.name.lowercase().contains(cleanQuery) ||
                entry.sector.lowercase().contains(cleanQuery) ||
                entry.address.lowercase().contains(cleanQuery) ||
                entry.code.lowercase().contains(cleanQuery)
            levelMatches && queryMatches
        }
    }
}
