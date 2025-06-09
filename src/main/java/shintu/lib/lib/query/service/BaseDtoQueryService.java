package shintu.lib.lib.query.service;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import shintu.lib.lib.query.dto.Filter;
import shintu.lib.lib.query.dto.PagingRequest;
import shintu.lib.lib.query.dtoFilter.BaseFieldDefinition;
import shintu.lib.lib.query.dtoFilter.BaseFieldRegistry;
import shintu.lib.lib.query.dtoFilter.ExpressionProvider;
import shintu.lib.lib.query.interfaces.EntityPath;

@RequiredArgsConstructor
public class BaseDtoQueryService<T, D> {

  private final Class<T> entityClass;
  private final Class<D> dtoClass;
  private final BaseFieldRegistry fieldRegistry;
  private final EntityManager em;

  public Page<D> filter(PagingRequest request) {
    Map<String, Expression<?>> expressionCache = new HashMap<>();
    Map<String, Join<?, ?>> joins = new HashMap<>();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Selection<?>> selections = new ArrayList<>();
    List<Expression<?>> groupBy = new ArrayList<>();
    List<Predicate> predicates = new ArrayList<>();
    List<Predicate> having = new ArrayList<>();
    List<Order> orders = new ArrayList<>();
    Set<String> handled = new HashSet<>();

    // Process fields including nested ones
    for (Field dtoField : getAllFields(dtoClass)) {
      String dtoFieldName = dtoField.getName();

      // Check for @EntityPath annotation
      EntityPath entityPath = dtoField.getAnnotation(EntityPath.class);
      String fieldKey = entityPath != null ? entityPath.value() : dtoFieldName;

      BaseFieldDefinition def = fieldRegistry.get(fieldKey);
      if (def == null || handled.contains(dtoFieldName))
        continue;

      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(dtoFieldName,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      selections.add(expr.alias(fieldKey)); // Use the full path as alias
      if (!def.isAggregate()) {
        groupBy.add(expr);
      }
      handled.add(dtoFieldName);
    }

    // Build filter
    for (Filter f : request.getFilter()) {
      String fieldName = f.getName();
      String value = f.getValue();
      if (value == null || value.trim().isEmpty())
        continue;
      BaseFieldDefinition def = fieldRegistry.get(fieldName);
      if (def == null)
        continue;
      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(fieldName,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      Predicate p = buildPredicate(cb, expr, f);

      if (def.isAggregate()) {
        having.add(p);
      } else {
        predicates.add(p);
      }
    }

    // Build sort
    if (request.getSort() != null) {
      String sortField = request.getSort().getSortBy();
      BaseFieldDefinition def = fieldRegistry.get(sortField);
      if (def != null) {
        @SuppressWarnings("unchecked")
        Expression<?> expr = expressionCache.computeIfAbsent(sortField,
            key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
        orders.add("DESC".equalsIgnoreCase(request.getSort().getOrder()) ? cb.desc(expr) : cb.asc(expr));
      }
    }
    if (orders.isEmpty() && dtoHasField("id")) {
      orders.add(cb.desc(root.get("id")));
    }

    cq.multiselect(selections);

    if (!groupBy.isEmpty())
      cq.groupBy(groupBy);
    if (!predicates.isEmpty())
      cq.where(predicates.toArray(new Predicate[0]));
    if (!having.isEmpty())
      cq.having(having.toArray(new Predicate[0]));
    if (!orders.isEmpty())
      cq.orderBy(orders);

    TypedQuery<Tuple> query = em.createQuery(cq);
    var im = query.getResultList();
    int totalElements = im.size();

    query.setFirstResult(request.getPage() * request.getSize());
    query.setMaxResults(request.getSize());
    List<Tuple> tuples = query.getResultList();

    int page = request.getPage();
    int size = request.getSize();

    List<D> content = tuples.stream().map(t -> {
      try {
        D dto = dtoClass.getDeclaredConstructor().newInstance();
        for (TupleElement<?> el : t.getElements()) {
          String alias = el.getAlias();
          Object value = t.get(alias);
          setNestedField(dto, alias, value);
        }
        return dto;
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }).toList();

    // Count query remains the same
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    Map<String, Join<?, ?>> countJoins = new HashMap<>();
    List<Predicate> countPredicates = new ArrayList<>();

    for (Filter f : request.getFilter()) {
      String fieldName = f.getName();
      String value = f.getValue();
      if (value == null || value.trim().isEmpty())
        continue;
      BaseFieldDefinition def = fieldRegistry.get(fieldName);
      if (def == null || def.isAggregate())
        continue;

      @SuppressWarnings("unchecked")
      Expression<?> expr = ((ExpressionProvider<T>) def.getExpressionProvider())
          .apply(countRoot, cb, countJoins);
      Predicate p = buildPredicate(cb, expr, f);
      countPredicates.add(p);
    }

    countQuery.select(cb.countDistinct(countRoot));
    if (!countPredicates.isEmpty()) {
      countQuery.where(countPredicates.toArray(new Predicate[0]));
    }

    return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
  }

  private void setNestedField(Object target, String fieldPath, Object value) throws Exception {
    String[] parts = fieldPath.split("\\.");
    Object current = target;

    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      Field field = getFieldRecursive(current.getClass(), part);
      field.setAccessible(true);

      // Get or create nested object
      Object nested = field.get(current);
      if (nested == null) {
        nested = field.getType().getDeclaredConstructor().newInstance();
        field.set(current, nested);
      }
      current = nested;
    }

    // Set the final value
    String finalField = parts[parts.length - 1];
    Field field = getFieldRecursive(current.getClass(), finalField);
    if (field != null) {
      field.setAccessible(true);

      // Handle entity to DTO conversion
      if (value != null && !field.getType().isInstance(value)) {
        // If the value is an entity but field expects DTO, we need to convert
        if (isJpaEntity(value.getClass()) && !isJpaEntity(field.getType())) {
          value = convertEntityToDto(value, field.getType());
        }
      }

      field.set(current, value);
    }
  }

  private boolean isJpaEntity(Class<?> clazz) {
    return clazz.getAnnotation(jakarta.persistence.Entity.class) != null;
  }

  private Object convertEntityToDto(Object entity, Class<?> dtoClass) throws Exception {

    Object dto = dtoClass.getDeclaredConstructor().newInstance();
    for (Field dtoField : getAllFields(dtoClass)) {
      EntityPath pathAnnotation = dtoField.getAnnotation(EntityPath.class);
      if (pathAnnotation != null) {
        String path = pathAnnotation.value();
        Object value = getEntityValue(entity, path);
        if (value != null) {
          dtoField.setAccessible(true);
          dtoField.set(dto, value);
        }
      } else {
        String path = dtoField.getName();
        Object value = getEntityValue(entity, path);
        if (value != null) {
          dtoField.setAccessible(true);
          dtoField.set(dto, value);
        }
      }
    }
    return dto;
  }

  private Object getEntityValue(Object entity, String path) throws Exception {
    String[] parts = path.split("\\.");
    Object current = entity;
    for (String part : parts) {
      if (current == null)
        return null;
      Field field = getFieldRecursive(current.getClass(), part);
      field.setAccessible(true);
      current = field.get(current);
    }
    return current;
  }

  private Field getFieldRecursive(Class<?> clazz, String fieldName) {
    while (clazz != null) {
      try {
        return clazz.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    return null;
  }

  public List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    while (clazz != null && clazz != Object.class) {
      fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  private Predicate buildPredicate(CriteriaBuilder cb, Expression<?> expr, Filter f) {
    String value = f.getValue();
    String select = f.getSelect();
    String type = f.getType();

    if (value == null || value.trim().isEmpty()) {
      return cb.conjunction();
    }

    try {
      switch (type.toLowerCase()) {
        case "string":
          switch (select) {
            case "1":
              return cb.like(cb.lower(expr.as(String.class)), "%" + value.toLowerCase() + "%");
            case "2":
              return cb.notLike(cb.lower(expr.as(String.class)), "%" + value.toLowerCase() + "%");
            case "3":
              return cb.equal(cb.lower(expr.as(String.class)), value.toLowerCase());
            case "4":
              return cb.notEqual(cb.lower(expr.as(String.class)), value.toLowerCase());
            case "5":
              return cb.isNull(expr);
          }
          break;

        case "number":
          Double num = Double.parseDouble(value);
          switch (select) {
            case "1":
              return cb.gt(expr.as(Double.class), num);
            case "2":
              return cb.lt(expr.as(Double.class), num);
            case "3":
              return cb.equal(expr.as(Double.class), num);
            case "4":
              return cb.le(expr.as(Double.class), num);
            case "5":
              return cb.ge(expr.as(Double.class), num);
          }
          break;

        case "date":
          LocalDate date = LocalDate.parse(value);
          Expression<LocalDate> dateExpr = cb.function("DATE", LocalDate.class, expr.as(java.util.Date.class));
          switch (select) {
            case "1":
              return cb.greaterThan(dateExpr, date);
            case "2":
              return cb.lessThan(dateExpr, date);
            case "3":
              return cb.equal(dateExpr, date);
            case "4":
              return cb.lessThanOrEqualTo(dateExpr, date);
            case "5":
              return cb.greaterThanOrEqualTo(dateExpr, date);
          }
          break;
      }
    } catch (Exception e) {
      System.out.println("Filter processing error: " + f + " - " + e.getMessage());
      return cb.conjunction();
    }

    return cb.conjunction();
  }

  /// excel

  public List<Tuple> getAllDataForExport(PagingRequest request) {
    Map<String, Expression<?>> expressionCache = new HashMap<>();
    Map<String, Join<?, ?>> joins = new HashMap<>();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    List<Selection<?>> selections = new ArrayList<>();
    List<Expression<?>> groupBy = new ArrayList<>();
    List<Predicate> predicates = new ArrayList<>();
    List<Predicate> having = new ArrayList<>();
    Set<String> handled = new HashSet<>();

    // Collect all fields to export (flattened structure)
    List<ExportField> exportFields = new ArrayList<>();
    for (Field dtoField : getAllFields(dtoClass)) {
      if (Modifier.isStatic(dtoField.getModifiers())) {
        continue; // Skip static fields
      }

      // Check if this is a nested DTO field
      if (!isSimpleType(dtoField.getType())) {
        // Process nested DTO fields
        for (Field nestedField : getAllFields(dtoField.getType())) {
          if (Modifier.isStatic(nestedField.getModifiers())) {
            continue;
          }

          String fieldPath = dtoField.getName() + "_" + nestedField.getName();
          String displayName = capitalize(dtoField.getName()) + capitalize(nestedField.getName());

          exportFields.add(new ExportField(
              displayName,
              fieldPath,
              fieldPath));
        }
      } else {
        // Simple field
        exportFields.add(new ExportField(
            capitalize(dtoField.getName()),
            dtoField.getName(),
            dtoField.getName()));
      }
    }
    // Build selections for the query
    for (ExportField field : exportFields) {
      BaseFieldDefinition def = fieldRegistry.get(field.fieldPath);
      if (def == null || handled.contains(field.alias)) {
        continue;
      }

      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(field.fieldPath,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      selections.add(expr.alias(field.alias));
      if (!def.isAggregate()) {
        groupBy.add(expr);
      }
      handled.add(field.alias);
    }
    // Build filters
    for (Filter f : request.getFilter()) {
      String fieldName = f.getName();
      String value = f.getValue();
      if (value == null || value.trim().isEmpty()) {
        continue;
      }

      BaseFieldDefinition def = fieldRegistry.get(fieldName);
      if (def == null) {
        continue;
      }

      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(fieldName,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      Predicate p = buildPredicate(cb, expr, f);

      if (def.isAggregate()) {
        having.add(p);
      } else {
        predicates.add(p);
      }
    }

    cq.multiselect(selections);
    if (!groupBy.isEmpty()) {
      cq.groupBy(groupBy);
    }
    if (!predicates.isEmpty()) {
      cq.where(predicates.toArray(new Predicate[0]));
    }
    if (!having.isEmpty()) {
      cq.having(having.toArray(new Predicate[0]));
    }
    return em.createQuery(cq).getResultList();
  }

  /**
   * Ghi dữ liệu vào Excel và trả về base64
   */
  public String writeDataToExcel(List<Tuple> tuples) {
    // Tạo exportFields như cũ
    List<ExportField> exportFields = createExportFields();

    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Export");

      // Tạo header
      createExcelHeader(sheet, exportFields);

      // Ghi dữ liệu
      writeExcelData(sheet, tuples, exportFields);

      // Auto-size columns
      autoSizeColumns(sheet, exportFields.size());

      workbook.write(out);
      return Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException("Export to Excel failed", e);
    }
  }

  // Các phương thức phụ trợ:

  protected List<ExportField> createExportFields() {
    List<ExportField> exportFields = new ArrayList<>();
    for (Field dtoField : getAllFields(dtoClass)) {
      if (Modifier.isStatic(dtoField.getModifiers()))
        continue;

      if (!isSimpleType(dtoField.getType())) {
        for (Field nestedField : getAllFields(dtoField.getType())) {
          if (Modifier.isStatic(nestedField.getModifiers()))
            continue;

          String fieldPath = dtoField.getName() + "_" + nestedField.getName();
          exportFields.add(new ExportField(
              capitalize(dtoField.getName()) + capitalize(nestedField.getName()),
              fieldPath,
              fieldPath));
        }
      } else {
        exportFields.add(new ExportField(
            capitalize(dtoField.getName()),
            dtoField.getName(),
            dtoField.getName()));
      }
    }
    return exportFields;
  }

  protected void createExcelHeader(Sheet sheet, List<ExportField> exportFields) {
    CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
    Row headerRow = sheet.createRow(0);

    for (int i = 0; i < exportFields.size(); i++) {
      Cell headerCell = headerRow.createCell(i);
      headerCell.setCellValue(exportFields.get(i).displayName);
      headerCell.setCellStyle(headerStyle);
    }
  }

  protected void writeExcelData(Sheet sheet, List<Tuple> tuples, List<ExportField> exportFields) {
    for (int rowIndex = 0; rowIndex < tuples.size(); rowIndex++) {
      Row dataRow = sheet.createRow(rowIndex + 1);
      Tuple tuple = tuples.get(rowIndex);

      for (int colIndex = 0; colIndex < exportFields.size(); colIndex++) {
        ExportField field = exportFields.get(colIndex);
        Object value = tuple.get(field.alias);
        setCellValue(dataRow.createCell(colIndex), value);
      }
    }
  }

  protected CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 12);
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.AQUA.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  protected void setCellValue(Cell cell, Object value) {
    if (value instanceof Number) {
      cell.setCellValue(((Number) value).doubleValue());
    } else if (value instanceof LocalDate) {
      cell.setCellValue(value.toString());
    } else if (value instanceof Boolean) {
      cell.setCellValue((Boolean) value);
    } else {
      cell.setCellValue(value != null ? value.toString() : "");
    }
  }

  protected void autoSizeColumns(Sheet sheet, int columnCount) {
    for (int i = 0; i < columnCount; i++) {
      sheet.autoSizeColumn(i);
    }
  }
  //////////////////////////////////////////////////////////

  private boolean isSimpleType(Class<?> type) {
    return type.isPrimitive() ||
        type.equals(String.class) ||
        type.equals(Integer.class) ||
        type.equals(Long.class) ||
        type.equals(Double.class) ||
        type.equals(Boolean.class) ||
        type.equals(LocalDate.class) ||
        type.equals(Date.class) ||
        type.getName().startsWith("java.");
  }

  private static class ExportField {
    String displayName; // e.g. "CustomerName"
    String fieldPath; // e.g. "customer_name" (for query)
    String alias; // e.g. "customer_name" (for tuple access)

    public ExportField(String displayName, String fieldPath, String alias) {
      this.displayName = displayName;
      this.fieldPath = fieldPath;
      this.alias = alias;
    }
  }

  private String capitalize(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return input.substring(0, 1).toUpperCase() + input.substring(1);
  }

  private boolean dtoHasField(String fieldName) {
    Class<?> clazz = dtoClass;
    while (clazz != null && clazz != Object.class) {
      try {
        clazz.getDeclaredField(fieldName);
        return true;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    return false;
  }
}