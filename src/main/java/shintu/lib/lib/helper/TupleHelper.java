package shintu.lib.lib.helper;

import java.util.List;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

public class TupleHelper {
  public static Tuple modifyTupleValue(Tuple original, String fieldName, Object newValue) {
    return new Tuple() {
      @Override
      public Object get(String alias) {
        return alias.equals(fieldName) ? newValue : original.get(alias);
      }

      @Override
      public Object get(int i) {
        String alias = original.getElements().get(i).getAlias();
        return fieldName.equals(alias) ? newValue : original.get(i);
      }

      @Override
      public <X> X get(String alias, Class<X> type) {
        Object value = get(alias);
        return type.cast(value);
      }

      @Override
      public <X> X get(int i, Class<X> type) {
        Object value = get(i);
        return type.cast(value);
      }

      @Override
      public <X> X get(TupleElement<X> tupleElement) {
        String alias = tupleElement.getAlias();
        Object value = get(alias);
        return tupleElement.getJavaType().cast(value);
      }

      @Override
      public Object[] toArray() {
        Object[] array = original.toArray();
        for (int i = 0; i < original.getElements().size(); i++) {
          if (original.getElements().get(i).getAlias().equals(fieldName)) {
            array[i] = newValue;
          }
        }
        return array;
      }

      @Override
      public List<TupleElement<?>> getElements() {
        return original.getElements();
      }
    };
  }
}
