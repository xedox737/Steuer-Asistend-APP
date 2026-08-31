from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'{label}: anchor not found in {path}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

# ReceiptViewModel: include supplemental data in every full Drive sync.
vm = 'app/src/main/java/com/example/ui/ReceiptViewModel.kt'
replace_once(
    vm,
    '                val backupSuccess = drivePersistenceRepository.saveStammdatenToDrive(token, config)\n\n                // 5. Upload each receipt',
    '                val backupSuccess = drivePersistenceRepository.saveStammdatenToDrive(token, config)\n                val supplementalBackup = com.example.data.SupplementalDriveBackup.backup(\n                    getApplication(), database, token, config.systemFolderId\n                )\n\n                // 5. Upload each receipt',
    'supplemental sync insertion'
)
replace_once(
    vm,
    '                if (csvSuccess && unitsSuccess && backupSuccess) {\n                    _driveSyncStatus.value = "Erfolgreich! Hauptbuch, Wohneinheiten, Stammdaten & $successCount Belege synchronisiert."',
    '                if (csvSuccess && unitsSuccess && backupSuccess && supplementalBackup.success) {\n                    _driveSyncStatus.value = "Erfolgreich! Hauptbuch, Wohneinheiten, Stammdaten, Miet-/Darlehensdaten & $successCount Belege synchronisiert."',
    'sync success condition'
)
replace_once(
    vm,
    '                    val report = drivePersistenceRepository.executeFullDriveRestore(token, initResult.config, mode)\n                    _restoreReport.value = report',
    '                    val report = drivePersistenceRepository.executeFullDriveRestore(token, initResult.config, mode)\n                    val supplementalRestore = com.example.data.SupplementalDriveBackup.restore(\n                        getApplication(), database, token, initResult.config.systemFolderId\n                    )\n                    _restoreReport.value = report',
    'supplemental restore insertion'
)
replace_once(
    vm,
    '                    _driveSyncStatus.value = if (report.isSuccess) {\n                        "Wiederherstellung erfolgreich! ${report.receiptsRestored} Belege geladen."\n                    } else {\n                        "Wiederherstellung mit ${report.errorCount} Fehlern abgeschlossen."\n                    }',
    '                    _driveSyncStatus.value = if (report.isSuccess && supplementalRestore.success) {\n                        "Wiederherstellung erfolgreich! ${report.receiptsRestored} Belege sowie Miet- und Darlehensdaten geladen."\n                    } else if (report.isSuccess) {\n                        "Belege wiederhergestellt, aber Zusatzdaten konnten nicht vollständig geladen werden: ${supplementalRestore.message}"\n                    } else {\n                        "Wiederherstellung mit ${report.errorCount} Fehlern abgeschlossen."\n                    }',
    'restore status'
)

# Loan feature: sync only after the database mutation really finished.
loan = 'app/src/main/java/com/example/ui/LoanFeature.kt'
replace_once(loan, 'fun LoanManagementSection() {', 'fun LoanManagementSection(viewModel: ReceiptViewModel) {', 'loan signature')
replace_once(
    loan,
    '        editor.apply()\n        assignmentVersion++',
    '        editor.apply()\n        assignmentVersion++\n        viewModel.syncAllToDrive()',
    'interest assignment sync'
)
replace_once(
    loan,
    '            onSave = { loan -> scope.launch { database.loanDao().upsertLoan(loan) }; showNewLoan = false }',
    '            onSave = { loan -> scope.launch { database.loanDao().upsertLoan(loan); viewModel.syncAllToDrive() }; showNewLoan = false }',
    'new loan sync'
)
replace_once(
    loan,
    '            onSave = { updated -> scope.launch { database.loanDao().upsertLoan(updated) }; editingLoan = null }',
    '            onSave = { updated -> scope.launch { database.loanDao().upsertLoan(updated); viewModel.syncAllToDrive() }; editingLoan = null }',
    'edit loan sync'
)
replace_once(
    loan,
    '                        assignments.filterValues { it == loan.id }.keys.forEach { assignReceipt(it, null) }\n                        scope.launch { database.loanDao().deleteLoan(loan.id) }\n                        pendingDelete = null',
    '                        assignments.filterValues { it == loan.id }.keys.forEach { assignReceipt(it, null) }\n                        scope.launch { database.loanDao().deleteLoan(loan.id); viewModel.syncAllToDrive() }\n                        pendingDelete = null',
    'delete loan sync'
)

# Dashboard passes the ViewModel into the loan feature.
replace_once(
    'app/src/main/java/com/example/ui/ReceiptAppUi.kt',
    '        LoanManagementSection()',
    '        LoanManagementSection(viewModel)',
    'dashboard loan call'
)

# Rent plan: persist NK/extras first, then update the unit; updateWohneinheit triggers the Drive sync.
rent = 'app/src/main/java/com/example/ui/RentIncomeFeature.kt'
replace_once(
    rent,
    '            onSave = { kalt, nk, other, start ->\n                viewModel.updateWohneinheit(unit.copy(kaltmiete = kalt, mietvertragsstart = start))\n                prefs.edit()\n                    .putFloat("nk_${unit.name}", nk.toFloat())\n                    .putFloat("other_${unit.name}", other.toFloat())\n                    .apply()\n                prefsVersion++',
    '            onSave = { kalt, nk, other, start ->\n                prefs.edit()\n                    .putFloat("nk_${unit.name}", nk.toFloat())\n                    .putFloat("other_${unit.name}", other.toFloat())\n                    .apply()\n                viewModel.updateWohneinheit(unit.copy(kaltmiete = kalt, mietvertragsstart = start))\n                prefsVersion++',
    'rent plan ordering'
)

# Tenant change: TenantHistoryStore is already saved before callback. Persist rent plan first,
# then update current unit so the automatic Drive sync captures both.
wrapper = 'app/src/main/java/com/example/ui/RentTenantWrapper.kt'
replace_once(
    wrapper,
    '            onCurrentTenantChanged = { newPeriod ->\n                viewModel.updateWohneinheit(\n                    unit.copy(\n                        status = "Vermietet",\n                        mieter = newPeriod.tenantName,\n                        kaltmiete = newPeriod.kaltmiete,\n                        mietvertragsstart = newPeriod.startDate\n                    )\n                )\n                rentPrefs.edit()\n                    .putFloat("nk_${unit.name}", newPeriod.nebenkosten.toFloat())\n                    .putFloat("other_${unit.name}", newPeriod.sonstige.toFloat())\n                    .apply()',
    '            onCurrentTenantChanged = { newPeriod ->\n                rentPrefs.edit()\n                    .putFloat("nk_${unit.name}", newPeriod.nebenkosten.toFloat())\n                    .putFloat("other_${unit.name}", newPeriod.sonstige.toFloat())\n                    .apply()\n                viewModel.updateWohneinheit(\n                    unit.copy(\n                        status = "Vermietet",\n                        mieter = newPeriod.tenantName,\n                        kaltmiete = newPeriod.kaltmiete,\n                        mietvertragsstart = newPeriod.startDate\n                    )\n                )',
    'tenant change ordering'
)
