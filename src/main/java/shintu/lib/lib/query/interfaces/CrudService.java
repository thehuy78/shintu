package shintu.lib.lib.query.interfaces;

import shintu.lib.lib.query.dto.CustomResult;
import shintu.lib.lib.query.dto.PagingRequest;

public interface CrudService<DtoPost, DtoGet, ID> {
  CustomResult findById(ID id);

  CustomResult create(DtoPost dto);

  CustomResult update(ID id, DtoPost dto);

  CustomResult get(PagingRequest request);

  CustomResult excel(PagingRequest request);
}