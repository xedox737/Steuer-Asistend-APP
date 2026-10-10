package com.example.ui

import com.example.data.Receipt

internal data class ReceiptUnitSelection(val unitId: String = "", val name: String = "")

/** IDs are authoritative for explicit choices; legacy names are scoped to one property. */
internal object ReceiptUnitResolver {
    fun isGeneral(name: String): Boolean = name.isBlank() ||
        name.equals("Gesamtobjekt / Allgemein", true) || name.equals("GESAMT", true) || name.equals("ALLG", true)

    fun resolve(
        name: String, existingUnitId: String, units: List<WohneinheitStatus>, selectedUnitId: String? = null
    ): ReceiptUnitSelection {
        if (selectedUnitId != null) {
            return units.singleOrNull { it.unitId == selectedUnitId && selectedUnitId.isNotBlank() }
                ?.let { ReceiptUnitSelection(it.unitId, it.name) } ?: ReceiptUnitSelection()
        }
        if (isGeneral(name)) return ReceiptUnitSelection()
        if (existingUnitId.isNotBlank()) {
            return units.singleOrNull { it.unitId == existingUnitId }
                ?.let { ReceiptUnitSelection(it.unitId, it.name) } ?: ReceiptUnitSelection()
        }
        return units.filter { it.name.equals(name, true) || it.label.equals(name, true) }.singleOrNull()
            ?.let { ReceiptUnitSelection(it.unitId, it.name) } ?: ReceiptUnitSelection()
    }

    fun error(receipt: Receipt, units: List<WohneinheitStatus>): String? {
        if (receipt.unitId.isBlank()) return null // Existing object-only/legacy records stay compatible.
        val unit = units.singleOrNull { it.unitId == receipt.unitId }
            ?: return "Die Einheit gehört nicht zur gewählten Immobilie. Bitte die Belegzuordnung erneut prüfen."
        val named = units.filter { it.name.equals(receipt.wohneinheit, true) || it.label.equals(receipt.wohneinheit, true) }
        return if (isGeneral(receipt.wohneinheit) || named.any { it.unitId != unit.unitId })
            "Einheitsname und gespeicherte Einheit widersprechen sich. Bitte die Belegzuordnung erneut prüfen."
        else null // An old name with no current match can be a rename of the same stable ID.
    }
}
