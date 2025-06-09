package shintu.lib.lib.query.service;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import shintu.lib.lib.query.dto.CustomResult;
import shintu.lib.lib.query.dto.PagingRequest;
import shintu.lib.lib.query.dtoFilter.BaseFieldRegistry;
import shintu.lib.lib.query.interfaces.CrudService;
import shintu.lib.lib.query.interfaces.DtoEntityMapper;

@RequiredArgsConstructor
public class BaseCrudService<DtoPost, DtoGet, E, ID> implements CrudService<DtoPost, DtoGet, ID> {

  private final JpaRepository<E, ID> repository;
  private final DtoEntityMapper<DtoPost, E> mapper;
  // private final Class<E> entityClass;
  // private final Class<DtoGet> dtoGetClass;
  private final BaseFieldRegistry fieldRegistry;
  @PersistenceContext
  private EntityManager em;

  protected Class<E> getEntityClass() {
    return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass())
        .getActualTypeArguments()[2];
  }

  protected Class<DtoGet> getDtoGetClass() {
    return (Class<DtoGet>) ((ParameterizedType) getClass().getGenericSuperclass())
        .getActualTypeArguments()[1];
  }

  @Override
  public CustomResult findById(ID id) {
    E entity = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    return new CustomResult(200, "Success", mapper.toDto(entity));
  }

  @Override
  @Transactional
  public CustomResult create(DtoPost dto) {
    try {
      beforeCreate(dto);
      E entity = mapper.toEntity(dto, null);
      repository.save(entity);
      afterCreate(dto, entity);
      return new CustomResult(200, "Success", null);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }

  @Override
  @Transactional
  public CustomResult update(ID id, DtoPost dto) {
    try {
      beforeUpdate(id, dto);
      E existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
      if (existing == null) {
        return new CustomResult(404, "Not found", null);
      }
      E updated = mapper.toEntity(dto, existing);
      repository.save(updated);
      afterUpdate(id, dto, updated);
      return new CustomResult(200, "Success", null);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }

  @Override
  public CustomResult get(PagingRequest request) {
    try {
      BaseDtoQueryService<E, DtoGet> queryService = new BaseDtoQueryService<>(getEntityClass(), getDtoGetClass(),
          fieldRegistry, em);
      Page<DtoGet> page = queryService.filter(request);
      return new CustomResult(200, "Success", page);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }
  }

  @Override
  public CustomResult excel(PagingRequest request) {
    try {
      BaseDtoQueryService<E, DtoGet> queryService = new BaseDtoQueryService<>(getEntityClass(), getDtoGetClass(),
          fieldRegistry, em);
      List<Tuple> tuple = queryService.getAllDataForExport(request);
      List<Tuple> processedData = beforeExcelData(tuple);
      String base64 = queryService.writeDataToExcel(processedData);
      return new CustomResult(200, "Success", base64);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }
}
