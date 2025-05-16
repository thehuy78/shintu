## SHINTU là một thư viện java spring boot giúp thực hiện CRUD nhanh chóng

## Feature
- Lấy dữ liệu get all có kèm sort filter phân page
- FindById
- Create
- Update


Add Hintu in pom.xml
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

Tại thư file run chính của thư mục
```
package unitech.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
		"unitech.demo", //package project (thay đổi theo tên package của bạn)
		"shintu.lib" // package thư viện cố định
})
@EnableCaching
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}

```



## DTO GET, DTO POST, FIELD REGISTER
- Để thực hiện được việc chia page thì Object Paging phải theo cấu trúc, class Dto, FieldRegister cho những field đặc biệt
```
//Paging nhận vào từ Frondent
// Tôi đã có một thư viện React UI hỗ trợ việc này
// tham khảo tại https://www.npmjs.com/package/hintu 
public class PagingRequest {
  private int page = 0; // trang lấy ra
  private int size = 10; // số phần tử trong 1 trang
  private List<Filter> filter; // các object filter.
  private SortRequest sort; // object sort.
}

//filter
public class Filter {
  private String name; // tên field filter
  private String value; // giá trị filter
  private String select; // select này sẽ đi theo với type định nghĩa sẵn bên dưới : "1", "2", ...
  private String type; // kiểu dữ liệu để ứng với biểu thức filter: "string", "number", "date"
}
//sort
public class SortRequest {
  private String sortBy; // tên field sort
  private String order; // ASC or DESC
}
```
```
//nếu class lồng nhau thì _ giữa các lần lồng là hệ thống sẽ hiểu để join bảng
// nhưng field đặc biệt cần phải khai báo trong fieldRegister
// Hiện tại getAll có filter và sort nên không hỗ trợ dto lồng nhau.
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
            detailJoin = root.join("planProduceDetails", JoinType.LEFT); // field trong entity
            joins.put("planProduceDetails", detailJoin);
          }
          return cb.sum(detailJoin.get("quantity"));
        }));

    // *VÍ DỤ CHO VIỆC JOIN LỒNG NHÌU BẢNG VÀO ĐỂ LẤY GIÁ TRỊ */
    // map.put("productName", new BaseFieldDefinition(
    // "productName",
    // "planProduceDetails.product.name",
    // false,
    // (root, cb, joins) -> {
    // // B1: Join từ root → planProduceDetails
    // Join<?, ?> detailJoin = joins.computeIfAbsent("planProduceDetails",
    // k -> root.join("planProduceDetails", JoinType.LEFT));

    // // B2: Join từ planProduceDetails → product
    // Join<?, ?> productJoin = joins.computeIfAbsent("planProduceDetails.product",
    // k -> detailJoin.join("product", JoinType.LEFT));

    // // B3: Lấy field trong bảng thứ 3
    // return productJoin.get("name");
    // }));

    return map;
  }
}

```
## MAPPER
- Để có thể FindById, Create, Update cần phải có mapper

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
# Lưu ý
- Ở entity cha muốn create hoặc update entity con lồng trong đó cần phải khai báo cascade rõ ràng
```
   @OneToMany(mappedBy = "materialImport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialImportDetail> materialImportDetails;
```


## Dùng nó ở SERVICE
 - Có thể dùng mặc định theo fieldRegister hoặc Mapper
 - Hoặc có thể Override lại tuỳ thích
 - Nếu getall không có field đặc biệt nào thì có thể dùng DefaultFieldRegister
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

- Nhúng service vào và gọi hàm từ service theo route
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

## Ngoài ra
```
// ngoài việc sử dụng hàm excel có sẵn từ baseCrudService để xuất excel theo DTO getAll
// thì cũng có thể dùng để xuất theo DTO và data mình muốn
ExcelExportUtil.export(null, null, null);

// tham số đầu tiên là list data
// tham số thứ 2 là class của data
// tham số thứ 3 là sheetname
```

## Contact

Developed by [The Huy](https://www.facebook.com/nthehuy2878/)

[Github](https://github.com/thehuy78)

Email: thehuy7800@gmail.com