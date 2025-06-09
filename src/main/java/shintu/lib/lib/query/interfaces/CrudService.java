package shintu.lib.lib.query.interfaces;

import java.util.List;

import jakarta.persistence.Tuple;
import shintu.lib.lib.query.dto.CustomResult;
import shintu.lib.lib.query.dto.PagingRequest;

public interface CrudService<DtoPost, DtoGet, ID> {
  CustomResult findById(ID id);

  CustomResult create(DtoPost dto);

  CustomResult update(ID id, DtoPost dto);

  CustomResult get(PagingRequest request);

  CustomResult excel(PagingRequest request);

  // Các phương thức hook mới
  default void beforeCreate(DtoPost dto) {
  }

  default void afterCreate(DtoPost dto, Object createdEntity) {
  }

  default void beforeUpdate(ID id, DtoPost dto) {
  }

  default void afterUpdate(ID id, DtoPost dto, Object updatedEntity) {
  }

  default List<Tuple> beforeExcelData(List<Tuple> data) {
    return data;
  }
}