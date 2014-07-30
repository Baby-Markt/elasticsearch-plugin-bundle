package org.xbib.elasticsearch.index.analysis.langdetect;

import java.io.IOException;

public class LanguageDetectionException extends IOException {

    private final static long serialVersionUID = -1L;

    public LanguageDetectionException(String message) {
        super(message);
    }

}
