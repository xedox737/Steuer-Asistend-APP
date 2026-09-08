from pathlib import Path
p = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
t = p.read_text()
needle = 'import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.stateIn\n'
replacement = 'import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.flowOn\nimport kotlinx.coroutines.flow.stateIn\n'
if needle not in t:
    raise SystemExit('ReceiptViewModel flow import anchor not found')
p.write_text(t.replace(needle, replacement, 1))
