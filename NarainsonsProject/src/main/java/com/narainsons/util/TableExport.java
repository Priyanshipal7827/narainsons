package com.narainsons.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Streams a table as CSV or as a real .xlsx file. Text that a spreadsheet would run as a formula
 * (customer names are user input) is neutralised, so opening an export cannot execute anything.
 */
public final class TableExport {

    private final boolean excel;
    private final Writer writer;
    private final ZipOutputStream zip;

    private TableExport(boolean excel, OutputStream out) throws IOException {
        this.excel = excel;
        if (excel) {
            zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
            writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        } else {
            zip = null;
            writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write('﻿'); // Lets Excel read ₹ and Hindi text in a CSV correctly.
        }
    }

    public static TableExport csv(OutputStream out) throws IOException {
        return new TableExport(false, out);
    }

    public static TableExport xlsx(OutputStream out) throws IOException {
        return new TableExport(true, out);
    }

    public void begin(List<String> columns) throws IOException {
        List<Object> headers = new ArrayList<>();
        for (String column : columns) {
            headers.add(headerText(column));
        }
        if (excel) {
            writeZipFile("[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            writeZipFile("_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            writeZipFile("xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="Export" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""");
            writeZipFile("xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            writeZipFile("xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
                    </styleSheet>""");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            writeXlsxRow(headers, true);
        } else {
            writeCsvRow(headers);
        }
    }

    public void row(List<Object> values) throws IOException {
        if (excel) {
            writeXlsxRow(values, false);
        } else {
            writeCsvRow(values);
        }
    }

    public void finish() throws IOException {
        if (excel) {
            writer.write("</sheetData></worksheet>");
            writer.flush();
            zip.closeEntry();
            zip.finish();
        }
        writer.flush();
    }

    /** "requested_amount" becomes "Requested Amount". */
    static String headerText(String column) {
        StringBuilder out = new StringBuilder();
        for (String word : column.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** A leading = + - @ tab or CR makes a spreadsheet treat text as a formula. */
    static String safeText(String text) {
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private void writeCsvRow(List<Object> values) throws IOException {
        List<String> cells = new ArrayList<>();
        for (Object value : values) {
            String text = value == null ? "" : (value instanceof Number ? value.toString() : safeText(value.toString()));
            if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
                text = "\"" + text.replace("\"", "\"\"") + "\"";
            }
            cells.add(text);
        }
        writer.write(String.join(",", cells));
        writer.write("\r\n");
    }

    private void writeXlsxRow(List<Object> values, boolean bold) throws IOException {
        writer.write("<row>");
        String style = bold ? " s=\"1\"" : "";
        for (Object value : values) {
            if (value == null) {
                writer.write("<c/>");
            } else if (value instanceof Number) {
                writer.write(String.format(Locale.ROOT, "<c%s><v>%s</v></c>", style, value));
            } else {
                writer.write("<c" + style + " t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                        + xml(safeText(value.toString())) + "</t></is></c>");
            }
        }
        writer.write("</row>");
    }

    private static String xml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    // XML 1.0 has no way to write other control characters.
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private void writeZipFile(String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        writer.write(content);
        writer.flush();
        zip.closeEntry();
    }
}
