package unitech.demo.lib.query.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Filter {
  private String name; // tên field filter
  private String value; // giá trị filter
  private String select; // select này sẽ đi theo với type định nghĩa sẵn bên dưới : "1", "2", ...
  private String type; // kiểu dữ liệu để ứng với biểu thức filter: "string", "number", "date"
}
