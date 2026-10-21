package com.pgds.reports;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void writesHeaderAndRows() {
        String csv = CsvWriter.toCsv(List.of("a", "b"), List.of(Arrays.asList("x", 5), Arrays.asList("y", null)));
        assertEquals("a,b\r\nx,5\r\ny,\r\n", csv);
    }

    @Test
    void quotesCommasAndQuotes() {
        assertEquals("\"Smith, John\"", CsvWriter.escape("Smith, John"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertEquals("'=SUM(A1:A2)", CsvWriter.escape("=SUM(A1:A2)"));
        assertEquals("'@cmd", CsvWriter.escape("@cmd"));
    }

    @Test
    void negativeNumbersAreNotAltered() {
        assertEquals("-5", CsvWriter.escape(-5));
    }
}
