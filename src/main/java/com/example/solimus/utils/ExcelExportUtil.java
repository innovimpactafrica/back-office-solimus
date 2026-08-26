package com.example.solimus.utils;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Générateur .xlsx générique pour les exports du module Finances — mise en forme commune
 * (en-tête coloré, lignes zébrées, bordures grises, format monétaire sur les colonnes montant)
 * partagée entre les exports Paiements/Impayés/Transactions pour ne pas dupliquer le style.
 * Ne dépend d'aucun DTO métier : reçoit une grille de valeurs déjà construite par l'appelant.
 */
public class ExcelExportUtil {

    private static final String BORDER_COLOR_HEX = "999999";
    private static final String HEADER_FONT_COLOR_HEX = "1A1A1A";
    private static final String EVEN_ROW_COLOR_HEX = "FFFFFF"; // ligne paire (2e, 4e, ...) = blanc
    private static final String ODD_ROW_COLOR_HEX = "F2F2F2";  // ligne impaire (1re, 3e, ...) = gris clair
    private static final String CURRENCY_FORMAT = "#,##0\" FCFA\"";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Largeur de colonne min/max (en caractères) utilisée à défaut d'auto-size — autoSizeColumn()
    // dépend d'AWT/fontconfig, absent des images Docker minimales utilisées en production
    private static final int MIN_COLUMN_WIDTH_CHARS = 14;
    private static final int MAX_COLUMN_WIDTH_CHARS = 40;

    private ExcelExportUtil() {
    }

    /**
     * Génère un classeur .xlsx à une feuille à partir d'une grille de données.
     *
     * @param sheetName             nom de l'onglet
     * @param headers               en-têtes de colonnes, dans l'ordre
     * @param rows                  lignes de données — chaque Object[] a la même taille que headers ;
     *                              types de cellule supportés : String, BigDecimal/Number, LocalDate,
     *                              LocalDateTime, null (cellule vide)
     * @param headerBackgroundHex   couleur de fond des en-têtes, format "RRGGBB" (sans #)
     * @param currencyColumnIndexes indices (0-based) des colonnes à formater en montant
     */
    public static byte[] generate(String sheetName, String[] headers, List<Object[]> rows,
                                   String headerBackgroundHex, Set<Integer> currencyColumnIndexes) {

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            CellStyle headerStyle = buildHeaderStyle(workbook, headerBackgroundHex);
            CellStyle evenStyle = buildDataStyle(workbook, EVEN_ROW_COLOR_HEX, false);
            CellStyle oddStyle = buildDataStyle(workbook, ODD_ROW_COLOR_HEX, false);
            CellStyle evenCurrencyStyle = buildDataStyle(workbook, EVEN_ROW_COLOR_HEX, true);
            CellStyle oddCurrencyStyle = buildDataStyle(workbook, ODD_ROW_COLOR_HEX, true);

            // En-têtes
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            // Données — alternance basée sur le numéro de ligne 1-based (1re ligne de données = impaire = grise)
            int excelRowIndex = 1;
            for (int i = 0; i < rows.size(); i++) {
                Object[] rowData = rows.get(i);
                boolean isEvenDataRow = (i + 1) % 2 == 0;

                Row row = sheet.createRow(excelRowIndex++);
                for (int col = 0; col < rowData.length; col++) {
                    Cell cell = row.createCell(col);
                    writeCellValue(cell, rowData[col]);
                    boolean isCurrency = currencyColumnIndexes.contains(col);
                    cell.setCellStyle(isCurrency
                            ? (isEvenDataRow ? evenCurrencyStyle : oddCurrencyStyle)
                            : (isEvenDataRow ? evenStyle : oddStyle));
                }
            }

            for (int col = 0; col < headers.length; col++) {
                int width = Math.max(MIN_COLUMN_WIDTH_CHARS, Math.min(MAX_COLUMN_WIDTH_CHARS, headers[col].length() + 4));
                sheet.setColumnWidth(col, width * 256);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    private static void writeCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof LocalDateTime ldt) {
            cell.setCellValue(ldt.format(DATETIME_FORMATTER));
        } else if (value instanceof LocalDate ld) {
            cell.setCellValue(ld.format(DATE_FORMATTER));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static CellStyle buildHeaderStyle(XSSFWorkbook workbook, String backgroundHex) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(hexToColor(backgroundHex));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(style);

        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(hexToColor(HEADER_FONT_COLOR_HEX));
        style.setFont(font);

        return style;
    }

    private static CellStyle buildDataStyle(XSSFWorkbook workbook, String backgroundHex, boolean currency) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(hexToColor(backgroundHex));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorder(style);
        if (currency) {
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat(CURRENCY_FORMAT));
        }
        return style;
    }

    private static void applyBorder(XSSFCellStyle style) {
        XSSFColor borderColor = hexToColor(BORDER_COLOR_HEX);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(borderColor);
        style.setBottomBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);
    }

    private static XSSFColor hexToColor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
    }
}
