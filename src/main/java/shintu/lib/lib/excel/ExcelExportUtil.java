// package unitech.demo.lib.excel;

// import org.apache.poi.ss.usermodel.*;
// import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// import org.springframework.stereotype.Component;

// import unitech.demo.lib.query.dto.CustomResult;

// import java.io.ByteArrayOutputStream;
// import java.lang.reflect.Field;
// import java.time.LocalDate;
// import java.time.format.DateTimeFormatter;
// import java.util.*;

// @Component
// public class ExcelExportUtil {
// public static <T> CustomResult export(List<T> data, Class<T> clazz, String
// sheetName) {
// try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new
// ByteArrayOutputStream()) {
// Sheet sheet = workbook.createSheet(sheetName);
// Map<Integer, String> headers = new TreeMap<>();
// Map<Integer, Field> fields = new TreeMap<>();

// // Lấy header từ annotation
// for (Field field : clazz.getDeclaredFields()) {
// if (field.isAnnotationPresent(ExcelColumn.class)) {
// ExcelColumn ann = field.getAnnotation(ExcelColumn.class);
// headers.put(ann.order(), ann.header());
// fields.put(ann.order(), field);
// }
// }

// // Header row
// Row headerRow = sheet.createRow(0);
// int colIndex = 0;
// for (String header : headers.values()) {
// Cell cell = headerRow.createCell(colIndex++);
// cell.setCellValue(header);
// }

// // Data rows
// int rowIndex = 1;
// for (T item : data) {
// Row row = sheet.createRow(rowIndex++);
// int ci = 0;
// for (Field field : fields.values()) {
// field.setAccessible(true);
// Object value = field.get(item);
// Cell cell = row.createCell(ci++);

// if (value instanceof LocalDate) {
// cell.setCellValue(((LocalDate)
// value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
// } else if (value != null) {
// cell.setCellValue(value.toString());
// }
// }
// }

// // Auto-size columns
// for (int i = 0; i < headers.size(); i++) {
// sheet.autoSizeColumn(i);
// }

// workbook.write(out);

// return new CustomResult(200, "Export Success",
// Base64.getEncoder().encodeToString(out.toByteArray()));

// } catch (Exception e) {
// throw new RuntimeException("Error exporting Excel", e);
// }
// }
// }
