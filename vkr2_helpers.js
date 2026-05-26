'use strict';
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  Footer, AlignmentType, LevelFormat, BorderStyle, WidthType, ShadingType,
  VerticalAlign, PageNumber, PageBreak, HeadingLevel
} = require('docx');
const fs = require('fs');
const path = require('path');

// ── Page / Margin constants (DXA) ────────────────────────────────────────────
// Matches sample: top=1134, right=851, bottom=1134, left=1701  (A4)
const PAGE_W = 11906, PAGE_H = 16838;
const MAR_L = 1701, MAR_R = 851, MAR_T = 1134, MAR_B = 1134;
const CONTENT_W = PAGE_W - MAR_L - MAR_R; // 9354 DXA

// ── Typography ────────────────────────────────────────────────────────────────
// 1 DXA = 1/20 pt  →  14pt = 280 half-pts (sz)  →  28 half-pts as docx "sz"
// docx library "sz" is in half-points: 14pt = 28, 12pt = 24, 13pt = 26
const FONT = 'Times New Roman';
const SZ   = 28;   // 14pt
const SZ12 = 24;   // 12pt
const SZ10 = 20;   // 10pt
const LINE = { line: 360, lineRule: 'auto' };   // 1.5 interline
const LINE_SNG = { line: 240, lineRule: 'auto' }; // single
const INDENT_FIRST = { firstLine: 709 };  // ~1.25cm first-line
const JC_BOTH = AlignmentType.BOTH;
const JC_CEN  = AlignmentType.CENTER;
const JC_LEFT = AlignmentType.LEFT;
const JC_RGT  = AlignmentType.RIGHT;
const JC_RIGHT = AlignmentType.RIGHT;

// ── Run factory ───────────────────────────────────────────────────────────────
function run(text, opts = {}) {
  return new TextRun({
    text,
    font: FONT,
    size: opts.size  || SZ,
    bold: opts.bold  || false,
    italics: opts.italics || false,
    allCaps: opts.caps || false,
    color: opts.color || '000000',
    break: opts.break || undefined,
  });
}

// ── Paragraph helpers ─────────────────────────────────────────────────────────

// Main body paragraph (осн_текст): TNR 14, 1.5x, firstLine indent, justified
function p(text, opts = {}) {
  const children = Array.isArray(text) ? text : [run(text, opts)];
  return new Paragraph({
    children,
    alignment: opts.alignment || JC_BOTH,
    spacing: opts.spacing || LINE,
    indent: opts.indent !== undefined ? opts.indent : INDENT_FIRST,
  });
}

// Centered paragraph, no indent
function pc(text, opts = {}) {
  return p(text, { ...opts, alignment: JC_CEN, indent: {} });
}

// Empty line
function empty() {
  return new Paragraph({ children: [run('')], spacing: LINE });
}

// Heading 1 — CAPS, bold, page break before, no indent, centered
function h1(text) {
  return new Paragraph({
    children: [run(text, { bold: true, caps: true })],
    heading: HeadingLevel.HEADING_1,
    pageBreakBefore: true,
    alignment: JC_CEN,
    spacing: { ...LINE, after: 180 },
    indent: {},
  });
}

// Heading 2 (sub-heading 1.1) — bold, no caps, no page break, left
function h2(text) {
  return new Paragraph({
    children: [run(text, { bold: true })],
    heading: HeadingLevel.HEADING_2,
    pageBreakBefore: false,
    alignment: JC_BOTH,
    spacing: { before: 360, after: 120, ...LINE },
    indent: {},
  });
}

// Figure caption — centered, TNR 14, spacing after=240
function figCaption(text) {
  return new Paragraph({
    children: [run(text, { bold: false })],
    alignment: JC_CEN,
    spacing: { after: 240, ...LINE },
    indent: {},
  });
}

// Table caption — left, keepNext
function tblCaption(text) {
  return new Paragraph({
    children: [run(text, { bold: false })],
    alignment: JC_LEFT,
    spacing: { before: 360, after: 0 },
    indent: {},
    keepNext: true,
  });
}

// Bullet list item (маркированный), em-dash
function li(text) {
  return new Paragraph({
    children: [run('–  ' + text)],
    alignment: JC_BOTH,
    spacing: { ...LINE, before: 0, after: 0 },
    indent: { left: 709, hanging: 355 },
  });
}

// Numbered list item
let _numCounter = 0;
function liN(text, reset = false) {
  if (reset) _numCounter = 0;
  _numCounter++;
  return new Paragraph({
    children: [run(String(_numCounter) + '. ' + text)],
    alignment: JC_BOTH,
    spacing: { ...LINE, before: 0, after: 0 },
    indent: { left: 709, hanging: 709 },
  });
}

// Code block paragraph (Courier New, no indent)
function code(text) {
  return new Paragraph({
    children: [new TextRun({ text, font: 'Courier New', size: 20, color: '1a1a1a' })],
    alignment: JC_LEFT,
    spacing: { line: 240, lineRule: 'auto' },
    indent: { left: 284 },
  });
}

// ── Table helpers ─────────────────────────────────────────────────────────────
const brd = { style: BorderStyle.SINGLE, size: 4, color: '000000' };
const borders = { top: brd, bottom: brd, left: brd, right: brd,
                  insideH: brd, insideV: brd };

function tc(text, opts = {}) {
  const isHeader = opts.header || false;
  const shading  = isHeader
    ? { fill: '1F497D', type: ShadingType.CLEAR }
    : (opts.shade ? { fill: 'F2F2F2', type: ShadingType.CLEAR }
                  : { fill: 'FFFFFF', type: ShadingType.CLEAR });
  return new TableCell({
    borders,
    width: { size: opts.width || 2000, type: WidthType.DXA },
    shading,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      children: [new TextRun({
        text,
        font: FONT,
        size: SZ12,
        bold: isHeader,
        color: isHeader ? 'FFFFFF' : '000000',
      })],
      alignment: opts.align || JC_LEFT,
      spacing: LINE_SNG,
      indent: {},
    })],
  });
}

// ── Image helper ──────────────────────────────────────────────────────────────
// docx v9: ImageRun transformation width/height in EMU
// Content width ~9354 DXA = 9354 * 635 EMU ≈ 5,939,490 EMU (use 5 900 000)
function img(filePath, wEMU, hEMU, altText) {
  const data = fs.readFileSync(filePath);
  const ext  = path.extname(filePath).replace('.', '').toLowerCase();
  return new Paragraph({
    children: [new ImageRun({
      type: ext === 'jpg' ? 'jpeg' : ext,
      data,
      transformation: { width: wEMU, height: hEMU },
      altText: { title: altText, description: altText, name: altText },
    })],
    alignment: JC_CEN,
    spacing: { before: 120, after: 120 },
    indent: {},
  });
}

const IMG_W = 5_900_000;  // full content width in EMU (~16.5 cm)

// ── Page-number footer ────────────────────────────────────────────────────────
const footer = new Footer({
  children: [new Paragraph({
    children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: SZ })],
    alignment: JC_CEN,
  })],
});

