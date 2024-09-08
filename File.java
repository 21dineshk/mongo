import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    public Mono<List<Map<String, Object>>> fromBase64String(String base64String) {
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);

        try (InputStream inputStream = new ByteArrayInputStream(decodedBytes);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<String> columnNames = getColumnNames(sheet);

            return Mono.just(getRows(sheet, columnNames));
        } catch (Exception e) {
            logger.error("Error processing base64 string: {}", base64String, e);
            throw new CustomFileProcessingException("Failed to process file", e);
        }
    }

    private List<String> getColumnNames(Sheet sheet) {
        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            logger.error("Header row is empty or missing");
            throw new CustomFileProcessingException("Header row is empty");
        }

        return IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .mapToObj(i -> getCellValue(headerRow.getCell(i)))
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getRows(Sheet sheet, List<String> columnNames) {
        return IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(i -> sheet.getRow(i))
                .filter(row -> row != null)
                .map(row -> processRow(row, columnNames))
                .collect(Collectors.toList());
    }

    private Map<String, Object> processRow(Row row, List<String> columnNames) {
        Map<String, Object> rowMap = new ConcurrentHashMap<>();
        IntStream.range(0, columnNames.size())
                .forEach(i -> {
                    String cellValue = getCellValue(row.getCell(i));
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        rowMap.put(columnNames.get(i), cellValue.trim());
                    }
                });
        return rowMap;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }
}

class CustomFileProcessingException extends RuntimeException {
    public CustomFileProcessingException(String message) {
        super(message);
    }

    public CustomFileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
