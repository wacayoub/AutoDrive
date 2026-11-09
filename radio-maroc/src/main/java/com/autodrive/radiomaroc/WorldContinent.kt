package com.autodrive.radiomaroc

/** UN M49 regions, split into North and South America, with TW and XK for directory lookups. */
internal enum class WorldContinent(val label: String, codes: String) {
    AFRICA("Afrique", "AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG EH ER ET GA GH GM GN GQ GW IO KE KM LR LS LY MA MG ML MR MU MW MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TF TG TN TZ UG YT ZA ZM ZW"),
    EUROPE("Europe", "AD AL AT AX BA BE BG BY CH CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT JE LI LT LU LV MC MD ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA XK"),
    ASIA("Asie", "AE AF AM AZ BD BH BN BT CN CY GE HK ID IL IN IQ IR JO JP KG KH KP KR KW KZ LA LB LK MM MN MO MV MY NP OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE"),
    NORTH_AMERICA("Amérique du Nord", "AG AI AW BB BL BM BQ BS BZ CA CR CU CW DM DO GD GL GP GT HN HT JM KN KY LC MF MQ MS MX NI PA PM PR SV SX TC TT US VC VG VI"),
    SOUTH_AMERICA("Amérique du Sud", "AR BO BR BV CL CO EC FK GF GS GY PE PY SR UY VE"),
    OCEANIA("Océanie", "AS AU CC CK CX FJ FM GU HM KI MH MP NC NF NR NU NZ PF PG PN PW SB TK TO TV UM VU WF WS"),
    ANTARCTICA("Antarctique", "AQ");

    val countryCodes = codes.split(' ').toSet()

    companion object {
        fun forCountry(code: String): WorldContinent? = entries.firstOrNull { code in it.countryCodes }
    }
}
