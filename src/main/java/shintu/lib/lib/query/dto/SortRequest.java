package shintu.lib.lib.query.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortRequest {
  private String sortBy; // tên field sort
  private String order; // ASC or DESC
}
