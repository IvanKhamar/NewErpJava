package ua.newerp.domain;

import java.lang.reflect.Field;

/** Sets the id that the database would give an entity. Tests have no database, so reflection does it. */
public final class TestIds {

    private TestIds() {
    }

    public static <T> T withId(T entity, int id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
