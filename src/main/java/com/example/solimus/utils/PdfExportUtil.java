package com.example.solimus.utils;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Générateur .pdf générique pour les exports (module Finances / fiche copropriétaire) — même
 * mise en forme que ExcelExportUtil (en-tête coloré, lignes zébrées, bordures grises) adaptée au
 * format PDF : page A4, logo SOLIMUS en en-tête si présent sur le classpath (sinon simplement
 * omis, aucune erreur), titre + sous-titre + ligne de filtres libres.
 * Ne dépend d'aucun DTO métier : reçoit une grille de valeurs déjà construite par l'appelant,
 * comme ExcelExportUtil.
 */
public class PdfExportUtil {

    // Chemin classpath du logo — absent du projet pour l'instant, le PDF s'en passe silencieusement ;
    // déposer le fichier à cet emplacement (src/main/resources/static/images/solimus-logo.png)
    // suffira à le faire apparaître, sans autre changement de code
    private static final String LOGO_CLASSPATH = "static/images/solimus-logo.png";

    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(153, 153, 153);
    private static final DeviceRgb HEADER_FONT_COLOR = new DeviceRgb(26, 26, 26);
    private static final DeviceRgb ODD_ROW_COLOR = new DeviceRgb(242, 242, 242);  // 1re, 3e... ligne de données = gris clair
    private static final DeviceRgb EVEN_ROW_COLOR = new DeviceRgb(255, 255, 255); // 2e, 4e... ligne de données = blanc
    private static final DeviceRgb MUTED_TEXT_COLOR = new DeviceRgb(102, 102, 102);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final NumberFormat CURRENCY_FORMAT = buildCurrencyFormat();

    private PdfExportUtil() {
    }

    /**
     * Génère un PDF A4 : en-tête (logo si disponible + titre + sous-titre + ligne de filtres
     * optionnelle) puis un tableau avec les mêmes couleurs/bordures que les exports Excel.
     *
     * @param title                 titre principal (ex : nom du copropriétaire)
     * @param subtitle              sous-titre sous le titre (ex : "Historique des paiements"), null si aucun
     * @param filtersLine           ligne d'info sur les filtres appliqués, null si aucune
     * @param headers               en-têtes de colonnes, dans l'ordre
     * @param rows                  lignes de données — types supportés : String, BigDecimal/Number,
     *                              LocalDate, LocalDateTime, null (cellule vide)
     * @param headerBackgroundHex   couleur de fond des en-têtes de tableau, format "RRGGBB" (sans #)
     * @param currencyColumnIndexes indices (0-based) des colonnes à formater en montant FCFA
     */
    public static byte[] generate(String title, String subtitle, String filtersLine, String[] headers,
                                   List<Object[]> rows, String headerBackgroundHex, Set<Integer> currencyColumnIndexes) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 30, 40, 30);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, title, subtitle, filtersLine, regularFont, boldFont);
            document.add(buildTable(headers, rows, headerBackgroundHex, currencyColumnIndexes, regularFont, boldFont));
            addFooter(document, regularFont);

            document.close();
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la génération du fichier PDF", e);
        }
    }

    private static void addHeader(Document document, String title, String subtitle, String filtersLine,
                                   PdfFont regularFont, PdfFont boldFont) {

        Image logo = loadLogo();
        if (logo != null) {
            logo.setMaxWidth(120);
            logo.setHorizontalAlignment(HorizontalAlignment.LEFT);
            document.add(logo);
        }

        Paragraph brand = new Paragraph("SOLIMUS")
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(MUTED_TEXT_COLOR)
                .setMarginBottom(0);
        document.add(brand);

        Paragraph titleParagraph = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(18)
                .setMarginTop(2)
                .setMarginBottom(subtitle != null && !subtitle.isBlank() ? 0 : 4);
        document.add(titleParagraph);

        if (subtitle != null && !subtitle.isBlank()) {
            Paragraph subtitleParagraph = new Paragraph(subtitle)
                    .setFont(regularFont)
                    .setFontSize(11)
                    .setFontColor(MUTED_TEXT_COLOR)
                    .setMarginTop(0)
                    .setMarginBottom(4);
            document.add(subtitleParagraph);
        }

        if (filtersLine != null && !filtersLine.isBlank()) {
            Paragraph filters = new Paragraph(filtersLine)
                    .setFont(regularFont)
                    .setFontSize(9)
                    .setFontColor(MUTED_TEXT_COLOR)
                    .setMarginBottom(10);
            document.add(filters);
        }
    }

    // Logo optionnel : absent du classpath => null, toute erreur de lecture est aussi silencieusement ignorée
    private static Image loadLogo() {
        try (InputStream in = PdfExportUtil.class.getClassLoader().getResourceAsStream(LOGO_CLASSPATH)) {
            if (in == null) return null;
            return new Image(ImageDataFactory.create(in.readAllBytes()));
        } catch (Exception e) {
            return null;
        }
    }

    private static Table buildTable(String[] headers, List<Object[]> rows, String headerBackgroundHex,
                                     Set<Integer> currencyColumnIndexes, PdfFont regularFont, PdfFont boldFont) {

        Table table = new Table(UnitValue.createPercentArray(headers.length)).useAllAvailableWidth();
        DeviceRgb headerBg = hexToColor(headerBackgroundHex);

        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header).setFont(boldFont).setFontColor(HEADER_FONT_COLOR).setFontSize(9))
                    .setBackgroundColor(headerBg)
                    .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                    .setPadding(5);
            table.addHeaderCell(cell);
        }

        for (int i = 0; i < rows.size(); i++) {
            Object[] rowData = rows.get(i);
            DeviceRgb rowColor = (i % 2 == 0) ? ODD_ROW_COLOR : EVEN_ROW_COLOR; // 1re ligne (index 0) = impaire = grise
            for (int col = 0; col < rowData.length; col++) {
                boolean isCurrency = currencyColumnIndexes.contains(col);
                Cell cell = new Cell()
                        .add(new Paragraph(formatCellValue(rowData[col], isCurrency)).setFont(regularFont).setFontSize(9))
                        .setBackgroundColor(rowColor)
                        .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                        .setPadding(5);
                if (isCurrency) {
                    cell.setTextAlignment(TextAlignment.RIGHT);
                }
                table.addCell(cell);
            }
        }

        return table;
    }

    private static void addFooter(Document document, PdfFont regularFont) {
        Paragraph footer = new Paragraph("Généré le " + LocalDateTime.now().format(DATETIME_FORMATTER))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(MUTED_TEXT_COLOR)
                .setMarginTop(15)
                .setTextAlignment(TextAlignment.RIGHT);
        document.add(footer);
    }

    private static String formatCellValue(Object value, boolean currency) {
        if (value == null) return "";
        if (currency && value instanceof BigDecimal bd) return CURRENCY_FORMAT.format(bd) + " FCFA";
        if (currency && value instanceof Number n) return CURRENCY_FORMAT.format(n) + " FCFA";
        if (value instanceof LocalDateTime ldt) return ldt.format(DATETIME_FORMATTER);
        if (value instanceof LocalDate ld) return ld.format(DATE_FORMATTER);
        return value.toString();
    }

    private static NumberFormat buildCurrencyFormat() {
        NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
        format.setMaximumFractionDigits(0);
        return format;
    }

    private static DeviceRgb hexToColor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new DeviceRgb(r, g, b);
    }
}
