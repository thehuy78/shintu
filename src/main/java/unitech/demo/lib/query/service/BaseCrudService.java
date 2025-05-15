package unitech.demo.lib.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import unitech.demo.lib.query.dto.CustomResult;
import unitech.demo.lib.query.dto.PagingRequest;
import unitech.demo.lib.query.dtoFilter.BaseFieldRegistry;
import unitech.demo.lib.query.interfaces.CrudService;
import unitech.demo.lib.query.interfaces.DtoEntityMapper;

@RequiredArgsConstructor
public class BaseCrudService<DtoPost, DtoGet, E, ID> implements CrudService<DtoPost, DtoGet, ID> {

  private final JpaRepository<E, ID> repository;
  private final DtoEntityMapper<DtoPost, E> mapper;
  private final Class<E> entityClass;
  private final Class<DtoGet> dtoGetClass;
  private final BaseFieldRegistry fieldRegistry;

  @PersistenceContext
  private EntityManager em;

  @Override
  public CustomResult findById(ID id) {
    E entity = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    return new CustomResult(200, "Success", mapper.toDto(entity));
  }

  @Override
  @Transactional
  public CustomResult create(DtoPost dto) {
    try {
      E entity = mapper.toEntity(dto, null);
      repository.save(entity);
      return new CustomResult(200, "Success", null);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }

  @Override
  @Transactional
  public CustomResult update(ID id, DtoPost dto) {
    try {
      E existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
      if (existing == null) {
        return new CustomResult(404, "Not found", null);
      }
      E updated = mapper.toEntity(dto, existing);
      repository.save(updated);
      return new CustomResult(200, "Success", null);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }

  @Override
  public CustomResult get(PagingRequest request) {
    try {
      BaseDtoQueryService<E, DtoGet> queryService = new BaseDtoQueryService<>(em, entityClass, dtoGetClass,
          fieldRegistry);
      Page<DtoGet> page = queryService.filter(request);
      return new CustomResult(200, "Success", page);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }
  }

  @Override
  public CustomResult excel(PagingRequest request) {
    try {
      BaseDtoQueryService<E, DtoGet> queryService = new BaseDtoQueryService<>(em, entityClass, dtoGetClass,
          fieldRegistry);
      String base64 = queryService.exportToExcelBase64(request);
      return new CustomResult(200, "Success", base64);
    } catch (Exception e) {
      throw new RuntimeException("Error:" + e.getMessage());
    }

  }
}
