package shintu.lib.lib.query.dtoFilter;

import java.util.Map;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

// public abstract class BaseFieldRegistry {
//   public abstract Map<String, BaseFieldDefinition> getFieldDefinitions();

//   // @Override
//   public BaseFieldDefinition get(String dtoField) {
//     // Các trường hợp được khai báo trong fieldRegister của dto
//     // Trường hợp đặc biệt có trong map thì lấy
//     if (getFieldDefinitions().containsKey(dtoField)) {
//       return getFieldDefinitions().get(dtoField);
//     }
//     // quy tắt đặt tên field dto để tự join là tenbangjoin_tenfield (nếu nhìu bảng
//     // thì cứ _ thì hệ thống sẽ tự join lồng nhau)
//     // Tự động xử lý join nếu có "_"
//     // nếu đặt tên không đúng quy tắt thì bắt buộc phải khai báo đường dẫn field ở
//     // file fieldRegister cho từng dto
//     if (dtoField.contains("_")) {
//       return new BaseFieldDefinition(
//           dtoField,
//           dtoField.replace("_", "."),
//           false,
//           (root, cb, joins) -> {
//             String[] parts = dtoField.split("_");
//             Join<?, ?> currentJoin = null;
//             StringBuilder pathKey = new StringBuilder();

//             for (int i = 0; i < parts.length - 1; i++) {
//               String part = parts[i];
//               if (pathKey.length() > 0) {
//                 pathKey.append("_").append(part);
//               } else {
//                 pathKey.append(part);
//               }

//               String key = pathKey.toString();

//               if (joins.containsKey(key)) {
//                 currentJoin = joins.get(key);
//               } else {
//                 currentJoin = (currentJoin == null ? root.join(part, JoinType.LEFT)
//                     : currentJoin.join(part, JoinType.LEFT));
//                 joins.put(key, currentJoin);
//               }
//             }

//             String finalField = parts[parts.length - 1];
//             return (currentJoin != null ? currentJoin.get(finalField) : root.get(finalField));
//           });
//     }

//     // Trường hợp không join, map thẳng field entity
//     return new BaseFieldDefinition(
//         dtoField,
//         dtoField,
//         false,
//         (root, cb, joins) -> root.get(dtoField));
//   }
// }
public abstract class BaseFieldRegistry {
  public abstract Map<String, BaseFieldDefinition> getFieldDefinitions();

  public BaseFieldDefinition get(String dtoField) {
    // Check if field is explicitly defined in registry
    if (getFieldDefinitions().containsKey(dtoField)) {
      return getFieldDefinitions().get(dtoField);
    }

    // Handle nested paths (either from annotation or dot notation)
    if (dtoField.contains(".")) {
      return handleNestedPath(dtoField);
    }

    // Handle traditional underscore notation
    if (dtoField.contains("_")) {
      return handleUnderscoreNotation(dtoField);
    }

    // Default case - direct field mapping
    return new BaseFieldDefinition(
        dtoField,
        dtoField,
        false,
        (root, cb, joins) -> root.get(dtoField));
  }

  private BaseFieldDefinition handleNestedPath(String dtoField) {
    return new BaseFieldDefinition(
        dtoField,
        dtoField,
        false,
        (root, cb, joins) -> {
          String[] parts = dtoField.split("\\.");
          Path<?> path = root;
          for (int i = 0; i < parts.length; i++) {
            path = path.get(parts[i]);
          }
          return path;
        });
  }

  private BaseFieldDefinition handleUnderscoreNotation(String dtoField) {
    return new BaseFieldDefinition(
        dtoField,
        dtoField.replace("_", "."),
        false,
        (root, cb, joins) -> {
          String[] parts = dtoField.split("_");
          Join<?, ?> currentJoin = null;
          StringBuilder pathKey = new StringBuilder();

          for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (pathKey.length() > 0) {
              pathKey.append("_").append(part);
            } else {
              pathKey.append(part);
            }

            String key = pathKey.toString();

            if (joins.containsKey(key)) {
              currentJoin = joins.get(key);
            } else {
              currentJoin = (currentJoin == null ? root.join(part, JoinType.LEFT)
                  : currentJoin.join(part, JoinType.LEFT));
              joins.put(key, currentJoin);
            }
          }

          String finalField = parts[parts.length - 1];
          return (currentJoin != null ? currentJoin.get(finalField) : root.get(finalField));
        });
  }
}