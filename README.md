## SHINTU is a Java Spring Boot library to enable fast CRUD operations

## Features
- Get all data with sorting, filtering, and pagination
- FindById
- Create
- Update
- Export Excel


Add Shintu to your pom.xml
```
 <repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
      <url>https://maven.pkg.github.com/thehuy78/shintu</url>
    </repository>
    </repositories>
	<dependencies>
<dependency>
  <groupId>io.github.thehuy78</groupId>
  <artifactId>shintu</artifactId>
  <version>3.0.0</version>
</dependency>
	</dependencies>
```

In your main run file
```
package unitech.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
		"unitech.demo", // your project package (change accordingly)
		"shintu.lib"  // fixed library package
})
@EnableCaching
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}

```



## DTO GET, DTO POST, FIELD REGISTER
- To support paging, the Paging object must follow this structure.
- Dto and FieldRegister are used for special fields.
```
// Paging input from frontend
// I already have a React UI library supporting this:
// see https://www.npmjs.com/package/hintu
public class PagingRequest {
  private int page = 0; // page number
  private int size = 10;// items per page
  private List<Filter> filter;// filter objects
  private SortRequest sort;   // sort object
}

//filter
public class Filter {
  private String name;   // field name to filter
  private String value; // filter value
  private String select; // select option matched with predefined types: "1", "2", ...
  private String type; // data type for filtering expression: "string", "number", "date"
}
//sort
public class SortRequest {
  private String sortBy; // field to sort by
  private String order; // ASC or DESC
}
```
```
// For nested classes, use '_' between nested levels and the system will understand joins
// Special fields need to be declared in fieldRegister
// Currently, getAll with filter and sort does not support nested DTOs.
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public class ProducePlanDto extends BasicEntity {
    private String customer_name;
    private String customer_code;
    private String planLotNo;
    private Date etd;
    private Date startDate;
    private String description;
    private String user_name;
    private String user_code;
    private Double sumQuantity;
  }

// fieldRegister.java
package unitech.demo.mapper.planProduce;

import java.util.*;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.*;
import shintu.lib.lib.query.dtoFilter.*;

@Component
public class ProducePlanFieldRegistry extends BaseFieldRegistry {

  @Override
  public Map<String, BaseFieldDefinition> getFieldDefinitions() {
    Map<String, BaseFieldDefinition> map = new HashMap<>();

    map.put("sumQuantity", new BaseFieldDefinition(
        "sumQuantity",
        "planProduceDetails.quantity",
        true, // vì là aggregate
        (root, cb, joins) -> {
          Join<?, ?> detailJoin;
          if (joins.containsKey("planProduceDetails")) {
            detailJoin = joins.get("planProduceDetails");
          } else {
            detailJoin = root.join("planProduceDetails", JoinType.LEFT); // field in entity
            joins.put("planProduceDetails", detailJoin);
          }
          return cb.sum(detailJoin.get("quantity"));
        }));

   // Example for multiple joins to get nested field value
    // map.put("productName", new BaseFieldDefinition(
    // "productName",
    // "planProduceDetails.product.name",
    // false,
    // (root, cb, joins) -> {
    //   Join<?, ?> detailJoin = joins.computeIfAbsent("planProduceDetails",
    //     k -> root.join("planProduceDetails", JoinType.LEFT));
    //
    //   Join<?, ?> productJoin = joins.computeIfAbsent("planProduceDetails.product",
    //     k -> detailJoin.join("product", JoinType.LEFT));
    //
    //   return productJoin.get("name");
    // }));

    return map;
  }
}

```
## MAPPER
- Mapper is needed for FindById, Create, Update operations.

