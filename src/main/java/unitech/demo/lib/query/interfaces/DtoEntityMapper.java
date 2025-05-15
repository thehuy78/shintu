package unitech.demo.lib.query.interfaces;

public interface DtoEntityMapper<DtoPost, E> {
  E toEntity(DtoPost dto, E existing); // có thể update

  DtoPost toDto(E entity);
}
