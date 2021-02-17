package io.earlisreal.ejournal.dao;

public interface CacheDAO {

    String get(String key);

    boolean insert(String key, String value);

}
