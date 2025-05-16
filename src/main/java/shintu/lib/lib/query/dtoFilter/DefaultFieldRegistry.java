package shintu.lib.lib.query.dtoFilter;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DefaultFieldRegistry extends BaseFieldRegistry {
  @Override
  public Map<String, BaseFieldDefinition> getFieldDefinitions() {
    return new HashMap<>(); // không định nghĩa gì cả
  }
}
