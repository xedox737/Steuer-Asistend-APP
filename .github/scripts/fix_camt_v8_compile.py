from pathlib import Path

bank = Path('app/src/main/java/com/example/data/BankImportV8.kt')
text = bank.read_text(encoding='utf-8')
text = text.replace('import javax.xml.XMLConstants\n', '')
text = text.replace('factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")', 'factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")')
text = text.replace('factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")', 'factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")')
bank.write_text(text, encoding='utf-8')

vm = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
text = vm.read_text(encoding='utf-8')
anchor = 'package com.example.ui\n\n'
if 'import com.example.data.parseCamtV8\n' not in text:
    text = text.replace(anchor, anchor + 'import com.example.data.parseCamtV8\n', 1)
vm.write_text(text, encoding='utf-8')
