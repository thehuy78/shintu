package shintu.lib.lib.query.dtoFilter;

import java.util.Map;

import jakarta.persistence.criteria.*;

@FunctionalInterface
public interface ExpressionProvider<T> {
  Expression<?> apply(Root<T> root, CriteriaBuilder cb, Map<String, Join<?, ?>> joins);
}
