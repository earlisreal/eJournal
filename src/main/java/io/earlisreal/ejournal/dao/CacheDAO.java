package io.earlisreal.ejournal.dao;

import java.util.Optional;

public interface CacheDAO {

    Optional<String> get(String key);

    boolean clear(int secretParam);

    boolean save(String key, String value);

}
