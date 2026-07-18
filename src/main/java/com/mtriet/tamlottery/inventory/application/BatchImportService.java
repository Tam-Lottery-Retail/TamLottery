package com.mtriet.tamlottery.inventory.application;

import com.mtriet.tamlottery.common.exception.BusinessException;
import com.mtriet.tamlottery.common.exception.ErrorCode;
import com.mtriet.tamlottery.inventory.api.InventoryDtos;
import com.mtriet.tamlottery.masterdata.domain.LotteryRegion;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
public class BatchImportService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 500;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("M/d/uu"));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm"),
            DateTimeFormatter.ofPattern("d-M-uuuu H:mm"),
            DateTimeFormatter.ofPattern("M/d/uu H:mm"));

    public InventoryDtos.BatchImportPreviewResponse preview(MultipartFile file) {
        validateFile(file);
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "import");
        try {
            return preview(fileName, file.getBytes());
        } catch (IOException exception) {
            throw invalid("Không thể đọc file: " + exception.getMessage());
        }
    }

    public InventoryDtos.BatchImportPreviewResponse preview(String fileName, byte[] content) {
        if (content == null || content.length == 0) throw invalid("Vui lòng chọn file CSV hoặc XLSX");
        if (content.length > MAX_FILE_SIZE) throw invalid("File vượt quá giới hạn 5 MB");
        fileName = blank(fileName) ? "import" : fileName;
        String extension = extension(fileName);
        List<Map<String, String>> rows;
        try {
            rows = switch (extension) {
                case "csv" -> readCsv(content);
                case "xlsx" -> readXlsx(content);
                default -> throw invalid("Chỉ hỗ trợ file .csv hoặc .xlsx");
            };
        } catch (IOException exception) {
            throw invalid("Không thể đọc file: " + exception.getMessage());
        }
        if (rows.isEmpty()) {
            throw invalid("File không có dòng dữ liệu nào");
        }

        List<String> warnings = new ArrayList<>();
        String agencyCode = sharedValue(rows, "agencyCode", "mã đại lý", warnings);
        String receiptCode = sharedValue(rows, "receiptCode", "mã phiếu nhận", warnings);
        String businessDateRaw = sharedValue(rows, "businessDate", "ngày bán", warnings);
        String receivedAtRaw = sharedValue(rows, "receivedAt", "thời điểm nhận", warnings);
        String note = sharedValue(rows, "note", "ghi chú", warnings);

        if (blank(agencyCode)) warnings.add("File chưa có mã đại lý; hãy chọn đại lý trước khi lưu.");
        if (blank(receiptCode)) warnings.add("File chưa có mã phiếu nhận; hãy nhập trước khi lưu.");
        if (blank(businessDateRaw)) warnings.add("File chưa có ngày bán; hệ thống dùng ngày hiện tại trên form.");
        if (blank(receivedAtRaw)) warnings.add("File chưa có thời điểm nhận; hệ thống dùng thời điểm hiện tại trên form.");

        List<InventoryDtos.BatchLineRequest> lines = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            int rowNumber = index + 2;
            String issuerName = required(row, "issuerName", rowNumber, "đơn vị phát hành");
            String provinceCode = required(row, "provinceCode", rowNumber, "mã tỉnh/đài");
            LotteryRegion region = parseRegion(required(row, "region", rowNumber, "khu vực"), rowNumber);
            LocalDate drawDate = parseDate(required(row, "drawDate", rowNumber, "ngày quay"), rowNumber, "ngày quay");
            Instant returnCutoffAt = parseInstant(
                    required(row, "returnCutoffAt", rowNumber, "hạn trả vé"), rowNumber, "hạn trả vé");
            int quantity = parsePositiveInt(required(row, "quantityReceived", rowNumber, "số lượng"), rowNumber);
            long unitCost = parseLong(required(row, "unitCost", rowNumber, "giá nhập"), rowNumber, "giá nhập", true);
            long unitSalePrice = parseLong(
                    required(row, "unitSalePrice", rowNumber, "giá bán"), rowNumber, "giá bán", false);
            lines.add(new InventoryDtos.BatchLineRequest(
                    issuerName, provinceCode, region, drawDate, returnCutoffAt, quantity, unitCost,
                    unitSalePrice, nullable(row.get("serialFrom")), nullable(row.get("serialTo"))));
        }

        return new InventoryDtos.BatchImportPreviewResponse(
                fileName,
                nullable(agencyCode),
                nullable(receiptCode),
                blank(businessDateRaw) ? null : parseDate(businessDateRaw, 1, "ngày bán"),
                blank(receivedAtRaw) ? null : parseInstant(receivedAtRaw, 1, "thời điểm nhận"),
                nullable(note),
                List.copyOf(lines),
                List.copyOf(warnings));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Vui lòng chọn file CSV hoặc XLSX");
        if (file.getSize() > MAX_FILE_SIZE) throw invalid("File vượt quá giới hạn 5 MB");
    }

    private List<Map<String, String>> readCsv(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .get();
        List<Map<String, String>> rows = new ArrayList<>();
        try (var parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                record.toMap().forEach((header, value) -> putKnown(row, header, value));
                addRow(rows, row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> readXlsx(byte[] bytes) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) return rows;
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(cellText(headerRow.getCell(column)));
            }
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row source = sheet.getRow(rowIndex);
                if (source == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    putKnown(row, headers.get(column), cellText(source.getCell(column)));
                }
                addRow(rows, row);
            }
        }
        return rows;
    }

    private void addRow(List<Map<String, String>> rows, Map<String, String> row) {
        if (row.values().stream().allMatch(BatchImportService::blank)) return;
        if (rows.size() >= MAX_ROWS) throw invalid("File chỉ được chứa tối đa 500 dòng dữ liệu");
        rows.add(row);
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return new DataFormatter(Locale.US).formatCellValue(cell).trim();
    }

    private void putKnown(Map<String, String> row, String header, String value) {
        String key = fieldName(header);
        if (key != null) row.put(key, value == null ? "" : value.trim());
    }

    private String fieldName(String header) {
        return switch (canonical(header)) {
            case "agencycode", "madaily" -> "agencyCode";
            case "receiptcode", "maphieu", "maphieunhan" -> "receiptCode";
            case "businessdate", "ngayban" -> "businessDate";
            case "receivedat", "thoidiemnhan", "ngaynhan" -> "receivedAt";
            case "note", "ghichu" -> "note";
            case "issuername", "donviphathanh", "tenphathanh", "dai" -> "issuerName";
            case "provincecode", "matinh", "madai" -> "provinceCode";
            case "region", "mien", "khuvuc" -> "region";
            case "drawdate", "ngayquay" -> "drawDate";
            case "returncutoffat", "hantra", "hantrave" -> "returnCutoffAt";
            case "quantityreceived", "soluong", "soluongnhan" -> "quantityReceived";
            case "unitcost", "gianhap" -> "unitCost";
            case "unitsaleprice", "giaban" -> "unitSalePrice";
            case "serialfrom", "tuserial", "sotuso" -> "serialFrom";
            case "serialto", "denserial", "sodenso" -> "serialTo";
            default -> null;
        };
    }

    private String sharedValue(List<Map<String, String>> rows, String field, String label, List<String> warnings) {
        List<String> values = rows.stream().map(row -> row.get(field)).filter(value -> !blank(value)).distinct().toList();
        if (values.size() > 1) warnings.add("Có nhiều giá trị " + label + "; hệ thống dùng giá trị ở dòng đầu tiên.");
        return values.isEmpty() ? null : values.getFirst();
    }

    private String required(Map<String, String> row, String field, int rowNumber, String label) {
        String value = row.get(field);
        if (blank(value)) throw invalid("Dòng " + rowNumber + " thiếu " + label);
        return value.trim();
    }

    private LotteryRegion parseRegion(String value, int rowNumber) {
        return switch (canonical(value)) {
            case "south", "miennam", "mn" -> LotteryRegion.SOUTH;
            case "central", "mientrung", "mt" -> LotteryRegion.CENTRAL;
            case "north", "mienbac", "mb" -> LotteryRegion.NORTH;
            default -> throw invalid("Dòng " + rowNumber + " có khu vực không hợp lệ: " + value);
        };
    }

    private LocalDate parseDate(String value, int rowNumber, String label) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try { return LocalDate.parse(value.trim(), formatter); }
            catch (DateTimeParseException ignored) { }
        }
        try { return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate(); }
        catch (DateTimeParseException ignored) {
            throw invalid("Dòng " + rowNumber + " có " + label + " không hợp lệ: " + value);
        }
    }

    private Instant parseInstant(String value, int rowNumber, String label) {
        String text = value.trim();
        List<Function<String, Instant>> parsers = List.of(
                Instant::parse,
                item -> OffsetDateTime.parse(item).toInstant());
        for (Function<String, Instant> parser : parsers) {
            try { return parser.apply(text); }
            catch (DateTimeException ignored) { }
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try { return LocalDateTime.parse(text, formatter).atZone(STORE_ZONE).toInstant(); }
            catch (DateTimeParseException ignored) { }
        }
        throw invalid("Dòng " + rowNumber + " có " + label + " không hợp lệ: " + value);
    }

    private int parsePositiveInt(String value, int rowNumber) {
        try {
            int parsed = Integer.parseInt(numeric(value));
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("Dòng " + rowNumber + " có số lượng không hợp lệ: " + value);
        }
    }

    private long parseLong(String value, int rowNumber, String label, boolean allowZero) {
        try {
            long parsed = Long.parseLong(numeric(value));
            if (parsed < 0 || (!allowZero && parsed == 0)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("Dòng " + rowNumber + " có " + label + " không hợp lệ: " + value);
        }
    }

    private String numeric(String value) {
        return value.trim().replace(" ", "").replace(".", "").replace(",", "");
    }

    private char detectDelimiter(String text) {
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        return firstLine.chars().filter(character -> character == ';').count()
                > firstLine.chars().filter(character -> character == ',').count() ? ';' : ',';
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String canonical(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replace('đ', 'd').replaceAll("[^a-z0-9]", "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullable(String value) {
        return blank(value) ? null : value.trim();
    }

    private static BusinessException invalid(String message) {
        return BusinessException.invalid(ErrorCode.INVALID_REQUEST, message);
    }
}
