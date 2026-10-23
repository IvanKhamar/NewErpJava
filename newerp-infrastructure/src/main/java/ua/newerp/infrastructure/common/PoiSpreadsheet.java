package ua.newerp.infrastructure.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ua.newerp.application.common.Spreadsheet;
import ua.newerp.domain.common.DomainException;

/** The .xlsx file through Apache POI (ClosedXML in .NET; the old site wrote XLSXWriter and read SimpleXLSX). */
@Component
class PoiSpreadsheet implements Spreadsheet {

    @Override
    public byte[] write(String sheetName, List<String> header, List<List<Object>> rows) {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet(sheetName);
            XSSFCellStyle bold = book.createCellStyle();
            Font font = book.createFont();
            font.setBold(true);
            bold.setFont(font);
            int[] widths = new int[header.size()];
            Row first = sheet.createRow(0);
            for (int c = 0; c < header.size(); c++) {
                Cell cell = first.createCell(c);
                cell.setCellValue(header.get(c));
                cell.setCellStyle(bold);
                widths[c] = header.get(c).length();
            }
            for (int r = 0; r < rows.size(); r++) {
                List<Object> values = rows.get(r);
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    Cell cell = row.createCell(c);
                    // Text goes in as text: the phone "0671234567" must not become a number without the zero.
                    // "instanceof Number n" is a type pattern, like C#'s "is Number n".
                    if (value instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                    if (c < widths.length && value != null) {
                        widths[c] = Math.max(widths[c], value.toString().length());
                    }
                }
            }
            // A width from the text length instead of autoSizeColumn, which needs the system's fonts (a server may have none).
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, Math.clamp(widths[c] + 2, 8, 60) * 256);
            }
            book.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<List<String>> read(byte[] file) {
        XSSFWorkbook book;
        try {
            book = new XSSFWorkbook(new ByteArrayInputStream(file));
        } catch (IOException | RuntimeException e) {
            throw new DomainException("Не вдалося прочитати файл. Потрібен файл Excel (.xlsx).");
        }

        try (book) {
            Sheet sheet = book.getSheetAt(0);
            var formatter = new DataFormatter();
            int lastColumn = 0;
            for (Row row : sheet) {
                lastColumn = Math.max(lastColumn, row.getLastCellNum());
            }
            var rows = new ArrayList<List<String>>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                var cells = new ArrayList<String>();
                for (int c = 0; c < lastColumn; c++) {
                    cells.add(row == null ? "" : formatter.formatCellValue(row.getCell(c)));
                }
                rows.add(cells);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
