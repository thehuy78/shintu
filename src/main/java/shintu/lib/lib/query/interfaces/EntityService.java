package shintu.lib.lib.query.interfaces;

import org.springframework.data.domain.Page;
import shintu.lib.lib.query.dto.PagingRequest;

public interface EntityService<E, D, ID> {
  Page<D> getAll(PagingRequest pagingRequest);

  E getById(ID id);

  E save(E entity);

  E update(ID id, E entity);

  void delete(ID id);

  String excel(PagingRequest pagingRequest);

  String excel(D dto);

}
