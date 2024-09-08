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
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64String);
        } catch (IllegalArgumentException e) {
            logger.error("Base64 decoding failed", e);
            throw new CustomFileProcessingException("Invalid Base64 input", e);
        }

        try (InputStream inputStream = new ByteArrayInputStream(decodedBytes);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new CustomFileProcessingException("No sheet found at index 0");
            }

            // Step 1: Validate and filter columns based on finalMappings
            List<String> columnNames = getColumnNames(sheet);
            List<String> validColumnNames = filterColumnsWithFinalMappings(columnNames);

            return Mono.just(getRows(sheet, validColumnNames));
        } catch (Exception e) {
            logger.error("Error processing file", e);
            throw new CustomFileProcessingException("File processing failed", e);
        }
    }

    private List<String> getColumnNames(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            logger.error("Header row is missing");
            throw new CustomFileProcessingException("Header row is empty");
        }

        List<String> columnNames = IntStream.range(0, headerRow.getPhysicalNumberOfCells())
                .mapToObj(i -> getCellValue(headerRow.getCell(i)))
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toList());

        if (columnNames.isEmpty()) {
            logger.error("No column names found");
            throw new CustomFileProcessingException("No valid column names in header");
        }

        return columnNames;
    }

    // Step 2: Filter out columns that do not have mappings in finalMappings
    private List<String> filterColumnsWithFinalMappings(List<String> columnNames) {
        return columnNames.stream()
                .filter(columnName -> {
                    boolean hasMapping = finalMappings.getColumnMappingKey(columnName) != null;
                    if (!hasMapping) {
                        logger.warn("Column '{}' does not have a corresponding finalMapping and will be ignored", columnName);
                    }
                    return hasMapping;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> getRows(Sheet sheet, List<String> validColumnNames) {
        return IntStream.range(1, sheet.getPhysicalNumberOfRows())
                .mapToObj(i -> {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        logger.warn("Skipping empty row at index {}", i);
                        return null;
                    }
                    return processRow(row, validColumnNames);
                })
                .filter(rowMap -> rowMap != null && !rowMap.isEmpty())
                .collect(Collectors.toList());
    }

    private Map<String, Object> processRow(Row row, List<String> validColumnNames) {
        Map<String, Object> rowMap = new ConcurrentHashMap<>();

        // Step 3: Process only the valid columns that have finalMappings
        IntStream.range(0, validColumnNames.size()).forEach(i -> {
            String cellValue = getCellValue(row.getCell(i));
            if (cellValue != null && !cellValue.trim().isEmpty()) {
                rowMap.put(validColumnNames.get(i), cellValue.trim());
            }
        });

        // finalMappings logic remains unchanged
        if (finalMappings != null) {
            validColumnNames.forEach(columnName -> {
                String mappedKey = finalMappings.getColumnMappingKey(columnName);
                Object columnValue = rowMap.get(columnName);

                if (mappedKey != null && columnValue != null) {
                    logger.debug("Mapping column '{}' to key '{}'", columnName, mappedKey);
                    rowMap.put(mappedKey, columnValue);
                    rowMap.remove(columnName); // Optionally remove original key
                }
            });
        }

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
