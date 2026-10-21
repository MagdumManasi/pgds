package com.pgds.reports;

import java.util.List;

/** Minimal RFC 4180 CSV writer with protection against spreadsheet formula injection. */
public final class CsvWriter {
    private CsvWriter() {}

    public static String toCsv(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header.stream().map(CsvWriter::escape).toList())).append("\r\n");
        for (List<Object> row : rows)
            sb.append(String.join(",", row.stream().map(CsvWriter::escape).toList())).append("\r\n");
        return sb.toString();
    }

    static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        // text starting with = + - @ would be executed as a formula by Excel; numbers are left alone
        if (value instanceof CharSequence && !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
