package io.earlisreal.ejournal.util;

public interface CommonUtil {

    static void handleException(Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
    }

}
