package com.docuinsight.docuinsight.service;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the Markdown tree and builds a styled Word document with Apache POI.
 * Word does not understand HTML, so every heading, bullet and table cell is created by hand.
 */
@Component
public class DocxReportWriter {

    private static final String FONT = "Calibri";
    private static final String CODE_FONT = "Consolas";
    private static final String BRAND = "1F4E79";
    private static final String TEXT = "1F2937";
    private static final String MUTED = "6B7280";
    private static final int LIST_INDENT = 360;

    /** How a piece of text looks; immutable, so each nesting level makes a new copy */
    private record Style(boolean bold, boolean italic, boolean code, int size, String color) {
        Style asBold()   { return new Style(true, italic, code, size, color); }
        Style asItalic() { return new Style(bold, true, code, size, color); }
        Style asCode()   { return new Style(bold, italic, true, size, color); }
        Style withColor(String c) { return new Style(bold, italic, code, size, c); }
    }

    private static final Style BODY = new Style(false, false, false, 11, TEXT);
    private static final Style TABLE_TEXT = new Style(false, false, false, 10, TEXT);

    private final MarkdownRenderer markdownRenderer;

    public DocxReportWriter(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = markdownRenderer;
    }

    public byte[] write(String typeLabel, String title, String meta, String markdown) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            writeHeader(doc, typeLabel, title, meta);

