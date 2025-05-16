package shintu.lib.lib.query.dto;

import java.util.List;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagingRequest {
  private int page = 0; // trang lấy ra
  private int size = 10; // số phần tử trong 1 trang
  private List<Filter> filter; // các object filter.
  private SortRequest sort; // object sort.
}
