/*
 * Copyright (C) 2014 Jörg Prante
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses
 * or write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code
 * versions of this program must display Appropriate Legal Notices,
 * as required under Section 5 of the GNU Affero General Public License.
 *
 */
package org.xbib.elasticsearch.common.langdetect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

public class LangdetectService {

    public final static String[] DEFAULT_LANGUAGES = new String[]{
            // "af",
            "ar",
            "bg",
            "bn",
            "cs",
            "da",
            "de",
            "el",
            "en",
            "es",
            "et",
            "fa",
            "fi",
            "fr",
            "gu",
            "he",
            "hi",
            "hr",
            "hu",
            "id",
            "it",
            "ja",
            // "kn",
            "ko",
            "lt",
            "lv",
            "mk",
            "ml",
            // "mr",
            // "ne",
            "nl",
            "no",
            "pa",
            "pl",
            "pt",
            "ro",
            "ru",
            // "sk",
            //"sl",
            // "so",
            "sq",
            "sv",
            // "sw",
            "ta",
            "te",
            "th",
            "tl",
            "tr",
            "uk",
            "ur",
            "vi",
            "zh-cn",
            "zh-tw"
    };
    private static final Logger logger = LogManager.getLogger(LangdetectService.class.getName());
    private static final Pattern word = Pattern.compile("[\\P{IsWord}]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Settings DEFAULT_SETTINGS = Settings.builder()
            .putArray("languages", DEFAULT_LANGUAGES)
            .build();
    private final Settings settings;
    private Map<String, double[]> wordLangProbMap = new HashMap<>();

    private List<String> langlist = new LinkedList<>();

    private Map<String, String> langmap = new HashMap<>();

    private String profile;

    private double alpha;

    private double alpha_width;

    private int n_trial;

    private double[] priorMap;

    private int iteration_limit;

    private double prob_threshold;

    private double conv_threshold;

    private int base_freq;

    private Pattern filterPattern;

    private boolean isStarted;

    public LangdetectService() {
        this(DEFAULT_SETTINGS);
    }

    public LangdetectService(Settings settings) {
        this(settings, null);
    }

    public LangdetectService(Settings settings, String profile) {
        this.settings = settings;
        this.profile = settings.get("profile", profile);
        load(settings);
        init();
    }

    public Settings getSettings() {
        return settings;
    }

    private void load(Settings settings) {
        if (settings.equals(Settings.EMPTY)) {
            return;
        }
        try {
            String[] keys = DEFAULT_LANGUAGES;
            if (settings.get("languages") != null) {
                keys = settings.get("languages").split(",");
            }
            int index = 0;
            int size = keys.length;
            for (String key : keys) {
                if (key != null && !key.isEmpty()) {
                    loadProfileFromResource(key, index++, size);
                }
            }
            logger.debug("language detection service installed for {}", langlist);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ElasticsearchException(e.getMessage() + " profile=" + profile);
        }
        try {
            // map by settings
            Settings map = Settings.EMPTY;
            if (settings.getByPrefix("map.") != null) {
                map = Settings.builder().put(settings.getByPrefix("map.")).build();
            }
            if (map.getAsMap().isEmpty()) {
                // is in "map" a resource name?
                String s = settings.get("map") != null ?
                        settings.get("map") : this.profile + "language.json";
                InputStream in = getClass().getResourceAsStream(s);
                if (in != null) {
                    map = Settings.builder().loadFromStream(s, in).build();
                }
            }
            this.langmap = map.getAsMap();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ElasticsearchException(e.getMessage());
        }
    }

    private void init() {
        this.priorMap = null;
        this.n_trial = settings.getAsInt("number_of_trials", 7);
        this.alpha = settings.getAsDouble("alpha", 0.5);
        this.alpha_width = settings.getAsDouble("alpha_width", 0.05);
        this.iteration_limit = settings.getAsInt("iteration_limit", 10000);
        this.prob_threshold = settings.getAsDouble("prob_threshold", 0.1);
        this.conv_threshold = settings.getAsDouble("conv_threshold", 0.99999);
        this.base_freq = settings.getAsInt("base_freq", 10000);
        this.filterPattern = settings.get("pattern") != null ?
                Pattern.compile(settings.get("pattern"), Pattern.UNICODE_CHARACTER_CLASS) : null;
        isStarted = true;
    }

    public void loadProfileFromResource(String resource, int index, int langsize) throws IOException {
        String profile = "/langdetect/" + (this.profile != null ? this.profile + "/" : "");
        InputStream in = getClass().getResourceAsStream(profile + resource);
        if (in == null) {
            throw new IOException("profile '" + resource + "' not found");
        }
        LangProfile langProfile = new LangProfile();
        langProfile.read(in);
        addProfile(langProfile, index, langsize);
    }

    public void addProfile(LangProfile profile, int index, int langsize) throws IOException {
        String lang = profile.getName();
        if (langlist.contains(lang)) {
            throw new IOException("duplicate of the same language profile: " + lang);
        }
        langlist.add(lang);
        for (String word : profile.getFreq().keySet()) {
            if (!wordLangProbMap.containsKey(word)) {
                wordLangProbMap.put(word, new double[langsize]);
            }
            int length = word.length();
            if (length >= 1 && length <= 3) {
                double prob = profile.getFreq().get(word).doubleValue() / profile.getNWords().get(length - 1);
                wordLangProbMap.get(word)[index] = prob;
            }
        }
    }

    public String getProfile() {
        return profile;
    }

    public List<Language> detectAll(String text) throws LanguageDetectionException {
        if (!isStarted) {
            load(settings);
            init();
        }
        List<Language> languages = new ArrayList<>();
        if (filterPattern != null && !filterPattern.matcher(text).matches()) {
            return languages;
        }
        List<String> list = new ArrayList<>();
        languages = sortProbability(languages, detectBlock(list, text));
        return languages.subList(0, Math.min(languages.size(), settings.getAsInt("max", languages.size())));
    }

    private double[] detectBlock(List<String> list, String text) throws LanguageDetectionException {
        // clean all non-work characters from text
        text = text.replaceAll(word.pattern(), " ");
        extractNGrams(list, text);
        double[] langprob = new double[langlist.size()];
        if (list.isEmpty()) {
            //throw new LanguageDetectionException("no features in text");
            return langprob;
        }
        Random rand = new Random();
        Long seed = 0L;
        rand.setSeed(seed);
        for (int t = 0; t < n_trial; ++t) {
            double[] prob = initProbability();
            double a = this.alpha + rand.nextGaussian() * alpha_width;
            for (int i = 0; ; ++i) {
                int r = rand.nextInt(list.size());
                updateLangProb(prob, list.get(r), a);
                if (i % 5 == 0) {
                    if (normalizeProb(prob) > conv_threshold || i >= iteration_limit) {
                        break;
                    }
                }
            }
            for (int j = 0; j < langprob.length; ++j) {
                langprob[j] += prob[j] / n_trial;
            }
        }
        return langprob;
    }

    private double[] initProbability() {
        double[] prob = new double[langlist.size()];
        if (priorMap != null) {
            System.arraycopy(priorMap, 0, prob, 0, prob.length);
        } else {
            for (int i = 0; i < prob.length; ++i) {
                prob[i] = 1.0 / langlist.size();
            }
        }
        return prob;
    }

    private void extractNGrams(List<String> list, String text) {
        NGram ngram = new NGram();
        for (int i = 0; i < text.length(); ++i) {
            ngram.addChar(text.charAt(i));
            for (int n = 1; n <= NGram.N_GRAM; ++n) {
                String w = ngram.get(n);
                if (w != null && wordLangProbMap.containsKey(w)) {
                    list.add(w);
                }
            }
        }
    }

    private boolean updateLangProb(double[] prob, String word, double alpha) {
        if (word == null || !wordLangProbMap.containsKey(word)) {
            return false;
        }
        double[] langProbMap = wordLangProbMap.get(word);
        double weight = alpha / base_freq;
        for (int i = 0; i < prob.length; ++i) {
            prob[i] *= weight + langProbMap[i];
        }
        return true;
    }

    private double normalizeProb(double[] prob) {
        double maxp = 0, sump = 0;
        for (double aProb : prob) {
            sump += aProb;
        }
        for (int i = 0; i < prob.length; ++i) {
            double p = prob[i] / sump;
            if (maxp < p) {
                maxp = p;
            }
            prob[i] = p;
        }
        return maxp;
    }

    private List<Language> sortProbability(List<Language> list, double[] prob) {
        for (int j = 0; j < prob.length; ++j) {
            double p = prob[j];
            if (p > prob_threshold) {
                for (int i = 0; i <= list.size(); ++i) {
                    if (i == list.size() || list.get(i).getProbability() < p) {
                        String code = langlist.get(j);
                        if (langmap != null && langmap.containsKey(code)) {
                            code = langmap.get(code);
                        }
                        list.add(i, new Language(code, p));
                        break;
                    }
                }
            }
        }
        return list;
    }
}
