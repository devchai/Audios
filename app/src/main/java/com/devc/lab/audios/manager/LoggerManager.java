package com.devc.lab.audios.manager;

import timber.log.Timber;

public class LoggerManager {
    public static void logger(String msg){
        Timber.tag("logger_tag_chai").d(msg);
    }
}
