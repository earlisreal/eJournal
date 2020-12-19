package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.EmailLastSync;

public interface EmailLastSyncDAO {

    EmailLastSync query(String email);

    boolean insert(EmailLastSync emailLastSync);

}