```
package unitech.demo.mapper.imported.material;

import org.checkerframework.checker.units.qual.m;
import org.checkerframework.checker.units.qual.s;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import shintu.lib.lib.query.interfaces.DtoEntityMapper;
import unitech.demo.entities.*;
import unitech.demo.lib.findEntity.FindByItem;

import unitech.demo.mapper.imported.material.IImportMaterialDto.*;
import unitech.demo.repositories.adminData.StockMaterialRepository;
import unitech.demo.repositories.adminData.SupplierMaterialRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class ImportMaterialMapper implements
    DtoEntityMapper<ImportMaterialDtoPost, MaterialImport> {

  @Autowired
  private FindByItem findByItem;

  @Autowired
  private SupplierMaterialRepository supplierMaterialRepo;

  protected SupplierMaterial findSupplierMaterial(Long idMaterial, Long idSupplier) {
    SupplierMaterial isSupplierMaterial = supplierMaterialRepo.findByMaterialIdAndSupplierId(idMaterial, idSupplier);
    if (isSupplierMaterial == null) {
      throw new RuntimeException("Supplier material not found");
    }
    return isSupplierMaterial;
  }

  @Override
  public MaterialImport toEntity(ImportMaterialDtoPost dto, MaterialImport existing) {
    MaterialImport entity = existing != null ? existing : new MaterialImport();
    entity.setInvoiceNo(dto.getInvoiceNo());
    entity.setImportDate(new Date());
    entity.setDescription(dto.getDescription());

    entity.setCustomsClearanceDate(dto.getCustomsClearanceDate());
    if (existing == null) {
      entity.setStatus("1");
    } else {
      if (dto.getStatus() != null) {
        entity.setStatus(dto.getStatus());
      }
    }
    // if (dto.getStore().getId() != null) {
    // entity.setStore(findByItem.findByIdOrThrow(dto.getStore().getId(),
    // Store.class));
    // }
    entity.setUser(findByItem.findByIdOrThrow(dto.getUser().getId(),
        User.class));
    if (dto.getPurchaseOrder() != null && dto.getPurchaseOrder().getId() != null) {
      entity.setPurchaseOrder(findByItem.findByIdOrThrow(dto.getPurchaseOrder().getId(),
          PurchaseOrder.class));
    }

    if (entity.getMaterialImportDetails() == null) {
      entity.setMaterialImportDetails(new ArrayList<>());
    } else {
      entity.getMaterialImportDetails().clear();
    }
    for (ImportMaterialDetailDto detailDto : dto.getMaterialImportDetails()) {
      MaterialImportDetail detail = new MaterialImportDetail();

      if (dto.getPurchaseOrder() != null) {

        detail.setSupplierMaterial(findSupplierMaterial(detailDto.getSupplierMaterial().getMaterial().getId(),
            dto.getPurchaseOrder().getSupplier().getId()));
      } else {
        detail.setSupplierMaterial(
            findByItem.findByIdOrThrow(detailDto.getMaterial().getSupplierMaterialId(), SupplierMaterial.class));
      }

      detail.setQuantity(detailDto.getQuantity());
      detail.setPrice(detailDto.getPrice());
      detail.setMaterialImport(entity); // liên kết ngược
      detail.setStatus("1");
      if (detailDto.getPurchaseOrderDetail() != null) {
        detail.setPurchaseOrderDetail(
            findByItem.findByIdOrThrow(detailDto.getPurchaseOrderDetail().getId(),
                PurchaseOrderDetail.class));
      }

      StockMaterial stockMaterial = new StockMaterial();
      stockMaterial.setSupplierMaterial(detail.getSupplierMaterial());
      // stockMaterial.setStore(entity.getStore());

      stockMaterial.setMaterialImportDetail(detail);
      if (dto.getPurchaseOrder() != null) {
        stockMaterial
            .setQuantity(detail.getQuantity() * detailDto.getSupplierMaterial().getMaterial().getConvertRate());

      } else {
        stockMaterial.setQuantity(detail.getQuantity() * detailDto.getMaterial().getConvertRate());

      }
      stockMaterial.setStatus("1");
      // stockMaterialRepo.save(stockMaterial);
      detail.setStockMaterial(stockMaterial);
      entity.getMaterialImportDetails().add(detail);
    }
    return entity;
  }

  @Override
  public ImportMaterialDtoPost toDto(MaterialImport entity) {
    List<ImportMaterialDetailDto> detailDtos = entity.getMaterialImportDetails().stream().map(detail -> {

      MaterialDto materialDto = new MaterialDto();
      BeanUtils.copyProperties(detail.getSupplierMaterial().getMaterial(), materialDto);

      StoreDto store = new StoreDto(detail.getSupplierMaterial().getMaterial().getStore().getCode(),
          detail.getSupplierMaterial().getMaterial().getStore().getName(),
          detail.getSupplierMaterial().getMaterial().getStore().getId());
      if (entity.getPurchaseOrder() != null) {
        return new ImportMaterialDetailDto(
            detail.getStatus(), detail.getId(), detail.getCreateDate(),
            detail.getUpdateDate(), detail.getQuantity(), detail.getPrice(), null,
            new SupplierMaterialDto(detail.getSupplierMaterial().getId(), materialDto),
            new PurchaseOrderDetailDto(detail.getPurchaseOrderDetail().getId(),
                detail.getPurchaseOrderDetail().getQuantity()),
            null, store);
      } else {
        return new ImportMaterialDetailDto(
            detail.getStatus(), detail.getId(), detail.getCreateDate(),
            detail.getUpdateDate(), detail.getQuantity(), detail.getPrice(), null,
            new SupplierMaterialDto(detail.getSupplierMaterial().getId(), materialDto),
            null,
            null, store);
      }
    }).toList();

    ImportMaterialDtoPost dto = new ImportMaterialDtoPost();
    BeanUtils.copyProperties(entity, dto);
    dto.setMaterialImportDetails(detailDtos);
    if (entity.getPurchaseOrder() != null) {
      dto.setPurchaseOrder(new PurchaseOrderDto(
          entity.getPurchaseOrder().getId(), entity.getPurchaseOrder().getPoNo(),
          new SupplierDto(
              entity.getPurchaseOrder().getSupplier().getCode(),
              entity.getPurchaseOrder().getSupplier().getName(),
              entity.getPurchaseOrder().getSupplier().getId()

          )));
    } else {
      dto.setPurchaseOrder(new PurchaseOrderDto(
          null, null,
          new SupplierDto(
              entity.getMaterialImportDetails().get(0).getSupplierMaterial().getSupplier().getCode(),
              entity.getMaterialImportDetails().get(0).getSupplierMaterial().getSupplier().getName(),
              entity.getMaterialImportDetails().get(0).getSupplierMaterial().getSupplier().getId())));
    }

    dto.setUser(new UserDto(entity.getUser().getCode(), entity.getUser().getName(), entity.getUser().getId()));
    return dto;
  }
}

```
# NOTE
- In the parent entity, to create or update nested child entities, you must declare cascade clearly.
```
   @OneToMany(mappedBy = "materialImport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialImportDetail> materialImportDetails;
```


