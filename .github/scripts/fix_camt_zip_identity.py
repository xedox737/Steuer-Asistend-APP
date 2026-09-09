from pathlib import Path

bank = Path('app/src/main/java/com/example/data/BankImportV8.kt')
text = bank.read_text(encoding='utf-8')
old = 'fallbackAccountName = zipFileName.substringBeforeLast(\'.\'),'
new = 'fallbackAccountName = name.substringAfterLast(\'/\').substringBeforeLast(\'.\').ifBlank { "Importiertes Konto" },'
if old not in text:
    raise SystemExit('ZIP fallback anchor not found')
text = text.replace(old, new, 1)
bank.write_text(text, encoding='utf-8')

test = Path('app/src/test/java/com/example/data/BankCamtZipV8AcceptanceTest.kt')
t = test.read_text(encoding='utf-8')
t = t.replace('val direct = BankImportParser.parseCamtV8(camt052(), "SYNTHETIC", "first", "direct.xml")', 'val direct = BankImportParser.parseCamtV8(camt052(), "inside", "first", "inside.xml")')
test.write_text(t, encoding='utf-8')

status = Path('app/src/test/java/com/example/data/BankCamtV8ReimportStatusAcceptanceTest.kt')
s = status.read_text(encoding='utf-8')
s = s.replace('val first = BankImportParser.parseCamtV8(xml, "SYNTHETIC", "first", "direct.xml")', 'val first = BankImportParser.parseCamtV8(xml, "inside", "first", "inside.xml")')
s = s.replace('val directAgain = BankImportParser.parseCamtV8(xml, "SYNTHETIC", "second", "again.xml")', 'val directAgain = BankImportParser.parseCamtV8(xml, "inside", "second", "inside.xml")')
status.write_text(s, encoding='utf-8')
