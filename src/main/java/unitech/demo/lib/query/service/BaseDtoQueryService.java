package unitech.demo.lib.query.service;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.*;
import unitech.demo.lib.query.dto.Filter;
import unitech.demo.lib.query.dto.PagingRequest;
import unitech.demo.lib.query.dtoFilter.BaseFieldDefinition;
import unitech.demo.lib.query.dtoFilter.BaseFieldRegistry;
import unitech.demo.lib.query.dtoFilter.ExpressionProvider;

@RequiredArgsConstructor
public class BaseDtoQueryService<T, D> {

  private final EntityManager em;
  private final Class<T> entityClass;
  private final Class<D> dtoClass;
  private final BaseFieldRegistry fieldRegistry;

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
    for (Field dtoField : getAllFields(dtoClass)) {
      String dtoFieldName = dtoField.getName();

      BaseFieldDefinition def = fieldRegistry.get(dtoFieldName);
      if (def == null || handled.contains(dtoFieldName))
        continue;
      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(dtoFieldName,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      selections.add(expr.alias(dtoFieldName));
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
        continue; // Bỏ qua nếu value rỗng
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

    // if (orders.isEmpty() && groupBy.isEmpty()) {
    // orders.add(cb.desc(root.get("id")));
    // }

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

    // o

    int page = request.getPage();
    int size = request.getSize();

    // query.setFirstResult(page * size);
    // query.setMaxResults(size);

    List<D> content = tuples.stream().map(t -> {
      try {
        D dto = dtoClass.getDeclaredConstructor().newInstance();
        for (TupleElement<?> el : t.getElements()) {
          String alias = el.getAlias();
          Object value = t.get(alias);
          Field field = getFieldRecursive(dtoClass, alias);
          if (field != null) {
            field.setAccessible(true);
            field.set(dto, value);
          }
        }
        return dto;
      } catch (Exception e) {
        throw new RuntimeException("DTO mapping failed", e);
      }
    }).toList();
    // Count
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    Map<String, Join<?, ?>> countJoins = new HashMap<>();
    List<Predicate> countPredicates = new ArrayList<>();

    for (Filter f : request.getFilter()) {
      String fieldName = f.getName();
      String value = f.getValue();
      if (value == null || value.trim().isEmpty())
        continue; // Bỏ qua nếu value rỗng
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

  // lấy tất cả các field có thể có trong supper class
  private Field getFieldRecursive(Class<?> clazz, String fieldName) {
    while (clazz != null) {
      try {
        return clazz.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass(); // tìm trong lớp cha
      }
    }
    return null;
  }

  // đệ quy tất cả các field có thể có trong supper class
  public List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    while (clazz != null && clazz != Object.class) {
      fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  // đọc từng filter để tạo expr cho nó
  private Predicate buildPredicate(CriteriaBuilder cb, Expression<?> expr, Filter f) {
    String value = f.getValue();
    String select = f.getSelect();
    String type = f.getType();

    if (value == null || value.trim().isEmpty()) {
      return cb.conjunction(); // Bỏ qua nếu value rỗng
    }

    try {
      switch (type.toLowerCase()) {
        case "string": // nếu kiểu dữ liệu của field là String
          switch (select) {
            case "1": // contains
              return cb.like(cb.lower(expr.as(String.class)), "%" + value.toLowerCase() + "%");
            case "2": // not contains
              return cb.notLike(cb.lower(expr.as(String.class)), "%" + value.toLowerCase() + "%");
            case "3": // equal
              return cb.equal(cb.lower(expr.as(String.class)), value.toLowerCase());
            case "4": // not equal
              return cb.notEqual(cb.lower(expr.as(String.class)), value.toLowerCase());
            case "5": // is null
              return cb.isNull(expr);
          }
          break;

        case "number": // nếu kiểu dữ liệu của field là number
          Double num = Double.parseDouble(value);
          switch (select) {
            case "1": // lớn hơn
              return cb.gt(expr.as(Double.class), num);
            case "2": // bé hơn
              return cb.lt(expr.as(Double.class), num);
            case "3": // bằng
              return cb.equal(expr.as(Double.class), num);
            case "4":// bé hơn bằng
              return cb.le(expr.as(Double.class), num);
            case "5": // lớn hơn bằng
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
      System.out.println("Lỗi khi xử lý filter: " + f + " - " + e.getMessage());
      return cb.conjunction();
    }

    return cb.conjunction(); // fallback nếu không match gì
  }

  public String exportToExcelBase64(PagingRequest request) {
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

    for (Field dtoField : getAllFields(dtoClass)) {
      String dtoFieldName = dtoField.getName();
      BaseFieldDefinition def = fieldRegistry.get(dtoFieldName);
      if (def == null || handled.contains(dtoFieldName))
        continue;

      @SuppressWarnings("unchecked")
      Expression<?> expr = expressionCache.computeIfAbsent(dtoFieldName,
          key -> ((ExpressionProvider<T>) def.getExpressionProvider()).apply(root, cb, joins));
      selections.add(expr.alias(dtoFieldName));
      if (!def.isAggregate())
        groupBy.add(expr);
      handled.add(dtoFieldName);
    }

    // Build filters
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

      if (def.isAggregate())
        having.add(p);
      else
        predicates.add(p);
    }

    cq.multiselect(selections);
    if (!groupBy.isEmpty())
      cq.groupBy(groupBy);
    if (!predicates.isEmpty())
      cq.where(predicates.toArray(new Predicate[0]));
    if (!having.isEmpty())
      cq.having(having.toArray(new Predicate[0]));

    List<Tuple> tuples = em.createQuery(cq).getResultList();

    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Export");
      // Tạo style cho header
      CellStyle headerStyle = workbook.createCellStyle();
      Font font = workbook.createFont();
      font.setBold(true);
      font.setFontHeightInPoints((short) 12); // Font to hơn 1 chút
      headerStyle.setFont(font);

      // Màu nền xám nhạt
      headerStyle.setFillForegroundColor(IndexedColors.AQUA.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      // Căn giữa
      headerStyle.setAlignment(HorizontalAlignment.CENTER);
      headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
      Row header = sheet.createRow(0);

      // Header
      int colIndex = 0;
      for (TupleElement<?> el : tuples.get(0).getElements()) {
        Cell headerCell = header.createCell(colIndex++);
        headerCell.setCellValue(capitalize(el.getAlias()));
        headerCell.setCellStyle(headerStyle);
      }
      // Rows
      for (int i = 0; i < tuples.size(); i++) {
        Row row = sheet.createRow(i + 1);
        Tuple t = tuples.get(i);
        int ci = 0;
        for (TupleElement<?> el : t.getElements()) {
          Object val = t.get(el.getAlias());
          Cell cell = row.createCell(ci++);
          if (val instanceof Number) {
            cell.setCellValue(((Number) val).doubleValue());
          } else if (val instanceof LocalDate) {
            cell.setCellValue(val.toString());
          } else {
            cell.setCellValue(val != null ? val.toString() : "");
          }
        }
      }
      for (int i = 0; i < tuples.get(0).getElements().size(); i++) {
        sheet.autoSizeColumn(i);
      }
      workbook.write(out);
      return Base64.getEncoder().encodeToString(out.toByteArray());

    } catch (Exception e) {
      throw new RuntimeException("Export to Excel failed", e);
    }
  }

  private String capitalize(String input) {
    if (input == null || input.isEmpty())
      return input;
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
