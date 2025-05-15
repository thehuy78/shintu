package unitech.demo.lib.query.dtoFilter;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseFieldDefinition {
  private String dtoField; // tên field dto ví dụ: quantitySum
  private String entityPath; // đường dẫn theo entity ví dụ: planProduceDetails.quantity
  private boolean isAggregate; // true nếu field là 1 phép tính tổng hợp (sum, avg, count,...).
                               // Quan trọng vì khi filter() phân biệt Predicate để where hay having.
  private ExpressionProvider<?> expressionProvider; // expression của field
}
