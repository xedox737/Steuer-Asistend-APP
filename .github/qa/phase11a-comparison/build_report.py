#!/usr/bin/env python3
"""Render the reviewed Markdown audit as a standalone, searchable PDF with source hyperlinks."""
import html
from pathlib import Path
import re
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle

FONT_ROOT = Path("/usr/share/fonts/truetype/dejavu")
for name, filename in [("Audit", "DejaVuSans.ttf"), ("Audit-Bold", "DejaVuSans-Bold.ttf"),
                       ("Audit-Italic", "DejaVuSans-Oblique.ttf"), ("AuditMono", "DejaVuSansMono.ttf")]:
    pdfmetrics.registerFont(TTFont(name, str(FONT_ROOT / filename)))
pdfmetrics.registerFontFamily("Audit", normal="Audit", bold="Audit-Bold", italic="Audit-Italic", boldItalic="Audit-Bold")

def inline(text):
    text = html.escape(text)
    text = re.sub(r"\[([^\]]+)\]\((https?://[^\s)]+)\)", r'<link href="\2" color="#15549B">\1</link>', text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"`([^`]+)`", r'<font name="AuditMono">\1</font>', text)
    return text

def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#CCD6E2"))
    canvas.line(45, 37, A4[0]-45, 37)
    canvas.setFont("Audit", 7)
    canvas.setFillColor(colors.HexColor("#526174"))
    canvas.drawString(45, 24, "ImmoPilot · Fehlervergleich · 25.10.2026 · ausschließlich Prüfung")
    canvas.drawRightString(A4[0]-45, 24, f"Seite {doc.page}")
    if doc.page > 1:
        canvas.drawString(45, A4[1]-26, "Original 69f3577  |  Test 8ddd556  |  keine Reparaturen / keine Live-Nutzerdaten")
    canvas.restoreState()

def build(source, target):
    styles = getSampleStyleSheet()
    normal = ParagraphStyle("AuditBody", fontName="Audit", fontSize=9.1, leading=13.5,
                            spaceAfter=7, alignment=TA_LEFT, splitLongWords=True)
    heading = ParagraphStyle("AuditHeading", parent=normal, fontName="Audit-Bold", fontSize=13,
                             leading=17, spaceBefore=14, spaceAfter=9, keepWithNext=True, textColor=colors.HexColor("#123C65"))
    sub = ParagraphStyle("AuditSubheading", parent=heading, fontSize=10.4, leading=14, spaceBefore=10)
    title = ParagraphStyle("AuditTitle", parent=heading, fontSize=23, leading=29, spaceBefore=4, spaceAfter=15)
    bullet = ParagraphStyle("AuditBullet", parent=normal, leftIndent=12, firstLineIndent=0, bulletIndent=0)
    cell = ParagraphStyle("AuditCell", parent=normal, fontSize=7.7, leading=10.5, spaceAfter=0)
    code = ParagraphStyle("AuditCode", parent=normal, fontName="AuditMono", fontSize=7, leading=9)
    lines = source.read_text().splitlines()
    story=[]; i=0
    while i < len(lines):
        line=lines[i].strip()
        if not line:
            i+=1; continue
        if line.startswith("|"):
            rows=[]
            while i < len(lines) and lines[i].strip().startswith("|"):
                values=[v.strip() for v in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r"[-: ]+", v) for v in values):
                    rows.append([Paragraph(inline(v),cell) for v in values])
                i+=1
            n=len(rows[0]); widths={2:[135,370],3:[92,195,218],4:[110,110,170,115],6:[57,76,89,112,42,129]}.get(n)
            if widths is None: widths=[505/n]*n
            table=Table(rows,colWidths=widths,repeatRows=1,hAlign="LEFT")
            table.setStyle(TableStyle([("BACKGROUND",(0,0),(-1,0),colors.HexColor("#DFEAF5")),
                ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white,colors.HexColor("#F5F7FA")]),
                ("VALIGN",(0,0),(-1,-1),"TOP"),("BOX",(0,0),(-1,-1),.4,colors.HexColor("#CBD5E1")),
                ("INNERGRID",(0,0),(-1,-1),.25,colors.HexColor("#D9E0E8")),
                ("LEFTPADDING",(0,0),(-1,-1),5),("RIGHTPADDING",(0,0),(-1,-1),5),
                ("TOPPADDING",(0,0),(-1,-1),6),("BOTTOMPADDING",(0,0),(-1,-1),6)]))
            story += [table,Spacer(1,10)]; continue
        if line.startswith("### "):
            story.append(Paragraph(inline(line[4:]),sub)); i+=1; continue
        if line.startswith("## "):
            story.append(Paragraph(inline(line[3:]),heading)); i+=1; continue
        if line.startswith("# "):
            story.append(Paragraph(inline(line[2:]),title)); i+=1; continue
        if line.startswith("- "):
            story.append(Paragraph(inline(line[2:]),bullet,bulletText="•")); i+=1; continue
        if re.match(r"\d+\. ",line):
            story.append(Paragraph(inline(line),normal)); i+=1; continue
        paragraph=[line]; i+=1
        while i < len(lines) and lines[i].strip() and not lines[i].strip().startswith(("#","|","- ")):
            paragraph.append(lines[i].strip());i+=1
        story.append(Paragraph(inline(" ".join(paragraph)),normal))
    document=SimpleDocTemplate(str(target),pagesize=A4,rightMargin=45,leftMargin=45,topMargin=44,bottomMargin=48,
        title="ImmoPilot-Fehlervergleich-TestApp-vs-Original",author="Repository-Prüfung / Codex",subject="Phase11A: getrennte Ursachenanalyse von main und Test-App; keine Reparaturen")
    document.build(story,onFirstPage=footer,onLaterPages=footer)

if __name__=="__main__":
    build(Path(sys.argv[1]),Path(sys.argv[2]))
