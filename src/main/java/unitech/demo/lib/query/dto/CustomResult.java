package unitech.demo.lib.query.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomResult {
  private Integer status;
  private String message;
  private Object data;
}