            Node root = markdownRenderer.parse(markdown);
            for (Node block = root.getFirstChild(); block != null; block = block.getNext()) {
                writeBlock(doc, block, 0, 0);
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    // ---------------- header ----------------

    private void writeHeader(XWPFDocument doc, String typeLabel, String title, String meta) {
        XWPFParagraph type = doc.createParagraph();
        type.setBorderTop(Borders.THICK);
        type.setSpacingAfter(60);
        addRun(type, typeLabel.toUpperCase(), new Style(true, false, false, 9, BRAND));

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setSpacingAfter(40);
        addRun(titlePara, title, new Style(true, false, false, 20, "111827"));

        XWPFParagraph metaPara = doc.createParagraph();
        metaPara.setBorderBottom(Borders.SINGLE);
        metaPara.setSpacingAfter(240);
        addRun(metaPara, meta, new Style(false, false, false, 9, MUTED));
    }

    // ---------------- block elements ----------------

    private void writeBlock(XWPFDocument doc, Node node, int depth, int indent) {
        if (node instanceof Heading heading) {
            writeHeading(doc, heading);
        } else if (node instanceof Paragraph paragraph) {
            XWPFParagraph p = bodyParagraph(doc, indent + LIST_INDENT * depth);
            writeInlines(p, paragraph, BODY);
        } else if (node instanceof ListBlock list) {
            writeList(doc, list, depth, indent);
        } else if (node instanceof BlockQuote) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                writeBlock(doc, child, depth, indent + 400);
            }
        } else if (node instanceof FencedCodeBlock code) {
            writeCode(doc, code.getLiteral(), indent);
        } else if (node instanceof IndentedCodeBlock code) {
            writeCode(doc, code.getLiteral(), indent);
        } else if (node instanceof TableBlock table) {
            writeTable(doc, table);
        } else if (node instanceof ThematicBreak) {
            // "---" lines: skip, headings already separate sections
        } else if (node instanceof HtmlBlock html) {
            XWPFParagraph p = bodyParagraph(doc, indent);
            addRun(p, html.getLiteral().trim(), BODY);   // shown as text, never interpreted
        } else {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                writeBlock(doc, child, depth, indent);
            }
        }
    }

    private void writeHeading(XWPFDocument doc, Heading heading) {
        int level = heading.getLevel();
        int size = switch (level) {
            case 1 -> 16;
            case 2 -> 14;
            case 3 -> 12;
            default -> 11;
        };
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(level <= 2 ? 280 : 200);
        p.setSpacingAfter(100);
        if (level == 2) {
            p.setBorderBottom(Borders.SINGLE);
        }
        writeInlines(p, heading, new Style(true, false, false, size, BRAND));
    }

    private void writeList(XWPFDocument doc, ListBlock list, int depth, int indent) {
        boolean ordered = list instanceof OrderedList;
        int number = 1;
        if (list instanceof OrderedList ol && ol.getMarkerStartNumber() != null) {
            number = ol.getMarkerStartNumber();
        }

        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            String marker = ordered ? (number++) + "." : "•";
            boolean first = true;
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                if (first && child instanceof Paragraph paragraph) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(indent + LIST_INDENT * (depth + 1));
                    p.setIndentationHanging(280);           // wrapped lines align with the text, not the bullet
                    p.setSpacingAfter(60);
                    p.setSpacingBetween(1.15);
                    addRun(p, marker, BODY.asBold().withColor(BRAND)).addTab();
                } else if (child instanceof ListBlock nested) {
                    writeList(doc, nested, depth + 1, indent);
                } else {
                    writeBlock(doc, child, depth + 1, indent);
                }
                first = false;
            }
        }
    }

    private void writeCode(XWPFDocument doc, String literal, int indent) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(indent + 240);
        p.setSpacingAfter(120);
        String[] lines = literal.stripTrailing().split("\n");
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = p.createRun();
            run.setText(lines[i]);
            run.setFontFamily(CODE_FONT);
            run.setFontSize(9);
            run.setColor("374151");
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private void writeTable(XWPFDocument doc, TableBlock tableBlock) {
        // TableBlock -> TableHead / TableBody -> TableRow -> TableCell
        List<TableRow> rows = new ArrayList<>();
        for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (row instanceof TableRow tableRow) {
                    rows.add(tableRow);
                }
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        int columns = 0;
        for (TableRow row : rows) {
            int count = 0;
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                count++;
            }
            columns = Math.max(columns, count);
        }

        XWPFTable table = doc.createTable(rows.size(), columns);
        table.setWidth("100%");

        for (int r = 0; r < rows.size(); r++) {
            int c = 0;
            for (Node node = rows.get(r).getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof TableCell cell) {
                    XWPFTableCell wordCell = table.getRow(r).getCell(c);
                    XWPFParagraph p = wordCell.getParagraphs().get(0);
                    if (cell.isHeader()) {
                        wordCell.setColor("F1F5F9");
                        writeInlines(p, cell, TABLE_TEXT.asBold());
                    } else {
                        writeInlines(p, cell, TABLE_TEXT);
                    }
                }
                c++;
            }
        }
        doc.createParagraph();   // breathing space after the table
    }

    // ---------------- inline elements ----------------

    private void writeInlines(XWPFParagraph p, Node parent, Style style) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Text text) {
                addRun(p, text.getLiteral(), style);
            } else if (node instanceof StrongEmphasis) {
                writeInlines(p, node, style.asBold());
            } else if (node instanceof Emphasis) {
                writeInlines(p, node, style.asItalic());
            } else if (node instanceof Code code) {
                addRun(p, code.getLiteral(), style.asCode());
            } else if (node instanceof SoftLineBreak) {
                addRun(p, " ", style);
            } else if (node instanceof HardLineBreak) {
                p.createRun().addBreak();
            } else if (node instanceof Link) {
                writeInlines(p, node, style.withColor(BRAND));
            } else if (node instanceof HtmlInline html) {
                addRun(p, html.getLiteral(), style);
            } else {
                writeInlines(p, node, style);
            }
        }
    }

    // ---------------- helpers ----------------

    private XWPFParagraph bodyParagraph(XWPFDocument doc, int indent) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(indent);
        p.setSpacingAfter(120);
        p.setSpacingBetween(1.15);
        return p;
    }

    private XWPFRun addRun(XWPFParagraph p, String text, Style style) {
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(style.bold());
        run.setItalic(style.italic());
        run.setFontFamily(style.code() ? CODE_FONT : FONT);
        run.setFontSize(style.code() ? style.size() - 1 : style.size());
        if (style.color() != null) {
            run.setColor(style.color());
        }
        return run;
    }
}