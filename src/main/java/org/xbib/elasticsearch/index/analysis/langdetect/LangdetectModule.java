package org.xbib.elasticsearch.index.analysis.langdetect;

import org.elasticsearch.common.inject.Binder;
import org.elasticsearch.common.inject.Module;

public class LangdetectModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(RegisterLangdetectType.class).asEagerSingleton();
    }
}
