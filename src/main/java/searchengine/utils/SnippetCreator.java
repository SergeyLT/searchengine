package searchengine.utils;

import lombok.RequiredArgsConstructor;
import searchengine.config.SearchResultSettings;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SnippetCreator {
    private final SearchResultSettings searchResultSettings;

    public String getSnippetWithParts(String text, Set<String> lemmasSet, String snippet) throws IOException {
        if (text == null || lemmasSet == null || snippet == null) {
            return  "";
        }

        SnippetPreparatoryBuilder preparatoryBuilder = new SnippetPreparatoryBuilder(text, lemmasSet, snippet);
        StringBuilder snippetBuilder = new StringBuilder(snippet);

        fillSnippetStringBuilderToMaxLength(text, snippetBuilder, preparatoryBuilder);

        for (int i = preparatoryBuilder.searchWordIndex; i < preparatoryBuilder.searchWords.size(); i++) {
            snippetBuilder.append(i == preparatoryBuilder.searchWordIndex ? "..." : ", ")
                    .append("<b>" + preparatoryBuilder.searchWords.get(i) + "</b>");
        }

        return snippetBuilder.toString();
    }

    private void fillSnippetStringBuilderToMaxLength(String text, StringBuilder snippetBuilder
    ,SnippetPreparatoryBuilder preparatoryBuilder) {
        int textLength = text.length();
        int startIndex = preparatoryBuilder.startIndex;
        int endIndex =preparatoryBuilder.endIndex;

        while(preparatoryBuilder.snippetLength < searchResultSettings.getSnippetMaxSize() && endIndex <= textLength
                && preparatoryBuilder.searchWordIndex < preparatoryBuilder.searchWords.size()) {
            addSnippetPartToStringBuilder(text.substring(startIndex, endIndex), snippetBuilder, preparatoryBuilder);

            if (endIndex >= textLength || preparatoryBuilder.searchWordIndex >= preparatoryBuilder.searchWords.size()) {
                break;
            }

            preparatoryBuilder.snippetLength = snippetBuilder.length();

            startIndex = getSearchedWordStartIndex(text.substring(endIndex + 1,textLength)
                    , preparatoryBuilder.searchWords.get(preparatoryBuilder.searchWordIndex));
            while (startIndex == -1 && preparatoryBuilder.searchWordIndex < preparatoryBuilder.searchWords.size() -1) {
                snippetBuilder.append("...<b>"
                        + preparatoryBuilder.searchWords.get(preparatoryBuilder.searchWordIndex) + "</b>...");
                preparatoryBuilder.searchWordIndex++;
                startIndex = getSearchedWordStartIndex(text.substring(endIndex + 1,textLength)
                        , preparatoryBuilder.searchWords.get(preparatoryBuilder.searchWordIndex));
            }

            if (startIndex == -1) {
                break;
            }

            int snippetPartSize = searchResultSettings.getSnippetPartSize();
            startIndex += endIndex + 1;
            endIndex = textLength <= startIndex + snippetPartSize ? textLength
                    : getTextPartIndex(text.substring(startIndex,textLength), snippetPartSize) + startIndex;
        }
    }

    private void addSnippetPartToStringBuilder(String text, StringBuilder snippetBuilder
            ,SnippetPreparatoryBuilder preparatoryBuilder){
        String snippetPart = createSnippetPartByLemmas(text, preparatoryBuilder.allWords);
        if (!snippetPart.isEmpty()) {
            snippetBuilder.append(snippetBuilder.isEmpty() ? "" : "...")
                    .append(snippetPart);
        }
        preparatoryBuilder.searchWordIndex++;
    }

    private int getSearchedWordStartIndex(String text, String searchWord) {
        int startIndex = text.indexOf(searchWord);
        while (startIndex != -1 && !isFullWord(text, searchWord, startIndex)) {
            int textStartIndex = startIndex + searchWord.length();
            startIndex = text.substring(textStartIndex).indexOf(searchWord) + textStartIndex;
        }
        return startIndex;
    }

    private boolean isFullWord(String text, String searchWord,int startIndex) {
        boolean separateWordBefore = startIndex == 0? true : isSeparateChar(text, startIndex-1, startIndex);
        int textStartIndex = startIndex + searchWord.length();
        boolean separateWordAfter = startIndex+searchWord.length() == text.length() - 1? true
                : isSeparateChar(text, textStartIndex, textStartIndex + 1);
        return separateWordBefore && separateWordAfter;
    }

    private boolean isSeparateChar(String text, int startIndex, int endIndex) {
        return Pattern.compile("[^a-zA-Zа-яА-ЯёЁ]").matcher(text.substring(startIndex, endIndex)).matches();
    }

    private String createSnippetPartByLemmas(String text, Set<String> matchingWords) {
        String snippet = "";
        if (!matchingWords.isEmpty()) {
            snippet = designSnippetText(text, matchingWords);
        }
        return snippet;
    }

    private String designSnippetText(String text, Set<String> matchingWords) {
        if (text == null || matchingWords == null || text.isEmpty()) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder(text);
        boolean isFoundWords = false;
        for (String matchingWord : matchingWords) {
            isFoundWords = replaceBuilderText(textBuilder, matchingWord, "<b>" + matchingWord + "</b>")
                    || isFoundWords;
        }

        return isFoundWords ? textBuilder.toString() : "";
    }

    private int getTextPartIndex(String text, int startIndex) {
        if (text == null) {
            return 0;
        }

        int textLength =  text.length();
        if (textLength == 0 || startIndex >= textLength) {
            return textLength;
        }

        int endIndex = startIndex;
        for (; endIndex < textLength; endIndex++) {
            if (Pattern.compile("\\s+").matcher(text.substring(endIndex, endIndex+1)).matches()) {
                return endIndex;
            }
            endIndex++;
        }

        return endIndex;
    }

    public boolean replaceBuilderText(StringBuilder stringBuilder, String oldText, String newText) {
        boolean result = false;
        int index = stringBuilder.indexOf(oldText);
        while (index != -1) {
            int endIndex = index + oldText.length();
            boolean isStartWordIndex = index == 0 || !Character.isLetterOrDigit(stringBuilder.charAt(index - 1));
            boolean isEndWordIndex = endIndex == stringBuilder.length()
                    || !Character.isLetterOrDigit(stringBuilder.charAt(endIndex));
            if (isStartWordIndex && isEndWordIndex) {
                stringBuilder.replace(index, endIndex, newText);
                result = true;
            }
            index = stringBuilder.indexOf(oldText, index + newText.length());
        }
        return result;
    }

    private class SnippetPreparatoryBuilder {
        private int snippetLength;
        private int startIndex;
        private int endIndex;
        private int searchWordIndex;
        private List<String> searchWords;
        private Set<String> allWords;

        public SnippetPreparatoryBuilder(String text, Set<String> lemmasSet, String snippet) throws IOException {
            snippetLength = snippet.length();
            int searchWordIndex = 0;
            int textLength = text.length();

            Map<String,Set<String>> matchingWords = LemmaParser.getInstance().getWordsByLemmas(text, lemmasSet);
            allWords = matchingWords.values().stream().flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            searchWords = new ArrayList<>();
            for (Set<String> set : matchingWords.values()) {
                if (!set.isEmpty()) {
                    searchWords.add(set.iterator().next());
                }
            }

            int snippetPartSize = searchResultSettings.getSnippetPartSize();
            startIndex = getSearchedWordStartIndex(text, searchWords.get(searchWordIndex));
            endIndex = textLength <= snippetPartSize ? textLength
                    : getTextPartIndex(text.substring(startIndex,textLength), snippetPartSize) + startIndex;
        }
    }
}
