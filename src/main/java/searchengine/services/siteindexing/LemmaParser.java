package searchengine.services.siteindexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
public class LemmaParser {
    private final LuceneMorphology luceneMorphologyRu;
    private final LuceneMorphology luceneMorphologyEn;

    public static LemmaParser getInstance() throws IOException {
        LuceneMorphology morphologyRu= new RussianLuceneMorphology();
        LuceneMorphology morphologyEn= new EnglishLuceneMorphology();
        return new LemmaParser(morphologyRu, morphologyEn);
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] wordsRu = arrayContainsWords(text, LanguagePropertyEnum.RU);
        String[] wordsEn = arrayContainsWords(text, LanguagePropertyEnum.EN);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : wordsRu) {
            findLemma(word, LanguagePropertyEnum.RU, lemmas);
        }

        for (String word : wordsEn) {
            findLemma(word, LanguagePropertyEnum.EN, lemmas);
        }

        return lemmas;
    }

    public Set<String> getLemmaSet(String text) {
        String[] textArrayRu = arrayContainsWords(text, LanguagePropertyEnum.RU);
        String[] textArrayEn = arrayContainsWords(text, LanguagePropertyEnum.EN);
        Set<String> lemmaSet = new HashSet<>();

        for (String word : textArrayRu) {
            findLemmaToSet(word, LanguagePropertyEnum.RU, lemmaSet);
        }

        for (String word : textArrayEn) {
            findLemmaToSet(word, LanguagePropertyEnum.EN, lemmaSet);
        }

        return lemmaSet;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms, LanguagePropertyEnum language) {
        return wordBaseForms.stream().anyMatch(w -> hasParticleProperty(w, language));
    }

    private boolean hasParticleProperty(String wordBase, LanguagePropertyEnum language) {
        for (String property : language.getParticlesNames()) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsWords(String text, LanguagePropertyEnum language) {
        Locale locale = language == LanguagePropertyEnum.RU ?
                Locale.forLanguageTag("ru-RU") : Locale.forLanguageTag("en");

        return text.toLowerCase(locale)
                .replaceAll(language.getWordReplaceRegexp(), " ")
                .trim()
                .split("\\s+");
    }

    private boolean isCorrectWordForm(String word, LuceneMorphology luceneMorphology
            , LanguagePropertyEnum languagePropertyEnum) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(languagePropertyEnum.getWordTypeRegex())) {
                return false;
            }
        }
        return true;
    }

    private void findLemma(String word, LanguagePropertyEnum language
            , HashMap<String, Integer> lemmas) {
        LuceneMorphology luceneMorphology = language == LanguagePropertyEnum.RU ?
                luceneMorphologyRu : luceneMorphologyEn;

        if (word.isBlank()) {
            return;
        }

        List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
        if (anyWordBaseBelongToParticle(wordBaseForms, language)) {
            return;
        }

        List<String> normalForms = luceneMorphology.getNormalForms(word);
        if (normalForms.isEmpty()) {
            return;
        }

        String normalWord = normalForms.get(0);

        if (lemmas.containsKey(normalWord)) {
            lemmas.put(normalWord, lemmas.get(normalWord) + 1);
        } else {
            lemmas.put(normalWord, 1);
        }
    }

    private void findLemmaToSet(String word, LanguagePropertyEnum language
            , Set<String> lemmaSet) {
        LuceneMorphology luceneMorphology = language == LanguagePropertyEnum.RU ?
                luceneMorphologyRu : luceneMorphologyEn;

        if (!word.isEmpty() && isCorrectWordForm(word, luceneMorphology, language)) {
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms, language)) {
                return;
            }

            lemmaSet.addAll(luceneMorphology.getNormalForms(word));
        }
    }

    public Set<String> getWordsByLemmas(String text, Set<String> lemmas) {
        Set<String> matchingWords = new LinkedHashSet<>();

        if (text == null || text.isEmpty() ||
                lemmas == null || lemmas.isEmpty()) {
            return matchingWords;
        }

        List<WordProperty> words = getWordsPropertyList(text);

        Set<String> ruLemmas = new HashSet<>();
        Set<String> enLemmas = new HashSet<>();
        LanguagePropertyEnum languagePropertyEnumRu = LanguagePropertyEnum.RU;
        LanguagePropertyEnum languagePropertyEnumEn = LanguagePropertyEnum.EN;

        lemmas.forEach(l -> {
            if (isWordEqualLanguage(l, Locale.forLanguageTag("ru-RU"), languagePropertyEnumRu)) {
                ruLemmas.add(l);
            }
            if (isWordEqualLanguage(l, Locale.forLanguageTag("en"), languagePropertyEnumEn)) {
                enLemmas.add(l);
            }
        });

        for (WordProperty wordProperty : words) {
            boolean isRuWord = wordProperty.language == LanguagePropertyEnum.RU;
            Set<String> langLemmas = isRuWord ? ruLemmas : enLemmas;
            addMatchingWord(matchingWords, wordProperty, langLemmas);
        }

        return matchingWords;
    }

    private void addMatchingWord(Set<String> matchingWords, WordProperty wordProperty, Set<String> langLemmas) {
        try {
            Set<String> currentWordLemmas = new HashSet<>();

            findLemmaToSet(wordProperty.word.toLowerCase(wordProperty.local)
                    , wordProperty.language, currentWordLemmas);

            if (langLemmas.stream().anyMatch(currentWordLemmas::contains)) {
                matchingWords.add(wordProperty.word);
            }
        } catch (WrongCharaterException e) {
        }
    }

    private List<WordProperty> getWordsPropertyList(String text) {
        String regexp = "([^а-яёА-ЯЁa-zA-Z\\s])";
        List<WordProperty> wordProperties = new ArrayList<>();
        String[] words = text.replaceAll(regexp, " ")
                .trim()
                .split("\\s+");

        for (String word : words) {
            LanguagePropertyEnum language = LanguagePropertyEnum.RU;
            Locale locale = Locale.forLanguageTag("ru-RU");

            if (!isWordEqualLanguage(word, locale, language)) {
                language = LanguagePropertyEnum.EN;
                locale = Locale.forLanguageTag("en");
            }

            wordProperties.add(new WordProperty(word, locale, language));
        }

        return wordProperties;
    }

    private boolean isWordEqualLanguage(String word, Locale locale, LanguagePropertyEnum language) {
        return !word.toLowerCase(locale)
                .replaceAll(language.getWordReplaceRegexp(), "")
                .isEmpty();
    }

    @Getter
    @RequiredArgsConstructor
    public enum LanguagePropertyEnum {
        RU("\\W\\w&&[^а-яёА-ЯЁ\\s]", "([^а-яё\\s])"
                , new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"})
        ,EN("\\W\\w&&[^a-zA-Z\\s]", "([^a-z\\s])"
                , new String[]{"PREP", "ARTICLE", "CONJ"});

        private final String wordTypeRegex;
        private final String wordReplaceRegexp;
        private final String[] particlesNames;
    }

    private record WordProperty(String word, Locale local, LanguagePropertyEnum language) {
    }
}
