package searchengine.services.search;

import searchengine.services.siteindexing.LemmaParser;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

public class SnippetCreator {
    public static final int SNIPPET_MAX_SIZE = 240;
    public static String getSnippetWithParts(String text, Set<String> lemmasSet, String snippet) throws IOException {
        if (text == null || lemmasSet == null || snippet == null) {
            return  "";
        }

        final int SNIPPET_PART = SNIPPET_MAX_SIZE / 3;
        int snippetLength = snippet.length();
        StringBuilder snippetBuilder = new StringBuilder(snippet);
        int startIndex = 0;
        int textLength = text.length();
        int endIndex = textLength <= SNIPPET_PART ? textLength : getTextPartIndex(text, SNIPPET_PART);

        Set<String> matchingWords = LemmaParser.getInstance().getWordsByLemmas(text, lemmasSet);

        while(snippetLength < SNIPPET_MAX_SIZE && endIndex <= textLength) {
            String snippetPart = createSnippetPartByLemmas(text.substring(startIndex, endIndex), matchingWords);
            if (!snippetPart.isEmpty()) {
                snippetBuilder.append(snippetBuilder.isEmpty() ? "" : "...")
                        .append(snippetPart);
            }

            if (endIndex >= textLength) {
                break;
            }

            snippetLength = snippetBuilder.length();
            startIndex = endIndex + 1;
            endIndex = textLength <= endIndex + SNIPPET_PART ? textLength
                    : getTextPartIndex(text, endIndex + SNIPPET_PART);
        }

        return snippetBuilder.toString();
    }

    public static String getSnippetFullText(String text, Set<String> lemmasSet) throws IOException {
        if (text == null || lemmasSet == null) {
            return  "";
        }
        Set<String> matchingWords = LemmaParser.getInstance().getWordsByLemmas(text, lemmasSet);
        return createSnippetPartByLemmas(text, matchingWords);
    }

    private static String createSnippetPartByLemmas(String text, Set<String> matchingWords) {
        String snippet = "";
        if (!matchingWords.isEmpty()) {
            snippet = designSnippetText(text, matchingWords);
        }
        return snippet;
    }

    private static String designSnippetText(String text, Set<String> matchingWords) {
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

    private static int getTextPartIndex(String text, int startIndex) {
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

    public static boolean replaceBuilderText(StringBuilder stringBuilder, String oldText, String newText) {
        boolean result = false;
        int index = stringBuilder.indexOf(oldText);
        while (index != -1) {
            int endIndex = index + oldText.length();
            if ((index == 0 || !Character.isLetterOrDigit(stringBuilder.charAt(index - 1))) &&
                    (endIndex == stringBuilder.length()
                            || !Character.isLetterOrDigit(stringBuilder.charAt(endIndex)))) {
                stringBuilder.replace(index, endIndex, newText);
                result = true;
            }
            index = stringBuilder.indexOf(oldText, index + newText.length());
        }
        return result;
    }
}
