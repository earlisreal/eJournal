package io.earlisreal.ejournal.dao;

import java.util.Optional;

public interface CacheDAO {

    Optional<String> get(String key);

    boolean insert(String key, String value);

    boolean clear(int secretParam);

    boolean update(String s, String toString);
    
}
