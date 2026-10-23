package ua.newerp.infrastructure.locations;

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

import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationSpreadsheet;
import ua.newerp.domain.common.DomainException;

/** The .xlsx file through Apache POI (ClosedXML in .NET; the old site wrote XLSXWriter and read SimpleXLSX). */
@Component
class PoiLocationSpreadsheet implements LocationSpreadsheet {

    @Override
    public byte[] write(List<String> header, List<LocationDto> rows) {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Склади");
            XSSFCellStyle bold = book.createCellStyle();
            Font font = book.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row first = sheet.createRow(0);
            for (int c = 0; c < header.size(); c++) {
                Cell cell = first.createCell(c);
                cell.setCellValue(header.get(c));
                cell.setCellStyle(bold);
            }
            for (int r = 0; r < rows.size(); r++) {
                LocationDto x = rows.get(r);
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(x.id());
                // Text goes in as text: the phone "0671234567" must not become a number without the zero.
                String[] values = {x.name(), x.description(), x.contact(), x.telephone(), x.city(), x.address(), x.type()};
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c + 1).setCellValue(values[c]);
                }
            }
            // A fixed width instead of autoSizeColumn, which needs the system's fonts (a server may have none).
            for (int c = 0; c < header.size(); c++) {
                sheet.setColumnWidth(c, (c == 0 ? 8 : 24) * 256);
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