## Using in SERVICE
 - You can use default FieldRegister or Mapper
 - Or override as needed
 - If getAll has no special fields, you can use DefaultFieldRegister
```
@Service
public class ProducePlanService extends BaseCrudService<ProducePlanDtoPost, ProducePlanDto, PlanProduce, Long>
    implements IProducePlan {

  public ProducePlanService(ProducePlanRepository producePlanRepo,
      PlanProduceMapper mapper,
      ProducePlanFieldRegistry registry) {
    super(producePlanRepo, mapper, PlanProduce.class, ProducePlanDto.class, registry);

  }
    }
```

## Controller

- Inject service and call functions via routes
```
@RestController
@RequestMapping("/api/produce")
public class ProducePlanController {

  @Autowired
  ProducePlanService service;

  @PostMapping("get")
  @RolesAllowed({ "admin" })
  public CustomResult Get(@RequestBody PagingRequest request) {
    return service.get(request);
  }

  @PostMapping("excel")
  @RolesAllowed({ "admin" })
  public CustomResult GetExcel(@RequestBody PagingRequest request) {
    return service.excel(request);
  }

  @PostMapping("create")
  @RolesAllowed({ "admin" })
  public CustomResult Create(@RequestBody ProducePlanDtoPost dto) {
    return service.create(dto);
  }

  @GetMapping("get/{id}")
  @RolesAllowed({ "admin" })
  public CustomResult GetById(@PathVariable Long id) {
    return service.findById(id);
  }

  @PutMapping("edit/{id}")
  @RolesAllowed({ "admin" })
  public CustomResult GetById(@PathVariable Long id, @RequestBody ProducePlanDtoPost dto) {
    return service.update(id, dto);
  }
}
```

## Additional
```
// Besides using the built-in excel export function from baseCrudService for exporting getAll DTO,
// you can also export with your own DTO and data:

ExcelExportUtil.export(null, null, null);

// parameters:
// 1st: list data
// 2nd: data class
// 3rd: sheet name
```

## Contact

Developed by [The Huy](https://www.facebook.com/nthehuy2878/)

[Github](https://github.com/thehuy78)

Email: thehuy7800@gmail.com