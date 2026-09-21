package com.prospr.service;

import com.prospr.dto.ImportTemplateAnalysisResponse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportTemplateService {

    private static final int PREVIEW_LIMIT = 8;

    private static final Pattern CSV_SEPARATOR =
            Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

    private static final Pattern DATE_AT_START =
            Pattern.compile(
                    "^(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b\\s*(.*)$"
            );

    private static final Pattern AMOUNT =
            Pattern.compile(
                    "([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2}|[0-9]+\\.[0-9]{2})"
            );

    private static final List<String> HEADER_HINTS = List.of(
            "transaction date",
            "value date",
            "narration",
            "description",
            "particular",
            "withdrawal",
            "deposit",
            "debit",
            "credit",
            "balance",
            "reference",
            "cheque",
            "amount",
            "date"
    );

    public ImportTemplateAnalysisResponse analyze(
            MultipartFile file,
            String password
    ) throws IOException {

        String filename = file.getOriginalFilename() == null
                ? "statement"
                : file.getOriginalFilename();

        String lower = filename.toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();

        List<List<String>> table;

        if (lower.endsWith(".csv")) {
            table = csv(bytes);
        } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            table = excel(bytes);
        } else if (lower.endsWith(".pdf")) {
            table = pdf(bytes, password);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file format. Upload CSV, Excel, or PDF."
            );
        }

        if (table == null || table.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No statement columns were detected."
            );
        }

        int headerIndex = findHeaderRow(table);
        List<String> columns = uniqueColumns(table.get(headerIndex));

        if (columns.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No statement columns were detected."
            );
        }

        List<Map<String, String>> rows = new ArrayList<>();

        for (
                int i = headerIndex + 1;
                i < table.size() && rows.size() < PREVIEW_LIMIT;
                i++
        ) {
            List<String> raw = table.get(i);

            if (isBlank(raw)) {
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();

            for (int c = 0; c < columns.size(); c++) {
                String value = c < raw.size() && raw.get(c) != null
                        ? raw.get(c)
                        : "";
                row.put(columns.get(c), value);
            }

            rows.add(row);
        }

        return new ImportTemplateAnalysisResponse(columns, rows);
    }

    private List<List<String>> csv(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8)
                .replace("\uFEFF", "");

        List<List<String>> result = new ArrayList<>();

        for (String line : content.split("\\R")) {
            if (line == null || line.isBlank()) {
                continue;
            }

            List<String> row = new ArrayList<>();

            for (String column : CSV_SEPARATOR.split(line, -1)) {
                row.add(cleanCsvValue(column));
            }

            if (!isBlank(row)) {
                result.add(row);
            }
        }

        return result;
    }

    private String cleanCsvValue(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim();

        if (result.length() >= 2
                && result.startsWith("\"")
                && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }

        return result.replace("\"\"", "\"").trim();
    }

    private List<List<String>> excel(byte[] bytes) throws IOException {
        List<List<String>> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(bytes)
        )) {
            if (workbook.getNumberOfSheets() == 0) {
                return result;
            }

            DataFormatter formatter = new DataFormatter();

            for (Row row : workbook.getSheetAt(0)) {
                int lastCell = row.getLastCellNum();

                if (lastCell < 0) {
                    continue;
                }

                List<String> cells = new ArrayList<>();

                for (int i = 0; i < lastCell; i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null
                            ? ""
                            : formatter.formatCellValue(cell).trim());
                }

                if (!isBlank(cells)) {
                    result.add(cells);
                }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This Excel file could not be read. If it is password protected, export it as CSV first.",
                    ex
            );
        }

        return result;
    }

    private List<List<String>> pdf(byte[] bytes, String password) throws IOException {
        String text;
        String actualPassword = password == null ? "" : password;

        try (PDDocument document = Loader.loadPDF(bytes, actualPassword)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (InvalidPasswordException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    actualPassword.isBlank()
                            ? "This PDF is password protected. Enter the statement password, then click Read statement."
                            : "The statement password is incorrect."
            );
        }

        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The PDF has no selectable text. Export the statement as CSV or Excel."
            );
        }

        String[] lines = text.split("\\R");
        List<List<String>> aligned = alignedPdfTable(lines);

        if (aligned.size() > 1) {
            return aligned;
        }

        return syntheticPdf(lines);
    }

    private List<List<String>> alignedPdfTable(String[] lines) {
        int header = -1;

        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase(Locale.ROOT);

            if (!lower.contains("date")
                    && !lower.contains("narration")
                    && !lower.contains("description")) {
                continue;
            }

            if (headerScore(splitWide(lines[i])) >= 2) {
                header = i;
                break;
            }
        }

        if (header < 0) {
            return List.of();
        }

        List<String> columns = splitWide(lines[header]);

        if (columns.size() < 2) {
            return List.of();
        }

        List<List<String>> table = new ArrayList<>();
        table.add(columns);

        for (int i = header + 1; i < lines.length && table.size() < PREVIEW_LIMIT + 1; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();

            if (line.isBlank() || isNoise(line)) {
                continue;
            }

            List<String> cells = splitWide(line);

            if (isBlank(cells)) {
                continue;
            }

            table.add(fit(cells, columns.size()));
        }

        return table.size() > 1 ? table : List.of();
    }

    private List<List<String>> syntheticPdf(String[] lines) {
        List<List<String>> table = new ArrayList<>();
        table.add(List.of(
                "Date",
                "Description",
                "Withdrawal Amt",
                "Deposit Amt",
                "Closing Balance"
        ));

        String date = null;
        StringBuilder description = new StringBuilder();
        List<String> amounts = new ArrayList<>();

        for (String source : lines) {
            String line = source == null ? "" : source.trim();

            if (line.isBlank() || isNoise(line)) {
                continue;
            }

            Matcher matcher = DATE_AT_START.matcher(line);

            if (matcher.matches()) {
                addSynthetic(table, date, description, amounts);
                date = matcher.group(1);
                description = new StringBuilder(stripAmounts(matcher.group(2)));
                amounts = amountsIn(matcher.group(2));
                continue;
            }

            if (date != null) {
                String extra = stripAmounts(line);

                if (!extra.isBlank()) {
                    if (!description.isEmpty()) {
                        description.append(' ');
                    }
                    description.append(extra);
                }

                amounts.addAll(amountsIn(line));
            }
        }

        addSynthetic(table, date, description, amounts);

        if (table.size() <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No statement columns were detected. Upload CSV or Excel, or a PDF with selectable transaction text."
            );
        }

        return table;
    }

    private void addSynthetic(
            List<List<String>> table,
            String date,
            StringBuilder description,
            List<String> amounts
    ) {
        if (date == null) {
            return;
        }

        String withdrawal = "";
        String deposit = "";
        String balance = "";

        if (amounts.size() >= 3) {
            withdrawal = amounts.get(amounts.size() - 3);
            deposit = amounts.get(amounts.size() - 2);
            balance = amounts.get(amounts.size() - 1);
        } else if (amounts.size() == 2) {
            withdrawal = amounts.get(0);
            balance = amounts.get(1);
        } else if (amounts.size() == 1) {
            balance = amounts.get(0);
        }

        table.add(List.of(
                date,
                description.toString().trim(),
                withdrawal,
                deposit,
                balance
        ));
    }

    private List<String> amountsIn(String value) {
        List<String> amounts = new ArrayList<>();

        if (value == null) {
            return amounts;
        }

        Matcher matcher = AMOUNT.matcher(value);

        while (matcher.find()) {
            amounts.add(matcher.group(1));
        }

        return amounts;
    }

    private String stripAmounts(String value) {
        if (value == null) {
            return "";
        }

        return AMOUNT.matcher(value).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private List<String> splitWide(String line) {
        List<String> cells = new ArrayList<>();

        if (line == null) {
            return cells;
        }

        for (String part : line.trim().split("\\s{2,}")) {
            String cell = part.trim();

            if (!cell.isBlank()) {
                cells.add(cell);
            }
        }

        return cells;
    }

    private List<String> fit(List<String> cells, int width) {
        List<String> fitted = new ArrayList<>();

        for (int i = 0; i < width; i++) {
            fitted.add(i < cells.size() ? cells.get(i) : "");
        }

        if (cells.size() > width && width > 0) {
            StringBuilder extra = new StringBuilder(fitted.get(width - 1));

            for (int i = width; i < cells.size(); i++) {
                extra.append(' ').append(cells.get(i));
            }

            fitted.set(width - 1, extra.toString().trim());
        }

        return fitted;
    }

    private int findHeaderRow(List<List<String>> table) {
        int best = 0;
        int bestScore = -1;
        int limit = Math.min(table.size(), 40);

        for (int i = 0; i < limit; i++) {
            int score = headerScore(table.get(i));

            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }

        if (bestScore >= 2) {
            return best;
        }

        for (int i = 0; i < table.size(); i++) {
            long filled = table.get(i).stream()
                    .filter(cell -> cell != null && !cell.isBlank())
                    .count();

            if (filled >= 2) {
                return i;
            }
        }

        return 0;
    }

    private int headerScore(List<String> row) {
        int score = 0;

        for (String cell : row) {
            String normalized = normalize(cell);

            if (normalized.isBlank()) {
                continue;
            }

            for (String hint : HEADER_HINTS) {
                if (normalized.equals(hint) || normalized.contains(hint)) {
                    score++;
                    break;
                }
            }
        }

        return score;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private List<String> uniqueColumns(List<String> headers) {
        List<String> columns = new ArrayList<>();
        int unnamed = 1;

        for (String header : headers) {
            String name = header == null ? "" : header.trim();

            if (name.isBlank()) {
                name = "Column " + unnamed;
                unnamed++;
            }

            String unique = name;
            int copy = 2;

            while (columns.contains(unique)) {
                unique = name + " (" + copy + ")";
                copy++;
            }

            columns.add(unique);
        }

        while (!columns.isEmpty() && columns.get(columns.size() - 1).startsWith("Column ")) {
            columns.remove(columns.size() - 1);
        }

        return columns;
    }

    private boolean isBlank(List<String> row) {
        if (row == null) {
            return true;
        }

        return row.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    private boolean isNoise(String line) {
        String lower = line.toLowerCase(Locale.ROOT);

        return lower.contains("statement of account")
                || lower.contains("page ")
                || lower.startsWith("page ")
                || lower.contains("this is a computer generated")
                || lower.startsWith("registered office")
                || lower.startsWith("generated on");
    }
}
